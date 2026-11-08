# DrawGuidance — Durum ve Mimari Notları

_Son güncelleme: 2026-09-17 (Claude Cowork oturumu) — v8: tuval türleri (Resim / Çalışma-Not), not kalemleri, şekil ekleme, yazı elemanı ve font ayarları, tuval kırpma, arka plan rengi, 2 sn basılı tutunca seçim_

## Özet

Kotlin (Android/Compose) + Express.js + SQLite (Room) tabanlı çizim (v6: sunucuda veritabanı yok)
uygulaması. Backend ve Android uygulaması yazıldı, **derlendi ve uçtan uca test
edildi**. Her şey kullanıcının bilgisayarında `C:\Users\ahmet\Documents\DrawGuidance\`
altında (Gezgin'deki "Documents" kısayolu farklı bir klasörü gösteriyor; tam yol
kullanılmalı):

- `android\`, `backend\` — kaynak kod
- `DrawGuidance-debug.apk` — derlenmiş uygulama
- `Telefonda-Ac.bat` — backend'i başlatır, USB'deki telefona APK'yı yükler ve
  bilgisayarın Wi-Fi adresini `server_url` olarak vererek açar
- `Emulatorde-Ac.bat` — backend'i başlatır, emülatörü açar, APK'yı yükler
  (sunucu `http://10.0.2.2:3000/`)
