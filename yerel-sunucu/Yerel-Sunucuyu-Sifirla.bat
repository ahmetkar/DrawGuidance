@echo off
title DrawGuidance - Yerel-Sunucuyu-Sifirla
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0yerel-sunucu.ps1" sifirla
echo.
pause
