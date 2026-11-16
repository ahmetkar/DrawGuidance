# v55 devir notu (Android 1.12.1 / 55) — dal `claude/v55`

_2026-11-16. Bu not, bu sohbeti hiç görmemiş yeni bir Claude oturumunun işi kaldığı yerden sürdürebilmesi için
yazıldı. Okuma sırası:_

1. Bu not.
2. `claude/v54-durum.md` (güvenlik ve resim kuralları).
3. `claude/v55-plan-cihaz-destegi.md` (cihaz desteği planı; iOS bölümü henüz uygulanmadı).
4. `backend/docs/v55-dagitim-plani.md`.
5. Eski ayrıntılar için `claude/YENI-SOHBET-DEVIR.md` (v49'a kadar) ve `claude/vNN-durum.md` belgeleri.

## 1. Kim, ne, nerede

| Konu | Bilgi |
|---|---|
| Kullanıcı | Ahmet. Türkçe yanıt. Komutları Claude çalıştırır. Her istenen özellik için test yazılır. |
| Uygulama | DrawGuidance / Çizim Rehberi. Android (Kotlin + Compose), iOS (SwiftUI), backend (Spring Boot + MongoDB, Docker Swarm), web (Next.js, Vercel). |
| Paket | `com.karapp.drawguidance`. Play geliştirici hesabı ahmetkar8040. |
| Bulut çalışma alanı | `/home/claude/drawguidance` (git). Dal `claude/v55` (taban `claude/v54` 7ae6d17). Commit c976cda: v55 Android değişiklikleri ve backend dağıtım planı. |
| Bilgisayar | `C:\Users\ahmet\Documents\DrawGuidance`. Git değil; kaynak ağacının kopyası. Claude masaüstü uygulamasıyla bağlanır (`device_bash` ile `$HOME/mnt/DrawGuidance`). |
| Sırlar | Bilgisayarda `yayin-anahtari\`; derleme ortamında `android/app/keystore/`. **Asla yazdırılmaz, commit edilmez.** GitHub Secrets değerleri de yazdırılmaz. Uygulama içi satın almanın `ASC_*` değişkenlerine dokunulmaz. |

## 2. v55'te yapılanlar (yalnız Android; iOS bilerek değişmedi)

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
- iOS'ta bu şablon kimliği yoktu; iOS'a dokunulmadı.

### 2.4 Sürüm

- `versionCode 55`, `versionName 1.12.1` (`android/app/build.gradle.kts`).
- iOS 1.12.0 (54) olarak kaldı.
- R8 tam kipi **kapalı kalmalı** (Android 10 Ayarlar çökmesi). Ahmet sordu; kazancı küçük, riski büyük olduğu için açılmadı.

## 3. Teslimler

| Dosya | Bilgi |
|---|---|
| `DrawGuidance-v55.aab` | Play'e yüklenecek. İmzalayan CN=Ahmet Kar (SHA-256 `42:96:62:1D…02:2A`). SHA-256 `29acecdc8a95bc47…eb4f69`. bundletool ile manifest doğrulandı. |
| `DrawGuidance-v55.apk` | Test için; imzalı. `apksigner` ile doğrulandı. |
| Değişen dosyalar | Bilgisayardaki proje klasörüne yazıldı. Eskiler `_eski_v54\` klasöründe. Silinen okul şablonu klasörü de `_eski_v54\` içine taşındı. |
| Backend planı | `backend/docs/v55-dagitim-plani.md`. Sunucuda kod değişikliği yok; plan üretimi 50.0.0'dan 54.0.0'a geçirmek ve web ortam değişkenleri içindir. |

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
- **iOS cihaz desteği:** `claude/v55-plan-cihaz-destegi.md` iOS bölümü (iOS 15 tabanı, Xcode 26) planlandı ama uygulanmadı. Ahmet "iOS şimdiki gibi kalsın" dedi.
- **iOS'ta resim sınırları** hâlâ v54'teki gibi (10 MB / 8192 px / detay). Android ile eşitlenmesi istenirse ayrı iş.
- **Sunucu:** üretimde hâlâ 50.0.0 olabilir. `v55-dagitim-plani.md` uygulanmalı:
  - önce `cli_password_hash` sırrı,
  - sonra imaj ve `stack deploy`,
  - sonra Vercel'de `IP_HASH_SALT`.
- **TestFlight:** yalnız Ahmet söyleyince yüklenir. Son koşu onun isteğiyle iptal edildi.
- Şablon yalnız resmi kontrol edildi; manga 2. sayfası ekran görüntüsüyle bakılmadı. İstenirse `V53TemplateExport` sonrası sayfa çıktısı alınabilir.

## 8. Yeni sohbete ilk mesaj önerisi

> "DrawGuidance'a devam ediyoruz. Projede `claude/v55-devir-notu.md` var, önce onu oku. Kurallar: Türkçe yanıt,
> komutları sen çalıştır, her özelliğe test yaz, bilgisayara yalnız söylediğimde yaz (eskileri `_eski_vNN`'ye yedekle),
> gizli anahtarları yazdırma, Play Console/TestFlight/prod değişikliğini ben söylemeden yapma. Yeni isteklerim: …"
