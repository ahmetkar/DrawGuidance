@echo off
title DrawGuidance - Yerel-Sunucu-Durumu
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0yerel-sunucu.ps1" durum
echo.
pause
