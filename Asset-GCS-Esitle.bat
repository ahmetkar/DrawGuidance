@echo off
rem DrawGuidance - Asset disa aktarma + Google Cloud Storage esitleme
rem   Onizleme (kuru):   Asset-GCS-Esitle.bat
rem   Gercek yukleme:    Asset-GCS-Esitle.bat -Upload
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Asset-GCS-Esitle.ps1" %*
