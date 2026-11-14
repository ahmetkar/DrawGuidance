# DrawGuidance — yeni sohbet için devir belgesi (v49 sonrası)

> **v50 (2026-11-14): önce `v50-devir-notu.md`yi okuyun.** v50 Android + iOS + sunucu tek dalda (GitHub `ahmetkar/DrawGuidance`, `ios/v49-port`); iOS sürümü, alıştırmalar, Google/Apple giriş, StoreKit 2, istisna hesabının kaldırılması. Sunucu güncelleme: `backend/docs/v50-sunucu-guncelleme.md`.

_2026-11-02 (v49; v48/v47/v46 bölümleri önceki günler). Bu belge, hafızası olmayan yeni bir Claude sohbetinin projeyi kaldığı yerden sürdürebilmesi için yazıldı. **Önce `v49-durum.md`'yi, sonra `v48-durum.md`'yi, sonra `v47-durum.md`'yi, sonra `v46-durum.md`'yi, sonra `v45-devir-notu.md` ve `v45-durum.md`'yi**, sonra bu belgeyi, sonra `v44-durum.md` (claude.ai projesinde `claude/v44-durum.md`), `backend/docs/v44-yayin-rehberi.md`, `v43-durum.md`, `backend/docs/v43-dagitim.md`, `v42-durum.md`, `belgeler/v41-durum.md`, `belgeler/v40-devir-notu.md` ve `v38-durum.md` dosyalarını okuyun. v49 güvenlik/backend için ayrıca `v49-backend-gcs-rehberi.md` ve `v49-dptshell-guvenlik-rehberi.md`._

**v49 özeti:** versionCode **49** (versionName **1.7.0**), Room **v23** (değişmedi), sunucu değişmedi (44.0.0). Üç istek + güvenlik:

1. **Basit modlarda dikey ekrandaki üç menü tek satır; sığmazsa yatay kaydırılır** (alt satıra sarmaz). `components/V48SimpleMenus.kt` → `SimpleMenuColumn`: `FlowRow` yerine her menü tek `Row`, taşarsa iç `Row` `horizontalScroll`. Çiz menüsünde **geri al / yinele** satır başında **sabit** (kaydırılmaz, hep görünür).
2. **Çizgi romanda eklenen mekân arka planı bırakıldığı panele (ya da tuvale) sığar.** `CanvasEditViewModel.insertLocationBackground` panelin dörtgenini hesaplar (sınır dikdörtgeni = hedef, köşe noktaları = kırpma) → `ImageController.insertBackdrop(path, target, clip, onDone)`; `Backdrop.panelClip(stroke)` yalnız tam 4 köşeli dörtgende kırpma noktaları, değilse `null` (sınır dikdörtgenine sığdırma). Panel yoksa tüm tuval.
3. **Eğitimler:** v48 yenilik grupları (mod menüsü, Özellikler, Mekân arka planı) korunur ve testle doğrulanır (`TutorialScripts.VERSION = 48`, `TutorialV48.kt`, `strings_v48_tutorial.xml` 7 dil).
4. **Güvenlik:**
   - **freeRASP (Talsec Community 19.3.1)** çalışma zamanı koruması. `settings.gradle.kts` (Talsec Maven depoları), `app/build.gradle.kts` (bağımlılık), `security/TalsecGuard.kt` (yeni), `DrawGuidanceApp.onCreate` → `TalsecGuard.start`. **Politika:** hook (Frida/Xposed) ve kurcalama/geçersiz imza (`onHooks`, `onAppIntegrity`) → uygulamayı **kapat** (yalnız yayın; hata ayıklamada log). Öteki tehditler yalnız log. İmza hash'leri (Base64 SHA-256): release/yükleme + 3 Play App Signing (`SIGNING_HASHES`). AAR kendi ProGuard kurallarını içerir (R8 keep gerekmez). Robolectric'te `start()` erken döner (`Build.FINGERPRINT == "robolectric"`). **R8 korundu** (dProtect **eklenmedi** — "R8 kalsın"). freeRASP'ın yerel kütüphanesi `libts.so` (~21 MB/ABI) APK/AAB'yi büyüttü (universal APK ~148 MB; AAB cihaz başına böler).
   - **dpt-shell (v2.21.1)** DEX/APK koruması: ayrı, **deneysel** etiketli APK `DrawGuidance-v49-dptshell.apk` (release anahtarıyla imzalı → freeRASP imzayı tanır). dpt'nin iç RASP'ı kapatılmaya çalışıldı ama gömülen `risk_check_flags=7` kaldı → cihazda freeRASP ile çakışabilir. **Cihazda launch testi yapılmadan dağıtılmamalı** (bu ortamda cihaz yok). Test başarısızsa yalnız ana APK/AAB. Ana APK/AAB **değiştirilmedi**.

