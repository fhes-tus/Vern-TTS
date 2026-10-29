import os
import sys
import time
import json
import random
import re
import html
import urllib.request
import urllib.parse
import urllib.error

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "")
REPO = os.environ.get("GITHUB_REPOSITORY", "fhes-tus/Vern-TTS")
WEBSITE_URL = "https://fhes-tus.github.io/Vern-TTS/"
CHANNEL_URL = "https://t.me/myreader_veritas"
TWITTER_URL = "https://x.com/_1st2us"
EMAIL_ADDRESS = "myreader.veritas@gmail.com"
GITHUB_URL = f"https://github.com/{REPO}"

with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "telegram_content.json"), encoding="utf-8") as content_file:
    CONTENT = json.load(content_file)

# In-memory release cache to prevent hitting GitHub API rate limits
_cached_release = None
_cached_release_time = 0
_seen_media_groups = set()

def strip_html_tags(text):
    """Strip HTML tags for fallback plain text sending."""
    return re.sub(r'<[^>]+>', '', text)

def markdown_to_telegram_html(md_text, max_bullets=14):
    """Clean markdown release notes and convert markdown annotations to Telegram HTML."""
    if not md_text:
        return ""

    lines = md_text.splitlines()
    clean_lines = []
    bullet_count = 0
    in_downloads = False

    for raw_line in lines:
        line = raw_line.strip()
        if not line:
            if clean_lines and clean_lines[-1] != "":
                clean_lines.append("")
            continue

        # Ignore horizontal rules
        if re.match(r'^(-{3,}|_{3,}|\*{3,})$', line):
            continue

        # Headers: #, ##, ###
        header_match = re.match(r'^#{1,6}\s*(.*)', line)
        if header_match:
            title = header_match.group(1).strip()
            lower_title = title.lower()
            if any(w in lower_title for w in ['download', 'artifact', 'verification']):
                in_downloads = True
                continue
            if in_downloads:
                continue

            # Skip top title header if it duplicates version/announcement title
            if "build" in lower_title or lower_title.startswith("vern"):
                continue

            title_escaped = html.escape(title)
            title_clean = re.sub(r'\*\*(.*?)\*\*', r'<b>\1</b>', title_escaped)
            clean_lines.append(f"\n<b>{title_clean}</b>")
            continue

        if in_downloads:
            continue

        lower = line.lower()
        if any(w in lower for w in ['sha-256', 'sha256', 'checksum', 'verification summary', 'compiledebug', 'compilerelease']):
            continue
        if re.search(r'[a-f0-9]{64}', line, re.I):
            continue

        # Bullets: * or -
        bullet_match = re.match(r'^[\*\-]\s+(.*)', line)
        if bullet_match:
            bullet_count += 1
            if max_bullets and bullet_count > max_bullets:
                continue
            content = bullet_match.group(1).strip()
            content = html.escape(content)
            # Markdown bold **text** -> HTML <b>text</b>
            content = re.sub(r'\*\*(.*?)\*\*', r'<b>\1</b>', content)
            # Markdown italic *text* or _text_ -> HTML <i>text</i>
            content = re.sub(r'(?<!\w)\*([^\*]+)\*(?!\w)', r'<i>\1</i>', content)
            # Markdown inline code `code` -> HTML <code>code</code>
            content = re.sub(r'`([^`]+)`', r'<code>\1</code>', content)
            # Markdown link [text](url) -> HTML <a href="\2">\1</a>
            content = re.sub(r'\[([^\]]+)\]\((https?://[^\s\)]+)\)', r'<a href="\2">\1</a>', content)
            clean_lines.append(f"• {content}")
            continue

        # Standard paragraph line
        escaped = html.escape(line)
        escaped = re.sub(r'\*\*(.*?)\*\*', r'<b>\1</b>', escaped)
        escaped = re.sub(r'(?<!\w)\*([^\*]+)\*(?!\w)', r'<i>\1</i>', escaped)
        escaped = re.sub(r'`([^`]+)`', r'<code>\1</code>', escaped)
        escaped = re.sub(r'\[([^\]]+)\]\((https?://[^\s\)]+)\)', r'<a href="\2">\1</a>', escaped)
        clean_lines.append(escaped)

    if max_bullets and bullet_count > max_bullets:
        clean_lines.append(f"\n• <i>...and {bullet_count - max_bullets} more enhancements!</i>")

    result = "\n".join(clean_lines).strip()
    result = re.sub(r'\n{3,}', '\n\n', result)
    return result

