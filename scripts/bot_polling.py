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

TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "8285832720:AAHM20ABnUrB1VzLM81eUgkO6NdXnb7Je-o")
REPO = os.environ.get("GITHUB_REPOSITORY", "fhes-tus/Vern-TTS")
WEBSITE_URL = "https://fhes-tus.github.io/Vern-TTS/"
CHANNEL_URL = "https://t.me/myreader_veritas"
TWITTER_URL = "https://x.com/_1st2us"
EMAIL_ADDRESS = "myreader.veritas@gmail.com"
GITHUB_URL = f"https://github.com/{REPO}"

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
    url = f"https://api.telegram.org/bot{TOKEN}/{method}"
    headers = {"Content-Type": "application/json"}
    data = json.dumps(payload).encode("utf-8") if payload else None
    req = urllib.request.Request(url, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=40) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="replace")
        print(f"API HTTP Error ({method} {e.code}): {err_body}")
        if e.code == 429:
            try:
                err_json = json.loads(err_body)
                retry_after = err_json.get("parameters", {}).get("retry_after", 3)
                print(f"Rate limited (429). Retrying after {retry_after}s...")
                time.sleep(retry_after)
                return api_call(method, payload)
            except Exception:
                time.sleep(3)
        return None
    except Exception as e:
        if "timed out" not in str(e).lower():
            print(f"API Call Error ({method}): {e}")
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
            tag = data.get("tag_name", "v2.6.0")
            name = data.get("name", f"Vern TTS {tag}")
            body = data.get("body", "")
            _cached_release = {
                "tag": tag,
                "name": name,
                "body": body,
                "arm64_url": f"{GITHUB_URL}/releases/download/{tag}/Veritas-Reader-{tag}-arm64-v8a-release.apk",
                "universal_url": f"{GITHUB_URL}/releases/download/{tag}/Veritas-Reader-{tag}-universal-release.apk",
                "arm32_url": f"{GITHUB_URL}/releases/download/{tag}/Veritas-Reader-{tag}-armeabi-v7a-release.apk"
            }
            _cached_release_time = now
            return _cached_release
    except Exception as e:
        print(f"Warning: could not fetch release from GitHub API ({e})")
        if _cached_release:
            return _cached_release
        tag = "v2.6.0"
        return {
            "tag": tag,
            "name": f"Vern TTS {tag}",
            "body": "",
            "arm64_url": f"{GITHUB_URL}/releases/download/{tag}/Veritas-Reader-{tag}-arm64-v8a-release.apk",
            "universal_url": f"{GITHUB_URL}/releases/download/{tag}/Veritas-Reader-{tag}-universal-release.apk",
            "arm32_url": f"{GITHUB_URL}/releases/download/{tag}/Veritas-Reader-{tag}-armeabi-v7a-release.apk"
        }

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
    rel = get_latest_release()
    tag = rel["tag"]
    body = rel.get("body", "")
    if not body:
        candidates = [
            os.path.join(os.getcwd(), f"RELEASE_NOTES_{tag}.md"),
            os.path.join(os.path.dirname(os.path.abspath(__file__)), f"RELEASE_NOTES_{tag}.md"),
            os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", f"RELEASE_NOTES_{tag}.md"),
            os.path.join(os.getcwd(), "RELEASE_NOTES_v2.6.0.md"),
            os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "RELEASE_NOTES_v2.6.0.md"),
        ]
        for c in candidates:
            if os.path.exists(c):
                try:
                    with open(c, "r", encoding="utf-8") as f:
                        body = f.read()
                    break
                except Exception:
                    pass

    formatted_notes = markdown_to_telegram_html(body, max_bullets=14)
    if not formatted_notes:
        formatted_notes = (
            "• <b>Classics Catalog & Shelves</b>: Curated 35+ masterpieces with authentic covers and background downloading.\n"
            "• <b>Floating Playback Capsule</b>: Hovering live playback bar with interactive waveforms & session lifecycle.\n"
            "• <b>Notes & Notebooks Upgrade</b>: Multi-notebook organization, trash recovery, and 30-step revision history.\n"
            "• <b>Pronunciation Rules Engine</b>: Literal boundary matching, custom substitutions, and audio auditioning.\n"
            "• <b>Liquid Glass & UI Alignment</b>: Dynamic status bar luminance tinting and movable batch import floater."
        )

    text = (
        f"📋 <b>What's New in Vern TTS {tag}</b>\n\n"
        f"{formatted_notes}\n\n"
        f"<i>Build: {tag} • 100% Private, On-Device Reading</i>"
    )
    markup = {
        "inline_keyboard": [
            [{"text": f"⬇️ Download {tag} APKs", "callback_data": "cmd_download"}],
            [{"text": "🐙 GitHub Release", "url": f"{GITHUB_URL}/releases/tag/{tag}"}, {"text": "🌐 Official Website", "url": WEBSITE_URL}],
            [{"text": "⬅️ Back to Main Menu", "callback_data": "cmd_menu"}]
        ]
    }
    return text, markup

