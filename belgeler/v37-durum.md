# DrawGuidance v37 — durum

_2026-10-20 · Room **v15** (sinir ağları tablosu) · minSdk 21 – targetSdk 36 · katalog **37000** · sunucu **37.0.0**, kapı 4747 · 7 dil_

## İstekler ve yapılanlar

### 1. Seçim pencerelerinde küçük resimler

- Şekil, efekt, karakter, şablon, formül ve 3B şekil pencerelerindeki küçük resimler artık tek ortak yoldan geliyor (`rememberKeyedThumb`, `ui/components/Progressive.kt`).
- Sorunun nedeni: küçük resmin durumu sekme değişince yenilenmiyordu, bu yüzden ilk sekmenin resimleri yerinde kalıyordu. Şimdi durum öğenin anahtarıyla birlikte yenileniyor: önbellekte varsa aynı karede, yoksa arka planda çiziliyor.
- Çizim hata verirse başka bir öğenin resmi görünmüyor; hata merkezi hata yönetimine bildiriliyor.

### 1.1. Karakterler

- Karakter çizimi anime ve çizgi roman stiline geçti: yeni baş çizimi (sivri saç tutamları ve perçem, iris ve kirpikli anime gözleri, saçın üstünde kaşlar, yüzde cel gölge, çene tipleri). 15 türün her biri ayrı ayrı yeniden çizildi.
- **26 poz** (eskiden 9): ayakta, yürüyor, koşuyor, oturuyor, el sallıyor, işaret ediyor, kollar kavuşturmuş, zıplıyor, düşünüyor, **saldırıyor, yumruk atıyor, tekme atıyor, dövüş duruşu, kavga ediyor, bağırıyor, savunuyor, yere düşüyor, yaralı, zafer, ağlıyor, gülüyor, uçuyor, güç topluyor, V işareti, korkup siniyor, selam veriyor**.
- **13 ifade** (eskiden 8): sakin, mutlu, üzgün, kızgın, şaşkın, korkmuş, düşünceli, utangaç, **öfkeli bağırma, kararlı, alaycı, şok olmuş, heyecanlı**.
- Toplam 15 × 13 × 26 = **5070 karakter**. Hepsi tuvalde vektör şekil olarak geliyor; arama yeni poz ve ifade adlarını da buluyor.
- Bütün karakterler kodla çizilmiş özgün tasarımlar; tanınmış bir karakter ya da internetten izlenen çizim kullanılmadı.

### 1.2. Merkezi hata yönetimi ve rapor penceresi

- Var olan koda en az dokunarak tek bir hata merkezi eklendi (`diagnostics/AppErrors.kt`). Eski `AppLog.e` çağrıları, kaydetme, dışa aktarma, küçük resim çizimi ve eşitleme hataları buraya geliyor; yeni özelliklerin hepsi de aynı yolu kullanıyor.
- **Kullanıcının fark ettiği hatada** "Bir sorun oluştu" penceresi açılıyor: ne olduğu, ayrıntılar, isteğe bağlı not, **Gönder / Gönderme**. Aynı hata bir oturumda bir kez soruluyor. Sessiz hatalar pencere açmadan kaydediliyor.
- **Çökme** diske yazılıyor ve uygulama sonraki açılışta "Uygulama beklenmedik şekilde kapandı" diye soruyor.
- Rapora tür, özellik, not, uygulama sürümü, cihaz, dil, kurulum kimliği ve son 30 işlemin izi giriyor. **Çizimler ve kişisel bilgiler gönderilmiyor.**
- Rapor olay tablosuyla gidiyor: bağlantı yoksa bekliyor, gelince gönderiliyor.
- **Ayarlar → Gelişmiş → Hata raporları:** Her seferinde sor / Sormadan gönder / Gönderme, ayrıca elle "Sorun bildir".
- **Sunucu:** `POST /api/v1/reports` ucu, `error_reports` koleksiyonu (aynı hatalar parmak iziyle gruplanıyor). Gövde en çok 256 KB; kurulum başına saatte 30, IP başına saatte 300 rapor sınırı.