def api_call(method, payload=None):
    if not TOKEN:
        raise RuntimeError("Set TELEGRAM_BOT_TOKEN in the environment.")
    url = f"https://api.telegram.org/bot{TOKEN}/{method}"
    data = json.dumps(payload).encode("utf-8") if payload else None
    for attempt in range(3):
        req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=40) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            if error.code == 429 and attempt < 2:
                try:
                    delay = int(json.loads(error.read()).get("parameters", {}).get("retry_after", 3))
                except (ValueError, TypeError):
                    delay = 3
                time.sleep(max(1, min(delay, 60)))
                continue
            print(f"Telegram API {method}: HTTP {error.code}")
            return None
        except Exception as error:
            print(f"Telegram API {method}: {type(error).__name__}")
            return None
    return None


def send_message(chat_id, text, reply_markup=None):
    if len(text) > 4000:
        text = text[:3980] + "\n\n<i>...[truncated]</i>"
    payload = {
        "chat_id": chat_id,
        "text": text,
        "parse_mode": "HTML",
        "disable_web_page_preview": True
    }
    if reply_markup:
        payload["reply_markup"] = reply_markup
    res = api_call("sendMessage", payload)
    if res and res.get("ok"):
        return res
    # Automatic fallback: if HTML parse fails, send clean plain text
    print("Retrying sendMessage without parse_mode (plain text fallback)...")
    payload["parse_mode"] = None
    payload["text"] = strip_html_tags(text)
    return api_call("sendMessage", payload)

def edit_message(chat_id, message_id, text, reply_markup=None):
    if len(text) > 4000:
        text = text[:3980] + "\n\n<i>...[truncated]</i>"
    payload = {
        "chat_id": chat_id,
        "message_id": message_id,
        "text": text,
        "parse_mode": "HTML",
        "disable_web_page_preview": True
    }
    if reply_markup:
        payload["reply_markup"] = reply_markup
    res = api_call("editMessageText", payload)
    if res and res.get("ok"):
        return res
    # Automatic fallback
    print("Retrying editMessageText without parse_mode...")
    payload["parse_mode"] = None
    payload["text"] = strip_html_tags(text)
    return api_call("editMessageText", payload)

def answer_callback(cb_id, text=""):
    return api_call("answerCallbackQuery", {"callback_query_id": cb_id, "text": text})

def get_latest_release():
    """Fetch the latest release info from GitHub API with a 15-minute cache."""
    global _cached_release, _cached_release_time
    now = time.time()
    if _cached_release and (now - _cached_release_time < 900):
        return _cached_release

    url = f"https://api.github.com/repos/{REPO}/releases/latest"
    req = urllib.request.Request(url, headers={"User-Agent": "Vern-Telegram-Bot"})
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            tag = data.get("tag_name", "v2.6.1")
            name = data.get("name", f"Vern TTS {tag}")
            body = data.get("body", "")
            assets = [a for a in data.get("assets", []) if a.get("name", "").endswith(".apk")
                      and a.get("browser_download_url", "").startswith(f"{GITHUB_URL}/releases/download/")]
            def asset(marker):
                return next((a["browser_download_url"] for a in assets if marker in a["name"]), f"{GITHUB_URL}/releases/latest")
            _cached_release = {
                "tag": tag,
                "name": name,
                "body": body,
                "arm64_url": asset("arm64"),
                "universal_url": asset("universal"),
                "arm32_url": asset("armeabi")
            }
            _cached_release_time = now
            return _cached_release
    except Exception as e:
        print(f"Warning: could not fetch release from GitHub API ({e})")
        if _cached_release:
            return _cached_release
        return {"tag":"Latest published release", "name":"Vern", "body":"",
            "arm64_url":GITHUB_URL + "/releases/latest", "universal_url":GITHUB_URL + "/releases/latest",
            "arm32_url":GITHUB_URL + "/releases/latest"}
