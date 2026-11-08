@echo off
title DrawGuidance - Sunucuyu Baslat
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\sunucu.ps1" baslat
echo.
pause
