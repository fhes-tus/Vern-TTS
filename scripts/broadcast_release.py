#!/usr/bin/env python3
"""
Autonomous Telegram Release Broadcaster for Vern TTS
Triggered by GitHub Actions on new release or executed locally.
"""

import os
import sys
import json
import re
import urllib.request
import urllib.parse
import urllib.error

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "8285832720:AAHM20ABnUrB1VzLM81eUgkO6NdXnb7Je-o")
CHANNEL_ID = os.environ.get("TELEGRAM_CHANNEL_ID", "@myreader_veritas")
REPO = os.environ.get("GITHUB_REPOSITORY", "fhes-tus/Vern-TTS")

def get_event_data():
    """Extract release data from GitHub Actions event if available."""
    event_path = os.environ.get("GITHUB_EVENT_PATH")
    if event_path and os.path.exists(event_path):
        try:
            with open(event_path, "r", encoding="utf-8") as f:
                event = json.load(f)
                if "release" in event:
                    return event["release"]
        except Exception as e:
            print(f"Could not read GITHUB_EVENT_PATH: {e}")
    return None

def get_release_data(tag=None):
    """Fetch release details from GitHub API."""
    if tag:
        url = f"https://api.github.com/repos/{REPO}/releases/tags/{tag}"
    else:
        url = f"https://api.github.com/repos/{REPO}/releases/latest"
    req = urllib.request.Request(url, headers={"User-Agent": "Vern-Broadcaster"})
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        print(f"Warning: could not fetch release from API ({e})")
        return None

def markdown_to_telegram_html(md_text, max_bullets=10):
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
            # Markdown link [text](url) -> HTML <a href="url">text</a>
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

def main():
    data = get_event_data()
    req_tag = os.environ.get("RELEASE_TAG")
    if not data:
        data = get_release_data(req_tag)
    
    tag = req_tag or (data.get("tag_name") if data else "v2.6.0")
    name = data.get("name", f"Vern TTS {tag}") if data else f"Vern TTS {tag}"
    body = data.get("body", "") if data else ""
    
    cleaned_highlights = markdown_to_telegram_html(body, max_bullets=10)
    if not cleaned_highlights:
        cleaned_highlights = (
            "• <b>Authentic Published Covers</b>: 35+ classic masterpieces with original artwork.\n"
            "• <b>Unified Paper Tone Sync</b>: Real-time consistency across text and PDF readers.\n"
            "• <b>Enhanced PDF Media Bar</b>: Next skip and instant orientation toggle.\n"
            "• <b>Intelligent Synopsis</b>: Automatic clean opening prose extraction.\n"
            "• <b>Streamlined UI</b>: Elevated drag reordering and split-action hero card."
        )

    # Extract asset links
    arm64_url = f"https://github.com/{REPO}/releases/download/{tag}/Veritas-Reader-{tag}-arm64-v8a-release.apk"
    universal_url = f"https://github.com/{REPO}/releases/download/{tag}/Veritas-Reader-{tag}-universal-release.apk"
    armeabi_url = f"https://github.com/{REPO}/releases/download/{tag}/Veritas-Reader-{tag}-armeabi-v7a-release.apk"
    
    if data and "assets" in data:
        for a in data["assets"]:
            aname = a.get("name", "")
            if "arm64" in aname:
                arm64_url = a.get("browser_download_url", arm64_url)
            elif "universal" in aname:
                universal_url = a.get("browser_download_url", universal_url)
            elif "armeabi" in aname:
                armeabi_url = a.get("browser_download_url", armeabi_url)

    msg = (
        f"🎉 <b>Vern TTS {tag} Is Officially Live!</b>\n\n"
        f"We're excited to announce the latest update for <b>Vern TTS</b> — the 100% private, "
        f"offline-first reading and speech engine for Android!\n\n"
        f"🌟 <b>WHAT'S NEW:</b>\n"
        f"{cleaned_highlights}\n\n"
        f"📦 <b>DIRECT DOWNLOADS:</b>\n"
        f"• <a href=\"{arm64_url}\">64-bit ARM APK (Recommended)</a>\n"
        f"• <a href=\"{universal_url}\">Universal Release APK</a>\n"
        f"• <a href=\"{armeabi_url}\">32-bit Legacy ARM APK</a>\n\n"
        f"🌐 <a href=\"https://fhes-tus.github.io/Vern-TTS/\">Official Website & Interactive Guide</a>"
    )

    markup = {
        "inline_keyboard": [
            [
                {"text": "⬇️ Download arm64 APK", "url": arm64_url},
                {"text": "🌐 Official Website", "url": "https://fhes-tus.github.io/Vern-TTS/"}
            ],
            [
                {"text": "⭐️ GitHub Repository", "url": f"https://github.com/{REPO}"},
                {"text": "📋 Full Changelog", "url": f"https://github.com/{REPO}/releases/tag/{tag}"}
            ]
        ]
    }

    url = f"https://api.telegram.org/bot{TOKEN}/sendMessage"
    payload = json.dumps({
        "chat_id": CHANNEL_ID,
        "text": msg,
        "parse_mode": "HTML",
        "reply_markup": markup
    }).encode("utf-8")

    req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as resp:
            res_json = json.loads(resp.read().decode("utf-8"))
            if res_json.get("ok"):
                msg_id = res_json["result"]["message_id"]
                print(f"Successfully broadcasted to {CHANNEL_ID} (Message ID: {msg_id})")
                
                # Pin message
                pin_url = f"https://api.telegram.org/bot{TOKEN}/pinChatMessage"
                pin_payload = json.dumps({"chat_id": CHANNEL_ID, "message_id": msg_id}).encode("utf-8")
                pin_req = urllib.request.Request(pin_url, data=pin_payload, headers={"Content-Type": "application/json"})
                with urllib.request.urlopen(pin_req) as pin_resp:
                    print("Message pinned successfully:", pin_resp.read().decode("utf-8"))
            else:
                print("Broadcast failed:", res_json)
    except urllib.error.HTTPError as e:
        print(f"HTTP Error {e.code}:", e.read().decode("utf-8"))
    except Exception as e:
        print("Error sending broadcast:", e)

if __name__ == "__main__":
    main()
