# DrawGuidance v36 — durum

_2026-10-19 · Room **v14** (kütüphane şekilleri tablosu, onay sütunları) · minSdk 21 – targetSdk 36 · katalog **36000** · sunucu **36.0.0**, kapı 4747 · 7 dil_

## İstekler ve yapılanlar

### 1–7. maddeler

1. **Ayarlar.**
   - Beş sekme: Genel, Çizim, Kaydetme, Bağlantı, Gelişmiş. Her sekme kendi kartlarını gösterir; sekme değişince sayfa baştan açılır.
   - Hesap başlığı en üstte, sekmelerin üzerinde, ekran genişliğinde ve beyaz zeminde (koyu temada temanın açık yüzeyi).
   - Google/Firebase kimliği, hesap kimliği ve sunucu durumu gibi teknik alanlar artık gösterilmiyor.
   - Dil, tasarıma uygun açılır seçim kutusundan seçiliyor (her dil kendi adıyla, seçili dil işaretli).
2. **Açılış ekranı.** Logonun altında Türkçede "Çizim Rehberi" harf harf yazılıyor; altında her dilde "created by karApp" imzası beliriyor.
3. **Kullanım Şartları ve KVKK onayı.**
   - Hesap açılırken (doğum tarihi penceresinde) iki onay kutusu var. İkisi işaretlenmeden "Hesabı aç" düğmesi kapalı.
   - Bağlantıya dokununca metin alttan açılan pencerede okunuyor. Metinler `assets/legal/` içinde Türkçe ve İngilizce; öteki dillerde İngilizce açılıyor.
   - Onay zamanı ve metin sürümü (`2026-10`) cihazda (`local_user`) ve sunucuda (`users`) saklanıyor. Onay alanı göndermeyen eski sürüm, var olan onayı silmiyor.
4. **3B boyama.** Boya aracında renk/doku seçmek artık son boyanan parçanın bütün yüzlerini boyamıyor; yalnız fırçanın rengini değiştiriyor. Boyama dokunulan yüze (kapsama göre yüzey/parça/tümü) uygulanıyor.
5. **Şekiller hem sunucuda hem cihaz veritabanında.**
   - Kütüphanedeki bütün vektör şekiller (hazır çizimler, nesneler, efektler…) katalog sürümü değişince bir kez Room'daki `catalog_vectors` tablosuna yazılıyor.
   - Sunucudan inen daha yeni katalog ayrı satırlarla tutuluyor ve açılışta kütüphaneye uygulanıyor.
   - Sunucu tarafında aynı şekiller `catalog_vectors` koleksiyonunda; tohum dosyası (`catalog.json.gz`) 36000 sürümüyle yeniden üretildi: 551 şablon, 829 vektör.
   - Karakterler (1080 birleşim) tohuma yazılmıyor; uygulamada tür/ifade/poz parçalarından anında üretiliyor.
6. **Üç renk grubu.** Ayarlar → Genel'de Lavanta (eski renkler), Okyanus ve Gün batımı. Her grubun açık ve koyu teması var; seçilince bütün uygulama yeniden başlatmadan yeni renklere geçiyor ve seçim saklanıyor. Bütün gruplarda yazı/zemin karşıtlığı 10:1'in üzerinde.
7. **Basit modda şekil düzenleme.** Basit resimde "Şekli düzenle" düğmesi: seçili şekil az noktalı düzenlemede açılıyor; seçim yokken araç bekliyor ve dokunulan şekli açıyor. "Noktaları azalt" görünümü bozmadan nokta sayısını düşürüyor.

### 8. madde — çizgi roman editörü

1. **Nesneler.** Şekil Ekle penceresinde "Nesneler" bölümü: 13 kategori, 160 nesne (günlük, ev, yiyecek, ulaşım, doğa, giyim, aletler, spor, fantastik, dedektif, bilim kurgu, şehir, müzik). Her nesne en az iki stilde (çizgi roman, anime, gerçekçi). Arama (ad, etiket, kategori ve çeviri), kategori ve stil süzgeci. Resim ve çizgi roman tuvallerinde çalışıyor.
2. **Efektler ve odak çizgileri tek pencerede.** Ekle menüsündeki iki eski düğmenin yerine tek "Efektler" düğmesi.
   - Efektler sekmesi: 4 grupta 27 vektör efekt (aksiyon, büyü, duygu, atmosfer) ve ses yazıları (POW!, BAM!, BOOM!, CRASH!, WHOOSH!, ZAP! önde).
   - Odak çizgileri sekmesi: 5 yeni tür (yakınlaşma, perspektif, ışın, yoğun manga, yumuşak anime). Odak noktası, kalınlık, renk ve opaklık; seçili çizgi grubu sonradan değiştirilebiliyor.
