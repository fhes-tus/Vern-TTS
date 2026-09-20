#!/usr/bin/env python3
"""
Weekly Classic Spotlight Broadcaster for Vern TTS
Rotates through the 35+ curated classics in Vern TTS and posts to @myreader_veritas.
"""

import os
import sys
import json
import datetime
import urllib.request
import urllib.parse
import urllib.error
import argparse

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "8285832720:AAHM20ABnUrB1VzLM81eUgkO6NdXnb7Je-o")
CHANNEL_ID = os.environ.get("TELEGRAM_CHANNEL_ID", "@myreader_veritas")
REPO = os.environ.get("GITHUB_REPOSITORY", "fhes-tus/Vern-TTS")

def load_books():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    json_path = os.path.join(script_dir, "classics_data.json")
    if os.path.exists(json_path):
        with open(json_path, "r", encoding="utf-8") as f:
            return json.load(f)
    return []

def send_photo_multipart(chat_id, photo_path, caption, reply_markup):
    url = f"https://api.telegram.org/bot{TOKEN}/sendPhoto"
    boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
    
    with open(photo_path, "rb") as f:
        photo_bytes = f.read()

    body = bytearray()
    
    def add_field(name, value):
        body.extend(f"--{boundary}\r\n".encode("utf-8"))
        body.extend(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("utf-8"))
        body.extend(f"{value}\r\n".encode("utf-8"))

    add_field("chat_id", chat_id)
    add_field("caption", caption)
    add_field("parse_mode", "HTML")
    add_field("reply_markup", json.dumps(reply_markup))

    # File part
    filename = os.path.basename(photo_path)
    body.extend(f"--{boundary}\r\n".encode("utf-8"))
    body.extend(f'Content-Disposition: form-data; name="photo"; filename="{filename}"\r\n'.encode("utf-8"))
    body.extend(b"Content-Type: image/jpeg\r\n\r\n")
    body.extend(photo_bytes)
    body.extend(b"\r\n")

    body.extend(f"--{boundary}--\r\n".encode("utf-8"))

    req = urllib.request.Request(
        url,
        data=bytes(body),
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"}
    )
    
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))

def main():
    parser = argparse.ArgumentParser(description="Post a weekly classic spotlight to Telegram.")
    parser.add_argument("--id", help="Specific book ID to spotlight (optional)")
    parser.add_argument("--dry-run", action="store_true", help="Print message without sending")
    args = parser.parse_args()

    books = load_books()
    if not books:
        print("Error: No classic books found in classics_data.json")
        sys.exit(1)

    selected = None
    if args.id:
        selected = next((b for b in books if b["id"] == args.id), None)
    
    if not selected:
        # Rotate by calendar week
        week_num = datetime.date.today().isocalendar()[1]
        idx = week_num % len(books)
        selected = books[idx]

    title = selected["title"]
    author = selected["author"]
    genre = selected["genre"]
    desc = selected["description"]
    quote = selected.get("quote", "")

    caption = (
        f"📚 <b>Weekly Classic Spotlight</b>\n\n"
        f"<b>{title}</b>\n"
        f"<i>by {author}</i>\n\n"
        f"🏷 <b>Category:</b> {genre}\n"
        f"💬 <i>\"{quote}\"</i>\n\n"
        f"📖 <b>Synopsis:</b>\n"
        f"{desc}\n\n"
        f"🎧 <i>Included completely free in Vern TTS with authentic cover art and offline human-like narration!</i>"
    )

    markup = {
        "inline_keyboard": [
            [
                {"text": "🤖 Chat with @VernTTS_bot", "url": "https://t.me/VernTTS_bot"},
                {"text": "⬇️ Download App", "url": f"https://github.com/{REPO}/releases/latest"}
            ],
            [
                {"text": "🌐 Official Website", "url": "https://fhes-tus.github.io/Vern-TTS/"}
            ]
        ]
    }

    # Find cover photo
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    cover_path = os.path.join(repo_root, "app", "src", "main", "assets", "covers", f"{selected['id']}.jpg")

    print(f"Spotlight Book: {title} by {author}")
    print(f"Cover path: {cover_path} (exists: {os.path.exists(cover_path)})")

    if args.dry_run:
        print("\n--- DRY RUN CAPTION ---")
        print(caption)
        return

    if not os.path.exists(cover_path):
        print(f"Warning: Cover not found at {cover_path}, sending as text only.")
        url = f"https://api.telegram.org/bot{TOKEN}/sendMessage"
        payload = json.dumps({
            "chat_id": CHANNEL_ID,
            "text": caption,
            "parse_mode": "HTML",
            "reply_markup": markup
        }).encode("utf-8")
        req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req) as resp:
            print("Message sent:", resp.read().decode("utf-8"))
    else:
        res = send_photo_multipart(CHANNEL_ID, cover_path, caption, markup)
        if res.get("ok"):
            print(f"Successfully posted spotlight for '{title}' to {CHANNEL_ID} (Message ID: {res['result']['message_id']})")
        else:
            print("Failed to post photo:", res)

if __name__ == "__main__":
    main()