def load_classics():
    """Load curated classic literature catalog."""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    candidates = [
        os.path.join(script_dir, "classics_data.json"),
        os.path.join(script_dir, "scripts", "classics_data.json"),
        os.path.join(os.getcwd(), "scripts", "classics_data.json"),
        os.path.join(os.getcwd(), "classics_data.json"),
    ]
    for p in candidates:
        if os.path.exists(p):
            try:
                with open(p, "r", encoding="utf-8") as f:
                    return json.load(f)
            except Exception as e:
                print(f"Error loading {p}: {e}")
    return []

def get_changelog_content():
    return CONTENT["release"], {"inline_keyboard": [[{"text": "Get it on Google Play", "url": "https://play.google.com/store/apps/details?id=com.veritas.reader"}], [{"text": "Full release notes", "url": "https://github.com/fhes-tus/Vern-TTS/blob/main/RELEASE_NOTES_v2.7.0.md"}], [{"text": "Main menu", "callback_data": "cmd_menu"}]]}


def get_welcome_content():
    return CONTENT["welcome"], {"inline_keyboard": [[{"text": "Get it on Google Play", "url": "https://play.google.com/store/apps/details?id=com.veritas.reader"}], [{"text": "What's new in 2.7.0", "callback_data": "cmd_changelog"}, {"text": "Explore classics", "callback_data": "cmd_classics"}], [{"text": "Features", "callback_data": "cmd_features"}, {"text": "Voices and listening", "callback_data": "cmd_guide_voice"}], [{"text": "Help / FAQs", "callback_data": "cmd_faq"}, {"text": "Telegram channel", "url": "https://t.me/myreader_veritas"}], [{"text": "Website", "url": "https://fhes-tus.github.io/Vern-TTS/"}]]}


def get_download_content():
    rel = get_latest_release()
    text = CONTENT["download"] + "\n\nLatest GitHub APK release: <b>" + html.escape(strip_html_tags(rel["tag"])) + "</b>"
    markup = {"inline_keyboard": [[{"text": "Get it on Google Play", "url": "https://play.google.com/store/apps/details?id=com.veritas.reader"}], [{"text": "Published GitHub APKs", "url": "https://github.com/fhes-tus/Vern-TTS/releases/latest"}], [{"text": "What's new", "callback_data": "cmd_changelog"}, {"text": "Support", "callback_data": "cmd_contact"}], [{"text": "Main menu", "callback_data": "cmd_menu"}]]}
    for key, label in [("universal_url", "Universal APK"), ("arm64_url", "ARM64 APK")]:
        if rel[key] != GITHUB_URL + "/releases/latest":
            markup["inline_keyboard"].insert(2, [{"text": label + " (" + strip_html_tags(rel["tag"]) + ")", "url": rel[key]}])
            break
    return text, markup


def get_features_menu():
    return CONTENT["features_intro"], {"inline_keyboard": [[{"text": "Reading and files", "callback_data": "feat_docs"}, {"text": "Voices and listening", "callback_data": "feat_speech"}], [{"text": "Notes and voice memos", "callback_data": "feat_memos"}, {"text": "Flashcards and quizzes", "callback_data": "feat_study"}], [{"text": "Page colours and text", "callback_data": "feat_tones"}, {"text": "Reading habits", "callback_data": "feat_insights"}], [{"text": "Page turns and scrolling", "callback_data": "feat_modes"}, {"text": "Privacy", "callback_data": "feat_privacy"}], [{"text": "Main menu", "callback_data": "cmd_menu"}]]}


def get_feature_detail(topic):
    detail = CONTENT["features"].get(topic, CONTENT["features"]["privacy"])
    return "<b>" + html.escape(detail["title"]) + "</b>\n\n" + detail["body"], {"inline_keyboard": [[{"text":"Back to features","callback_data":"cmd_features"}, {"text": "Main menu", "callback_data": "cmd_menu"}]]}


def get_guide_content():
    return CONTENT["voice"], {"inline_keyboard": [[{"text": "Main menu", "callback_data": "cmd_menu"}]]}