### 2. Not alma modlarında yazı, diyagram ve formül

- Not ve basit not modlarında **Yazı, Diyagram ve Formül** bulundukları menünün en başına alındı.
- Yazı seçilince kutu **dokunulan yere** konuyor; sürükleyerek istenen boyutta kutu da çizilebiliyor. Kutu tutamağıyla taşınıyor ("Yazıyı taşı").
- Kutu türleri: **Başlık, Kısa yazı, Paragraf**. Kısa yazı tuvalin sağ kenarında, paragraf kutunun eninde **kendiliğinden alt satıra geçiyor**. Başlık, başlık biçimiyle (büyük yazı) yazılıyor.

### 2.1 ve 11. Sinir ağları

- **Not alma modunda iki ayrı ağ:**
  - **Şekil düzeltme ağı** (`mode_notes.bin`): yalnız şekiller için, resim ağının gövdesinden sıfırdan kuruldu (238 sınıf). Eski not alma ağının verisi kullanılmadı.
  - **Yazı düzeltme ağı** (`notes_text.bin`, yeni): **Latin, Kiril, Arapça, Japonca ve Korece** için ayrı çıkış katmanları var. Korece heceler üç parçasından (ilk ünsüz, ünlü, son ünsüz) tanınıyor, böylece 11.172 hecenin hepsi kapsanıyor. Yazı tanımaya arayüz dili ipucu olarak veriliyor.
- **Eski not alma ağının verisi silindi:** telefonda öğrenilmiş eski not ağı verisi açılışta siliniyor. Resim ve çizgi roman ağlarının cihazda öğrendikleri korunuyor.
- **Mod ağları yeniden eğitildi (11):** ART 566 sınıf (160 yeni nesne), COMIC 614 sınıf (nesneler, 15 karakter, 24 efekt). Ağ sürümleri 37; çocuk (KIDS) ağı değişmedi.
- **Saklama:** ağlar cihazda Room'daki yeni `catalog_nets` tablosunda (Room v15), sunucuda `catalog_nets` koleksiyonunda. Yerleşik ağlar ilk açılışta veritabanına yazılıyor; sunucudan daha yenisi gelirse ayrı satırda tutuluyor. v36'dan kalan ağ dosyaları veritabanına taşınıyor.
- **Eşitleme:** yazı ağı katalogda `NOTES_TEXT` adıyla taşınıyor ve telefonla sunucu arasında öteki ağlar gibi eşitleniyor.

Doğruluk (eğitimde kullanılmayan **yapay el yazısıyla** ölçüldü; gerçek el yazısında düşük çıkacaktır):

| Konu | Önce | Sonra |
|---|---|---|
| Resim ağı, eski sınıflar (ilk tahmin) | %93,8 | %99,8 |
| Çizgi roman ağı, eski sınıflar | %92,0 | %99,6 |
| Not ağı (şekil), eski sınıflar | %96,6 | %99,9 |
| Çizgi romanda eğitimde görülmemiş poz/ifade birleşimleri | %34,9 | %76,9 |
| Latin dizgiler (dil ipucusuz) | %97,8 | %98,2 |
| Bozulmuş Latin sözcükler | %90,2 | %93,6 |

| Yazı sistemi (dil ipucuyla) | Tek karakter | Sözcük, tam doğru |
|---|---|---|
| Kiril | %99,8 | %84,0 |
| Arapça | %98,3 | %89,9 |
| Japonca (kana ve 86 yaygın kanji) | %99,3 | %95,8 |
| Korece | %95,4 (hiç görülmemiş hecede %83,5) | %90,3 |

### 3 ve 3.1. Çocuk modunda boyama ve Ana sayfa önerisi