def get_welcome_content():
    rel = get_latest_release()
    tag = rel["tag"]
    text = (
        "👋 <b>Welcome to the Vern TTS Knowledge Center!</b>\n\n"
        "<b>Vern TTS is an offline-first Android reading workstation. Built with on-device speech synthesis, a spaced-repetition study hub, embedded voice memos, and zero telemetry tracking.</b>\n\n"
        "⚡️ <b>Workstation Capabilities:</b>\n"
        "• 🎙 <b>Embedded Voice Memos</b>: Spoken margin notes & audio reflections attached to any passage.\n"
        "• 🧠 <b>Spaced-Repetition Study Hub</b>: Active recall flashcards, Leitner review scheduling, & retention scoring.\n"
        "• 🎧 <b>On-Device Neural Speech</b>: High-fidelity narration with lockscreen media controls & sentence sync.\n"
        "• 📄 <b>Universal Document Studio</b>: PDF, EPUB, TXT, DOCX, & Markdown with automatic prose synopses.\n"
        "• 👁 <b>4 Unified Paper Tones</b>: Clean White, Warm Sepia, Soft Dark, & True AMOLED Black.\n"
        "• 📚 <b>35+ Curated Classics</b>: Built-in library pre-loaded with authentic published first-edition covers.\n"
        "• 📊 <b>Local Reading Tracker</b>: WPM speed, daily reading minutes, streaks, and local SQLite data.\n"
        "• 🛡 <b>Zero Surveillance</b>: 0 ads, 0 trackers, 0 telemetry, and no account required.\n\n"
        f"<i>Current Verified Build: <b>{tag}</b> • Tap any section below to explore:</i>"
    )
    markup = {
        "inline_keyboard": [
            [{"text": "⬇️ Download APKs", "callback_data": "cmd_download"}, {"text": f"📋 What's New ({tag})", "callback_data": "cmd_changelog"}],
            [{"text": "🌟 Workstation Features", "callback_data": "cmd_features"}, {"text": "📚 Curated Classics", "callback_data": "cmd_classics"}],
            [{"text": "🧠 Study Hub & Cards", "callback_data": "feat_study"}, {"text": "🎙 Voice Memos", "callback_data": "feat_memos"}],
            [{"text": "🎧 Voice Setup Guide", "callback_data": "cmd_guide_voice"}, {"text": "❓ In-Depth FAQs", "callback_data": "cmd_faq"}],
            [{"text": "📢 Telegram Community", "url": CHANNEL_URL}, {"text": "🌐 Official Website", "url": WEBSITE_URL}]
        ]
    }
    return text, markup

