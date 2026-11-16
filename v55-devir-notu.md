# v55 devir notu (Android ve iOS 1.12.1 / 55) — dal `claude/v55`

_2026-11-16. Bu not, bu sohbeti hiç görmemiş yeni bir Claude oturumunun işi kaldığı yerden sürdürebilmesi için
yazıldı. Okuma sırası:_

1. Bu not.
2. `claude/v54-durum.md` (güvenlik ve resim kuralları).
3. `claude/v55-plan-cihaz-destegi.md` (cihaz desteği planı; iOS 16 aşaması uygulandı, iOS 15 aşaması yapılmadı).
4. `v55-app-store-rehberi.md` (App Store yükleme rehberi).
5. `backend/docs/v55-dagitim-plani.md`.
6. Eski ayrıntılar için `claude/YENI-SOHBET-DEVIR.md` (v49'a kadar) ve `claude/vNN-durum.md` belgeleri.

## 1. Kim, ne, nerede

| Konu | Bilgi |
|---|---|
| Kullanıcı | Ahmet. Türkçe yanıt. Komutları Claude çalıştırır. Her istenen özellik için test yazılır. |
| Uygulama | DrawGuidance / Çizim Rehberi. Android (Kotlin + Compose), iOS (SwiftUI), backend (Spring Boot + MongoDB, Docker Swarm), web (Next.js, Vercel). |
| Paket | `com.karapp.drawguidance`. Play geliştirici hesabı ahmetkar8040. |
| Bulut çalışma alanı | `/home/claude/drawguidance` (git). Dal `claude/v55` (taban `claude/v54` 7ae6d17). Commit c976cda: v55 Android değişiklikleri ve backend dağıtım planı. |
| Bilgisayar | `C:\Users\ahmet\Documents\DrawGuidance`. Git değil; kaynak ağacının kopyası. Claude masaüstü uygulamasıyla bağlanır (`device_bash` ile `$HOME/mnt/DrawGuidance`). |
| Sırlar | Bilgisayarda `yayin-anahtari\`; derleme ortamında `android/app/keystore/`. **Asla yazdırılmaz, commit edilmez.** GitHub Secrets değerleri de yazdırılmaz. Uygulama içi satın almanın `ASC_*` değişkenlerine dokunulmaz. |

## 2. v55'te yapılanlar (2.1–2.4 Android, 2.5 iOS)

### 2.1 Geniş cihaz desteği (plan B1–B5)

**Manifest:**

- `AndroidManifest.xml`'e dört özellik `required="false"` olarak eklendi:
  - `android.hardware.wifi` (Wi-Fi izinleri yüzünden zorunlu sayılmasın diye),
  - `android.hardware.touchscreen`,
  - `android.hardware.faketouch`,
  - `glEsVersion 0x00020000`.
- 3B, ekran kartı yoksa yazılım çiziciye düşer (`Model3DPrefs.gpuFailed`, `GlProbe`).
- Cihaz eleyen izin yok (kamera, mikrofon, konum, Bluetooth, depolama).

**Düşük RAM sınıfı (`platform/DeviceCaps.kt`):**

- `isLowRam(flag, totalRamMb)`: Android Go **ya da** toplam bellek ≤ 2200 MB.
- `historyLimit(normal)`: düşük RAM'de normalin %40'ı, en az 20.
  - Uygulandığı yerler: `EditorStore` (100→40), `PhotoEditController` (80→32), `Model3DEditorViewModel` (60→24).
- Düşük RAM'de saklanan resmin uzun kenarı 1600 px (`ImageImportValidator.STORE_MAX_SIDE_LOW_RAM`).
- Tier LOW zaten 3B bütçelerini düşürüyor.

**Testler:** `V55DeviceSupportTest` (6 test).

**Play Console tarafı (Ahmet kendisi yapacak, Claude yapmayacak; "Hiçbirini yapma" dedi):**

- Redmi Note 8/10'da "uyumlu değil" uyarısının nedeni uygulama değil, Play Console'un mağaza girişi **cihaz bütünlüğü kontrolleri**.
- Ayrıca bir **Android Go hariç tutma kuralı** 66 cihazı eliyor.
- İkisi de Play Console → Sürüm → Cihaz kataloğu bölümünden kapatılır.

### 2.2 Eklenen resimlerde detay ve boyut sınırı yok (tüm modlar)

`data/images/ImageImportValidator.kt`:

- **Kaldırılan sınırlar:**
  - 10 MB dosya ve 8 MB uyarısı,
  - 8192 px kenar, 64 MP çözünürlük, en az 16 px kenar,
  - detay reddi (%30) ve detay uyarısı (%15).
- **Yalnız çökme koruması kaldı:**
  - `maxImportBytes`: Java yığınının üçte biri, 16–256 MB arası.
  - `MAX_PIXELS` = 1 gigapiksel.
  - Akış `readLimited` ile sınır içinde okunur; aşılırsa `TooLargeException` → `ImageIssue.TooLarge`.
- **Kalanlar:**
  - Saklarken küçültme: 3000 px (giriş düzeyinde 2000, düşük RAM'de 1600). Uyarı metni "küçültülecek".
  - Büyük resim tam boyda açılmaz: Android 9+ ImageDecoder hedef boyda çözer; daha eski sürümlerde inSampleSize ile en çok 24 MP.
  - `ImageSecurity` denetimi ve her zaman yeniden kodlama (güvenlik; kaldırılmadı).
- `ImageImporter.prepareSaved` da `readLimited` kullanıyor.
- Yeni hata metinleri `strings_v55.xml` dosyasında, 7 dilde: `v55_image_err_memory`, `v55_image_err_pixels`. `CanvasEditScreen.issueText` bunları kullanıyor.
- Eski v54 metinleri dosyada duruyor ama kullanılmıyor.
- **Testler:**
  - `ImageImportValidatorTest`: 10 MB üstü kabul, detaylı resim uyarısız kabul, 10 px ve 9000×40 kabul, bellek koruması.
  - `EditorEraserImageTest`: gürültülü resim ve 16 MB PNG artık ekleniyor.

### 2.3 "Okul macerası" yerine örnek manga şablonu

- `comic_story_school` silindi. Yerine `comic_story_manga` geldi: **"Manga: Avlu düellosu"**.
  - 2 sayfa. Okuma sağdan sola (`readingOrder(true)`).
  - Sayfa 1 "manga" kare düzeni, sayfa 2 "four_grid".
  - Mekân `avlu2` (siyah-beyaz avlu).
  - Ekran tonları, hız, odak ve darbe çizgileri; efektler VUUUŞ ve ŞAK!.
- **Karakterler:** şövalye **Aras** (`sovalye_aras`) ve beyaz saçlı kılıççı **Doruk** (`kilicci_doruk`).
  - Ahmet "Aiko yerine başka erkek karakter" istedi.
  - Kenji Premium olduğu için şablonda kullanılamadı. Şablonlar yalnız Free karakter, mekân ve efekt kabul ediyor (`TemplateExportBase` `require`). Bu yüzden Kenji de Aras ile değiştirildi.
- **Üretim:**
  - Üretici: `V53TemplateExport.comicStoryManga`.
  - Komut: `gradle :app:testDebugUnitTest --tests '*V53TemplateExport.comicStoryManga' -PtemplateOut=/tmp/tplout`.
  - Çıktı `assets/templates/` klasörüne kopyalandı. `catalog.json` sırası `TemplateExportBase.ORDER`'da.
- **Çeviriler:** `V53TemplateInfo` içinde 7 dilde ad ve açıklama.
- **Test:** `V46TemplateFilesTest.v55MangaStoryReplacesSchoolAdventure`.
- iOS şablonları aynı klasörü (Android `assets/templates`) okuduğu için değişiklik iOS'a da geçti.

### 2.4 Sürüm

- `versionCode 55`, `versionName 1.12.1` (`android/app/build.gradle.kts`).
- iOS da 1.12.1 (55) oldu (bkz. 2.5).
- R8 tam kipi **kapalı kalmalı** (Android 10 Ayarlar çökmesi). Ahmet sordu; kazancı küçük, riski büyük olduğu için açılmadı.


## 2.5 iOS v55 (Ahmet'in 21:14 istekleri; "hepsi yalnız iOS için")

- **Cihaz desteği:** dağıtım hedefi 17.0 → **16.0** (`project.yml`). iOS 17 API'leri için `Core/Platform/Compat16.swift`:
  `onChangeCompat` (33 yer), `navigationDestinationCompat(item:)` (2), `scrollBounceBasedOnSizeCompat`; `.topBarTrailing` →
  `.navigationBarTrailing`; `MagnifyGesture` → `MagnificationGesture`; `.snappy` → `.easeOut`. iOS 15 (plan C2) yapılmadı.
- **İzinler:** Info.plist'ten `NSCameraUsageDescription` ve `NSPhotoLibraryUsageDescription` kaldırıldı. Kalanlar: galeriye
  kaydetme ve yerel ağ. `Resources/PrivacyInfo.xcprivacy` eklendi (UserDefaults CA92.1, FileTimestamp C617.1, SystemBootTime 35F9.1).
- **Düşük RAM:** `Budget3D.lowRam` (≤ 2,2 GB) ve `historyLimit` (EditorStore, Model3D). Düşük RAM'de saklanan resim 1600 px.
- **Resim sınırları:** `Data/Images/ImageImportValidator.swift` Android ile aynı: detay ve sabit boyut sınırı yok;
  `maxImportBytes` = fiziksel belleğin 1/16'sı (32–256 MB), 1 gigapiksel. Metinler `v55_image_err_*`
  (`Android.strings` `tools/strings-export/convert.py` ile yeniden üretildi).
- **Sayfalar (hata):** iOS'ta sayfalar oluşuyor ama düzenleyicide sayfa geçişi yoktu. Yeni `Features/CanvasEdit/PageStripView.swift`
  (tuvalin altında: konum yazısı, sayfa çipleri, ekle; uzun basınca çoğalt/sil/kapak ekle). `CanvasForm` sayfa sayısı seçicisi
  boyutun hemen altına taşındı.
- **Manga şablonu:** iOS şablonları Android `assets/templates` klasörünü paylaşıyor; Android'deki değişiklik iOS'a da geçti.
- **Sürüm:** 1.12.1 (55). Testler: `DrawGuidanceTests/V55Tests.swift`, `ImageTests` güncellendi.
- **Uygulama simgesi (önemli):** iOS simge kataloğunda hiç simge dosyası yoktu (yalnız boş `Contents.json`). Apple
  yüklemeyi reddetti. Android uyarlanabilir simgesinden üretilen `AppIcon.appiconset/AppIcon-1024.png` eklendi.
- **Xcode 26:** App Store artık yalnız iOS 26 SDK ile derlenmiş yüklemeleri kabul ediyor. `macos-15-intel` kalıbında
  öntanımlı Xcode 16.4 (iOS 18.5 SDK) idi. `ios.yml`'deki iki işe de "Xcode 26 seç" adımı eklendi (şu an 26.3). Bu
  yüzden v52'nin imzalı IPA'sı da bu haliyle App Store'a yüklenemezdi.
- **CI:** dal `claude/v55`. Hızlı denetim (koşu 43950911758) geçti. Her tuval türünde ikinci ekran görüntüsü (47. sn)
  eklendi; çizgi roman ve not almada sayfa şeridi görünüyor (`1 / 1`, `Kapak · 1`). İlk denetimde düzenleyicinin 22. sn'de
  görünmemesi simülatör yavaşlığıydı.
- **TestFlight:** koşu 43961863712 (Xcode 26.3) 1.12.1 (55)'i App Store Connect'e yükledi ("Upload succeeded"). Ardından
  yüklemesiz koşu 43965920918 imzalı IPA dosyası için başlatıldı.
- `ios.yml` aynı dalda yeni koşu başlayınca öncekini iptal eder (concurrency): bir koşu sürerken başkasını başlatmayın.
- **iOS testleri** (birim + arayüz, `tests_only=true`) bu sürüm için henüz çalıştırılmadı.

## 2.6 Backend 44.2.0 → 54.0.0 ve Android Apple girişi

- Üretim 44.2.0'dı; plan `backend/docs/v55-dagitim-plani.md` (tek adımda 54.0.0, iki sır, `sudo env` ile deploy, Mongo yedeği
  `dgbackup` kullanıcısıyla). Ahmet sunucuyu güncelledi (22:18).
- Yeni `backend/docker/swarm/dg-cli.sh` (boşluklu argümanlar, sudo, şifre sorusu); GitHub raw adresinden indirilir.
- Android'de Apple ile giriş `ERROR_INVALID_CREDENTIAL` veriyor (Firebase `OAuthProvider("apple.com")`; sunucuyla ilgisiz).
  Ahmet Services ID, anahtar ve Firebase ayarlarının doğru olduğunu söyledi. Ayrıntı için `adb logcat` çıktısı istendi
  (invalid_client / audience / redirect_uri). Uygulama hata mesajını değil yalnız kodu gösteriyor.

## 3. Teslimler

| Dosya | Bilgi |
|---|---|
| `DrawGuidance-v55.aab` | Play'e yüklenecek. İmzalayan CN=Ahmet Kar (SHA-256 `42:96:62:1D…02:2A`). SHA-256 `29acecdc8a95bc47…eb4f69`. bundletool ile manifest doğrulandı. |
| `DrawGuidance-v55.apk` | Test için; imzalı. `apksigner` ile doğrulandı. |
| Değişen dosyalar (Android turu) | Bilgisayardaki proje klasörüne yazıldı. Eskiler `_eski_v54\` klasöründe. Silinen okul şablonu klasörü de `_eski_v54\` içine taşındı. |
| iOS 1.12.1 (55) | TestFlight'a yüklendi (koşu 43961863712). İmzalı IPA: koşu 43965920918 `ios-signed` artifact'ı. |
| Backend planı | `backend/docs/v55-dagitim-plani.md`: 44.2.0'dan 54.0.0'a tek adımda güncelleme. Ahmet uyguladı. |
| App Store rehberi | `v55-app-store-rehberi.md` |

## 4. Test durumu

**Yeşil:**

- ImageImportValidatorTest (8)
- V55DeviceSupportTest (6)
- V54SecurityTest
- V45R8ConfigTest
- EditorEraserImageTest
- V46TemplateFilesTest (yeni manga testi dahil)
- V46TemplateFormTest

**Tam takım çalıştırılmadı** (1–2 saat sürer). Önceden bilinen kırmızılar:

- CanvasFlowTest ve V33BackendTest yerel sunucu ister.
- V37KidsMascotTest, V46NewCharactersTest, V23Model3DCanvasTest, V25CanvasAdd3DTest.

## 5. Derleme ipuçları (bu bulut ortamı)

- `ANDROID_HOME=/home/claude/sdk`, gradle `/opt/gradle-8.14.3/bin/gradle`, `--offline`.
- Yayın derlemesi **`--max-workers=1`** ile yapılmalı (lint/R8 bellek yetmezliği). AAB ve APK ayrı ayrı derlenmeli. Her biri ~12–15 dk sürer.
- Uzun işleri `setsid nohup … ; echo EXIT=$?` ile arka planda çalıştırın.
- Doğrulama:
  - `apksigner verify --print-certs` (`/home/claude/sdk/build-tools/36.0.0/`),
  - `java -jar /tmp/bundletool.jar dump manifest --bundle X.aab`.
- Robolectric testleri `forkEvery=1`; tek sınıf ~5–10 dk.

## 6. Bilgisayara aktarma yöntemi

1. Değişen dosyalar zip olarak `SendUserFile` ile gönderilir.
2. `device_commit_files` ile `_aktarim_vNN\` klasörüne yazılır.
3. `device_bash` ile python betiği çalıştırılır: önce yedek (`_eski_vNN\`), sonra üzerine yazma. Betik bir done-list tutar; her tur en çok 150 sn sürer ve kaldığı yerden devam eder.
4. `device_bash` çağrısı bitince arka plan işleri ölür.
5. Silme kapalıdır: silinecek dosyalar `mv` ile `_eski_vNN\` klasörüne taşınır.

## 7. Açık konular

- **Play Console (Ahmet):**
  - Cihaz bütünlüğü kontrolleri ve Android Go hariç tutma kuralı kapatılmalı (bkz. 2.1).
  - AAB yüklenince cihaz kataloğunda Redmi Note 8/10 "destekleniyor" görünmeli.
- **iOS 15 desteği** (plan C2: NavigationStack, Layout, PhotosPicker vb. uyum katmanı) yapılmadı; büyük iş.
- **iPhone Duo** (katlanabilir, iOS 27): uygulama çalışır ama açık iç ekrana ve katla/aç geçişine özel uyarlama ve
  deneme yapılmadı. CI'da Duo simülatörü yok.
- **Android Apple girişi** `ERROR_INVALID_CREDENTIAL`: `adb logcat` çıktısı bekleniyor (2.6). Gerekirse `appleError`
  hata mesajını da gösterecek şekilde değiştirilmeli.
- **Sunucu sürümü kaydı:** mağazalarda yayımlanınca `dg-cli.sh surum ekle --kod 55 --ad 1.12.1 …`.
- **TestFlight ve prod değişiklikleri** yalnız Ahmet söyleyince yapılır.
- Şablon yalnız resmi kontrol edildi; manga 2. sayfası ekran görüntüsüyle bakılmadı. İstenirse `V53TemplateExport` sonrası sayfa çıktısı alınabilir.

## 8. Yeni sohbete ilk mesaj önerisi

> "DrawGuidance'a devam ediyoruz. Projede `claude/v55-devir-notu.md` var, önce onu oku. Kurallar: Türkçe yanıt,
> komutları sen çalıştır, her özelliğe test yaz, bilgisayara yalnız söylediğimde yaz (eskileri `_eski_vNN`'ye yedekle),
> gizli anahtarları yazdırma, Play Console/TestFlight/prod değişikliğini ben söylemeden yapma. Yeni isteklerim: …"