3. **Arka planlar.** Mevcut Arka plan penceresinde 21 sahne. Gündüz/gece, yağmur/kar/sis çeşitleri var; sahne ilk kullanılışta çizilip sıkıştırılarak saklanıyor.
4. **Konuşma balonu.** Mevcut pencere Tür, Yazı ve Kuyruk sekmeleriyle genişletildi.
   - Türler: normal, düşünce, bağırma, fısıltı, anlatıcı kutusu, telefon mesajı, serbest yazı ve SFX.
   - Yazı: Türkçe harfler, yazı tipi, boyut, renk ve hizalama.
   - Kuyruk: yön, uzunluk ve eğrilik; hem yeni balonda hem seçili balonda.
5. **Karakterler.** Ayrı "Karakterler" düğmesi ve penceresi (çizgi roman ve resim tuvalinde; notta yok).
   - 15 tür × 8 ifade × 9 poz = 1080 karakter.
   - Tür, ifade ve poz süzgeci; arama; önizleme; aynalama.
   - Eklenen karakter vektör şekil olarak geliyor; taşınıyor, ölçekleniyor, kopyalanıyor.
6. **Şablonlar.** Ayrı "Şablonlar" düğmesi ve penceresi (yalnız çizgi roman). 4 grupta 22 düzen:
   - Temel: tek panel, iki, üç, dört ve altı panelli düzenler.
   - Dikey webtoon (3 çeşit).
   - Manga: sağdan sola okunuyor; aksiyon ve yonkoma çeşitleri var.
   - Türler: aksiyon (eğik kesimler), komedi, romantizm (yuvarlak köşe ve oval iç panel), korku (kalın çizgi), fantastik ve bilim kurgu (kesik köşe).

   Şablon seçilince paneller otomatik kuruluyor. Paneller sekmesinde şunlar yapılabiliyor:
   - Seçili panelin genişliği ve yüksekliği değişiyor.
   - Sırası değişiyor (içindeki çizim panelle birlikte taşınıyor).
   - Yanına, altına ya da sona panel ekleniyor; panel siliniyor.
   - Panel aralığı ve kenar boşluğu ayarlanıyor.

   Yeni şablon eskisinin yerine geçiyor; çizimler yerinde kalıyor. Elle silinen panelin yerini komşusu dolduruyor. Düzen kayıtta korunuyor.
7. **Bütünleştirme.** Çizgi roman tuvalinde nesne, efekt, balon, karakter ve şablon paneli seçiliyor, taşınıyor, ölçekleniyor, kopyalanıyor ve siliniyor; her işlem geri alınabiliyor. Küçük telefonda (360×640 dp) bütün pencereler ekrana sığıyor.

**Lisans:** Bütün nesneler, efektler, sahneler, karakterler ve şablonlar kodla çizilmiş özgün vektörlerdir. İnternetten resim izlenmedi, telifli karakter kullanılmadı, olmayan bir varlık hazırmış gibi gösterilmedi.

### Hız, bellek ve sıkıştırma

Ölçümler bu ortamda yapıldı (JVM, aynı veri, ısınmadan sonra; kayıt biçiminde 5 koşunun ortalaması):

| Konu | Önce | Sonra |
|---|---|---|
| Çizim kayıt biçimi (`ContentCodec`, 3 kalabalık sayfa, 926 KB): kodlama | 106 ms | 63 ms |
| Aynı veri: çözme | 28 ms | 17 ms |
| Kütüphane şekillerinin cihaz veritabanındaki boyutu (829 şekil; ham JSON 10,3 MB) | 2,76 MB (JSON + Deflate) | 1,35 MB (ikili + Deflate) |
| Aynı şekillerin veritabanından çözülmesi | 147 ms (JSON) | 8 ms (ikili) |
| Sunucu tohumu (`catalog.json.gz`, 13,8 MB JSON) | 3,95 MB (gzip 6) | 3,91 MB (gzip 9) |

- **Kayıt biçimi (`ContentCodec`):** biçim değişmedi, eski kayıtlar aynen açılıyor. Kilitsiz, büyüyen bayt yazıcı kullanılıyor; Deflate kopyasız okuyor. UUID'ler metinden doğrudan baytlara çözülüyor, renk denetimi düzenli ifade yerine karakter karşılaştırmasıyla yapılıyor. Şekil tanımında varsayılan alanlar yazılmıyor.
- **İkili yol biçimi (`CatalogCodec.packPaths`):**
  - Koordinatlar 4 basamağa yuvarlanıp tamsayıya çevriliyor; her nokta bir öncekinden farkıyla (zigzag + değişken uzunluklu sayı) yazılıyor.
  - Metin ayrıştırma yok, doğrudan baytlardan okunuyor.
  - Sunucu API'si JSON olarak kaldı.