def get_download_content():
    rel = get_latest_release()
    tag = rel["tag"]
    text = (
        f"📦 <b>Download Vern TTS ({tag} Latest Stable)</b>\n\n"
        "Select the optimal build for your device architecture:\n\n"
        f"• <a href=\"{rel['arm64_url']}\"><b>64-bit ARM APK (arm64-v8a)</b></a> ⭐️ <i>Recommended</i>\n"
        "  Tailored for modern Android devices (Android 8.0 through Android 15). Smallest footprint and highest execution performance.\n\n"
        f"• <a href=\"{rel['universal_url']}\"><b>Universal Release APK</b></a>\n"
        "  Contains runtime binaries for all chipsets. Compatible with any supported phone, tablet, or Chromebook.\n\n"
        f"• <a href=\"{rel['arm32_url']}\"><b>32-bit Legacy APK (armeabi-v7a)</b></a>\n"
        "  For older devices and budget hardware architectures running 32-bit chipsets.\n\n"
        "🛡 <b>Safe Sideloading Note:</b>\n"
        "If Android prompts <i>\"Install from Unknown Sources\"</i>, tap <b>Allow</b>. This is standard for apps installed outside Google Play. Vern contains zero ads, zero telemetry, and is 100% open source under the Apache 2.0 license.\n\n"
        "⏳ <b>Google Play Store:</b> Application review is currently in progress. The official Play Store listing will be available shortly!"
    )
    markup = {
        "inline_keyboard": [
            [{"text": f"⬇️ Download arm64 APK ({tag})", "url": rel["arm64_url"]}],
            [{"text": f"📋 What's New in {tag}", "callback_data": "cmd_changelog"}, {"text": "🌐 Website", "url": WEBSITE_URL}],
            [{"text": "📦 All Builds on GitHub", "url": f"{GITHUB_URL}/releases/latest"}],
            [{"text": "⬅️ Back to Main Menu", "callback_data": "cmd_menu"}]
        ]
    }
    return text, markup

def get_features_menu():
    text = (
        "🌟 <b>Vern TTS Workstation Architecture</b>\n\n"
        "Vern TTS transforms your Android device into a focused, distraction-free reading workstation.\n\n"
        "Select an engineering pillar below to inspect how it works:"
    )
    markup = {
        "inline_keyboard": [
            [{"text": "🎙 Embedded Voice Memos", "callback_data": "feat_memos"}, {"text": "🧠 Spaced-Repetition Study Hub", "callback_data": "feat_study"}],
            [{"text": "🎧 On-Device Speech Synthesis", "callback_data": "feat_speech"}, {"text": "📄 Document Studio & Formats", "callback_data": "feat_docs"}],
            [{"text": "👁 4 Unified Paper Tones", "callback_data": "feat_tones"}, {"text": "📊 Reading Tracker & Insights", "callback_data": "feat_insights"}],
            [{"text": "📖 Dual Reading Modes", "callback_data": "feat_modes"}, {"text": "🛡 100% Offline Privacy", "callback_data": "feat_privacy"}],
            [{"text": "⬅️ Back to Main Menu", "callback_data": "cmd_menu"}]
        ]
    }
    return text, markup

