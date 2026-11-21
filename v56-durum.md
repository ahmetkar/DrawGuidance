# v56 durum

Son güncelleme: 2026-11-21, K3A durağı (otomatik geçiş: K7 sonuna kadar duraklarda beklenmiyor).

| Durak | Durum | Not |
|---|---|---|
| Aşama 0A (ödüllü reklam, madde 23) | Bitti | Android + iOS |
| K0 (aşama 0: canlı yayın) | Bitti | Android + iOS |
| K1 (V11 model, katman motoru, seçim maskesi, kes/kopyala/yapıştır, fark paketi) | Bitti | Android + iOS |
| K2 (hızlı fırça seçici, gren ve doku, Dönüştür paneli, yansıma, sıvılaştır, 2E dönüştürme/hizalama/efektler) | Bitti | Android + iOS |
| K3 (aşama 7, 8, 8A, 9: renk ayarları, katman çoğalt/birleştir, hazır ayarlar, simetri, desen, gelişmiş görünüm, bağlı kopya, opaklık maskesi, canlı Tekrarla, SVG/PSD/DXF) | Bitti | Android + iOS |
| **K3A** (aşama 9C: yol işlemleri, şekil oluşturucu, Bezier düğümleri, bıçak, efektler, zarf) | **Bitti** | Android + iOS |
| K4 (aşama 10: 3B temizlik, tuvale 3B ekleme) | Sırada | |
| K5–K6 (3B Çizim) | Başlamadı | |
| K7 (aşama 14) | Başlamadı | |
| K8 (9A e-posta girişi, 9B mağaza flavor'ları, aşama 15) | Başlamadı, Ahmet'in onayı gerekir | Ahmet: "9a ve 9b yi en sona al" |

## K3A'da yapılanlar

- **Yol işlemleri (Nesne menüsü, yalnız Resim):** Birleştir, Öndekini çıkar, Kesişim, Hariç tut, Böl, Kırp (görünen
  parçalar), Aynı renkleri birleştir, Kırpma (üstteki şekille), Anahat. Kapalı şekiller, kement dolgu ve açık
  yollar/kalem izleri (konturuyla) girer; sonuç tek geri alma adımı. Kendi poligon boolean motoru (`V56Bool`):
  kesişimlerde bölme, kenar sınıflandırma, halka zincirleme; delikli sonuçlar desteklenir.
- **Şekil oluşturucu:** seçimdeki şekillerin bütün bölgeleri tuvalde gösterilir; dokunuş bölgeyi birleştirilecek
  yapar, çift dokunuş silinecek yapar; Uygula/İptal alt panelde.
- **Bezier düğümleri:** Şekil düzenle › Noktalar'da tek seçili düğümün tutamakları görünür ve sürüklenir (kübik
  eğri); Köşe / Yumuşak düğüm (yumuşakta karşı tutamak aynalanır); **Yolu uzat** uç düğümden Çizgi çiz aracını açar.
- **Yol düzenle:** Konturu yola çevir (uç ve köşe biçimine göre), **Bıçak** (Çiz'de çizilen çizgi seçili ya da
  kilitsiz katmanlardaki şekilleri keser; açık yollar da bölünür), Yolu ayır, Yolu kapat, Yönü ters çevir.
- **Efektler:** Nesne › Efektler'e **Efekt listesi** (Öteleme, Kıvırma, Pürüz, Zikzak, Dalga; eklenir, ayarlanır,
  kaldırılır, önizlemeli; değiştirilebilir: taban geometri saklanır) ve **Zarf bozma** (üstteki kapalı şekil zarf olur,
  alttakiler içine bükülür).

### Plandan sapmalar (K3A)

- Düğüm türü ve tutamaklar `ShapeSpec`'te saklanmaz; yoğun çizgiden türetilir (tutamak = çizgi uzunluğunun üçte biri,
  ilk ara noktaya doğru). Sürükleme çizgiyi kübik eğriye çevirir.
- Efektler `style2.effects` + `style2.effectBase` içinde; dönüşümler tabanı da taşır. Düğüm düzenlemesi efektleri
  kalıcı yapar (taban silinir). Zarf, girdap/büzüştür/şişir yıkıcı kalır.
- Yolu uzat ayrı bir kalem modu değil, uç düğümden Çizgi çiz (CONNECT) aracıdır. "Yolu birleştir" mevcut Bağla aracıyla
  yapılır; "Yolu ayır" alt yolları ayrı nesnelere böler.
