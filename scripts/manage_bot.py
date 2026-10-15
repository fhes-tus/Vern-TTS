#!/usr/bin/env python3
"""
Telegram Bot Manager for Vern TTS (@VernTTS_bot)
Utility for setting webhooks, inspecting bot health, and clearing webhooks.
"""

import os
import sys
import json
import argparse
import re
import urllib.request
import urllib.parse
import urllib.error

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "")

def api_call(method, payload=None):
    url = f"https://api.telegram.org/bot{TOKEN}/{method}"
    headers = {"Content-Type": "application/json"}
    data = json.dumps(payload).encode("utf-8") if payload else None
    req = urllib.request.Request(url, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        print(f"Error ({e.code}): {body}")
        return None
    except Exception as e:
        print(f"Request failed: {type(e).__name__}")
        return None

def check_status():
    print("=" * 55)
    print("🤖 Vern TTS Bot Status Check")
    print("=" * 55)
    me = api_call("getMe")
    if me and me.get("ok"):
        res = me["result"]
        print(f"Name:     {res.get('first_name')}")
        print(f"Username: @{res.get('username')}")
        print(f"Bot ID:   {res.get('id')}")
        print("Status:   Token Valid & Active ✅")
    else:
        print("Status:   Invalid Bot Token ❌")
        return

    print("-" * 55)
    hook = api_call("getWebhookInfo")
    if hook and hook.get("ok"):
        res = hook["result"]
        url = res.get("url")
        pending = res.get("pending_update_count", 0)
        last_err = res.get("last_error_message")
        last_err_date = res.get("last_error_date")

        if url:
            print(f"Mode:            Webhook Active 🌐")
            print(f"Webhook URL:     {url}")
            print(f"Pending Updates: {pending}")
            if last_err:
                print(f"Last Error:      {last_err} (timestamp: {last_err_date})")
            else:
                print("Webhook Health:  Healthy (0 errors) ✅")
        else:
            print("Mode:            No Webhook Configured (Polling Mode / Inactive)")
            print(f"Pending Updates: {pending}")
            print("\n💡 NOTE: If you are NOT running 'python scripts/bot_polling.py',")
            print("   your bot is currently OFFLINE because no webhook is receiving updates.")
    print("=" * 55)

def set_webhook(url):
    parsed = urllib.parse.urlparse(url)
    secret = os.environ.get("TELEGRAM_WEBHOOK_SECRET", "")
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
        sys.exit("Webhook must be a credential-free HTTPS URL.")
    if not re.fullmatch(r"[A-Za-z0-9_-]{32,256}", secret):
        sys.exit("Set a random TELEGRAM_WEBHOOK_SECRET of 32–256 supported characters.")
    print(f"Setting webhook to: {url}...")
    payload = {
        "url": url,
        "allowed_updates": ["message", "callback_query"],
        "secret_token": secret
    }
    res = api_call("setWebhook", payload)
    if not (res and res.get("ok")):
        try:
            import socket
            hostname = urllib.parse.urlparse(url).hostname
            if hostname:
                ip = socket.gethostbyname(hostname)
                print(f"Retrying with resolved IP ({ip})...")
                payload["ip_address"] = ip
                res = api_call("setWebhook", payload)
        except Exception:
            pass

    if res and res.get("ok"):
        print("✅ Webhook set successfully! The bot is now running 24/7.")
    else:
        print(f"❌ Failed to set webhook: {res}")

def delete_webhook():
    print("Deleting webhook...")
    res = api_call("deleteWebhook", {"drop_pending_updates": False})
    if res and res.get("ok"):
        print("✅ Webhook deleted! You can now run long-polling via 'python scripts/bot_polling.py'.")
    else:
        print(f"❌ Failed to delete webhook: {res}")

def register_commands():
    print("Registering bot slash commands...")
    commands = [
        {"command": "start", "description": "Welcome & Workstation Overview"},
        {"command": "download", "description": "Get latest verified APK releases"},
        {"command": "changelog", "description": "Latest published release notes"},
        {"command": "milestone", "description": "Upcoming 2.6.2 improvements"},
        {"command": "customize", "description": "Upcoming appearance options"},
        {"command": "security", "description": "Security and update compatibility"},
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
        print("✅ Bot commands registered successfully with Telegram!")
    else:
        print(f"❌ Failed to register commands: {res}")

def main():
    parser = argparse.ArgumentParser(description="Manage Vern TTS Telegram Bot")
    parser.add_argument("--status", action="store_true", help="Check current bot & webhook status")
    parser.add_argument("--set-webhook", type=str, metavar="URL", help="Set webhook URL (e.g. Cloudflare Worker URL)")
    parser.add_argument("--delete-webhook", action="store_true", help="Clear webhook for local polling")
    parser.add_argument("--setup-commands", action="store_true", help="Register slash commands with Telegram")
    args = parser.parse_args()
    if not TOKEN:
        sys.exit("Set TELEGRAM_BOT_TOKEN in the environment.")

    if args.set_webhook:
        set_webhook(args.set_webhook)
    elif args.delete_webhook:
        delete_webhook()
    elif args.setup_commands:
        register_commands()
    else:
        check_status()

if __name__ == "__main__":
    main()
