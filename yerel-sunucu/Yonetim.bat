@echo off
rem DrawGuidance v43 yonetim komutlari: Yonetim.bat yardim
rem Ornek: Yonetim.bat bildirim ekle --baslik "Merhaba" --aciklama "Yeni surum hazir"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0yerel-sunucu.ps1" yonetim %*
