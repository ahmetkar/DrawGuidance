# DrawGuidance Android

Kotlin + Jetpack Compose (Material 3) + Room (SQLite) + Retrofit + Koin.

## ÖNEMLİ — bu proje henüz gerçek bir derlemeden geçmedi

Bu kod, Anthropic'in bulut çalışma alanında (Android SDK/Gradle/Maven'a ağ
erişimi olmayan bir Linux sandbox'ta) yazıldı. `dl.google.com`,
`maven.google.com`, `repo1.maven.org`, `services.gradle.org` gibi adresler bu
ortamın ağ politikası tarafından engellendiği için **Gradle bağımlılıkları
hiç indirilemedi ve `gradlew build` bir kez bile çalıştırılamadı.** Kod
dikkatle ve güncel, birbirine uyumlu (bilinen) kütüphane sürümleriyle
yazıldı, ancak gerçek ilk derleme sizin bilgisayarınızda yapılacak ve küçük
hatalar (örn. bir ikon adı, bir API imzası) çıkması olası — bunları birlikte,
gerçek derleme çıktısına bakarak hızlıca düzelteceğiz.

## Proje Yapısı

- `app/src/main/java/com/karapp/drawguidance/`
  - `data/local` — Room (SQLite): `CanvasEntity`, `CanvasDao`, `AppDatabase`
  - `data/remote` — Retrofit `ApiService` + DTO'lar
  - `data/model` — Domain modelleri (`Stroke`, `CanvasUi`, ...)
  - `data/repository` — `CanvasRepository` (offline-first + senkron mantığı)
  - `di` — Koin modülü (`AppModule.kt`)
  - `ui/theme` — Material 3 tema
  - `ui/navigation` — Navigation-Compose grafiği
  - `ui/screens/canvaslist` — Tuval Listesi ekranı + modaller (yeni tuval,
    tuval ayarları) + context/üst menü
  - `ui/screens/canvasedit` — Tuval Düzenleme ekranı, fırça seçim modalı, yan
    ayar paneli, çizim motoru (`DrawingCanvas`)

## Gradle Wrapper Hakkında

Bu depoda `gradlew` / `gradlew.bat` **kasıtlı olarak yok** — wrapper jar'ı
(`gradle-wrapper.jar`) küçük bir ikili dosya ve bu ortamdan indirilemedi.
Bilgisayarınızda bağımsız (standalone) bir Gradle kurulduktan sonra, proje
kök dizininde şunu çalıştırmanız yeterli:

```powershell
gradle wrapper --gradle-version 8.7 --distribution-type all
```

Bu komut `gradlew.bat`, `gradlew` ve `gradle/wrapper/gradle-wrapper.jar`
dosyalarını sizin ortamınıza göre doğru şekilde oluşturur. Bundan sonra tüm
komutları `.\gradlew.bat ...` ile çalıştırabilirsiniz.

## Backend Bağlantısı

`app/build.gradle.kts` içinde `API_BASE_URL = "http://10.0.2.2:4747/"` olarak
ayarlandı — bu, Android **emülatörden** host makinedeki (sizin bilgisayarınız)
`localhost:4747` backend'ine ulaşmanın standart yoludur. Gerçek bir cihazda
test ederseniz bu adresi bilgisayarınızın yerel ağ IP'siyle değiştirmeniz
gerekir.

## Bilinen Basitleştirmeler (MVP)

- Silgi fırçası, gerçek "clear" yerine arkaplan rengiyle çizim yapar.
- Stroke'lar düz çizgi segmentleriyle (moveTo/lineTo) çizilir; eğri
  yumuşatma (smoothing) yok.
- Kimlik doğrulama yok; tüm veriler backend'de sabit `default-user`'a ait.
- Şema değişikliklerinde Room, veriyi silip yeniden oluşturur
  (`fallbackToDestructiveMigration`) — prod öncesi gerçek migration eklenmeli.