- **Veritabanına yazma:** Yerleşik şekiller tek tek üretilip sıkıştırılıyor; bütün yollar aynı anda bellekte tutulmuyor. Tek bir Deflater/Inflater yeniden kullanılıyor, yerel zlib belleği her şekil için yeniden ayrılmıyor.
- **Çizim önbelleği (`ShapeRenderer`):** Önbellek, öğe sayısı yerine toplam nokta sayısıyla sınırlanıyor (400 bin nokta). Pencerelerdeki küçük önizlemeler önbelleği atlıyor; 1080 karakterlik ızgara tuvaldeki şekillerin önbelleğini boşaltmıyor.
- **Panel düzeni:** Hesaplar kutulamasız `float` dizileriyle yapılıyor. Panellerle taşınan çizimlerin bulunması O(n²) yerine O(n).
- **Sunucu:** JSON yanıt sıkıştırması ve MongoDB bağlantı sıkıştırması (zstd) zaten açıktı; katalog gövdeleri önceden gzip'li.

## Doğrulama

- **Çekirdek modüller** (`:core:model`, `:core:content`, bu ortamda): 48 sınıf, 234 test, hepsi geçti. Önizleme resimleri gözle kontrol edildi.
- **Uygulama testleri** (Robolectric, yardımcı derleme ortamında, 127.0.0.1:4747'de gerçek Spring Boot + MongoDB açıkken):
  - Bütün takım: 237 sınıf, 427 test. İlk koşuda 417'si geçti.
  - Düşen 10 sınıftan 7'si eski beklentiler yüzünden düştü (sekmeler, Room v14, sunucu 36.0.0) ve uyarlandı. `V9PerformanceTest` (süre sınırı) ve `V33SearchTest` yük altında düştü.
  - Bu sınıflar `V36DataTest` ile birlikte yeniden koşuldu: 17 testin 16'sı geçti; süre sınırını aşan iki test tek başına geçti.
  - `V36DataTest` daha önceki bir koşuda gerçek bir hata yakaladı: sunucudan gelen şekil, aynı kimlikli yerleşik kaydı siliyordu. Tablonun anahtarı kaynak + kimlik yapıldı.
  - Tek kalan: `V18Api24SmokeTest` (Android 7.0). Robolectric'in Android 7 kütüphanesi Maven sınırı yüzünden indirilemedi; kodla ilgisi yok.
  - Sonuç: 427 testin 426'sı geçti.
- **Sunucu:** 30 testin hepsi geçti (sürüm 36.0.0; yardımcı ortamda Docker/MongoDB ile).
- **APK:** `assembleDebug` başarılı (app-debug.apk, 34 MB).
- **Yeni testler:**
  - `V36BasicsTest`: ayarlar sekmeleri ve hesap başlığı, renk grupları, yasal metinler, 3B boyama, şekil veritabanı (sıkıştırma, geri okuma, sunucu kaydı).
  - `V36SimpleShapeEditTest`, `V36ObjectPickerTest`, `V36EffectsTest`, `V36ScenesTest`, `V36BalloonTest`, `V36CharactersTest`, `V36TemplatesTest`, `V36IntegrationTest` (bütünleştirme ve küçük telefon).
  - Çekirdek: `V36ObjectLibraryTest`, `V36EffectsTest`, `V36ScenesTest`, `V36CharactersTest`, `V36TemplatesTest`, `V36CatalogPackTest`, `V36CodecPerfTest`.
  - Sunucu: `UserApiTest.termsConsentIsStoredAndKeptByOlderClients`.
- **Uyarlanan eski testler:**
  - `V30Test`, `V31Test`: başlık "Çizim Rehberi" ve imza.
  - `V33SyncTest`, `V34Test`: onay kutuları; onay sürümünün sunucuya gitmesi; teknik kimlikler gizli; Room v14 göçü; gerçek sunucu sürümü 36.0.0.
  - `SettingsFlowTest`, `V10SaveSettingsTest`, `V10ShotsTest`, `V13EditorTest`, `V13ShotsTest`, `V18ShotsTest`, `V21EditorTest`, `V23ShotsTest`, `WideLayoutTest`: sekmeler.
  - `DatabaseAndExportTest`: Room v14 ve `catalog_vectors` tablosu.
  - `V34LanguageTest`: açılır dil kutusu.
  - `V29Test`: Efektler penceresi.
  - `HealthApiTest`: sürüm 36.0.0.

## Kalan eksikler ve öneriler

- **Yasal metinler taslaktır.** Yayından önce bir hukukçu gözden geçirmeli; veri sorumlusunun iletişim bilgisi (e-posta/adres) eklenmeli. Metinler yalnız Türkçe ve İngilizce.
- **Eski "Panel düzeni" düğmesi** Şablonlar'ın yanında duruyor (mevcut tasarımı korumak için). İkisi birleştirilebilir.
- **Paneller boyutlanınca** içindeki çizimler yerinde kalıyor; yalnız sıra değişince panelle taşınıyor.
- **Sunucu tohumu** hâlâ JSON + gzip (API değişmesin diye). İstenirse katalog uç noktası da ikili yol biçimine geçirilebilir.
- **Gerçek cihaz denemesi:** Google ile giriş ve onay akışı gerçek hesapla, renk grupları ve yeni pencereler gerçek telefonda denenmeli.
- **Mod ağları** (ART/NOTES/COMIC) yeni nesne ve karakterlerle yeniden eğitilmedi; öneriler eski şablonlara göre çalışıyor.

## Dosyalar

Değişen ve eklenen bütün dosyalar git'te sahnelenmiş durumda (`git diff --cached --stat`). Ana dosyalar:

- **Uygulama:**
  - Ayarlar: `ui/screens/settings/SettingsScreen.kt`, `SettingsUi.kt` (yeni), `LanguageSettings.kt`.
  - Hesap: `ui/screens/account/AccountUi.kt`, `AccountViewModel.kt`, `LegalTexts.kt` (yeni), `assets/legal/*.txt` (yeni); `data/account/AccountRepository.kt`, `data/local/SyncEntities.kt`, `data/remote/v1/RemoteDtos.kt`.
  - Açılış ve renkler: `ui/components/AppBranding.kt`, `ui/theme/Theme.kt`, `Neumorphism.kt`, `ThemePalettes.kt` (yeni).
  - Veritabanı: `data/local/AppDatabase.kt` (v14), `CatalogEntities.kt` (yeni), `data/catalog/CatalogVectorRepository.kt` (yeni), `CatalogSyncer.kt`, `di/AppModule.kt`, `DrawGuidanceApp.kt`.
  - 3B: `ui/screens/model3d/Model3DEditorViewModel.kt`, `Model3DPanels.kt`.
  - Editör: `ui/screens/canvasedit/CanvasEditScreen.kt`, `CanvasEditViewModel.kt`, `state/ComicController.kt`, `state/ShapeController.kt`, `state/PanelSets.kt` (yeni), `components/EffectsSheet.kt`, `CharactersSheet.kt`, `TemplatesSheet.kt` (yeni), `ComicSheets.kt`, `ShapePickerModal.kt`, `DrawingCanvas.kt`, `ToolHelp.kt`, `tutorial/TutorialScripts.kt`.
  - Çizim: `ui/drawing/ShapeRenderer.kt`, `BalloonRenderer.kt`, `VectorEdit.kt`, `ShapePickerFilter.kt` (yeni); `ui/components/BackgroundPicker.kt`, `data/library/PaperRepository.kt`.
- **Çekirdek:**
  - `core/model`: `ContentCodec.kt`, `Stroke.kt` (`PanelRef`).
  - `core/content`: `catalog/Catalog.kt`; `comic/` (`ComicEffects`, `ComicCharacters`, `CharacterKit`, `ComicTemplates`, `PanelPage`, `Comic.kt`); `library/objects/` (8 dosya); `library/Library.kt`, `LibraryCategories.kt`; `paper/Scenes.kt`, `SceneKit.kt`; `vector/VectorRaster.kt`.
- **Çeviri:** `res/values/strings_v36.xml` ve `strings_v31.xml`, `res/values-*/strings.xml`, `resources/i18n/*.tsv`; üretici `android/tools/i18n/v36/` (124 arayüz metni, 205 içerik adı, 7 dil).
- **Sunucu:** `user/User.java`, `UserRequest.java`, `UserService.java` (onay alanları); `build.gradle.kts` ve `application.yml` (36.0.0); `catalog/catalog.json.gz` (36000); testler `UserApiTest.java`, `HealthApiTest.java`.
- **Belgeler:** bu dosya, `YENI-SOHBET-DEVIR.md`.