- Şekil oluşturucu Seç kipinde çalışır (sürükleyerek birleştirme yok; dokunuşla). Bıçak, açılırken seçili olanları
  (seçim yoksa kilitsiz/görünür katmanlardaki tüm şekilleri) keser; serbest kalem izlerini kesmez.
- Boolean sonuçları dolgulu şekil olur; kontur genişliği Böl/Kırp/Kırpma/Aynı renkleri birleştir'de sıfırlanır.

## Testler (K3A)

- Android: `V56PathOpsTest` 4/4 + `V56PathOpsUiTest` 1/1; V56*, *Codec*, *VectorEdit*, *ShapeEdit* takımları 0 hata.
  Derleme hatasız.
- iOS: `V56PathOpsTests` (5 test, Bezier tutamakları dahil) yazıldı (Swift derleyicisi yok, duraklarda CI yok).

## K3'te yapılanlar

- **Aşama 7, renk ayarları (2D-4):** Özellikler penceresinde Ton/Doygunluk/Açıklık (HSL), Renk Dengesi (gölge, orta
  ton, parlak; açıklığı koru) ve Eğriler; seçime ya da katmana uygulanır, tek geri alma adımı. Katman menüsünde
  **çoğalt** ve **aşağıyla birleştir / görünenleri birleştir**. Ekle menüsünde hazır ayarlar: ışık katmanı (Ekran),
  gölge katmanı (Çarp), parıltı fırçası.
- **Aşama 8, simetri ve desen:** Kılavuzlar penceresinde Simetri (dikey, yatay, dörtlü, radyal 2–16 kol). Simetri
  açıkken Çiz/Kalem/Boya kiplerinde alt panel: tür, kol sayısı, eksen açısı, merkez (çubuklar + Ortala), canlı ayna,
  kapat. Eksenler tuvalde görünür; çizilen çizginin kopyaları ayrı eleman olarak kaydedilir. Seçimde **Desen yap**
  (aralık çubuğu, kopya sayısı gösterilir, en çok 200). Basit resimde üst menüde simetri aç/kapa düğmesi.
- **Aşama 8A (2E):** Özellikler penceresinde **Gelişmiş görünüm**: uç biçimi, köşe birleşimi, sivri sınırı, çizgi
  hizası (orta/iç/dış), kesik çizgi, ok uçları, karışım modu, serbest gradyan, ek dolgular ve ek konturlar.
  **Bağlı kopya** (klon; kaynak değişince kopya da değişir, bağı kopar), **opaklık maskesi** (üstteki nesnenin
  parlaklığı alttakinin opaklığı olur; maskeyi bırak), Nesne › **Tekrarla** canlı (doğrusal, ızgara, dairesel, aynalı,
  adımla çoğalt; önizlemeli; sonradan değiştirilir ya da kaldırılır).
- **Aşama 9, dışa aktarma (2D-3):** Kaydet penceresinde **SVG** (her katman ayrı grup, Inkscape katman adı, opaklık ve
  karışım modu; düz kalemler, şekiller, yazılar ve kement dolgu vektör; doku fırçaları, resimler ve silgili katmanların
  fırça izleri gömülü PNG), **PSD** (katmanlı: ad, görünürlük, opaklık, 17 karışım modu, alfa kilidi, alttakine kırpma;
  en altta "Arka plan"; birleşik görüntü; Photoshop, Krita, GIMP okur) ve **DXF** (R12, mm, katman tablosu; çizgiler
  ve şekiller POLYLINE, yazılar TEXT; resimler ve boya izleri yazılmaz, sayısı bildirilir). Dosyalar Kaydedilenler'e
  eklenir. Çocuk tuvalinde yok.

### Plandan sapmalar (K3)