- Çocuk modunda boyama düğmesi **silginin hemen yanında** ve boya kovası simgesiyle.
- Kova seçilip renk seçilince parmağın üstünde bir **boya kovası imleci** beliriyor. Altındaki şekil parlak bir çerçeveyle seçiliyor, boyanacak bölge seçilen renkte yarı saydam ve kesikli çizgiyle gösteriliyor.
- **Dokununca** ya da sürükleyip bırakınca altındaki bölge boyanıyor. **2 saniye basılı tutunca** bir halka doluyor ve bütün şekil o renge boyanıyor (kapı tokmağı gibi küçük koyu ayrıntılar korunuyor). Her boyama tek adımda geri alınıyor.
- **12 yaşından küçük** (doğum tarihi hesapta olan) kullanıcıya Ana sayfada öneri kartı çıkıyor: el sallayan maskot "İstersen en eğlenceli çizimleri yapmaya başla" diyor. "Hadi başla" çocuk tuvalini açıyor, "Şimdi değil" kartı gizliyor ve seçim hatırlanıyor. Kart internet yokken de görünüyor.

### 4 ve 4.1. Çocuk modu maskotu "Boyacık"

- Özgün, 3B görünümlü bir karakter: mor bereli, parlak turuncu bir boya damlası. Işık geçişleri, kenar ışığı ve yer gölgesi var. Ekranın alt köşesinde duruyor.
- **7 yüz ve 7 el ifadesi** var; her konuşmada başka bir ifadeye geçiyor. Zıplıyor, göz kırpıyor, balondaki yazı yazılırken **ağzı oynuyor**.
- **İlk girişte eğitim:** 9 adımda araçları tanıtıyor ve örnek bir resim yapıyor (güneş, ev, balonlar, kovayla boyama, 2 sn basılı tutarak hepsini boyama, silgi, geri al, kâğıt rengi, çimen). Konuşma balonu kafasının üstünde açılıyor; **ok simgesiyle Tamam, çarpı simgesiyle Sonlandır**.
- **Tuvalde sürekli kalabiliyor:** yüz düğmesiyle açılıyor, açık kalıyor ve seçim hatırlanıyor. Maskota dokununca ipucu veriyor.
- **Yapay zekâ önerileri** çocuk modunda maskotun balonunda: şekil, yazı ve parça önerileri için her türün 2–3 farklı cümle kalıbı var. **Tik simgesiyle Uygula**, **çarpı simgesiyle Kapat** (karakteri ve balonu kapatır). Uygulayınca maskot övgü cümlesi söylüyor.

### 5. Depolama (Ayarlar → Kaydetme → Depolama)

- Uygulamanın cihazda kullandığı yer bölüm bölüm: tuvaller ve resimler, katalog ve sinir ağları, dışa aktarılanlar, önbellek, toplam.
- **Önbellek sınırı** (sınırsız ya da MB/GB): aşılınca en eski önbellek dosyaları siliniyor. Tuvaller ve dışa aktarılanlar silinmiyor.
- **Önbelleği temizle** ve **Yer aç** düğmeleri; ne kadar yer boşaldığı yazılıyor.

### 5.1 ve 5.2. Eşitleme ayarları (Ayarlar → Bağlantı → Eşitleme ayarları)

- **Ağ:** "Wi-Fi ve mobil veri" ya da "Yalnız Wi-Fi". Yalnız Wi-Fi seçiliyken mobil veride bekliyor.
- **Eşitlemeyi durdur / başlat:** durdurulunca işler kaybolmuyor, başlatınca gönderiliyor.
- **Günlük indirme ve yükleme sınırları:** sınır dolunca ertesi gün sürüyor. Bugünkü indirme/yükleme miktarı ve eşitlemenin durumu kartta yazıyor.
- İki kart da öteki ayar kartlarıyla aynı görünümde, mevcut sekmelerin içinde.

### 6. Tekrar eden kodlar ve hız

- **Tekrar azaltma:**
  - 26 dosyadaki 32 alt pencere ortak `NeuSheet` kabını kullanıyor (her birinde tekrarlanan başlık kodu kalktı).
  - İki ayrı formül ayrıştırıcısı birleştirildi; çizim tarafı `Formula`, içerik modülündeki `Expression`'ı kullanıyor.
  - Bütün seçici pencerelerin küçük resimleri tek ortak yoldan geliyor (madde 1).