**v48 özeti:** versionCode 48 (1.6.0), Room **v23** (değişmedi), sunucu değişmedi (44.0.0). Menülerde gruplama yok (`TOOL_GROUPING = false`); gelişmiş modların yatay ekranında üstteki menü tek parça (`ToolRowSplitMode.NONE`); basit resim ve basit notun dikey ekranında üç menü (`components/V48SimpleMenus.kt`: ekle / çiz / seç ve düzenle, adlar simgenin yanında, v48'de sığmayan alt satıra — **v49'da tek satır + yatay kaydırma**), basit modlarda kement yok, çocukta 3B yok. Taşı ve Genişlet yine iki düğme (Düzenle satırında): Taşı eleman seçiliyken, Genişlet tuval seçiliyken (`MoveExpandRules`); v47'nin Taşı'da kenardan genişletmesi geri alındı. Çizgi roman şablonları yalnız panel ve balonlu üç boş şablon (`comic_blank_pages`, `comic_blank_manga`, `comic_blank_webtoon`; 4 sayfa, her sayfa başka düzen; üretici `V48TemplateExport`). Karakter kategorileri değişti (13 karakter), beyaz tulumlu astronotun bozuk ilk pozu silindi (`ComicPacks.REMOVED_ITEMS`). Efektler penceresinde yalnız Efektler (resimli) ve Odak çizgileri.

**v47 özeti:** versionCode 47 (1.5.0), Room **v23** (değişmedi), sunucu değişmedi (44.0.0). Yeni menü sistemi: "Fırça ayarları" → **Özellikler** (seçili aracın ayarları; ayarı olmayan araçta boş uyarı; `components/V47PanelMenu.kt`: `PanelMenu`, `Properties`, `PanelRail`); gelişmiş modlarda mod menüsü **Seç, Çiz, Ekle, Düzenle** (`EditMode.Toolbar`), Taşı ve Genişlet tek düğme ("Taşı ve genişlet", Düzenle satırında; `MoveTool` kâğıt kenarından genişletir; `EditMode.menuFamily`); Elemanlar/Katmanlar/Özellikler dikeyde mod menüsünün son grubu (`ModeSelector extra`), yatayda sağda açılır alanın soluna yapışık ray. Çocuk menüsü sırası `KidsMenu.FRONT`; çocuk şekillerinde 6–9 yaşa göre boyanacak desenler (`kids/KidsDetail.kt`). Çizgi roman şablonları yenilendi (kamu malı kaynaklar). Basit notta 3 şablon. Üretici `V47TemplateExport`. Testlerde `EditorTestBase.fittedScreen/fittedCanvas`.

**v46 özeti:** versionCode 46 (1.4.0), Room **v23**, sunucu 44.0.0. 5+5 hazır şablon, Lunara kaldırıldı (3 Premium elf büyücü bekliyor), v46 eğitim yenilikleri, formülle element/serbest cisim/makara/kaldıraç, iki parçalı üst menü, panel hatası düzeltildi. R8 tam kipi kapalı.