def get_random_book_content():
    books = load_classics()
    if not books:
        return "📚 <b>No classics catalog found.</b>", {"inline_keyboard": [[{"text": "⬅️ Back to Menu", "callback_data": "cmd_menu"}]]}

    book = random.choice(books)
    title = html.escape(book.get("title", ""))
    author = html.escape(book.get("author", ""))
    genre = html.escape(book.get("genre", ""))
    quote = html.escape(book.get("quote", ""))
    desc = html.escape(book.get("description", ""))

    text = (
        f"🎲 <b>Classic Spotlight</b>\n\n"
        f"📖 <b>{title}</b>\n"
        f"<i>by {author}</i>\n\n"
        f"🏷 <b>Category:</b> {genre}\n"
    )
    if quote:
        text += f"💬 <b>Notable Quote:</b>\n<i>\"{quote}\"</i>\n\n"
    text += (
        f"📝 <b>Synopsis:</b>\n{desc}\n\n"
        f"✨ <i>Pre-loaded inside Vern TTS with authentic first-edition cover art!</i>"
    )
    markup = {
        "inline_keyboard": [
            [{"text": "🎲 Another Random Classic", "callback_data": "cmd_random_book"}],
            [{"text": "📚 Browse Categories", "callback_data": "cmd_classics"}, {"text": "⬇️ Download App", "callback_data": "cmd_download"}],
            [{"text": "⬅️ Back to Main Menu", "callback_data": "cmd_menu"}]
        ]
    }
    return text, markup

def get_classics_menu():
    text = (
        "📚 <b>35+ Curated Public Domain Classics</b>\n\n"
        "Vern TTS comes with a built-in library of timeless literary works, each pre-loaded with "
        "<b>authentic, published first-edition cover art</b>, memorable quotes, and clean typography.\n\n"
        "<i>Choose a genre below to explore titles or roll for a random book:</i>"
    )
    markup = {
        "inline_keyboard": [
            [{"text": "🎲 Random Book Spotlight", "callback_data": "cmd_random_book"}],
            [{"text": "👨‍👩‍👧 Family & Youth (6)", "callback_data": "cls_cat_Family & Youth"}, {"text": "🔍 Mystery (4)", "callback_data": "cls_cat_Mystery"}],
            [{"text": "🗺 Adventure (5)", "callback_data": "cls_cat_Adventure"}, {"text": "📜 Epic Classics (6)", "callback_data": "cls_cat_Epic Classics"}],
            [{"text": "💡 Life & Habits (6)", "callback_data": "cls_cat_Life & Habits"}, {"text": "⚡️ Quick Reads (4)", "callback_data": "cls_cat_Quick Reads"}],
            [{"text": "❤️ Romance & Drama (4)", "callback_data": "cls_cat_Romance & Drama"}],
            [{"text": "⬇️ Download App to Read", "callback_data": "cmd_download"}],
            [{"text": "⬅️ Back to Main Menu", "callback_data": "cmd_menu"}]
        ]
    }
    return text, markup

def get_classics_category_content(category):
    books = load_classics()
    cat_books = [b for b in books if b.get("genre") == category or b.get("category") == category]

    text = f"🏷 <b>Category: {html.escape(category)}</b> ({len(cat_books)} titles)\n\n"
    for i, b in enumerate(cat_books, 1):
        t = html.escape(b.get("title", ""))
        a = html.escape(b.get("author", ""))
        d = html.escape(b.get("description", ""))
        text += f"<b>{i}. {t}</b>\n<i>by {a}</i>\n{d}\n\n"

    text += "✨ <i>All 35+ titles are available inside Vern TTS with 1-tap download and zero cloud accounts.</i>"
    markup = {
        "inline_keyboard": [
            [{"text": "🎲 Random Classic", "callback_data": "cmd_random_book"}, {"text": "📚 All Categories", "callback_data": "cmd_classics"}],
            [{"text": "⬇️ Download App to Read", "callback_data": "cmd_download"}],
            [{"text": "⬅️ Back to Main Menu", "callback_data": "cmd_menu"}]
        ]
    }
    return text, markup

