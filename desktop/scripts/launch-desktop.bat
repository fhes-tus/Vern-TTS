@echo off
cd /d "%~dp0\.."
echo %DATE% %TIME% Starting >> "%TEMP%\bat_debug.log"
powershell.exe -ExecutionPolicy Bypass -File .\scripts\native.ps1 >> "%TEMP%\bat_debug.log" 2>&1
echo %DATE% %TIME% Exited with %ERRORLEVEL% >> "%TEMP%\bat_debug.log"
