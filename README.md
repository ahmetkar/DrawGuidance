# DrawGuidance (Çizim Rehberi)

- `ios/` — yerel SwiftUI iOS uygulaması (v49 Android özelliklerinin aktarımı).
- `android-v49` dalı — Android v49 kaynakları (başvuru).

iOS projesi XcodeGen ile üretilir: `cd ios && xcodegen generate`.
Derleme, simülatör testleri ve imzasız .ipa: `.github/workflows/ios.yml` (macOS runner).