def get_feature_detail(topic):
    if topic == "memos":
        title = "🎙 <b>Embedded Voice Memos & Spoken Annotations</b>"
        body = (
            "• <b>Frictionless Spoken Notes</b>: While reading any book or document, tap any paragraph or margin to record audio reflections without breaking your concentration.\n"
            "• <b>Synchronized Timestamps</b>: Every voice memo is tied directly to the exact sentence and chapter where it was recorded.\n"
            "• <b>In-Line Playback</b>: Tap the audio memo icon beside any highlighted passage to play back your recorded thoughts directly on the page.\n"
            "• <b>100% Local Storage</b>: Audio recordings are stored locally inside the application sandbox with zero cloud transmission or AI data scraping."
        )
    elif topic == "study":
        title = "🧠 <b>Spaced-Repetition Study Hub & Active Recall</b>"
        body = (
            "• <b>Active Recall Flashcards</b>: Highlight notable quotes, definitions, or complex arguments to instantly generate interactive Question / Answer study cards.\n"
            "• <b>Spaced-Repetition Scheduling</b>: Review cards using calibrated intervals that reinforce memory retention right before forgetting sets in.\n"
            "• <b>Study Decks by Book</b>: Filter cards by specific documents, tags, or browse your master library deck.\n"
            "• <b>Mastery Progress</b>: Track card retention rates, mastery levels, and review intervals locally on your device."
        )
    elif topic == "speech":
        title = "🎧 <b>On-Device Neural Speech Synthesis Engine</b>"
        body = (
            "• <b>Zero-Cloud Latency</b>: All speech generation runs locally using on-device engines. Zero audio streaming delays and zero data sent to servers.\n"
            "• <b>Continuous Background Audio</b>: Keep listening seamlessly while multitasking, using other apps, or with your phone display turned off.\n"
            "• <b>Lockscreen & Notification Media Controls</b>: Native Android media player controls with Play, Pause, Previous/Next sentence skip, and progress scrub.\n"
            "• <b>Synchronized Sentence Highlighting</b>: Words and active sentences dynamically illuminate on screen in sync with the spoken voice.\n"
            "• <b>In-App Engine Customization</b>: Tap the speaker icon while reading to switch between Vern Lite, Vern Studio, Google Speech, or Samsung engines, with real-time rate (0.5x–3.0x) and pitch tuning."
        )
    elif topic == "insights":
        title = "📊 <b>Reading Tracker & Local Insights</b>"
        body = (
            "• <b>Reading Speed (WPM)</b>: Accurately computes your personal Words-Per-Minute reading pace across different genres.\n"
            "• <b>Daily Reading Minutes & Streaks</b>: Visual tracking of reading time and consistency to cultivate steady daily reading habits.\n"
            "• <b>Book Completion Statistics</b>: Pages turned, chapters read, and milestones achieved across your bookshelf.\n"
            "• <b>Air-Gapped Analytics</b>: All statistics remain exclusively inside your phone's encrypted SQLite storage. No remote analytics SDKs."
        )
    elif topic == "docs":
        title = "📄 <b>Universal Document Studio & Formats</b>"
        body = (
            "• <b>Supported Formats</b>: PDF, EPUB, TXT, DOCX (Word), and Markdown (.md).\n"
            "• <b>Intelligent Heuristic Synopsis</b>: Automatically bypasses Gutenberg boilerplate headers, legal notices, and licenses to extract the clean opening prose as a synopsis.\n"
            "• <b>Dedicated PDF Media Bar</b>: Tailored PDF controls featuring 1-tap screen orientation switching (Portrait / Landscape) and Next sentence skip.\n"
            "• <b>Universal Storage Access</b>: Import documents directly from Internal Storage, SD Cards, Google Drive, or Downloads."
        )
    elif topic == "tones":
        title = "👁 <b>4 Unified Paper Tones & Eye Comfort</b>"
        body = (
            "Vern features 4 carefully calibrated color palettes designed for maximum reading endurance:\n\n"
            "1. <b>Clean White</b> (Daylight): Crisp, high-contrast black typography on neutral white.\n"
            "2. <b>Warm Sepia</b> (Extended Reading): Warm cream background with softened amber tint that eliminates blue-light eye strain during long sessions.\n"
            "3. <b>Soft Dark</b> (Evening): Gentle slate-slate contrast that eliminates harsh glare in low-light environments.\n"
            "4. <b>AMOLED Black</b> (Night / Battery Saver): Pure 0% luminance black (#000000) pixels that completely deactivate OLED subpixels, saving massive battery life."
        )
    elif topic == "modes":
        title = "📖 <b>Dual Reading Modes & Navigation</b>"
        body = (
            "• <b>Paged Reading Mode</b>: Authentic physical book simulation with horizontal page flips, subtle page shadows, and haptic feedback.\n"
            "• <b>Continuous Flow Mode</b>: Smooth, frictionless vertical scrolling with responsive typography that automatically reflows to fit your screen size.\n"
            "• <b>Sentence Snap</b>: Tap any sentence anywhere on a page to immediately jump audio narration directly to that sentence.\n"
            "• <b>Fluid Library Gestures</b>: Drag-and-drop to reorder your bookshelf, hold-to-select for batch actions, and swipe to bookmark."
        )
    else:
        title = "🛡 <b>100% Offline Privacy Guarantee</b>"
        body = (
            "• <b>Zero Analytics & Telemetry</b>: No Google Analytics, no Firebase tracking, no Crashlytics data collection.\n"
            "• <b>Zero Accounts Required</b>: No logins, passwords, or emails. Download and start reading in 2 seconds.\n"
            "• <b>Zero Ads Forever</b>: Clean, distraction-free reading with zero banners, popups, or subscriptions.\n"
            "• <b>Local SQLite Storage</b>: All reading history, bookmarks, and notes are stored strictly on your phone's internal sandbox.\n"
            "• <b>Open Source</b>: Complete source code is auditable on GitHub under the Apache 2.0 license."
        )

    text = f"{title}\n\n{body}"
    markup = {
        "inline_keyboard": [
            [{"text": "⬅️ Back to Features", "callback_data": "cmd_features"}, {"text": "🏠 Main Menu", "callback_data": "cmd_menu"}]
        ]
    }
    return text, markup