**v45 özeti:** versionCode 45 (1.3.0), Room **v23**. Çocuk boya fırçası, büyük renkli çocuk menüsü, çizgi roman kapağı, dikeyde düğme adları, grafik formülleri, parametreli bilim şekilleri, kimya/fizik, Panel ekle, dörderli menü grupları, tablette sürüklenen pencereler. **R8 tam kipi kapalı kalmalı** (Android 10 Ayarlar çökmesi nedeni).

**v44 özeti:** versionCode 44 (1.2.0), Room **v23** (`kids_lock`), sunucu **44.0.0**. Yayında yalnız `https://drawapi.ahmetkar.com/`. Çocuk kilidi, kalemde çizgi eğme, 3B GLB dışa aktarma Premium, geçici istisna hesabı, çocuk menüsünde Fırça ayarları, kalem dolgusu. `stack.yml`'ye v43 dağıtım sırları.

**v43 özeti:** versionCode 43 (1.1.0), Room **v22**, sunucu **43.0.0**. Firebase'siz içerik dağıtımı (cihaz kaydı HMAC+AES-GCM, ETag yoklama, uzun yoklama, CLI). Paket resimleri GCS kovası `dgpackets` (imzalı V4 adresler). Arkaplan ile Nesneyi Uyumla, Nesneyi Mekâna Yerleştir, Play içi güncelleme.

**v42 özeti:** versionCode 42, Room **v21** (çizgi roman tema paketleri), 129 genel şekil, 471 efekt + 479 nesne, Kaydedilenler, tek kaydetme penceresi (CMYK), web sitesi + yönetim paneli, yerel sunucu paketi.

**v41 özeti:** v40 dalları birleşti. Paket `com.karapp.drawguidance`, Room v20, minSdk 23, R8. Sunucu `https://drawapi.ahmetkar.com`, site `drawguidance.ahmetkar.com`.

---

## 1. Proje nedir

**DrawGuidance**, Android için çizim uygulamasıdır. Kotlin ve Jetpack Compose ile yazılmıştır. Arayüzün kaynak dili Türkçedir; v34'ten beri 7 dilde çalışır. Ana özellikler:

- **Tuval türleri:**
  - Gelişmiş: Resim (`ART`), Not alma (`NOTES`), Çizgi roman (`COMIC`), ayrıca 3B şekil oluşturma.
  - Basit: Basit resim (`SIMPLE_ART`), Basit not (`SIMPLE_NOTES`), Çocuklar için çizim (`KIDS`).
- **Çizim:** Fırçalar, silgi, kalem/yol aracı, katmanlar, sayfalar; kılavuzlar, perspektif, arka plan (renk, desen, resim, gradyan).
- **Elemanlar:** Vektör şekiller (550+), diyagramlar, formüller, yazı, resim ekleme/düzenleme, 3B modeller (GLB/glTF), çizgi roman balonu/paneli, çocuk boyama şekilleri, maskot "Boyacık".
- **Yapay zekâ (cihazda):** Çizimi şablonlarla eşleştirip şekil/diyagram/yazı önerir; her mod için ayrı sinir ağı; el yazısı tanıma. Kullanıcı kabul/retlerinden cihazda öğrenir.
- **Hesap ve eşitleme:** Google ile giriş (Firebase Auth), hesap silme; sunucu işleri çevrimdışı olay tablosundan (`sync_events`). **Kullanıcının çizimleri sunucuya gitmez.**
- **Dışa aktarma:** PNG/JPEG/SVG/PDF, galeri, paylaşma.
- **Eğitim modu:** her moda ilk girişte adım adım anlatım.
- **Güvenlik (v49):** freeRASP çalışma zamanı koruması (hook/kurcalamada kapanır); yan yükleme için isteğe bağlı dpt-shell DEX kabuğu.

Sunucu (backend) Spring Boot + MongoDB. Yalnız kullanıcı kaydı, hazır katalog ve hata raporları tutar.