- **Veri yapıları:**
  - Dokunma sınaması (`ElementIndex.hitTest`): her öğenin sınır kutusu bir kez hesaplanıyor, kutunun dışındaki öğeler atlanıyor ve en üstteki ilk isabette duruluyor.
  - Taşıma ve döndürme sırasında hareket eden çizgiler her karede yeniden hesaplanmıyor; yalnız tuval dönüşümü uygulanıyor.
  - Seçili çizgiler bir kez bulunuyor (her karede dört ayrı tarama yerine).
  - Yerleşik şekil adları tabloda (doğrusal arama yerine).
  - Öneri dizinine kabul edilen şablon eklenirken dizin baştan kurulmuyor.

### 7 ve 8. Panel düzeni ve Şablonlar

- Eski "Panel düzeni" düğmesi kaldırıldı; içindeki bütün düzenler Şablonlar penceresinde (eksik olan "İki + büyük" eklendi).
- Paneller boyutlanınca (yükseklik, genişlik, aralık, kenar boşluğu) içlerindeki çizimler **panelle birlikte taşınıyor ve ölçekleniyor**, panelin içinde kalıyor. Kaydırıcı sürüklenirken küçültüp büyütmek kayıpsız.

### 9 ve 9.1. Basit çizim modları

- Her düğmeye dokununca **adı yanında bir süre görünüyor**.
- Şekil düzenlemede şekiller **çok az düzenleme noktasıyla** açılıyor (12 köşeli bir şekil en çok 6 noktayla).
- **Kenardan genişletme:** çizim alanının kenarına basılı tutunca genişletme modu açılıyor ve kâğıt sürüklenerek büyütülüyor. Araç satırında ayrıca **Genişlet** düğmesi var. Basit resim ve çocuk modunda var, **basit not modunda yok**.

### 10. Katalog ikili biçimde

- Sunucu vektörleri ve sinir ağlarını ikili biçimde de veriyor (`Accept: application/vnd.drawguidance.catalog+bin` ya da `?format=bin`). Ayrıntı: `backend/docs/v37-api.md`.
- Uygulama, manifest ikili bölümleri bildirirse ikili istiyor. Yollar cihaz veritabanına yeniden kodlanmadan yazılıyor. Eski sunucuyla JSON'la çalışmayı sürdürüyor.
- Eski uçların JSON yanıtları değişmedi; eski uygulamalar etkilenmiyor.
- Boyut (829 vektör): JSON 10,7 MB (gzip 2,1 MB) → ikili 2,4 MB (gzip ~1 MB).
- Tohum dosyası 37000 sürümüyle yeniden üretildi: 750 şablon, 829 vektör, 5 ağ.

### 12. Google ağ hatası ve Çıkış yap

- "Google ağ hatası 7"nin nedeni senin bulduğun gibi sunucunun çalıştırılmasıydı; kodda değişiklik gerekmedi.
- **Hesap ayarlarına "Çıkış yap"** eklendi: hesap silmenin üstünde ayrı bir "Oturum" kartında. Arada geniş boşluk ve "Tehlikeli bölge" ayracı var. Çıkış onaylanınca hesap silinmeden oturum kapanıyor.

## Doğrulama

Bütün koşular bu bulut ortamında yapıldı (Android SDK ve Google kütüphaneleri yardımcı ortamdan kopyalandı; sunucu testleri Docker/MongoDB ile).

