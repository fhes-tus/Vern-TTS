@echo off
setlocal
cd /d "%~dp0"
copy /b "Vern.aab.part1"+"Vern.aab.part2"+"Vern.aab.part3"+"Vern.aab.part4" "Vern-2.8.2-build49-unsigned.aab"
if errorlevel 1 exit /b 1
echo.
echo Created Vern-2.8.2-build49-unsigned.aab
echo Expected SHA256: d2dae9f8015707e3c97adc86215bff13da162ecd768460a38ce62efc527011c0
certutil -hashfile "Vern-2.8.2-build49-unsigned.aab" SHA256
echo.
echo This bundle is unsigned. Sign it with your upload key before submitting to Google Play.
pause