def get_guide_content():
    text = (
        "🎧 <b>Masterclass: How to Customize & Preview Voices</b>\n\n"
        "Vern TTS gives you complete control over narration directly <b>inside the app</b>:\n\n"
        "<b>1. In-App Voice Engine & Picker:</b>\n"
        "While reading any document, tap the <b>Speaker icon</b> on the bottom player bar. Tap the voice picker to switch between available engines (such as Vern Lite, Vern Studio, Google Speech, or Samsung) and preview different vocal styles.\n\n"
        "<b>2. Real-Time Pitch & Velocity:</b>\n"
        "Use the in-app sliders to adjust speech speed from <b>0.5x up to 3.0x</b> and tune vocal pitch to your preference.\n\n"
        "<b>3. Optional Extra Voice Packs:</b>\n"
        "If you want additional neural accents or languages, you can also install them in Android Settings > Accessibility > Text-to-Speech output. Vern TTS will automatically detect and list them in your in-app voice menu!"
    )
    markup = {
        "inline_keyboard": [
            [{"text": "⬇️ Download Vern TTS", "callback_data": "cmd_download"}, {"text": "❓ FAQs", "callback_data": "cmd_faq"}],
            [{"text": "⬅️ Back to Main Menu", "callback_data": "cmd_menu"}]
        ]
    }
    return text, markup

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
    text = (
        "📬 <b>Vern TTS Official Channels & Access</b>\n\n"
        "Connect directly with the project and maintain data sovereignty:\n\n"
        f"• 📢 <b>Telegram Community:</b> @myreader_veritas\n"
        f"• 🐙 <b>GitHub Repository:</b> <a href=\"{GITHUB_URL}\">fhes-tus/Vern-TTS</a>\n"
        f"• 𝕏 <b>X (Twitter):</b> <a href=\"{TWITTER_URL}\">@_1st2us</a>\n"
        f"• ✉️ <b>Support & Inquiries:</b> <code>{EMAIL_ADDRESS}</code>\n"
        f"• 🌐 <b>Official Website:</b> <a href=\"{WEBSITE_URL}\">{WEBSITE_URL}</a>\n\n"
        "<i>Open source under Apache 2.0 license. Zero telemetry, zero corporate surveillance.</i>"
    )
    markup = {
        "inline_keyboard": [
            [{"text": "📢 Telegram Community", "url": CHANNEL_URL}, {"text": "🐙 GitHub Repository", "url": GITHUB_URL}],
            [{"text": "𝕏 Follow on X", "url": TWITTER_URL}, {"text": "🌐 Official Website", "url": WEBSITE_URL}],
            [{"text": "✉️ Email Support", "url": f"mailto:{EMAIL_ADDRESS}?subject=Vern%20TTS%20Inquiry"}],
            [{"text": "⬅️ Back to Main Menu", "callback_data": "cmd_menu"}]
        ]
    }
    return text, markup

