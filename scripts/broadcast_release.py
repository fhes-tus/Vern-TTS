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

def clean_body_text(body):
    """Clean markdown release notes for Telegram HTML, stripping hashes and technical noise."""
    if not body:
        return ""
    # Strip any SHA256 lines or hashes
    body = re.sub(r"[a-fA-F0-9]{64}", "", body)
    body = re.sub(r"(?i)sha-?256.*", "", body)
    
    # Filter lines
    clean_lines = []
    for line in body.splitlines():
        line = line.strip()
        if not line:
            continue
        if any(w in line.lower() for w in ["checksum", "sha256", "hash", "md5", "retrace"]):
            continue
        # Convert markdown bullets to bold/clean
        if line.startswith("- ") or line.startswith("* "):
            clean_lines.append(f"• {line[2:].strip()}")
        elif line.startswith("## "):
            clean_lines.append(f"\n<b>{line[3:].strip()}</b>")
        else:
            clean_lines.append(line)
            
    # Keep it punchy (at most 10 lines)
    if len(clean_lines) > 10:
        clean_lines = clean_lines[:10] + ["• ...and more improvements!"]
    return "\n".join(clean_lines)

def main():
    data = get_event_data()
    req_tag = os.environ.get("RELEASE_TAG")
    if not data:
        data = get_release_data(req_tag)
    
    tag = req_tag or (data.get("tag_name") if data else "v2.5.1")
    name = data.get("name", f"Vern TTS {tag}") if data else f"Vern TTS {tag}"
    body = data.get("body", "") if data else ""
    
    cleaned_highlights = clean_body_text(body)
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
