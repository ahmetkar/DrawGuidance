# DrawGuidance — proje paketi (v41)

_2026-10-24. Bu paket, projeyi hiç bilmeyen yeni bir Claude sohbetinin kaldığı yerden devam edebilmesi için hazırlandı._

**Proje açıklaması:** Bu proje mobil tabanlı, bulut (sunucu) ve yapay zekâ destekli bir çizim uygulamasıdır. Android uygulaması Kotlin + Jetpack Compose ile, sunucu Spring Boot + MongoDB ile yazılmıştır.

## Önce bunları okuyun (sırayla)

1. `YENI-SOHBET-DEVIR.md` — projenin tam özeti:
   - mimari, paketler ve önemli dosyalar,
   - derleme ve test komutları, test ipuçları,
   - çeviri iş akışı,
   - kullanıcının çalışma kuralları,
   - sürüm geçmişi, açık konular.
2. `belgeler/v41-durum.md` ve `belgeler/v40-devir-notu.md` — son iki sürümde yapılanlar (v39–v40 yayın, abonelik, sunucu ve Swarm bilgileri devir notunda).
3. Gerekirse `belgeler/v9-durum.md` … `v38-durum.md`: her sürümün istekleri, yapılanlar, testler ve dosyalar. `durum-ve-mimari.md` v8 dönemine ait eski bir mimari not; Node.js sunucusundan söz eder, artık Spring Boot kullanılıyor.

Devir belgesinde `docs/…` diye geçen belgeler bu pakette `belgeler/` klasöründe.

## İçerik

| Klasör / dosya | Ne |
|---|---|
| `android/` | Gradle projesi. Modüller: `:app`, `:core:model`, `:core:content`. `tools/i18n` çeviri araçlarını, `tools/modenet` ağ eğitim yardımcılarını içerir. Kaynak dil Türkçe; 7 dil (`res/values-*`, `resources/i18n/*.tsv`). |
| `backend/` | Spring Boot sunucusu: `users` ve katalog, Firebase jeton doğrulaması. `docker/` klasöründe MongoDB + API; kapı **4747**. |
| `Telefonda-Ac.bat`, `Emulatorde-Ac.bat`, `Sunucuyu-Baslat.bat`, `Sunucuyu-Durdur.bat`, `scripts/` | Windows betikleri. Bilgisayarda projenin kökü `C:\Users\ahmet\Documents\DrawGuidance`. |
| `belgeler/` | Sürüm belgeleri v9–v35, çizgi film araştırma notu, eski mimari notu. |

## Pakette olmayanlar

- **Derleme çıktıları:** `build/`, `.gradle/` klasörleri ve APK. Son APK: `DrawGuidance-v35-debug.apk`; bilgisayardaki proje klasöründe ve önceki sohbette var.
- **Bilgisayara özel dosyalar:**
  - `android/local.properties` (Android SDK yolu).
  - `backend/docker/.env`: veritabanı parolaları; ilk çalıştırmada `.env.example` dosyasından üretilir.
- **Pakette olan ama gizli olmayan dosyalar:** `android/app/google-services.json` (Firebase yapılandırması) ve `android/app/keystore/debug.keystore` (ortak hata ayıklama anahtarı; SHA-1'i Firebase'de kayıtlı).

## Hızlı başlangıç (yeni sohbet için)

```
cd android
./gradlew :app:assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # ~600 test (Robolectric), uzun sürer
./gradlew :app:lintDebug              # 0 hata olmalı
cd ../backend && ./gradlew test       # Testcontainers: Docker gerekir
```

Gereken araçlar: JDK 21 (hedef 17), Android SDK 36, Gradle sarmalayıcısı (8.14.3).
