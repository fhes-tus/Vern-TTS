#!/usr/bin/env python3
"""
Autonomous Telegram Release Broadcaster for Vern TTS
Triggered by GitHub Actions on new release or executed locally.
"""

import os
import sys
import json
import re
import html
from pathlib import Path
import urllib.request
import urllib.parse
import urllib.error

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "")
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

def build_announcement(data):
    if not data or data.get("draft") or data.get("prerelease") or not data.get("tag_name"):
        raise ValueError("A published stable release is required")
    with Path(__file__).with_name("telegram_content.json").open(encoding="utf-8") as content_file:
        content = json.load(content_file)
    tag = data["tag_name"]
    release_url = f"https://github.com/{REPO}/releases/tag/" + urllib.parse.quote(tag, safe="")
    if tag.lstrip("v") == content["version"]:
        message = content["channel_post"]
    else:
        highlights = markdown_to_telegram_html(data.get("body", ""), max_bullets=5)
        # Whole rendered lines only: avoid cutting an HTML tag at Telegram's limit.
        if len(highlights) > 2400:
            highlights = "Full details are in the release notes below."
        message = "<b>Vern " + html.escape(tag) + "</b>\n\n" + (highlights or "A new Vern update is ready. Read the full release notes below.")
    keyboard = [
        [{"text":"Get it on Google Play", "url":content["play_url"]}],
        [{"text":"Full release notes", "url":release_url}, {"text":"Ask Vern Bot", "url":"https://t.me/VernTTS_bot"}],
        [{"text":"Website", "url":"https://fhes-tus.github.io/Vern-TTS/"}]
    ]
    assets = [asset for asset in data.get("assets", [])
              if asset.get("name", "").endswith(".apk")
              and asset.get("browser_download_url", "").startswith(f"https://github.com/{REPO}/releases/download/")]
    universal = next((asset for asset in assets if "universal" in asset["name"].lower()), None)
    if universal:
        keyboard.append([{"text":"Alternative: GitHub APK", "url":universal["browser_download_url"]}])
    return message, {"inline_keyboard":keyboard}


def main():
    if not TOKEN:
        sys.exit("Set TELEGRAM_BOT_TOKEN in the environment.")
    data = get_event_data() or get_release_data(os.environ.get("RELEASE_TAG"))
    try:
        message, markup = build_announcement(data)
    except ValueError as error:
        sys.exit(str(error) + "; no announcement sent.")
    payload = {"chat_id":CHANNEL_ID, "text":message, "parse_mode":"HTML",
               "disable_web_page_preview":True, "reply_markup":markup}
    request = urllib.request.Request(f"https://api.telegram.org/bot{TOKEN}/sendMessage",
        data=json.dumps(payload).encode("utf-8"), headers={"Content-Type":"application/json"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            result = json.load(response)
        if not result.get("ok"):
            sys.exit("Telegram did not accept the announcement.")
        message_id = result["result"]["message_id"]
        # Do not retry sendMessage after a pin failure; preserve its accepted message ID.
        print(f"Channel announcement sent: {CHANNEL_ID}/{message_id}")
        pin = urllib.request.Request(f"https://api.telegram.org/bot{TOKEN}/pinChatMessage",
            data=json.dumps({"chat_id":CHANNEL_ID,"message_id":message_id,"disable_notification":True}).encode("utf-8"),
            headers={"Content-Type":"application/json"})
        with urllib.request.urlopen(pin, timeout=30) as response:
            if not json.load(response).get("ok"):
                sys.exit("Announcement sent, but Telegram did not confirm the pin.")
        print("Announcement pinned.")
    except urllib.error.HTTPError as error:
        sys.exit("Telegram returned HTTP " + str(error.code) + "; check the channel before retrying.")
    except Exception as error:
        sys.exit("Telegram request failed (" + type(error).__name__ + "); check the channel before retrying.")


if __name__ == "__main__":
    main()
