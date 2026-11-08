@echo off
title DrawGuidance - Yerel-Sunucuyu-Baslat
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0yerel-sunucu.ps1" baslat
echo.
pause