- `scripts\` — bu iki dosyanın PowerShell mantığı (`common.ps1`). `server.js`
  değiştiyse çalışan eski backend'i (port 3000'deki node) durdurup yenisini başlatır
  (`logs\backend.version` içinde MD5 damgası).
- `server\backend\server.js` — esbuild ile tek dosyalık backend (veritabanısız, 0.0.0.0:3000).
  v6'da taşınabilir PostgreSQL (`server\pgsql`, `server\pgdata`) kaldırılıyor (bkz. Açık Adımlar).
- `android-sdk\` — Windows emülatörü, platform-tools, Android 34 sistem imajı
- `avd\` — AVD tanımı ve verisi (`avd\DrawGuidance_API34.avd`, ~1,5 GB). 2026-09-15'te
  D:\HyperV'den C:'ye taşındı (C:'de ~12 GB boş kaldı); hızlı açılış snapshot'ı
  taşınmadı, ilk açılış soğuk açılış olur.
- `logs\` — scriptlerin logları (Claude buradan okuyup hata ayıklıyor)
- `v4-onizleme.png`, `v5-onizleme.png` (v7 teslimiyle `v7-onizleme.png`) — tur sonu ekran önizlemeleri

## Mimari

- **Backend (v6):** Node.js + TypeScript + Express 5, **veritabanı yok**. Yalnızca `GET /api/health`;
  diğer tüm yollar JSON 404. Çizim verisi sunucuya gönderilmez (kullanıcı isteği). Sunucu ileride yapay
  zeka özellikleri için zemin olarak duruyor. (v1–v5'te Drizzle + PostgreSQL ile /api/canvases CRUD vardı.)
- **Android:** Kotlin 1.9.24, AGP 8.5.2, Gradle 8.9, Compose (Material 3),
  Room (v5), Retrofit (yalnızca sağlık kontrolü) + kotlinx.serialization 1.6.3, Koin, Navigation-Compose, Coil.
  Paket: `com.karapp.drawguidance`. Tuvaller yalnızca cihazda; cloud anahtarı/senkron menüsü/senkron durumu
  v6'da kaldırıldı (`MIGRATION_3_4` senkron kolonlarını siler, çizimleri korur).
- **Sunucu adresi:** `ServerConfig` (SharedPreferences) + OkHttp interceptor ile
  çalışma anında değiştirilebilir; Ayarlar ekranı; açılış intent'i `--es server_url <url>`.
- **Veri modeli:** Tuval = ad, genişlik, yükseklik, arkaplan rengi,
  aiEnabled, içerik (vektör stroke listesi, **tuval koordinatlarında**). Stroke = id, points, colorHex, brushType, sizePx, opacity,
  order, groupId, assetId, flow, strength. Yerelde ayrıca `saved_images` ve `canvas_assets` tabloları.

### Çizim ekranı (v2–v4)

- **Renk:** HSV paleti (doygunluk/parlaklık alanı + ton çubuğu) ve altında
  kullanıcıya özel **Kayıtlı Renklerim** (SharedPreferences, en fazla 24; + ile ekle,
  basılı tutunca sil). Siyah/gride ton seçilince canlı renge geçer.
- **Seçili fırça göstergesi:** çizim alanının köşesinde ikon+ad+renk+boyut; çizim
  sırasında parmağın yanında fırça ikonu.
- **Araç menüsü (v3):** Geri al / Fırça Seç / Silgi / Fırça Ayarları / Elemanlar / Resim ekle /
  yakınlaştırma oranı (dokununca Sığdır); modlar: Çiz, Seç, Yakınlaştır, Taşı, Döndür,
  Ölçekle, Genişlet (`EditMode`). Çiz dışındaki modlarda altta açıklama + hızlı işlemler çubuğu
  (`ModeBar`). İki parmak her modda yakınlaştırır.
- **Seç modu:** dokunulan eleman (grubuyla) seçilir, çift dokunulan elemanın seçimi kalkar; seçili
  elemanların kesik çizgili kutusunun köşesinde adı ("Marker 1") yazar.
- **Dönüşümler:** Taşı, Döndür (15° katlarına yapışır), Ölçekle (köşe = orantılı, kenar = tek yön;
  fırça kalınlığı √(sx·sy) ile ölçeklenir). Hepsi noktalara uygulanır, geri alınabilir.
- **Genişlet:** kağıt kenar/köşeleri sürüklenir (64–8192); boyut + içerik tek sorguda yazılır.
- **Görünüm:** `ViewportState` (ekran = offset + tuval×scale, %25–%1200).
- **Elemanlar (v3):** eleman = birbirine değen çizimlerin bağlı bileşeni (union-find + parça sınır
  kutuları). Silgi çizgileri listede yok; en çok üzerinden geçtiği önceki elemana gizlice bağlanır.
  Gruplama eleman üzerinden (`groupId`). "Renk Ver" palet alt sayfası.
- **Resim olarak kaydet:** tuval listesinde karta uzun basınca üst menüde; PNG Galeri `Pictures/DrawGuidance`.
- **v4 — Fırçalar:** Silgi üst menüde ayrı düğme; "Opaklık" etiketi; fırçaya özgü varsayılan boyutlar
  (`BrushType.defaultSize()`), değiştirilen boyut oturum boyunca fırça başına hatırlanır.
- **v4 — Tamamen silinenler:** `ElementOps.pruneErased` silgilerin tamamen örttüğü çizim/resimleri ve
  artık hiçbir şeye değmeyen silgileri veriden çıkarır (v5: yalnızca tam akış + tam opaklıktaki silgiler örter).
- **v4 — Resim ekleme:** `ImageImportValidator` (PNG/JPEG/WEBP, ≤1 MB, kenar 16–4096 px, ≤16 MP, Sobel
  detay puanı ≥%30 red / ≥%15 uyarı, >1600 px küçültme). `BrushType.IMAGE` elemanı, baytlar
  `canvas_assets` tablosunda; cloud'a gönderilmez.
- **v4 — Sıkıştırılmış kayıt:** `canvases.content` BLOB = `ContentCodec` (ikili + DEFLATE, kayıpsız,
  JSON'dan ~10 kat küçük). Room v3, `MIGRATION_2_3`.
- **Alt menü (v3):** Tuvallerim / Resimlerim / Ayarlar neumorphic alt menüden (`MainBottomBar`).
- **Tasarım:** neumorphism (`ui/theme/Neumorphism.kt`, `ui/components/NeuComponents.kt`).

### v5 — Fırça kategorileri, akışkanlık/çizim gücü, performans, komut kutusu, yatay/tablet

- **Fırça motoru yeniden yazıldı** (`ui/drawing/BrushPainter.kt`, `BrushTextures.kt`, `BrushStyle.kt`):
  yerel `android.graphics.Canvas` üzerinde motorlar PATH, TAPERED_PATH, RIBBON, BRISTLE, DABS (kâğıda
  sabit BitmapShader tanecik dokusu), SOFT_DABS, STAMP_SHAPE, PATTERN, SCATTER, HALFTONE. 23 fırça,
  8 kategori (`BrushCategory`): Sanat Kalemleri (Kalem, Mürekkep Kalemi, Teknik Kalem, Kaligrafi Ucu,
  Kömür), Geniş Uçlu (Marker, Yassı Fırça, Fosforlu), Sert Uçlu (Sert Fırça, **Pastel** — sert uçlu,
  tanecikli, kenardan kırıntılı), Yumuşak Uçlu (Yumuşak Fırça, Airbrush), Şekil İzi (Yıldız, Kalp,
  Yaprak), Desen (Nokta Dizisi, Zikzak, Tarama), Doku (Sünger, Tuval Dokusu, Serpinti), Yarım Ton
  (Nokta, Çizgi). Fırça seçici kategori çipli, önizlemeli kart ızgarası.
- **Akışkanlık (flow, 0.05–1) ve Çizim Gücü (strength, 0.1–1)** fırça ayarlarında, çizime kaydedilir.
  Akış = damga başına boya; sık damgalı motorlarda aralığa göre normalize (`1-(1-flow)^spacing`), tek
  geçişte ~flow oranında boya, üst üste geçişte birikir; opaklık üst sınır (saveLayer). Güç =
  bastırma: genişlik 0.45+0.55·s, koyuluk 0.35+0.65·s, doku/yarım ton dolgunluğu seviyeleri.
  Veri: Stroke.flow/strength (varsayılan 1), ContentCodec F_DYNAMICS bayrağı, backend zod alanları.
- **Performans:** `ElementIndexer` artımlı eleman dizini (ızgara + union-find; 3000 çizimde yeni çizim
  başına ~0,8 ms, tam hesap ~138 ms) tek arka plan işçisinde; silgi temizliği arka planda ve silgi
  alanına odaklı (yeni çizim araya girerse güncel liste üzerinde tekrar); `CanvasSaveQueue` birleştirilmiş
  (conflated) uygulama kapsamlı kayıt; `visualVersion` ile gereksiz yeniden çizim yok; görünüm önbelleğine
  yeni çizimler eklenerek çizilir, tam yeniden çizim arka planda; tuval listesi WhileSubscribed + çözümleme
  önbelleği.
- **Komut kutusu (deneysel):** çizim alanının üstünde her zaman açık (`CommandBar`). "boşlukları doldur"
  (ve boşluğu/aralığı kapat/birleştir varyantları, Türkçe karakter bağımsız) → `GapFiller`: seçili
  çizimlerin serbest uçlarını yön uyumuna göre eşleştirip çizgi yönlerini sürdüren kübik eğrilerle
  bağlar; köprü ilk çizginin fırçası/rengi/boyutu/akış/gücüyle çizilir, birleşenler tek eleman olur.
  Seçim yoksa / boşluk yoksa / komut tanınmazsa bildirim.
- **Yatay ekran / tablet** (`genişlik ≥ 600dp` veya yatay): ana bölümlerde alt menü yerine sol dikey menü
  (`MainNavRail`/`MainSectionFrame`); çizim ekranında araçlar başlık satırında, modlar solda dikey; alçak
  ekranda (yükseklik < 600dp) komut kutusu da başlık satırında. Resimlerim ızgarası çok sütunlu, önizleme
  yüksekliği sınırlı; Ayarlar 640dp ile ortalanır. Manifest yön kilidi yok.

### v6 — Bulut kaydı kaldırıldı, canlı çizim performansı, silinenlerin temizliği, paylaşım

- **Akışlı fırça motoru** (`ui/drawing/StrokeStream.kt`): her motor "önek-kararlı" — çizginin başı sonradan
  gelen noktalarla değişmez. `CurveSampler` artımlı yeniden örnekleme (eski tüm-çizgi örneklemeyle birebir aynı,
  testli); rastgelelik `DabRandom` (tohum + damga sırası). Pastel kırıntıları, airbrush tanecikleri, yassı fırça
  kuru boşlukları artık çizgi uzadıkça yer değiştirmiyor. `BrushPainter.paint` statik çizimi aynı motorla yapar.
- **Canlı katman** (`LiveStrokeLayer`): kesinleşen parçalar 256 px ekran karolarına bir kez yazılır, her karede
  yalnızca karolar + kısa kuyruk çizilir; parmak kalkınca karolar görünüm önbelleğine kopyalanır (yeniden çizim yok).
  Tam ekran çizim katmanı yalnızca silgi/taşıma gerektiğinde açılır; parmak baloncuğu ayrı bileşen (her harekette
  tuval yeniden oluşturulmaz); arka plan önbellek çizimi yeni görünüm isteğinde iptal edilir.
  Ölçüm (masaüstü CPU, 600 noktalı uzun çizgi, kare başına): pastel 8,2→0,9 ms, kömür 10,9→1,1, yumuşak fırça
  11,1→0,8, airbrush 18,5→1,6, yarım ton 7,7→0,9 (çizginin son yarısı).
- **Kayıt:** `ContentCodec.EncodeCache` (çizim başına kodlanmış bayt önbelleği, çıktı birebir aynı) + kayıt
  kuyruğunda 400 ms birleştirme.
- **Silinen elemanlar** (`ErasedStrokePruner`): sonraki silgilerin değdiği her çizim küçük maskede tam kapsamayla
  çizilip silgiler gerçek ayarlarıyla uygulanır; görünür mürekkep kalmazsa kaldırılır (birden çok/yarı akışkan
  silgi geçişi, dokulu fırçalar, resimler). Boşa kalan silgiler, fırçanın doku/saçılma erişimi hesaba katılarak
  temkinli temizlenir (eski geometrik temizlik serpinti gibi fırçalarda kullanılan silgiyi atıp silinmiş izi geri
  getirebiliyordu). Tuval açılışında da bir kez çalışır.
- **Paylaşım** (`data/images/ImageSharer.kt`): Resimlerim görüntüleyicide WhatsApp (Business yedeği), Instagram,
  Pinterest ve sistem seçicisi; `ACTION_SEND` + FileProvider (`res/xml/file_paths.xml`), yüklü değilse uyarı ve
  Play Store bağlantısı, dosya silinmişse uyarı. Manifest'e `<queries>` eklendi. API anahtarı yok.
- **PC başlatıcıları:** `scripts\common.ps1` PostgreSQL başlatmıyor; `.env` yalnızca PORT/HOST.

### v7 — Başlıkta resim kaydetme, dosya boyutu, doğru eleman sayısı, hızlı fırça seçici

- **Resim olarak kaydet** çizim ekranı başlık satırının en sağında (dikey: başlık satırı; yatay/tablet: araçların
  sonunda). Kayıt sürerken ilerleme göstergesi; bitince `ImageSavedDialog` (önizleme, piksel boyutu, dosya boyutu,
  konum; yatayda küçük önizleme + kaydırılabilir). Tuval listesindeki kaydetme de aynı pencereyi açar.
- **Dosya boyutu:** `saved_images.sizeBytes` (Room v5, `MIGRATION_4_5`); PNG önce bayta kodlanıp yazılır. Eski
  kayıtların boyutu Resimlerim açılınca dosyadan okunup yazılır. Liste satırı: `1080×1080 px · 10,6 KB · tarih`.
- **Eleman = gerçekten birleşik çizgiler:** `ElementIndexer` tam silgileri (akış ve opaklık ≥ 0,99) ızgarada tutar.
  İki çizginin temas noktaları (`StrokeShape.contacts`) sonraki tam silgilerce tamamen silindiyse bağlantı kopar
  (kısmi/yumuşak silgi koparmaz; iki temas varsa ikisi de silinmeli). Çift sonucu önbellekte; yeni silgide yalnız
  altındaki çiftler yeniden hesaplanır, yeniden oynatmada tek tam hesap. Masaüstünde 3000 yoğun çizim: tam 643 ms,
  ekleme 17 ms (arka planda).
- **Toast sayısı:** silgi temizliği birikimli, birleştirilmiş tek işçide (hızlı silmede temizlik kaybolmaz; uygulamadan
  önce anlık görüntü yeniden denetlenir). Sayılan: tüm çizgileri kaldırılan **elemanlar**, bu silgiler hesaba
  katılmadan önceki dizine göre. Metin: "Tamamen silinen N eleman listeden ve kayıttan kaldırıldı".
- **Fırça seçici:** `BrushPreviewCache` — örnek izler arka planda bir kez bitmap'e çizilir (bayt sınırlı LruCache,
  16 MB), editör açılınca/renk değişince önceden hazırlanır; seçici içeriği `graphicsLayer` ile tek katmanda.
  Ölçüm (masaüstü CPU, 23 önizleme bir karede): 44,8 ms → 3,2 ms; ön hazırlık bir kez ~118 ms arka planda.
  Dar telefonda (360 dp) araç satırı eşit aralıklı (yakınlaştırma oranı kırpılmıyordu).

### v8 — Tuval türleri, şekiller, yazı, kırpma, arka plan

- **Tuval türü** (`CanvasKind`, Room v6 `kind` kolonu, `MIGRATION_5_6`; eski tuvaller ART): Yeni Tuval modalında iki
  kart. **Resim**: tüm sanat fırçaları + manzara/portre şekilleri. **Çalışma/Not**: not kalemleri + matematik/geometri
  şekilleri, varsayılan boyut **A4 dikey 1240×1754** (`CanvasSizeTemplate.NOTE_A4`). Tür tuval oluşturulurken seçilir,
  sonradan değişmez; tuval kartında "Not" etiketi.
- **Not kalemleri** (`BrushCategory.kind`): Kurşun, Tükenmez, Dolma, Jel, İnce Uçlu (Yazı Kalemleri) + Keçeli, Fosforlu
  (İşaretleme). Fırça seçici ve önizleme ön hazırlığı tuval türüne göre filtrelenir; not tuvalinin varsayılanı Tükenmez.
- **Şekil ekleme** (`ShapeCatalog`, `ui/drawing/ShapeCatalog.kt`): "Ekle" menüsü → Şekil ekle → tür kataloğu
  (Resim: Temel/Manzara/Portre; Not: Doğru-Açı/Çokgenler/Katı Cisimler/Grafik — küp, prizma, silindir, koni, küre,
  piramit, koordinat ekseni, sayı doğrusu, kareli alan, parabol, sinüs). Şekil seçilince Şekil moduna geçilir; tuvalde
  sürüklenince o kutuya, dokununca küçük kenarın %30'u boyutunda çizilir. Çizgiler seçili fırça/renk/kalınlıkla
  eklenir (köşeler keskin kalsın diye 4 birimde bir noktaya bölünür), görünmeyen ayrıtlar kesik çizgi, parçalı
  şekiller tek grup olur. Önizleme ile eklenen birebir aynıdır (kimlikler hareket kimliğinden türetilir).
- **Yazı elemanı** (`BrushType.TEXT`, `Stroke.text: TextSpec`, `ui/drawing/TextRenderer.kt`): Ekle → Yazı ekle →
  tuvale dokun → yazı sayfası (metin, önizleme, yazı tipi ayarları). Yazı, 4 köşeli kutusuyla saklanır (resim gibi):
  taşınır, döndürülür, ölçeklenir, silgiyle silinir, rengi verilebilir; kendi elemanıdır (çizgilerle birleşmez).
  Ayarlar panelinde (Fırça sekmesi) yazı seçiliyken ya da Yazı modunda **Yazı Ayarları**: yazı tipi (Sans, Serif,
  Daktilo, Dar, El Yazısı, Gündelik), boyut, kalın/eğik/altı çizili, hizalama — seçili yazılara anında uygulanır
  (kaydırıcı sürüklemesi tek geri alma adımı) ve kutu sol-üst köşesinden yeniden ölçülür. Kayıt biçimine yazı alanı
  eklendi (yalnızca TEXT türünde yazılır; eski kayıtlar etkilenmez).
- **Kırpma** (`EditMode.CROP`): mod menüsünde Yakınlaştır'ın yerini alır. Çerçeve kenarlardan daraltılır ya da içinden
  sürüklenerek taşınır, dışarıda kalan alan koyulaşır; "Kırp" ile tuval küçülür ve çizimler yeni orijine kayar.
  **Çizimler silinmez**, tuval yeniden genişletilince geri görünür.
- **Arka plan rengi**: başlık satırında (kaydetmenin solunda) ve kırpma/genişletme çubuğunda; palet + kağıt tonları,
  geri alınabilir ve kayıt kuyruğuyla yazılır.
- **Yakınlaştırma yalnızca elle**: `EditMode.ZOOM` kaldırıldı (iki parmak her modda yakınlaştırır, oran düğmesi sığdırır).
- **2 sn basılı tutma**: bir elemanın üstünde parmak hareket etmeden 2 sn tutulunca Seç moduna geçilir ve yalnızca o
  eleman (grubuyla) seçilir; başlayan çizim iptal olur, titreşimle bildirilir.
- **Not:** Yazı sayfası `ModalBottomSheet`; AlertDialog içindeki metin kutusu Compose'da ölçüm döngüsüne giriyordu.

## Doğrulama

- **v8:** Android 97/97 test (40 sınıf) + assembleDebug; backend 3/3. Yeni: `ShapesAndTextTest` (tür katalogları, her şeklin
  kutusuna sığması, kesik ayrıtlar, yazı kayıt/ölçüm/refit, yazının kutusunda çizilmesi ve silinmesi),
  `EditorNotesCanvasTest` (A4 not tuvali, kalem listesi, küp tek grup, dokunarak daire, kart etiketi),
  `EditorTextLongPressTest` (yazı ekleme/fontlar, panelden yazı tipi, metni düzenleme, 2 sn basılı tutma),
  `EditorCropBackgroundTest` (kırpma gizler, genişletince geri gelir, arka plan rengi geri alınabilir),
  `V8RenderShotsTest` + `V8*ShotsTest` (görsel kontrol), DB v6 geçişi.
- **v7:** Android 86/86 test (32 sınıf) + assembleDebug; backend 3/3. Yeni: `EditorSaveImageTest` (düğme başlıkta
  en sağda, pencere, gerçek dosya boyutu, Resimlerim satırı), `EditorElementCountTest` (toast = liste sayısı, kesişimi
  silince 3 eleman), `ElementOpsTest` +4 (kesişim silinince bölünür, kısmi/yumuşak silgi bölmez, iki temas,
  hiç değmeyenler ayrı), `IndexerAndGapFillerTest` +1, `BrushPickerPreviewBench`, `V7ShotsTest` (DG_SHOTS ile
  dikey 360 dp/yatay ekran görüntüleri), DB v5 kolon testi.
- **v6:** Android 76/76 test (27 sınıf) + assembleDebug; backend 3/3. Yeni: `StrokeStreamTest` (örnekleme
  eşitliği, 25 fırçada canlı≈kayıtlı, önbelleğe aktarım), `LiveDrawingPerformanceTest`, `ErasedStrokePrunerTest`
  (rastgele sahnelerde temizlik görüntüyü değiştirmiyor), `EditorEraseAllTest`, `ImageShareTest`,
  `ImageShareMissingFileTest`, codec önbellek testi, v3→v4 geçiş testi.
- **v5:** Android 61/61 test + assembleDebug. Yeni: `BrushDynamicsTest` (her fırça tek kategoride, 23
  fırçanın hepsi iz bırakır ve birbirinden farklı çizer, düşük akış daha az boya: 25% akışta %16–37,
  düşük güç daha dar/açık, pastel tanecikli ve sert kenarlı), `IndexerAndGapFillerTest` (400 adımlık
  rastgele ekleme/silme/taşımada artımlı dizin = tam hesap, performans, odaklı silgi temizliği,
  köprü fırça/yön/tek eleman, kırık kare 4 köşe, Türkçe komut ayrıştırma — "boşluğu" varyantı bu testle
  bulunan hataydı), `EditorCommandTest` (UI: seçim yok uyarısı, tanınmayan komut, Seç modunda iki
  Kömür çizgisi seçilip "Boşlukları doldur" → 1 köprü, tek eleman, tekrar çalıştırınca boşluk yok),
  `EditorStressTest` (96 hızlı çizim + iki satırı silerken yeni çizim; kayıt/temizlik veri kaybetmez),
  `LandscapePhone/TabletLandscape/TabletPortrait/PortraitPhoneLayoutTest` (dikey menü/alt menü, dikey mod
  seçici, komut kutusu tuvalin üstünde; `DG_SHOTS=<klasör>` verilirse tüm pencerelerin ekran görüntüleri).
  `CanvasFlowTest` 4 kaydırıcı (akış/güç kaydı). Backend 15/15 (yeni fırça tipleri + flow/strength,
  aralık dışı red); bundle smoke test.
- Android (v4): 41/41 test; `ContentCodecTest`, `ImageImportValidatorTest`, `EditorEraserImageTest`,
  `ElementOpsTest`, DB v1→v3 migration.
- Android (v3): `ElementOpsTest`, `EditorElementsTest`, `EditorTransformTest`, `CanvasImageExportTest`.
- Android (v2): Robolectric NATIVE grafik, gerçek Room, gerçek backend: `CanvasFlowTest`,
  `SettingsFlowTest`, `BrushRenderingTest`, `ColorPaletteStoreTest`, `DatabaseAndExportTest`,
  `ServerConfigNormalizeTest`.

## Önemli Kararlar / Düzeltmeler

- Prisma yerine Drizzle: Prisma motor ikilileri ağ politikasınca engellendi.
- kotlinx.serialization 1.7.1 → 1.6.3 (Kotlin 1.9 uyumu).
- Room güncellemeleri hedefli SQL UPDATE'ler; v5'te kayıtlar `CanvasSaveQueue` ile birleştirilerek sıralı.
- Stroke koordinatları tuval koordinatı (küçük resimler ve PNG çıktısı doğru ölçeklensin).
- Testler her sınıf için ayrı JVM'de (`forkEvery = 1`): Koin Compose kök kapsamını statik önbelleğe
  alıyor — bu yüzden her ekran boyutu testi ayrı sınıf.
- v5: sık damgalı fırçalarda akış aralığa göre normalize edildi (aksi halde %25 akışta pastel %76 boya bırakıyordu).
- Emülatör: AVD C:'de; yazılımsal GPU (swiftshader).

## Çalışma Kuralı (kullanıcı isteği)

- Her kodlama + test turu bittiğinde yeni/değişen dosyalar
  `C:\Users\ahmet\Documents\DrawGuidance` klasörüne yerleştirilir (gerekirse mevcutların
  üzerine yazılır) ve kullanıcıya neyin nereye konduğu söylenir.
- Kullanıcı komutların Claude tarafından çalıştırılmasını istiyor (betik verilmemeli).

## Ortam Kısıtları

- Derleme ve testler Claude'un bulut ortamında; dosyalar cihaz köprüsüyle commit edilip MD5 ile
  doğrulanıyor (her turda yeni bir hazırlık klasörü; aynı yol eski APK'yı gönderebiliyor).
- Cihaz kabuğu (device_bash) son turlarda "Workspace unavailable"; Windows tarafı .bat çift tıklamayla.

## Açık Adımlar

- **v8 teslim edildi (2026-09-17):** 89 dosya `C:\Users\ahmet\Documents\DrawGuidance` altına yazıldı (v6+v7+v8 birikimli).
  APK, önizleme, strings.xml ve package.json MD5 ile; `android\app\src\main\java` ağacının tamamı dosya boyutlarıyla
  doğrulandı (kaynakla birebir).
- **Kullanıcının Gezgin'den sileceği eski dosyalar** (cihaz kabuğu kapalı olduğu için Claude silemiyor): `server\pgsql\`,
  `server\pgdata\`, `server\backend\drizzle\`, `backend\src\db\`, `backend\src\routes\`, `backend\src\validation\`,
  `backend\src\lib\` (boş), `backend\src\middleware\validate.ts`, `backend\drizzle\`, `backend\drizzle.config.ts`,
  `backend\test\canvases.test.ts`, `android\app\src\main\java\com\ahmet\drawguidance\data\remote\dto\`.
- v8 APK'nın telefonda denenmesi (ilk açılışta veritabanı v6'ya dönüşür; eski tuvaller Resim türünde kalır).
- Komut kutusu kural tabanlı; ileride yapay zeka komutlarına bağlanabilir (şekil/yazı komutları için iyi bir zemin).
- Cihaz aktarım sınırı: bağlı klasörden 7 seviyeden derindeki dosyalar geri okunamıyor (doğrulama klasör listesiyle yapılır).