def search_classics(query):
    books = load_classics()
    query_clean = query.lower().strip()
    matches = []
    for b in books:
        if (query_clean in b.get("title", "").lower() or
            query_clean in b.get("author", "").lower() or
            query_clean in b.get("description", "").lower() or
            query_clean in b.get("genre", "").lower()):
            matches.append(b)

    if not matches:
        text = (
            f"🔍 <b>No matching classics found for \"{html.escape(query)}\"</b>\n\n"
            "Try searching by author (e.g. <code>Austen</code>, <code>Doyle</code>, <code>Shelley</code>, <code>Marcus</code>) "
            "or explore curated categories:"
        )
        markup = {
            "inline_keyboard": [
                [{"text": "📚 Browse All Categories", "callback_data": "cmd_classics"}],
                [{"text": "🎲 Random Book", "callback_data": "cmd_random_book"}]
            ]
        }
        return text, markup

    text = f"🔍 <b>Found {len(matches)} Classic(s) for \"{html.escape(query)}\":</b>\n\n"
    for i, b in enumerate(matches[:6], 1):
        t = html.escape(b.get("title", ""))
        a = html.escape(b.get("author", ""))
        g = html.escape(b.get("genre", ""))
        q = html.escape(b.get("quote", ""))
        text += f"<b>{i}. {t}</b> — <i>{a}</i> ({g})\n"
        if q:
            text += f"💬 <i>\"{q}\"</i>\n"
        text += "\n"

    if len(matches) > 6:
        text += f"<i>...and {len(matches) - 6} more titles.</i>\n\n"

    text += "📥 <i>Read these titles inside Vern TTS!</i>"
    markup = {
        "inline_keyboard": [
            [{"text": "📚 Browse Categories", "callback_data": "cmd_classics"}, {"text": "⬇️ Download App", "callback_data": "cmd_download"}],
            [{"text": "⬅️ Back to Main Menu", "callback_data": "cmd_menu"}]
        ]
    }
    return text, markup

def get_contact_content():
    return "<b>Stay in touch.</b>\n\nFollow @myreader_veritas for updates and reading ideas.\n\nNeed help? Email myreader.veritas@gmail.com. For a bug report, include your Android version, document format, and what happened.", {"inline_keyboard":[[{"text":"Telegram channel","url":"https://t.me/myreader_veritas"},{"text":"Website","url":"https://fhes-tus.github.io/Vern-TTS/"}],[{"text":"Source code","url":"https://github.com/fhes-tus/Vern-TTS"}],[{"text":"Main menu","callback_data":"cmd_menu"}]]}


def get_faq_menu():
    return '<b>A few useful answers.</b>\n\nChoose a topic below.', {"inline_keyboard":[[{"text":"Voices and offline listening","callback_data":"faq_voice"}],[{"text":"Reading and files","callback_data":"faq_reading"},{"text":"Notes and studying","callback_data":"faq_study"}],[{"text":"Privacy","callback_data":"faq_security"},{"text":"Updates and backups","callback_data":"faq_updates"}],[{"text":"Main menu","callback_data":"cmd_menu"}]]}


def get_faq_detail(topic):
    legacy = {"audio":"voice", "formats":"reading", "display":"reading"}
    detail = CONTENT["faqs"].get(legacy.get(topic, topic), CONTENT["faqs"]["reading"])
    return "<b>" + html.escape(detail["title"]) + "</b>\n\n" + detail["body"], {"inline_keyboard": [[{"text":"Back to FAQs","callback_data":"cmd_faq"}, {"text": "Main menu", "callback_data": "cmd_menu"}]]}


def setup_bot_commands():
    """Register rich command menu in Telegram for autocompletion."""
    commands = CONTENT["commands"]
    res = api_call("setMyCommands", {"commands": commands})
    if res and res.get("ok"):
        print("Bot commands registered successfully with Telegram.")
    else:
        print(f"Note: setMyCommands response: {res}")