- **Uygulama testleri** (Robolectric, 258 sınıf, 127.0.0.1:4747'de gerçek Spring Boot + MongoDB açıkken): 478 test. İlk koşuda 473'ü geçti, 4'ü bilerek atlanıyor.
  - Düşen 5 test uyarlandı ve yeniden koşuldu, hepsi geçiyor:
    - `V33BackendTest` (sunucu 37.0.0) ve `V33SyncEngineTest` (Room v15): eski sürüm beklentileri.
    - `V23NotesTextBoxTest`: not tuvalinde uzun yazı artık sağ kenarda alt satıra geçtiği için kutunun eni baştan var; test kutunun gerçekten daralmasını bekliyor.
    - `V37ErrorReporterTest`: gönderilen rapor sayacı yanıt döndükten sonra artıyor; test bunu bekliyor (yük altında yarış).
    - `CanvasFlowTest`: yerel sunucu, test başladıktan sonra açıldığı için bağlantı reddedildi; sunucu açıkken geçiyor.
- **Çekirdek modüller:** `:core:content` 237 test (18'i yalnız bayrakla çalışan eğitim/teşhis testi, atlandı), `:core:model` 34 test; hepsi geçti.
- **Sunucu:** 58 testin hepsi geçti (`CatalogApiTest` 12, `CatalogSeedingTest` 9, `CatalogBinaryTest` 9, `ReportApiTest` 8, `UserApiTest` 11, `HealthApiTest` 3, `ErrorReportServiceTest` 4, `RateLimiterTest` 2). Yeni tohumla sunucu manifesti: sürüm 37000, 750 şablon, 829 vektör, 5 ağ.
- **Çeviri:** 2180 metnin hepsi 6 dilde var, yer tutucular aynı (betikle denetlendi). Android lint bu ortamda çalışmadı: lint motoru yalnız Google deposundan iniyor ve o adres buradan kapalı.
- **APK:** `assembleDebug` başarılı (36 MB, minSdk 21, targetSdk 36). Ortak hata ayıklama anahtarıyla imzalı (SHA-1 `0E:16:DE:72:…:A0:17`); yeni ağ dosyaları içinde.
- **Yeni testler:**
  - Uygulama: `V37AccountThumbTest`, `V37ErrorsTest` (hata merkezi, rapor gönderimi, pencere ve ayar), `V37NotesTextTest`, `V37KidsTest` (16), `V37StorageSyncTest`, `V37PanelsTest`, `V37SimpleModesTest` (basit modlar ve az nokta), `V37DedupeTest`, `V37PerfTest`, `V37CatalogBinarySyncTest`, `V37NetsStorageTest`.
  - Çekirdek: `V37CharactersTest`, `HandwritingNetTest`, `ModeNetsV37Test`, `catalog/CatalogBinaryTest`.
  - Sunucu: `CatalogBinaryTest`, `ReportApiTest`, `ErrorReportServiceTest`, `RateLimiterTest`.
- **Uyarlanan eski testler:** `V33SyncTest` (ağ sürümü, sunucu 37.0.0, Room v15), `DatabaseAndExportTest` (Room v15, `catalog_nets`), `V23EditorTest` (yazının kenarda alt satıra geçmesi), `CatalogTest` (katalog 37000).

## Kalan eksikler ve öneriler

- **Yazı ağı yalnız yapay el yazısıyla ölçüldü.** Gerçek el yazısında doğruluk düşük çıkacaktır; gerçek örneklerle denenmeli.
- **Kiril yazı türü ipucusuz zor ayırt ediliyor** (birçok harf Latin harfine benziyor). Uygulama arayüz dilini ipucu olarak verdiği için Rusça arayüzde sorun yok; Türkçe arayüzde Rusça yazı Latin okunabilir.
- **Çince yok.** Arapça harfler ayrı ayrı (bitişik olmayan) yazılmalı.
- Latin yazı tanıma biraz yavaşladı (6 karakterlik dizgide 5,9 ms → 10,6 ms, masaüstü JVM).
- **Karakterler daha ayrıntılı** (karakter başına yaklaşık 4700 nokta); büyük ızgarada eski telefonlarda ilk açılış biraz uzun sürebilir.
- Çocuk modunda kovayla basılı tutarken iki parmakla yakınlaştırılırsa küçük bir görüntü kalıntısı kalabiliyor.
- **Gerçek cihaz denemesi:** maskot, kova boyama, kenardan genişletme ve eşitleme sınırları gerçek telefonda denenmeli.
- Kullanım Şartları ve KVKK metinleri hâlâ taslak (v36'dan).

## Dosyalar

Değişen ve eklenen bütün dosyalar git'te sahnelenmiş durumda (`git diff --cached --stat`). Ana dosyalar:

- **Uygulama:**
  - Hata yönetimi: `diagnostics/AppErrors.kt` (yeni), `AppLog.kt`, `Diagnostics.kt`; `data/reports/ErrorReporter.kt` (yeni); `ui/components/ErrorReportSheet.kt` (yeni); `ui/screens/settings/ReportSettings.kt` (yeni).
  - Küçük resimler ve ortak kap: `ui/components/Progressive.kt`, `NeuSheet.kt` (yeni) ve onu kullanan 26 pencere dosyası.
  - Not modları: `canvasedit/components/TextTools.kt`, `InlineTextEditor.kt`, `EditorToolbar.kt`; `state/TextController.kt`.
  - Çocuk modu: `data/settings/KidsPrefs.kt`, `ui/components/KidsMascot.kt`, `KidsMascotTalk.kt`, `canvasedit/components/KidsPaint.kt`, `KidsMascotLayer.kt`, `state/KidsPaint.kt`, `KidsSuggest.kt`, `tutorial/TutorialKids.kt`, `TutorialKidsUi.kt`, `home/KidsHomeCard.kt` (hepsi yeni); `home/HomeScreen.kt`.
  - Depolama ve eşitleme: `data/settings/StorageManager.kt`, `data/sync/SyncPolicy.kt`, `ui/screens/settings/StorageSyncSettings.kt` (yeni); `data/sync/SyncEngine.kt`, `Connectivity.kt`.
  - Paneller: `state/PanelSets.kt`, `state/ComicController.kt`, `components/TemplatesSheet.kt`.
  - Basit modlar: `components/DrawingCanvas.kt`, `ToolHelp.kt`, `CanvasEditScreen.kt`, `CanvasEditViewModel.kt`, `ui/drawing/VectorEdit.kt`.
  - Hız: `editor/ElementOps.kt`, `ui/drawing/ShapeRegistry.kt`, `Formula.kt`, `data/repository/ShapeTemplateRepository.kt`.
  - Katalog ve ağlar: `data/catalog/CatalogSyncer.kt`, `CatalogNetRepository.kt` (yeni), `CatalogVectorRepository.kt`, `data/local/CatalogEntities.kt`, `AppDatabase.kt` (v15), `data/remote/v1/*`, `di/AppModule.kt`, `DrawGuidanceApp.kt`, `state/TextSuggester.kt`.
  - Hesap: `ui/screens/account/AccountUi.kt`.
- **Çekirdek (`core/content`):**
  - Karakterler: `comic/ComicCharacters.kt`, `CharacterKit.kt`, `CharacterHead.kt`, `CharacterFig.kt`.
  - Yazı ağı: `ink/HandwritingNet.kt`, `ScriptDecoder.kt`, `TextFeatures.kt`, `InkScript.kt` (yeni); `Handwriting.kt`, `Decoder.kt`, `Prep.kt`.
  - Şekil ağları: `shapes/LibraryTemplates.kt` (yeni), `ShapeNet.kt`, `ModeShapes.kt`; `resources/shapenet/*.bin`, `resources/shapes/library_templates.bin`.
  - Katalog: `catalog/Catalog.kt` (37000), `CatalogBinary.kt` (yeni).
  - Eğitim komutları: `android/tools/modenet/README.md`.
- **Çeviri:** `res/values/strings_v37.xml`, `strings_v37_kids.xml` (yeni) ve 6 dilin `values-*/strings.xml` dosyaları, `resources/i18n/*.tsv`.
- **Sunucu:** `catalog/CatalogBinary.java` (yeni), `CatalogController.java`, `CatalogService.java`; `report/` (yeni: `ErrorReportController`, `ErrorReportRequest`, `ErrorReportService`, `RateLimiter`); `config/*`, `security/SecurityConfig.java`; `application.yml` ve `build.gradle.kts` (37.0.0); `catalog/catalog.json.gz` (37000); `docs/v37-api.md`; testler `CatalogBinaryTest`, `ReportApiTest`, `ErrorReportServiceTest`, `RateLimiterTest`.
- **Belgeler:** bu dosya, `YENI-SOHBET-DEVIR.md`.
