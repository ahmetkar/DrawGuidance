@echo off
title DrawGuidance - Sunucuyu Durdur
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\sunucu.ps1" durdur
echo.
pause