---

## 2. Dosyalar nerede

| Yer | İçerik |
|---|---|
| Kullanıcının bilgisayarı: `C:\Users\ahmet\Documents\DrawGuidance\` (tam yol kullanın) | `android\` (Gradle projesi), `backend\` (Spring Boot), `scripts\`, `*.bat`, `vNN-durum.md` belgeleri, APK'lar, `logs\`. Eski `server\` (v6 Node.js) kullanılmıyor. |
| claude.ai Projesi "DrawGuidanceProject" | `claude/v9…v49-*.md` sürüm belgeleri, `claude/YENI-SOHBET-DEVIR.md`, `claude/v49-backend-gcs-rehberi.md`, `claude/v49-dptshell-guvenlik-rehberi.md`. Her sohbette görünür. |
| Son teslim (v49) | İmzalı `DrawGuidance-v49.apk` ve `DrawGuidance-v49.aab` (R8 + freeRASP; versionCode 49, 1.7.0) + deneysel `DrawGuidance-v49-dptshell.apk` + `v49-durum.md` ve iki rehber: sohbette teslim edildi. Kaynak değişiklikleri 2026-11-02'de bilgisayara yazıldı (eskiler `_eski_v49\`'te). APK/AAB bilgisayara yazılmadı. Önceki teslimler (v48/v47/v46…) sohbette verildi; v48 kaynağı bilgisayara yazıldı. Bulutta değişiklikler git'te `e7ce7d1` üstünde staged (commit yok). |
| Sırlar (git'te yok, yazdırılmaz) | Bilgisayarda `yayin-anahtari\`: yükleme anahtarı `drawguidance-upload.jks` ve `keystore.properties`, `sunucu.env`, `play-service-account.json`, `gcs-service-account.json`. Derleme ortamında `android/app/keystore/drawguidance-upload.jks` + `release.properties`, `backend/docker/.env`, `backend/docker/secrets/play.json`. |
| Web sitesi | `web/` (Next.js, Vercel). Geliştirici adı `KarApplication`, iletişim `ahmetkar.1258@gmail.com`. |

**Yeni sohbette çalışmak için:** Claude masaüstü uygulamasında `Documents\DrawGuidance` klasörünü bağlayın. Bulut çalışma alanında derlemek için proje oraya kopyalanmalıdır.

---

## 3. Teknoloji yığını

- **Android:** AGP 8.13.2, Gradle 8.14.3, Kotlin 2.2.21, JDK 17 hedef (derleme JDK 21). Compose BOM 2025.11.01, Material3 1.4.0. Room (şema **v23**, elle göçler `ALL_MIGRATIONS`), Koin, Navigation-Compose. Play Billing 8, Google Mobile Ads + UMP. Retrofit + OkHttp + kotlinx.serialization, WorkManager. Credential Manager + googleid + firebase-auth + play-services-auth. google-services eklentisi 4.4.2.
  - **v49 güvenlik:** `com.aheaditec.talsec.security:TalsecSecurity-Community:19.3.1` (freeRASP); Talsec Maven depoları `settings.gradle.kts`'de.
- **minSdk 23, targetSdk 36.** Paket `com.karapp.drawguidance`, versionCode **49**, versionName **1.7.0** (Play'e her yüklemede versionCode artar). Yayında R8 küçültme + kaynak küçültme + kod iyileştirme açık, **R8 tam kipi kapalı** (`android.enableR8.fullMode=false`, kaldırmayın); `.aab`'de dil bölme kapalı.
- **Modüller** (`android/settings.gradle.kts`): `:app`, `:core:model` (saf Kotlin belge modeli), `:core:content` (saf Kotlin vektör/şablon/formül/3B/el yazısı/katalog/mod ağları).
- **Sunucu** (`backend/`): Spring Boot (Java 21), MongoDB 7, Firebase ID jetonu doğrulama. Testcontainers; Docker Compose.
- **Ortak hata ayıklama anahtarı:** `android/app/keystore/debug.keystore` (SHA-1 `0E:16:DE:72:…:A0:17`). Firebase projesi: `drawguidance`.

---

## 4. Android uygulaması — paketler ve önemli dosyalar

Kök: `android/app/src/main/java/com/karapp/drawguidance/`

- **Giriş noktaları:** `DrawGuidanceApp.kt` (Application; Koin, ısınma iş parçacığı, dil, **v49: `security.TalsecGuard.start`**), `MainActivity.kt`.
- **`security/TalsecGuard.kt`** (v49) — freeRASP başlatma; `SIGNING_HASHES`, tehdit politikası, Robolectric atlama.
- **`di/AppModule.kt`** — bütün bağımlılıklar.
- **`data/local/`** — Room `AppDatabase.kt` (v23, `ALL_MIGRATIONS`); tablolar `canvases`, `saved_images`, `sync_events`, `catalog_vectors`, `catalog_nets`, `kids_lock`…
- **`data/account/`** — `Auth.kt` (Credential Manager + eski Google Sign-In yedek), `AccountRepository.kt`.
- **`data/sync/`**, **`data/inbox/`** (v43 dağıtım), **`data/catalog/`**, **`data/remote/v1/`**, **`data/settings/`**.
- **`i18n/`**, **`ui/i18n/LocalizedText.kt`** (uygulamanın `Text` bileşeni; `I18n.tr`).
- **`ui/screens/canvasedit/`** — düzenleyici:
  - `CanvasEditScreen.kt` (çok büyük; "method too large" sınırına yakın), `CanvasEditViewModel.kt` (`EditMode`; **v49 `insertLocationBackground` panel kırpma**).
  - `state/`: `ImageController` (**v49 `insertBackdrop` clip; `Backdrop.panelClip`**), `ShapeController`, `LayerController`, `PageController`, suggester'lar…
  - `components/`: **`V48SimpleMenus.kt` (v49 tek satır + yatay kaydırma)**, `EditorToolbar.kt`, `V47PanelMenu.kt`, `EditSheets.kt`, `DrawingCanvas.kt`, `ToolHelp.kt`.
  - `tutorial/`: `TutorialScripts.kt` (`VERSION = 48`), `TutorialV48.kt`, `TutorialV46.kt`, eğitim senaryoları.
- **Kaynaklar:** `res/values/strings*.xml` (sürüme bölünmüş; **`strings_v48_tutorial.xml` 7 dil**), `res/values-xx/strings.xml`, `resources/i18n/<dil>.tsv`.

`android/core/content/.../content/`: `shapes/`, `library/`, `catalog/Catalog.kt` (`CatalogInfo.VERSION = 38000`), `comic/`, `formula/`, `graph/`, `model3d/`, `ink/`, `trace/`, `paper/`, `shapes/ShapeNet.kt`.

---

## 5. Sunucu (backend)

- **Paketler:** `server/user`, `server/catalog`, `server/report`, `server/health`, `server/security`, `server/config`, `server/distribution` (v43). Sözleşme `backend/docs/v37-api.md`.
- **Kapı 4747.** Sürüm **44.0.0**. Canlı `https://drawapi.ahmetkar.com`.
- **v43 dağıtım ortamı:** `GCS_BUCKET=dgpackets`, `GCS_SERVICE_ACCOUNT_JSON`, `DEVICE_DATA_KEY`. Belge `backend/docs/v43-dagitim.md`. **v49 backend/GCS rehberi:** `v49-backend-gcs-rehberi.md` (v49 sunucu değişikliği gerektirmez; bekleyen GCS kurulumu orada toplandı).
- **Testler:** `./gradlew test` (Testcontainers, Docker gerekir).