- 9A (e-posta/şifre girişi) ve 9B (mağaza flavor'ları, sunucu 56.0.0) Ahmet'in isteğiyle K8'e alındı.
- Renk ayarı önizlemesi yalnız vektörlerde canlı; resimler Uygula'da işlenir. Katman renk ayarı yalnız açık sayfaya
  uygulanır. Renk ayarı hesabı `core/model` içinde (`ColorAdjust`).
- Simetri belge düzeyinde `DocExtras` yerine tuvalin kılavuz ayarında (`GuideSpec.symmetry`) tutulur; merkez tuvalde
  sürüklenmez, çubuklarla ve "Ortala" ile ayarlanır. Basit resimdeki simetri düğmesi yalnız dikey simetriyi açar/kapar
  (çocuk modunda yok).
- Opaklık maskesi nesnenin görünümüne gömülür (`style2.opacityMask`); maske nesnesi tuvalden kalkar, "Maskeyi bırak"
  geri koyar.
- Bağlı kopya ve canlı Tekrarla tek modelde (`CloneLink`: kaynak + afin matris); eşitleme `EditorStore.commit` ve
  önizlemede yapılır. Klon, bağı kopar ve opaklık maskesi Seç satırında; Tekrarla Nesne menüsünde.
- Ek dolgu/kontur rengi Android'de hazır renk satırından, iOS'ta renk satırından seçilir. Serbest gradyan ters uzaklık
  ağırlıklı (48 px'lik bitmap ölçeklenir).
- Resim modlarında (Resim, Basit resim, Çizgi roman dışı) eski resim kaydetme penceresi korunur; başlığında "Diğer
  biçimler: SVG / PSD / DXF" düğmeleri birleşik pencereyi o sekmeyle açar. Sayfalı tuvallerde sekmeler doğrudan
  pencerede. iOS'ta Kaydet penceresinde sekme olarak.
- SVG/PSD/DXF açık sayfayı 1x tuval boyutunda yazar (sayfa seçimi ve ölçek yok). PNG, JPEG, PDF ve CBZ zaten vardı.

## Testler (K3)

- Android: `V56ColorTest`, `V56SymmetryRulesTest` 3/3 + `V56SymmetryUiTest` 2/2, `V56VectorRulesTest` 3/3 +
  `V56VectorUiTest` 1/1, `V56ExportRulesTest` 4/4 + `V56ExportUiTest` 1/1. 8A sonunda V56*, *Codec* ve V47* takımları
  (87 test) geçti; 9 sonunda V42SaveTest 4/4 ve V40EditorUiTest 6/6 geçti. Derleme hatasız.
- iOS: `V56ColorTests`, `V56SymmetryTests`, `V56VectorTests`, `V56ExportTests` yazıldı (Swift derleyicisi yok).

## K2'de yapılanlar

- **Aşama 4, hızlı fırça seçici:** köşe düğmesi; son kullanılan ve sabitlenen fırçalar; boyut ve opaklık; silgi; tüm
  fırçalar; iki parmakla fırça geçişi; çizerken kapanır, kaleme yol verir; tablette köşesi taşınır. Renk düğmesi ve
  renk penceresinde damlalık; damlalıkta canlı renk dairesi. Android sağ kenar boşluğu (rail inset) gerekmedi. iOS hızlı
  renk penceresi sistemin ColorPicker'ını kullanır.
- **Aşama 5, gren ve doku:** 6 hazır doku (kâğıt, tuval, kömür, gürültü, nokta, tarama; Android ve iOS aynı karolar),
  fırça greni (doku, ölçek, güç, kayan), resimden fırça (resim uç olarak iz boyunca damgalanır), dokuyla doldur (seçim
  alanı, katman menüsü, Ekle › Doku).
- **Aşama 6, Dönüştür paneli:** Serbest, Tekdüze, Bozma (perspektif), Çarpıtma (4×4 ağ; "Gelişmiş ağ" 6×6; ağ
  çizgileri eğri). Yaslama ayarları (mıknatıslar, kılavuza yaslama, mesafe, hız; tuval türü başına kayıtlı), yatay/
  dikey çevir, 45° ve 90° döndür, tuvale sığdır (çizgi romanda panele), ara değerleme (en yakın / çift doğrusal / çift
  kübik), sayısal giriş, sıfırla, iptal, bitti. Bitti tek geri alma adımı; Sıfırla ve İptal belgeyi değiştirmez.
  Yapıştırınca panel açılır. Seç satırında Dönüştür, Yansıma, Sıvılaştır.
- **Yansıma:** seçimin altına dikey çevrilmiş, dalgalı ve aşağı doğru solan kopya (tek adım).
- **Sıvılaştır:** it, döndür, büzüştür, şişir; boyut ve güç. Seçili resim yoksa seçim ya da etkin katman önce resme
  çevrilir; deformasyon resmin 17×17 ağında tutulur (asıl resim değişmez).
- **Aşama 6A (2E, yalnız Resim):** Kaydır/Eğ kipi; referans noktası (9 nokta + tuvalde sürüklenen serbest nişan;
  döndürme ve çevirme bu noktanın çevresinde); her birini dönüştür; dönüşümü tekrarla; dönüştür ve çoğalt; sayısalda
  orantıyı kilitle. Seç satırında **Nesne** menüsü: hizala (sola, yatay orta, sağa, üste, dikey orta, alta; kâğıda /
  seçime / ana nesneye göre), dağıt (yatay, dikey, boşlukları eşitle), efektler (girdap, büzüştür, şişir: önizlemeli,
  tek adım; yansıma oluştur), tekrarla (dönüşümü tekrarla / çoğalt).

### Plandan sapmalar (K2)

- Bozma ve Çarpıtma'da yazı, şema ve formül değişmez (panelde "N yazı/şema bu kipte değişmez" bilgisi); yola
  çevrilmezler.
- 45° düğmesine uzun basmak 90° yerine ayrı "90° döndür" düğmesi var (uzun basma her düğmede araç yardımını açıyor).
- Nesne menüsü üst menüde değil, Seç satırında bir düğme (aynı yerde Dönüştür, Yansıma, Sıvılaştır).
- Ana nesne: seçimde en son dokunulan nesne (dokunulmadıysa en büyük nesne).
- Girdap/büzüştür/şişir kalıcı uygulanır (tek geri alma adımı); sonradan değiştirilebilen efekt listesi
  (`Stroke.effects`) K3A'daki öteki efektlerle gelecek.

## Testler (K2)

- Android: yeni `V56QuickBrushTest`, `V56GrainTest` 7/7, `V56TransformTest` 10/10, `V56ArrangeTest` 7/7. K2 sonunda
  V56 testlerinin hepsi, V45EditorUiTest, BrushDynamics ve V33ToolHelp birlikte koşuldu: 66/66 geçti. Derleme hatasız.
- iOS: `V56QuickBrushTests`, `V56GrainTests`, `V56TransformTests`, `V56ArrangeTests` yazıldı (Swift derleyicisi yok;
  derleme Ahmet'te).

## K1'de yapılanlar

- **Aşama 1, V11 içerik:** katmana opaklık, 17 karışım modu, alfa kilidi, alttakine kırpma; çizgiye kırpma maskesi,
  simetri, resim dönüşümü (dörtgen/ağ/dalga), gren ve kement dolgu alanları. Eski sürüme paylaşırken içerik
  düzleştirilir ve uyarı çıkar (`V11Flatten`). Yeni özellikler `V56Flags` ile açılıp kapanır. Android ile iOS aynı baytları
  üretir (test var).
- **Aşama 2, katman birleştirme:** opaklık, karışım modları, alttakine kırpma ve alfa kilidi çizimde uygulanır; katman
  önbelleği yalnız değişen katmanda yenilenir; çizilmekte olan çizgi de katmanın ayarıyla görünür; katman panelinde
  ayarlar.
- **Aşama 3, seçim:** tümünü seç, seçimi ters çevir, gruba çift dokunup içine girme, sihirli değnek (renk / kontur /
  görünüm, tolerans çubuğu), seçim alanı (maske: ters, yumuşatma, genişlet/daralt; alan varken yeni çizgiler yalnız
  içine boyanır), kement dolgu, kırpma maskesi yap/kaldır.
- **Aşama 3A, kes/kopyala/yapıştır:** uygulama içi pano sayfalar ve tuvaller arasında çalışır. Yapıştırılan öğeler yeni
  kimlik alır; gruplar ve kırpma maskesi korunur; resim varlıkları kopyalanır (pano, kopyalama anındaki resmi saklar,
  kaynak tuval silinse de yapıştırılır). Aynı yere üst üste yapıştırma 16 px kaydırır; boş yere uzun basınca oraya
  yapıştırır. Çocuk modunda kapalı.
- **Aşama 3B, canlı yayın fark paketi:** yayıncı artık her karede bütün tuvali değil, yalnız değişen sayfanın değişen
  çizgilerini gönderir (`LIVE_DIFF`, tür 16). 20 karede bir, sayfa ya da ayar değişince tam paket gider. Alan taraf bir
  kare kaçırırsa `LIVE_RESYNC` (tür 17) ister. Alan taraf farkı bellekte uygular, 2 sn'de bir diske yazar, ayrılınca
  hemen yazar. Fark yalnız içerik sürümü 11 olan cihazlara gider; eskilere tam paket sürer.

## Testler (K1)

- Android: tam birim test takımı 1085 test; K1'in bozduğu 8 test düzeltildi (yeni düğmelerin yardım metinleri, "Kopyala" ad çakışması → pano düğmesi "Panoya al", fırça kategorisi testi). Kalan düşenler K1'den önce de düşüyordu (v55 tabanında aynı 9 test: V13GalleryShots, V23Model3DCanvas, V25CanvasAdd3D, V34I18nTables, V37KidsMascot, V39GatingUi, V40RulerUi, V40Security, V46NewCharacters) ya da sunucu/ağ ister (CanvasFlow, V33Backend, V40Android10). V25Model3DViewModelTest toplu koşuda düştü, tek başına 6/6 geçti. Yeni: `V56ModelTest`, `V56LayerCompositeTest`, `V56SelectionTest`,
  `V56ClipboardTest` 5/5, `V56LiveDiffTest` 2/2, `ShareProtocolTest` 4/4. Derleme hatasız.
- iOS: aynı adlı Swift testleri yazıldı (`V56ModelTests`, `V56LayerTests`, `V56SelectionTests`, `V56ClipboardTests`,
  `V56LiveDiffTests`). Buradaki ortamda Swift derleyicisi yok; duraklarda CI çalıştırılmadı (Ahmet'in kararı).

## K0'da yapılanlar

- **Ödüllü reklam (alıştırmalar):** tüm adımları kapsayan 25 sn süre sınırı (eskiden başlatma ve abonelik sorgusu
  takılınca dönen simge kalıyordu), liste açılınca ön yükleme, envanter yoksa bir kez yeniden deneme, yine yoksa
  alıştırma açılır ve bilgi gösterilir (NO_FILL). iOS'ta reklam artık kapanmakta olan kilit penceresinin üstüne sunulmaya
  çalışılmıyor.
- **Canlı yayın ara-verme hatası:** her düzenleyici sahip kimliğiyle kaydolur; kapanan eski düzenleyicinin geç gelen
  "ara ver"i yeni düzenleyicinin yayınını artık durdurmaz. Fotoğraf düzenleyici, geçici düzenleme ve arka plan araları
  sahibine bağlı.
- **Yayın onayı önce, şifre sonra:** canlı yayın isteği karşı cihazda her zaman önce sorulur; eşleşme şifresi ancak
  kabulden sonra üretilip gösterilir (eşleşmiş cihazda şifre yok, onay yine sorulur). 60 sn yanıt yoksa geri çevrilir;
  1 dakikada 3 ret 5 dakika engeller; bağlanırken verilen onay teklifte yeniden sorulmaz. "Yayından önce sor" anahtarı
  kalktı, yerine bilgi satırı geldi. Bağlanan cihaz ret nedenini görür (reddedildi / yanıt yok / engellendi).
- **Canlı mürekkep (LIVE_INK, tür 15):** çizilmekte olan çizgi ~60 ms'de bir küçük paketle gider, alan tarafta
  tuvalin üstünde çizilir; kare gelince ya da ara verilince silinir. Eski sürümler bilinmeyen türü yok sayar.
- **Alan taraf:** canlı tuvalde iki parmakla yakınlaştırma/kaydırma, çift dokunuşla sıfırlama; ara 5 sn'yi geçince
  "Yayıncı başka bir ekranda" yazısı.

## K0 testleri

- Android: `V56LiveShareTest` 9/9 geçti (ara-ver sahipleri, şifre kapısı, onay kuralları, mürekkep gidiş-dönüş ve
  birleştirme). Derleme hatasız.
- iOS: `V56Tests` (aynı testler + Android JSON çözümü). CI #145: Release derleme başarılı, testler iptal (duraklarda derleme yok).

## Açık konular

- Ödüllü reklamda AdMob tarafı: uygulamanın AdMob'da "onaylı" görünmesi ve app-ads.txt kontrolü Ahmet'te.
- Seçim alanı varken çizilmekte olan çizginin önizlemesi maskelenmiyor (bırakınca maskelenir).
- Tuval küçük resimleri katman karışım modlarını henüz yansıtmıyor.
- `DocExtras` modelde var ama kullanılmıyor (simetri `GuideSpec.symmetry`'de).
- iOS'ta K2 ve K3 kodu derlenmedi (ortamda Swift yok); ilk derlemede küçük düzeltmeler gerekebilir.
- Sıvılaştır ve çarpıtılmış resim iOS'ta ağdan önbellekli bir görüntüyle çizilir; çok büyük resimde ilk çizim yavaş olabilir.
