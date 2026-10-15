@echo off
setlocal
cd /d "%~dp0"
title Vern TTS Telegram Bot Runner (@VernTTS_bot)
if not defined TELEGRAM_BOT_TOKEN (
    echo Set TELEGRAM_BOT_TOKEN in your environment first.
    exit /b 1
)

echo =======================================================
echo   Vern TTS Autonomous Telegram Bot (@VernTTS_bot)
echo =======================================================
echo.

python -c "import sys; sys.exit(0)" 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Error: Python is not installed or not in your PATH.
    pause
    exit /b 1
)

echo Starting continuous bot polling...
echo Press Ctrl+C at any time to stop.
echo.

:loop
python scripts\bot_polling.py
echo.
echo Bot polling stopped or encountered network disconnect.
echo Restarting in 5 seconds... (Press Ctrl+C to exit)
timeout /t 5 /nobreak >nul
goto loop

endlocal
