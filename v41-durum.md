# DrawGuidance v41 — durum

_2026-10-24 · versionCode **41** (versionName 1.0.0) · Room **v20** · minSdk 23 – targetSdk 36 · katalog **38000** · sunucu **39.0.0**, kapı 4747 · 7 dil_

v40 dallarının hepsi `master`'a birleştirildi ve Ahmet'in 24 Ekim isteklerinin hepsi yapıldı. Ayrıntılı devir bilgisi `YENI-SOHBET-DEVIR.md` belgesinde.

## İstekler ve yapılanlar

### 1. Arka plan penceresinde Uygula

- Arka plan penceresinde seçilen kağıt rengi, gradyan, desen ve resim önce **taslakta** toplanır. Tuval ancak **Uygula**'ya basınca değişir.
  - Uygula düğmesi pencerenin üstünde. Değişiklik yokken soluk; değişiklik varken "Seçimler henüz uygulanmadı" yazar.
  - Pencere uygulamadan kapatılırsa tuval olduğu gibi kalır.
  - Not tuvalinde birden çok sayfa varken **Tüm sayfalara uygula** de taslağı uygular.
- **Uygula eski arka planı kaldırır:** bu pencerede yeni bir tür seçilince (gradyan, desen ya da resim), eski arka planın bu pencerede seçilmeyen türleri kalkar.
  - Aynı pencerede birden çok tür seçilirse hepsi kalır (ör. resim ve üstünde desen).
  - Desen rengi, ölçek, örtücülük ya da resmin yerleşimi değişince öteki türler kalkmaz.
  - Yeni resim seçilince eski resmin taşıma/boyutlandırması sıfırlanır.
- Uygula **tek geri alma adımıdır** (kağıt rengi dahil). Geri alınınca eski arka plan bütünüyle döner.
  - Bunun için geçmiş kaydı ortak arka planı artık bütünüyle saklayabiliyor (`DocumentState.sharedBackgroundFull`). Yalnız taşıma/boyutlandırma adımları eskisi gibi yalnız resmin yerini ve boyunu geri alır.
- Kod: `BackgroundSheet.kt` (`BackgroundDraft`), `CanvasEditViewModel.applyBackground`, `EditorStore.commit(shared = …)`, `FrameController.setBackgroundColor(shared = …)`.

### 2. Arka plan eleman gibi seçilir

- Arka plan (gradyan, desen ya da resim varken) eleman gibi seçilebiliyor:
  - **Seç** modunda, hiçbir eleman seçili değilken kağıdın boş bir yerine dokunmak arka planı seçer. Seçim varken ilk dokunuş yalnız seçimi kaldırır.
  - **Elemanlar** panelinin en altında **Arka plan** satırı var. Dokununca panel kapanır, arka plan seçilir.
- Seçiliyken üstte eylem çubuğu:
  - **Örtücülük** kaydırıcısı (resim varsa resmin, yoksa desenin);
  - **Değiştir** (arka plan penceresini açar);
  - **Sıfırla** (resim taşındıysa);
  - **Tüm sayfalara** (not tuvalinde birden çok sayfa varken);
  - **Sil** (arka planı kaldırır; kağıt rengi kalır; geri alınabilir);
  - **Bitti**.
- Resim varsa (not alma dışında) seçiliyken taşınır ve köşelerden ya da iki parmakla boyutlandırılır (v40 davranışı).
- Kağıdın dışına dokunmak, mod değişmesi ya da bir elemanın seçilmesi arka planın seçimini kaldırır.
- Kod: `BackgroundTransformOverlay.kt`, `CanvasTools.SelectTool.tap` (`onEmptyTap`), `EditorSidePanel.ElementsPanelContent`.

### 3. Çizgi roman varlıkları (v40-comic-assets birleştirildi)

