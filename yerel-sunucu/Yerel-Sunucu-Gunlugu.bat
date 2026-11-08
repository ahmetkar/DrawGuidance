@echo off
title DrawGuidance - Yerel-Sunucu-Gunlugu
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0yerel-sunucu.ps1" gunluk
echo.
pause