---

## 6. Önemli tasarım kararları

- Kullanıcının çizimleri/3B/resimleri **yalnız cihazda**. Sunucuda yalnız kullanıcı kaydı ve katalog.
- Hesap işleri yalnız internet varken görünür; işler önce `sync_events`'e yazılır.
- **Dil:** ilk açılışta sistem dili; `language_settings` esas; dil değişince uygulama yeniden başlar. Arapçada arayüz RTL, düzenleyiciler LTR.
- Tanınmış karakterler/markalar kullanılmaz; hep özgün tasarım.

---

## 7. Derleme ve test

```
cd android
./gradlew :app:compileDebugKotlin --offline -q
./gradlew :app:testDebugUnitTest --tests 'com.karapp.drawguidance.V49*'
./gradlew :core:content:test :core:model:test
./gradlew :app:lintRelease :app:assembleRelease :app:bundleRelease --max-workers=2   # imzalı APK + .aab
cd ../backend && ./gradlew test
```

- Bulut ortamında bellek az: `--max-workers=2`; uzun işler `setsid nohup` ile arka planda.
- **v49: R8 yayın derlemesi daha büyük** (freeRASP `libts.so` ~21 MB/ABI × 4). AAB cihaz başına böler. `assembleRelease` + `bundleRelease` birlikte çalışınca daemon OOM olabildi; **`bundleRelease`'i ayrı, `-Dorg.gradle.jvmargs=-Xmx3g` ile** çalıştırmak yardımcı oldu.
- **freeRASP + Robolectric:** `TalsecGuard.start` Robolectric'te erken döner (yoksa `androidx.security.BNatives` JVM'de yüklenemez → testler düşer).
- Tam takım ~95 dk; yerel sunucu (127.0.0.1:4747) / ağ gerektiren testler (ör. `CanvasFlowTest.*WithoutCloud`) bulut ortamında ortamsal olarak düşer (yerel MongoDB+sunucu yoksa). v49'da **değişikliklerin blast-radius'u** (20 sınıf: V48/V37SimpleModes/V33SimpleMode/V41Tutorials/V46ToolRowSplit/V45Portrait/V45Kids/V47Menu/… ) yeşil.
- Pencereyi PID ile durdurun (`pkill -f` kendi kabuğunu da öldürür).