- Karakterler ve arka planlar Ahmet'in gönderdiği `karakter.zip` ve `background.zip`'ten yeniden üretildi (`tools/comic_assets/build_assets.py`): **57 karakter**, 617 çeşit (304 ifade, 102 poz, 144 aksiyon, 67 günlük) ve **31 arka plan**, toplam yaklaşık 29 MB WebP.
- **Manga kategorisi:** istenen 11 karakter (demirci, uzaylı büyücü, öğrenci kız, ofis çalışanı kadın, yaşlı nine, büyücü kadın, kılıççı beyaz saçlı, elf büyücü, elf okçu, kadın samuray, kadın kılıççı) Manga'da. v40 WIP'te istek dışında Manga'ya geçirilmiş 5 karakter (astronot, kadın doktor, gizli ajan, arkeolog, piyade) Çizgi roman'a geri alındı. Sonuç: 41 manga, 16 çizgi roman karakteri.
- **Kişi adları kaldırıldı:** karakterler yalnız meslek, yaş ya da rolle anılır (ör. "Yaşlı nine", "Dedektif (kadın, trençkotlu)"). 7 dilde.
- **Premium karakterler** yalnız istenenler: iki yeniçeri, Moğol atlı, gizli ajan, komando, iki samuray, buz büyücüsü, uzaylı büyücü, siborg, ateş yumruklu dövüşçü, gezgin kılıççı, maskeli hırsız (13). Ötekiler Free.
- **Şekiller ve diyagramlar** tamamen ücretsiz.
- **Arka plan çiftleri:** neredeyse aynı her çiftin biri Çizgi roman'da, öteki siyah-beyaz tramlı manga sürümüyle Manga'da (31 arka planın 10'u manga).

### 4. Mekân arka planı ekle (çizgi roman)

- Hazır arka planlar tuval oluşturma ekranından ve Arka plan penceresinden kaldırıldı. Çizgi roman düzenleyicisinde **Ekle → Mekân arka planı** penceresinde:
  - Manga ve Çizgi Roman sekmeleri, alt kategori süzgeci (Şehir, Sokak, Doğa/Park, Okul, Gece/Yağmur, Kış, Yıkım, İç mekân), küçük resimler.
  - Şehir, Sokak ve Doğa/Park Free; ötekiler Premium (önizleme açık; eklemek "Premium gerekli" açar).
- **Yerleştirme:**
  - Sayfada şablon (panel düzeni) varsa paneller işaretlenir. Bir panele dokunulur ya da parmak sürüklenip bırakılır: altındaki panel vurgulanır ve arka plan orada önizlenir. Bırakılınca arka plan **o panele sığar** (panelin oranında ortadan kırpılır).
  - Panellerin dışına bırakılırsa ya da **Bütün sayfa**'ya basılırsa **sayfayı kaplar**. **Vazgeç** bir şey eklemez.
  - "Buraya ekle" menüsünden bir panele dokunularak açıldıysa doğrudan o panele; sayfada şablon yoksa doğrudan bütün sayfaya konur.
- Arka plan resim elemanı olarak **bütün elemanların arkasına** konur (panel çizgileri üstte kalır), tek geri alma adımıdır. Panel düzeni değişince panelin içindekilerle birlikte taşınır.
- Kod: `components/LocationBackgrounds.kt`, `ImageController.insertBackdrop` (`Backdrop.cropRect`), `CanvasEditViewModel.insertLocationBackground`.

### 5. Cetvel (v40-ruler birleştirildi, doğrulandı)

- Cetvel ve Şekil kalıbı ayrı düğmeler; bilgi çubuğu ekranın altında; tür, birim, ölçek ve Bitir cetvelin yanındaki tutamak düğmelerinde.
- **Düzeltme:** ekran kenarına itilen tutamak düğmesi döndürme tutamağının üstüne biniyordu ve cetvel döndürülemiyordu (`rotateHandleSnapsAngleAndSettingsShowOnRuler` testi düşüyordu). Düğme artık tutamağı örtecekse tutamağın yanına geçiyor (`RulerLayer.HandleAnchor`).

### 6. Hesap ve eşitleme (v40-account-sync birleştirildi), abonelik sayfası

- Giriş sonucu ve notlu hata pencereleri, mod başına geri bildirim, güncelleme ve hareketsizlik bildirimleri, eşitleme izni dilimleri, abonelik ayrıntıları, rapor için giriş zorunluluğu. Room v20.
- **Abonelik sayfası SaaS düzeninde:**
  - Üstte başlık ve kısa açıklama.
  - Plan kartları (geniş ekranda yan yana, telefonda alt alta): büyük fiyat, özellik listesi ve kartın içinde eylem düğmesi. Premium "Önerilen".
  - Başlıklar altında bölümler: **Aboneliğin** (plan, durum, ayrıntılar; altında **Yönet** başlığıyla Google Play'de otomatik yenileme ve iptal/yeniden başlatma), **Faturalandırma** (satın alımları geri yükle, Google Play'de yönet), **Sık sorulanlar** (iptal, başka cihaz, Free'ye dönüş).
- **Android 10'da Ayarlar çökmesi:** bulut ortamında Android 10 cihaz ya da hızlandırılmış emülatör yok; hata Robolectric'te (API 29) yeniden üretilemedi. Yapılanlar:
  - `V40Android10Test`: Ayarlar girişsiz ve profil fotoğraflı girişli hesapla açılıyor, beş sekmenin hepsi baştan sona kaydırılıyor, abonelik sayfası açılıyor; API 29'da geçiyor.
  - `lintRelease` hatalarının hepsi giderildi: Android 6'da (API 23) çökecek iki `Configuration.locales` çağrısı (`ConfigurationCompat`), `remember` içinde dönüşsüz yan etki.
  - `.aab`'de dil bölme kapatıldı: uygulama içinde dil seçilebildiği için Play yalnız cihazın dilini indirirse başka dile geçince metinler eksik kalırdı.
  - **Kalan:** yayın APK'sı Android 10 telefonda denenmeli. Çökerse uygulama yeniden açılınca çıkan hata raporu penceresindeki metin (ya da Ayarlar → Gelişmiş → Hata raporları) gönderilirse kesin düzeltme yapılır.

### 7. Eğitimler (v40-tutorials birleştirildi, bitirildi)

- Gelişmiş türlerde (resim, not, çizgi roman) "Yeni" grubu; basit resim ve basit not için tanıtım; çocukta yenilikler. Eski eğitimi bitirmiş kullanıcıya yenilikler bir kez kendiliğinden gösterilir.
- `RulerLayer` çakışması çözüldü: eğitim işaretleri yeni yerlerinde (alttaki bilgi çubuğu, cetvelin yanındaki tür ve birim/ölçek düğmeleri, Bitir).
- Cetvel metinleri yeni düğmelere göre yazıldı (7 dil): "Cetvel ve kalıp düğmesi" yerine ayrı **Cetvel** ve **Şekil kalıbı** düğmeleri; tür, birim ve ölçek "cetvelin yanındaki düğmelerden"; açı ve boy "alttaki çubukta".
- Çizgi roman eğitiminin hazır arka plan adımı **Ekle → Mekân arka planı**'nı gösteriyor.

### 8. Ahmet'in bilgileri

- **GitHub:** `ahmetkar` → Swarm imaj adresi `ghcr.io/ahmetkar` (`swarm/README.md`, `stack.yml`'de `REGISTRY` varsayılanı).
- **Play geliştirici adı:** `KarApplication` → web sitesi (`web/src/lib/config.ts` `DEVELOPER_NAME`), Play yayın rehberi.
- **Herkese açık e-posta:** `ahmetkar.1258@gmail.com` → web sitesi (`CONTACT_EMAIL` öntanımlısı, `.env.example`), Play yayın rehberi.
- Uygulamanın açılışındaki "created by karApp" imzası değiştirilmedi (istenmedi).

## Doğrulama

- **Uygulama testlerinin tamamı** (yerel sunucu 4747'de açıkken: Spring Boot jar + MongoDB kopya kümesi): `:app:testDebugUnitTest` **697 test**, 12 atlandı (ekran görüntüsü testleri), 4 düştü; düşenler tek başına yeniden çalıştırılınca geçti:
  - `V33ToolHelpComicTest`: yeni "Mekân arka planı" düğmesinin 2 sn açıklaması eksikti → `ToolDescriptions`'a eklendi (7 dil), geçiyor.
  - `V40KidsChallengePremiumTest`, `V40KidsChallengeSuggestTest`: tam takımda zamanlama yüzünden düştü, tek başına geçiyor.
  - `V9VectorShapesTest`: Robolectric ortam sıfırlama hatası (`AppOpsManager`), tek başına geçiyor.
- `:core:content:test` 254 test (15 atlandı), `:core:model:test` 34 test: hepsi geçti.
- **Sunucu testleri:** `backend` `./gradlew test` **139 test**, hepsi geçti (Testcontainers).
- **Hız testleri** (`V9PerformanceTest`, `V13RenderPerformanceTest`, `LiveDrawingPerformanceTest`, `V37PerfTest`, `V22BrushMemoryTest`) tam takımın içinde geçti.
- **Geniş uyumluluk:** `V41CompatTest` Android 7.0 (API 24), 10 (API 29) ve 14 (API 34) üzerinde her tuval türünü açıp çiziyor, Ayarlar ve abonelik sayfasını açıyor: geçti. `lintRelease`: **0 hata** (minSdk 23 için NewApi dahil).
- **Çeviri denetimi (7 dil):** 2693 metin anahtarının hepsi 6 dilde var, yer tutucular (`%1$s` …) her dilde aynı. Aynı kalanlar marka ve terim adları (Premium, Manga, Webtoon, WhatsApp…).
- **Web sitesi:** `npm ci`, `next typegen`, `tsc --noEmit` ve `eslint` temiz.
- **Yeni testler:** `V41BackgroundDraftTest` (4), `V41BackgroundUiTest` (5), `V41BackdropCropTest` (2), `V40ComicAssetsTest` (Mekân arka planı 3 test, Manga listesi ve kişi adı denetimi), `V41TutorialsTest` (7), `V40Android10Test` (2), `V41CompatTest` (3 SDK), `V41PlayShotsTest` (Play ekran görüntüleri).
- **Yayın derlemesi:** `:app:assembleRelease` ve `:app:bundleRelease` R8 ile, yükleme anahtarıyla imzalı (`CN=Ahmet Kar, O=DrawGuidance`). `com.karapp.drawguidance` versionCode 41, versionName 1.0.0. APK 43,7 MB, .aab 50,5 MB. R8 `mapping.txt` Play Console'a yüklenmek üzere saklandı.
- **Play ekran görüntüleri:** her mod için (resim, not, çizgi roman, çocuk, basit resim, basit not, 3B) ve abonelik sayfası, 1233×2193 px (9:16).

## Teslim

- `DrawGuidance-v41.apk`: imzalı yayın APK'sı, telefona doğrudan kurulur (R8'li). Bu APK'da Google girişinin çalışması için yükleme anahtarının SHA-1 parmak izi Firebase'de kayıtlı olmalı (Firebase → Proje ayarları → `com.karapp.drawguidance` → SHA parmak izi ekle; değer `keytool -list -v -keystore drawguidance-upload.jks` ile görülür). Play'den kurulan sürüm için Play App Signing SHA-1'i de eklenmeli.
- `DrawGuidance-v41.aab`: Play Console'a yüklenecek paket (versionCode 41). `mapping.txt` aynı sürümün "Uygulama paketi gezgini → İndirmeler → ReTrace eşleme dosyası" bölümüne yüklenir.
- `play-ekran-goruntuleri\`: her modun Play görüntüsü.

## Dosyalar

- Yeni: `components/LocationBackgrounds.kt`, `res/values*/strings_v41_background.xml`, `strings_v41_locations.xml`, `strings_v41_subscription.xml`, testler `V41BackgroundTest.kt`, `V41TutorialsTest.kt`, `V41PlayShotsTest.kt`.
- Değişen başlıcaları: `BackgroundSheet.kt`, `BackgroundTransformOverlay.kt`, `CanvasEditScreen.kt`, `CanvasEditViewModel.kt`, `EditorStore.kt`, `FrameController.kt`, `BackgroundTransformController.kt`, `EditHistory.kt`, `ImageController.kt`, `EditorSidePanel.kt`, `CanvasTools.kt`, `DrawingCanvas.kt`, `BackgroundPicker.kt`, `RulerLayer.kt`, `TutorialV40.kt`, `Tutorial.kt`, `SubscriptionScreen.kt`, `SubscriptionDetails.kt`, `KidsChallengeUi.kt`, `KidsMascotLayer.kt`, `Theme.kt`, `app/build.gradle.kts`, `tools/comic_assets/manifest.py`, `assets/comic/**`.
