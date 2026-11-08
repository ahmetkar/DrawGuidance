@echo off
title DrawGuidance - Yerel-Sunucuyu-Durdur
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0yerel-sunucu.ps1" durdur
echo.
pause