def get_faq_menu():
    text = (
        "❓ <b>Vern TTS Knowledge Base & FAQs</b>\n\n"
        "Choose a category to find comprehensive answers:"
    )
    markup = {
        "inline_keyboard": [
            [{"text": "🎧 Voice Quality & Audio", "callback_data": "faq_audio"}, {"text": "📄 Formats & Scanned PDFs", "callback_data": "faq_formats"}],
            [{"text": "👁 Paper Tones & AMOLED", "callback_data": "faq_display"}, {"text": "📝 Study Cards & Voice Memos", "callback_data": "faq_study"}],
            [{"text": "🛡 Privacy & Safe Sideloading", "callback_data": "faq_security"}, {"text": "🔄 Updates & Progress", "callback_data": "faq_updates"}],
            [{"text": "⬅️ Back to Main Menu", "callback_data": "cmd_menu"}]
        ]
    }
    return text, markup

def get_faq_detail(topic):
    if topic == "audio":
        title = "🎧 <b>Audio & Speech Engine FAQs</b>"
        body = (
            "<b>Q: Why does the voice sound robotic, and how do I customize it?</b>\n"
            "A: You can customize voices directly inside the app! While reading, tap the speaker icon on the bottom player bar to open the Voice Picker. You can switch between engines (Vern Lite, Vern Studio, Google, or Samsung) and select smoother voices. You can also download extra neural voice packs in Android Settings > Text-to-speech, and Vern will automatically list them in the in-app picker.\n\n"
            "<b>Q: Can I listen with my phone screen turned off?</b>\n"
            "A: Yes! Vern TTS features a persistent Android Media Notification service. It continues narration in the background while your phone is locked or while using other apps.\n\n"
            "<b>Q: Can I change playback speed and pitch?</b>\n"
            "A: Yes. Tap the speaker icon on the bottom reading bar to adjust speech rate from 0.5x up to 3.0x, tune vocal pitch, and select specific voice engines.\n\n"
            "<b>Q: Does text highlight as the voice speaks?</b>\n"
            "A: Yes! Active sentences and paragraphs dynamically highlight on screen in sync with the spoken audio."
        )
    elif topic == "formats":
        title = "📄 <b>Document Formats & Scanned PDFs</b>"
        body = (
            "<b>Q: What file formats are supported?</b>\n"
            "A: Vern TTS supports PDF, EPUB, TXT, DOCX (Microsoft Word), and Markdown (.md).\n\n"
            "<b>Q: Can Vern read scanned image-only PDFs?</b>\n"
            "A: Standard PDFs with digital text layers extract and read instantly. Image-only scanned PDFs (photocopies without embedded text) require OCR before text can be extracted for speech.\n\n"
            "<b>Q: Where are the 35+ classic books stored?</b>\n"
            "A: The classics catalog metadata is pre-indexed inside the app. When you tap a book to read, it downloads the clean public domain text on-demand and saves it locally."
        )
    elif topic == "display":
        title = "👁 <b>Paper Tones & Display FAQs</b>"
        body = (
            "<b>Q: How does AMOLED Black save battery?</b>\n"
            "A: On OLED/AMOLED smartphone screens, black pixels (#000000) are physically turned OFF completely and consume 0% power. Reading in AMOLED Black can extend your battery life by up to 40% during long sessions.\n\n"
            "<b>Q: Why is Warm Sepia recommended for reading?</b>\n"
            "A: Blue light from white screens disrupts melatonin production and causes eye fatigue. Warm Sepia filters out harsh high-frequency blue spectrums, creating a comforting experience identical to aged physical book pages.\n\n"
            "<b>Q: Can I switch between paged flips and vertical scrolling?</b>\n"
            "A: Yes! You can toggle between physical-style Paged Mode (with page-turn haptics) and continuous vertical Flow Mode at any time."
        )
    elif topic == "study":
        title = "📝 <b>Study Hub & Voice Memos FAQs</b>"
        body = (
            "<b>Q: How do Study Cards work?</b>\n"
            "A: While reading, highlight any passage or sentence and tap <i>Save to Study Hub</i>. You can review them anytime with interactive flip cards (Question / Answer) to memorize key concepts using spaced repetition.\n\n"
            "<b>Q: How do Embedded Voice Memos work?</b>\n"
            "A: Tap on any paragraph to record quick spoken thoughts or reflections. They attach directly to that line with a timestamp and play back inline with zero cloud uploads.\n\n"
            "<b>Q: Does Vern track my reading speed?</b>\n"
            "A: Yes! Vern calculates your Words Per Minute (WPM), total minutes read, pages completed, and streaks. All stats are calculated 100% locally on your phone."
        )
    elif topic == "security":
        title = "🛡 <b>Privacy & Safe Sideloading FAQs</b>"
        body = (
            "<b>Q: Why does Android show \"Unknown App / Source\" when installing?</b>\n"
            "A: Android displays this warning for *any* application installed from an APK file outside the Google Play Store. It is standard Android behavior. Vern TTS contains zero malware, zero trackers, and is 100% open source on GitHub.\n\n"
            "<b>Q: Does Vern require any internet permissions?</b>\n"
            "A: Reading, text extraction, speech generation, voice memos, and study cards all run 100% offline. Internet is only used if you choose to download a new classic from the catalog.\n\n"
            "<b>Q: Does Vern collect or sell my data?</b>\n"
            "A: Never. Vern has no telemetry, no tracking IDs, no ads, and no user accounts. Your library and progress remain strictly on your device."
        )
    else:
        title = "🔄 <b>Updating & Reading Progress FAQs</b>"
        body = (
            "<b>Q: Will I lose my books and bookmarks when I update?</b>\n"
            "A: No! When you install a newer version over your existing Vern TTS app, Android preserves your SQLite database, bookmarks, reading progress, and custom settings automatically.\n\n"
            "<b>Q: How do I get notified of updates?</b>\n"
            "A: Join our official Telegram channel <b>@myreader_veritas</b>! New releases and changelogs are published there automatically as soon as they are ready."
        )

    text = f"{title}\n\n{body}"
    markup = {
        "inline_keyboard": [
            [{"text": "⬅️ Back to FAQs", "callback_data": "cmd_faq"}, {"text": "🏠 Main Menu", "callback_data": "cmd_menu"}]
        ]
    }
    return text, markup