**Robolectric ipuçları:** `TestDrawGuidanceApp` (`CanvasFlowTest.kt`); taban sınıflar `EditorTestBase` → `V33EditorBase`; `waitFor`, `exists(tag)`, `shot(name)`; kaplanan düğmede `performSemanticsAction(OnClick)`; **sabit hap (geri al/yinele) `performScrollTo` istemez** (v49: yatay kaydırma dışında). Strings XML'de `\'`.

---

## 8. Çeviri iş akışı (yeni metin eklerken şart)

1. Türkçe metni `res/values/strings_vNN.xml`'e ekleyin.
2. Aynı adı 6 dilin `values-xx/strings.xml`'ine çevirisiyle ekleyin (yoksa lint `MissingTranslation`).
3. Kodda Türkçe metin → `resources/i18n/<dil>.tsv`'ye `Türkçe<TAB>çeviri`. Değişkenli: `I18n.trf`.
4. `V34I18nTablesTest` denetler. Ayrıntı `android/tools/i18n/README.md`.

---

## 9. Kullanıcıyla çalışma kuralları (Ahmet)

- Türkçe yanıt verin. Komutları Claude çalıştırır; kullanıcıya betik verilmez. PowerShell tercih edilir.
- **Her turun sonunda:** değişen dosyalar sahnelenir (`vNNsrc/`, `vNN-kaynak.tar.gz`); `vNN-durum.md` yazılır ve projeye `claude/vNN-….md` eklenir; **APK/AAB teslim edilir**; bilgisayara yalnız kullanıcı söylediğinde yazılır (nereye ne yazıldığı söylenir).
- **Bilgisayara yazma:** değişenler tek `.tar.gz`, `SendUserFile` → `device_commit_files` → `device_bash` ile geçici klasöre açılıp `cp -r --no-preserve=mode,ownership,timestamps` ile üstüne; `cmp` ile doğrula. Eskiler silinmez, `_eski_vNN\`'ye taşınır. Tek dosya sınırı 30 MB (APK `split -b 18000000`).
- Bilgisayardaki fazladan `CanvasDto.kt`'ye dokunmayın. Windows ayarlarını değiştirmeyin. Yalnız isteneni silin.
- İnternetten resim izlemeyin, telifli karakter çizmeyin. **Gizli anahtarları yazdırmayın.**
- Her istenen özellik için test yazılır. Ekran görüntüleriyle tasarım kontrol edilir.

---

## 10. Sürüm geçmişi (özet)

| Sürüm | Başlıca konular |
|---|---|
| v1–v35 | Temel çizim → vektör/diyagram/3B/çizgi roman, eğitim modu, basit/çocuk modu, Google giriş, sunucu, 7 dil, Google giriş yedek akışı, kapı 4747 (ayrıntı eski belgeler) |
| v36 | Sekmeli Ayarlar, "Çizim Rehberi" açılışı, KVKK onayı, kütüphane şekilleri Room'da (v14), çizgi roman nesneleri/efektler/sahneler; sunucu 36.0.0 |
| v37 | Anime/çizgi roman karakterleri, merkezi hata yönetimi + rapor, not yazı kutuları, ayrı şekil/yazı ağları (Room v15), çocuk boya kovası + maskot, depolama/eşitleme ayarları, ikili katalog; sunucu 37.0.0 |
| v38 | Resimde Karakterler yok, yatayda düğme adları, AI önerileri sıfırdan, çocuk 15 seviyeli Çizim Görevi; sunucu 38.0.0 |
| v39 | Paket `com.karapp.drawguidance`, Free/Premium abonelik (Play Billing 8), AdMob + UMP; sunucu 39.0.0 |
| v40 | Çocuk görevi 50 seviye, güvenlik denetimi + R8, Docker Swarm, CMYK/PDF/CBZ, basılı tutup taşıma, tema, cetvel/kalıp, hazır çizgi roman içerikleri (Room v20) |
| v41 | v40 dalları birleşti; arka plan Uygula/eleman gibi seçim; çizgi romanda Mekân arka planı; 13 karakter Premium; SaaS abonelik sayfası; imzalı APK + .aab |
| v42 | 129 genel şekil, yol üzerinde yazı, 471 efekt + 479 nesne, 14 tema paketi (Room v21), tek kaydetme (CMYK), Kaydedilenler, web sitesi + panel, yerel sunucu paketi |
| v43 | Firebase'siz dağıtım (ETag/uzun yoklama/CLI, `dgpackets` GCS), Uyumla/Mekâna yerleştir, Play içi güncelleme, alanla seçim/damlalık; Room v22, sunucu 43.0.0 |
| v44 | Kalemde çizgi eğme, 3B GLB Premium, çocuk kilidi (Room v23), kalem dolgusu, yerel sunucu uygulamadan kaldırıldı (yalnız HTTPS); sunucu 44.0.0 |
| v45 | Çocuk boya fırçası + renkli menü, çizgi roman kapağı, dikeyde düğme adları, grafik formülleri, kimya/fizik, Panel ekle, dörderli menü grupları, tablette pencereler; DEX iyileştirmesi (tam kip kapalı) |
| v46 | 5+5 hazır şablon, Lunara kaldırıldı, v46 eğitim yenilikleri, formülle element/mekanik, iki parçalı üst menü, panel hatası |
| v47 | "Özellikler" menüsü, mod menüsü (Seç/Çiz/Ekle/Düzenle), Taşı+Genişlet tek düğme, kamu malı çizgi roman şablonları, çocuk desen şekilleri |
| v48 | Gruplama yok, basit modda üç menü (alt alta/sarma), iki boş+şablonlu çizgi roman, 13 karakter, bozuk astronot pozu silindi |
| **v49** | **Basit modlarda üç menü tek satır + yatay kaydırma; çizgi roman mekân arka planı panele/tuvale sığar; eğitimler doğrulandı; freeRASP çalışma zamanı koruması (hook/kurcalamada kapanır, R8 korundu); dpt-shell deneysel DEX kabuğu APK'sı; versionCode 49 (1.7.0)** |

Ayrıntılar claude.ai projesindeki `claude/vNN-*.md` belgelerinde.

---

## 11. Açık konular ve öneriler

- **v49'dan kalanlar:**
  - **freeRASP gerçek cihazda denenmeli:** Play iç test kanalından inen (Play'in yeniden imzaladığı) kopya açılıp **kapanmamalı** (imza hash'leri doğru demektir); yan yüklenen imzalı APK de açılmalı; bir Frida/Xposed denemesi uygulamayı kapatmalı. Kapanırsa logcat `Talsec` etiketi nedeni söyler; `SIGNING_HASHES` eksikse `v49-backend-gcs-rehberi.md` A.2'ye göre tamamlayın.
  - **dpt-shell APK'sı cihazda launch testinden geçmeden dağıtılmamalı** (`v49-dptshell-guvenlik-rehberi.md` §5). Geçmezse yalnız ana APK/AAB.
  - **APK/AAB boyutu büyüdü** (freeRASP `libts.so` 4 ABI). İstenirse yan yükleme APK'sında x86/x86_64 ABI'leri `abiFilters` ile çıkarılabilir (emülatör x86 ise dikkat); AAB zaten böler.
  - `TalsecGuard.WATCHER_MAIL` tehdit telemetrisi içindir; Talsec portalına kaydolmazsanız etkisizdir, değiştirilebilir.
- **v46'dan kalanlar:** 3 elf büyücü resmi karakter sayfaları gelince `tools/comic_assets` ile eklenmeli.
- **v45'ten kalanlar:** `v45-devir-notu.md` → "Bilinen sorunlar" (PDF sayfa aralığı kapağı sayar, dg-warmup test yarışı, maskot testi…).
- **v44/v43'ten kalanlar (GCS/sunucu):** uzak sunucuya v43/v44 dağıtım kurulmalı: `GCS_BUCKET=dgpackets`, `GCS_SERVICE_ACCOUNT_JSON`, `DEVICE_DATA_KEY`; taban paketler kovaya. **Hepsi `v49-backend-gcs-rehberi.md` B bölümünde adım adım.** Kurulmazsa uygulama yoklamada 404 alıp sessizce bekler.
- **v42'den kalanlar:** Web paneli Vercel ortam değişkenleri + `npm run db:migrate`. Ayarlar çökmesi (Android 10) cihazda doğrulanmadı.
- **Play Console:** Play App Signing SHA-1/SHA-256 Firebase'e eklenmeli; çocuk modu için hedef kitle/Aile politikası gözden geçirilmeli.
- **Google girişi (v35):** "iptal edildi" sorunu cihazda doğrulanmalı.
- **`CanvasEditScreen.kt` boyutu:** yeni özellikler ayrı dosyalara alınmalı.
- Kullanım Şartları/KVKK taslak; yayından önce hukuken gözden geçirilmeli.

---

## 12. Yeni sohbete ilk mesaj için öneri

> "DrawGuidance Android projesine devam ediyoruz. Bağlı klasörde `YENI-SOHBET-DEVIR.md` ve `v49-durum.md` var, önce onları oku (güvenlik için `v49-backend-gcs-rehberi.md` ve `v49-dptshell-guvenlik-rehberi.md`). Proje belgeleri claude.ai projesinde. Kurallar: Türkçe yanıt, komutları sen çalıştır, her istek için test yaz, değişen dosyaları sahnele, APK/AAB ver, bilgisayara yalnız söylediğimde yaz. Yeni isteklerim: …"
