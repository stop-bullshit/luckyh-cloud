@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0support\scripts\start-services.ps1" %*
if errorlevel 1 (
    echo Startup failed. See .local\logs for details.
    pause
    exit /b 1
)