def handle_update(update):
    if "message" in update:
        msg = update["message"]
        chat_id = msg["chat"]["id"]

        # Deduplicate media group albums so multi-image messages don't spam replies
        media_group_id = msg.get("media_group_id")
        if media_group_id:
            if media_group_id in _seen_media_groups:
                return
            _seen_media_groups.add(media_group_id)
            if len(_seen_media_groups) > 500:
                _seen_media_groups.clear()

        # Extract text or media caption
        raw_text = (msg.get("text") or msg.get("caption") or "").strip()
        text = raw_text.lower()

        if re.match(r"^/milestone(@\w+)?(?:\s|$)", text):
            send_message(chat_id, *get_changelog_content())
            return
        if re.match(r"^/customize(@\w+)?(?:\s|$)", text):
            send_message(chat_id, *get_feature_detail("tones"))
            return
        if re.match(r"^/security(@\w+)?(?:\s|$)", text):
            send_message(chat_id, *get_faq_detail("security"))
            return

        # If message contains only media (photo, video, document, voice, audio) without text
        if not raw_text and ("photo" in msg or "video" in msg or "document" in msg or "audio" in msg or "voice" in msg):
            media_reply = (
                "📸 <b>Media Received!</b>\n\n"
                "Vern TTS is an offline Android reading workstation supporting <b>PDF, EPUB, TXT, DOCX, and Markdown (.md)</b> documents.\n\n"
                "• <b>To read books:</b> Transfer files to your Android device storage and open them in Vern TTS.\n"
                "• <b>To explore classics:</b> Tap <b>Classics</b> to browse 35+ curated public domain books pre-loaded with authentic covers.\n\n"
                "<i>Select an option below to get started:</i>"
            )
            markup = {
                "inline_keyboard": [
                    [{"text": "⬇️ Download App", "callback_data": "cmd_download"}, {"text": "📋 Latest release notes", "callback_data": "cmd_changelog"}],
                    [{"text": "📚 Curated Classics", "callback_data": "cmd_classics"}, {"text": "🏠 Main Menu", "callback_data": "cmd_menu"}]
                ]
            }
            send_message(chat_id, media_reply, markup)
            return

        if text.startswith("/start") or text.startswith("/help") or text == "menu":
            t, m = get_welcome_content()
            send_message(chat_id, t, m)
        elif text.startswith("/download") or text == "download" or "apk" in text:
            t, m = get_download_content()
            send_message(chat_id, t, m)
        elif (text.startswith("/changelog") or text.startswith("/release") or text.startswith("/whatsnew") or
              text.startswith("/update") or text.startswith("/notes") or "what's new" in text or "release note" in text or "changelog" in text):
            t, m = get_changelog_content()
            send_message(chat_id, t, m)
        elif text.startswith("/features") or text == "features" or text == "feature":
            t, m = get_features_menu()
            send_message(chat_id, t, m)
        elif text.startswith("/study") or "flashcard" in text or "study hub" in text or "recall" in text:
            t, m = get_feature_detail("study")
            send_message(chat_id, t, m)
        elif text.startswith("/memos") or text.startswith("/memo") or "voice memo" in text or "voice note" in text:
            t, m = get_feature_detail("memos")
            send_message(chat_id, t, m)
        elif text.startswith("/insights") or "wpm" in text or "reading speed" in text or "streak" in text:
            t, m = get_feature_detail("insights")
            send_message(chat_id, t, m)
        elif text.startswith("/voice") or text.startswith("/guide") or text.startswith("/tips") or "robotic" in text or "voice" in text:
            t, m = get_guide_content()
            send_message(chat_id, t, m)
        elif text.startswith("/random") or "random book" in text or "quote" in text:
            t, m = get_random_book_content()
            send_message(chat_id, t, m)
        elif text.startswith("/search"):
            query = raw_text[7:].strip()
            if not query:
                send_message(chat_id, "🔍 <i>Please specify a search term. Example:</i> <code>/search austen</code>")
            else:
                t, m = search_classics(query)
                send_message(chat_id, t, m)
        elif text.startswith("/classics") or text == "classics" or text == "books" or text == "catalog":
            t, m = get_classics_menu()
            send_message(chat_id, t, m)
        elif text.startswith("/faq") or text == "faq" or text == "faqs" or "question" in text:
            t, m = get_faq_menu()
            send_message(chat_id, t, m)
        elif text.startswith("/contact") or text.startswith("/community") or "channel" in text or "developer" in text or "support" in text:
            t, m = get_contact_content()
            send_message(chat_id, t, m)
        elif any(w in text for w in ["library", "import", "add book", "add to library", "open file"]):
            t = (
                "📚 <b>How to Add Books & Documents to Vern TTS</b>\n\n"
                "1. <b>Storage Files:</b> Place any PDF, EPUB, TXT, DOCX, or MD file on your device (Internal Storage or Downloads).\n"
                "2. <b>Instant Import:</b> In Vern TTS, tap <b>+ Add Document</b> or use the batch file browser to import files directly into your personal library.\n"
                "3. <b>Classics Shelf:</b> Tap the <b>Classics</b> tab inside the app to download any of the 35+ curated public domain books with authentic covers.\n\n"
                "<i>All imported documents remain 100% private and offline on your device sandbox.</i>"
            )
            m = {
                "inline_keyboard": [
                    [{"text": "⬇️ Download App", "callback_data": "cmd_download"}, {"text": "📚 Browse Classics", "callback_data": "cmd_classics"}],
                    [{"text": "🏠 Main Menu", "callback_data": "cmd_menu"}]
                ]
            }
            send_message(chat_id, t, m)
        else:
            fallback = (
                "🤖 <b>Vern TTS Assistant</b>\n\n"
                "I can help you explore offline reading, on-device neural speech, embedded voice memos, the spaced-repetition study hub, and book classics.\n\n"
                "<i>Select an option below or type <code>/help</code> for available commands:</i>"
            )
            markup = {
                "inline_keyboard": [
                    [{"text": "⬇️ Download App", "callback_data": "cmd_download"}, {"text": "📋 Latest release notes", "callback_data": "cmd_changelog"}],
                    [{"text": "🌟 Features", "callback_data": "cmd_features"}, {"text": "📚 Curated Classics", "callback_data": "cmd_classics"}],
                    [{"text": "🧠 Study Hub", "callback_data": "feat_study"}, {"text": "🎙 Voice Memos", "callback_data": "feat_memos"}],
                    [{"text": "🎲 Random Classic", "callback_data": "cmd_random_book"}, {"text": "❓ Help and FAQs", "callback_data": "cmd_faq"}]
                ]
            }
            send_message(chat_id, fallback, markup)

    elif "callback_query" in update:
        cq = update["callback_query"]
        chat_id = cq["message"]["chat"]["id"]
        msg_id = cq["message"]["message_id"]
        data = cq.get("data", "")
        answer_callback(cq["id"])

        if data == "cmd_menu":
            t, m = get_welcome_content()
            edit_message(chat_id, msg_id, t, m)
        elif data == "cmd_download":
            t, m = get_download_content()
            edit_message(chat_id, msg_id, t, m)
        elif data in ("cmd_changelog", "cmd_release"):
            t, m = get_changelog_content()
            edit_message(chat_id, msg_id, t, m)
        elif data == "cmd_features":
            t, m = get_features_menu()
            edit_message(chat_id, msg_id, t, m)
        elif data in ("cmd_guide", "cmd_guide_voice"):
            t, m = get_guide_content()
            edit_message(chat_id, msg_id, t, m)
        elif data == "cmd_classics":
            t, m = get_classics_menu()
            edit_message(chat_id, msg_id, t, m)
        elif data == "cmd_random_book":
            t, m = get_random_book_content()
            edit_message(chat_id, msg_id, t, m)
        elif data.startswith("cls_cat_"):
            category = data.replace("cls_cat_", "")
            t, m = get_classics_category_content(category)
            edit_message(chat_id, msg_id, t, m)
        elif data == "cmd_contact":
            t, m = get_contact_content()
            edit_message(chat_id, msg_id, t, m)
        elif data == "cmd_faq":
            t, m = get_faq_menu()
            edit_message(chat_id, msg_id, t, m)
        elif data.startswith("feat_"):
            topic = data.replace("feat_", "")
            t, m = get_feature_detail(topic)
            edit_message(chat_id, msg_id, t, m)
        elif data.startswith("faq_"):
            topic = data.replace("faq_", "")
            t, m = get_faq_detail(topic)
            edit_message(chat_id, msg_id, t, m)
        else:
            t, m = get_welcome_content()
            edit_message(chat_id, msg_id, t, m)

def main():
    if not TOKEN:
        sys.exit("Set TELEGRAM_BOT_TOKEN in the environment.")
    print("Starting Vern TTS Autonomous Bot (Resilient Long-Polling mode)...")
    setup_bot_commands()
    offset = 0
    api_call("deleteWebhook", {"drop_pending_updates": False})

    while True:
        try:
            updates = api_call("getUpdates", {"offset": offset, "timeout": 20})
            if updates and updates.get("ok"):
                for item in updates.get("result", []):
                    offset = max(offset, item["update_id"] + 1)
                    try:
                        handle_update(item)
                    except Exception as err:
                        print(f"Error handling update {item.get('update_id')}: {err}")
                # Immediate quick poll for the next batch
                time.sleep(0.1)
            else:
                # Brief rest on network hiccup
                time.sleep(1)
        except KeyboardInterrupt:
            print("\nBot polling stopped cleanly.")
            break
        except Exception as e:
            print(f"Polling loop unexpected exception: {e}")
            time.sleep(2)

if __name__ == "__main__":
    main()