def setup_bot_commands():
    """Register rich command menu in Telegram for autocompletion."""
    commands = [
        {"command": "start", "description": "Welcome & Workstation Overview"},
        {"command": "download", "description": "Get latest verified APK releases"},
        {"command": "changelog", "description": "What's new in v2.6.0 release"},
        {"command": "features", "description": "Workstation capabilities & tools"},
        {"command": "study", "description": "Spaced repetition & study hub"},
        {"command": "memos", "description": "Embedded voice memos & notes"},
        {"command": "voice", "description": "Natural voice customization guide"},
        {"command": "classics", "description": "Browse 35+ curated classics"},
        {"command": "random", "description": "Get an instant classic book spotlight"},
        {"command": "search", "description": "Search books by title or author"},
        {"command": "insights", "description": "Reading speed & tracker analytics"},
        {"command": "faq", "description": "Knowledge base & troubleshooting"},
        {"command": "contact", "description": "Official support, X, & GitHub"}
    ]
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
                    [{"text": "⬇️ Download App", "callback_data": "cmd_download"}, {"text": "📋 What's New (v2.6.0)", "callback_data": "cmd_changelog"}],
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
                    [{"text": "⬇️ Download App", "callback_data": "cmd_download"}, {"text": "📋 What's New (v2.6.0)", "callback_data": "cmd_changelog"}],
                    [{"text": "🌟 Workstation Features", "callback_data": "cmd_features"}, {"text": "📚 Curated Classics", "callback_data": "cmd_classics"}],
                    [{"text": "🧠 Study Hub", "callback_data": "feat_study"}, {"text": "🎙 Voice Memos", "callback_data": "feat_memos"}],
                    [{"text": "🎲 Random Classic", "callback_data": "cmd_random_book"}, {"text": "❓ Knowledge Base", "callback_data": "cmd_faq"}]
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
