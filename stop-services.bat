@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0support\scripts\stop-services.ps1" %*
if errorlevel 1 (
    echo Stop failed. Check the messages above.
    pause
    exit /b 1
)
