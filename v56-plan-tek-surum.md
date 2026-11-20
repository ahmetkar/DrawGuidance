# v56 planı (tek döküman): 2B özellikler, hızlı fırça seçici, Dönüştür paneli, 3B Çizim, canlı yayın ve sayfalar arası kes-yapıştır

_2026-11-17, 2026-11-20'de 2D bölümü (Ahmet'in 2–6. maddeleri) eklendi. Son güncelleme: 3B kararları onaylandı, hedef tek oturum, K0–K7 kontrol noktaları, 2C yayın ve pano düzeltmeleri._

- Sürüm: Android ve iOS **1.13.0 (56)**.
- Taban: `claude/v55`.
- Kaynak: `claude/v56-plan-video-ozellikleri.md` (üç videonun analizi ve özelliklerin ayrıntısı).
- Görünüm taslağı: `v56-taslak.png`.
  - **Gerçek ekran görüntüsü değil**; uygulamada çalıştırılmadı, elle çizilmiş bir tasarım taslağıdır.

## 1. Kapsam

**Erişim kararı (2026-11-17):** v56'daki özelliklerin **hepsi ücretsiz**. Hiçbiri Premium kapısının
arkasında değil; `PremiumFeatures`'a v56 için yeni kilit eklenmez, kilit rozeti gösterilmez. Mevcut Premium
özellikler (v55 ve öncesi) olduğu gibi kalır.

| # | Özellik | Erişim |
|---|---|---|
| 1 | Seçimin içine boyama (seçim maskesi) | Free |
| 2 | Kement dolgu aracı | Free |
| 3 | Katman opaklığı ve karışım modları | Free (tüm modlar) |
| 4 | Alfa kilidi ve alttakine kırpma | Free |
| 5 | Seçim işlemleri: ters çevir, tümünü seç, yumuşat, genişlet/daralt | Free |
| 6 | Grenli fırçalar, resimden fırça, katmanı dokuyla doldur, hazır doku paketi | Free |
| 7 | Çizim katmanına ve seçime renk ayarları (Renk Dengesi, Ton/Doygunluk, Eğriler) | Free |
| 8 | **Dönüştür paneli**: Serbest, Tekdüze, Bozma, Çarpıtma kipleri; alt panelde Yaslama, Çevir, Döndür, Tuvale sığdır (bkz. 2A) | Free |
| 9 | Yansıma oluştur ve sıvılaştır fırçası | Free |
| 10 | Hazır ayarlar: ışık katmanı, gölge katmanı, parıltı fırçası | Free |
| 11 | Simetri kılavuzu (dikey, yatay, dörtlü, radyal) ve "Desen yap" | Free |
| 12 | **Hızlı fırça seçici** (tuvalin üst köşesinde) | Free |
| 13 | **3B Çizim modu**: 3B Şekillerim düzenleyicisinin yerine düzleme çizim, dolgu, yüzeye boya, toon görünüm, resim ve video kaydı (bkz. 2B) | Free |
| 14 | **Tuvalde 3B şekil ekleme**: yalnız kütüphane ve GLB dosyası, açı seçimi; "Düzenle" kalkar (bkz. 2B) | Free |
| 15 | **Canlı yayın düzeltmesi**: alan taraf canlı ilerlemeyi görür; canlı mürekkep ve fark paketi (bkz. 2C-1) | Free |
| 16 | **Yayın onayı**: yayın her zaman önce alana sorulur, şifre onaydan sonra (bkz. 2C-2) | Free |
| 17 | **Sayfalar arası kes, kopyala, yapıştır** (bkz. 2C-3) | Free |

Modlar:

- **Resim (gelişmiş çizim) ve Basit resim:** 1–12, 14–17.
- **Çizgi roman:** 1–11, 14–17; hızlı fırça seçici (12) yok.
- **3B Çizim** (ana ekrandaki eski 3B Şekillerim): 13. Çizim ekranı tuvalin arayüz parçalarını kullanır; 2B özelliklerden
  yalnız Dönüştür panelinin yerleşimi (Seç modunda) alınır.
- **Çocuk modu:** yalnız 11 (simetri).
- **Not alma:** 1–14 arası özelliklerin **hiçbiri** eklenmez. Yalnız 15–17 (yayın düzeltmesi, yayın onayı,
  sayfalar arası kes-yapıştır) not almaya da girer; bunlar Ahmet'in istediği düzeltmelerdir. Tek istisna zorunlu
  temizlik: modelleme düzenleyicisi kalktığı için 3B şekil penceresinden "Düzenle" ve "Yeni 3B şekil" burada da kalkar.
- Mod kuralı saf bir yardımcıda tutulur: `V56Flags.enabled(feature, kind)`; testte her mod için beklenen liste
  doğrulanır (not almada boş, hızlı fırça yalnız Resim ve Basit resimde).

## 2. Hızlı fırça seçici (yeni)

**Modlar:** yalnız **Basit resim** ve **Resim (gelişmiş çizim)**. Çizgi roman, not alma ve çocuk modunda yok.

**Amaç:** Fırça değiştirmek için üst menüdeki "Fırça seç" penceresini açmadan, tuvalin köşesinden tek dokunuşla
değiştirmek.

**Yer:**

- Tuval alanının **sol üst köşesi**, kâğıdın değil görünür tuval alanının köşesi.
- Arapçada (RTL) sağ üst köşe.
- Tablette sürüklenip dört köşeden birine bırakılabilir; konum saklanır. v45 tablet pencereleri mantığı kullanılır.

**Kapalı hali:** 44 pt'lik yuvarlak düğme. İçinde etkin fırçanın simgesi, kenarında seçili rengin halkası, altında
boyut noktası.

**Açık hali** (dokununca yatay şerit açılır):

- **Son kullanılan 6 fırça:** her biri gerçek çizgi önizlemesiyle (`BrushIcons`).
- **Sabitlenmiş favoriler:** en çok 6. Uzun basınca "Sabitle / Kaldır".
- **İki mini kaydırıcı:** boyut ve opaklık. Seçili araç ayarları; mevcut Özellikler panelinden aynı değerler.
- **"Tüm fırçalar"** düğmesi: mevcut fırça penceresini açar.
- Silgi kısayolu.
- Seçimin içine boyama etkinse şeritte küçük bir "Seçim içinde" rozeti.

**Yatay ekranda sol menüyle çakışmama:**

- Yatayda (telefon ve tablet) soldaki mod menüsü (Android `CanvasEditScreen` içindeki mod rayı `ModeSelector(vertical = true)`,
  iOS `V53EditorChrome`) bugün dikeyde ortalanıyor; hızlı fırça düğmesiyle sol üstte üst üste gelebilir.
- **Çözüm:** sol menü biraz aşağı iner. Sol üst köşede hızlı fırça için ayrılmış bir yuva olur:
  - yuva yüksekliği = düğme (44 pt/dp) + 8 boşluk + üst kenar boşluğu → yaklaşık **60 pt/dp**;
  - mod menüsü bu yuvanın **altından** başlar (ortalama kalkar, üste hizalanır, üst boşluğu yuva kadar);
  - menü kalan yüksekliğe sığmazsa kendi içinde kaydırılır (telefon yatayında kısa ekranlar için); düğmeler küçülmez.
- Açık şerit düğmenin **sağ kenarından** başlar ve tuvalin üstüne açılır. Sol menü sütunu düğmeyle aynı genişlikte
  kaldığı için şerit ne kadar uzarsa uzasın menünün üstüne binmez.
- Tablette mod menüsü sürüklenebiliyor (v45 pencereleri): bırakılan konum yuvanın içine düşerse yuvanın altına itilir.
  Hızlı fırça düğmesi başka köşeye taşınırsa sol üst yuva boşalır ve menü eski yerine (ortaya) döner.
- RTL'de hepsi aynalanır: yuva sağ üstte, menü sağda ve yuvanın altında.
- Dikey ekranda değişiklik yok (sol menü yok; düğme tuval alanının köşesinde).
- Saf yardımcı: `QuickBrushLayout.railTopInset(landscape, quickCorner, rtl)` → Android ve iOS'ta aynı kural, testli.

**Davranış:**

- Çizerken şerit kendiliğinden kapanır ve düğme %40 saydamlaşır.
- Kalem düğmenin üstünden geçerse düğme kenara kayar; çizgiyi engellemez.
- **İki parmakla dokunma:** son iki fırça arasında geçiş.
- **Kalıcılık:** son kullanılanlar ve favoriler mod başına saklanır.
  - Android: `DataStore` `brush_quick`.
  - iOS: `UserDefaults` `brushQuick.<mod>`.
- Erişilebilirlik:
  - Her öğenin fırça adıyla etiketi var.
  - VoiceOver ve TalkBack'te düğme "Hızlı fırça seçici, etkin: Kurşun kalem" diye okunur.

**Dosyalar:**

- Android: `components/V56QuickBrush.kt`, `state/QuickBrushStore.kt`.
- iOS: `Features/CanvasEdit/QuickBrushView.swift`, `State/QuickBrushStore.swift`.

**Testler:**

- Son kullanılan sırası ve en çok 6 fırça.
- Favori sabitleme.
- Mod başına ayrı liste.
- Not alma, çizgi roman ve çocuk modunda düğme hiç görünmüyor.
- RTL'de sağ köşe.
- Yatayda sol menü yuvanın altından başlıyor; düğme ile menünün sınırları kesişmiyor (telefon ve tablet, LTR ve RTL).
- Düğme başka köşeye taşınınca menü ortaya dönüyor.
- Kısa yatay ekranda menü kayıyor, düğmeler küçülmüyor.
- Çizim sırasında kapanma.
- Ekran görüntüsü testi: telefon dikey, telefon yatay, tablet yatay.

## 2A. Dönüştür paneli (Serbest, Tekdüze, Bozma, Çarpıtma) ve alt panel

**Amaç:** Seçili her şeyi tek, tutarlı bir dönüşüm aracıyla dönüştürmek. Kapsam: elemanlar, seçim içeriği, katmanın
tamamı, resim, yazı, şekil, çizgi roman paneli.

**Açılış:** Resim, Basit resim ve Çizgi romanda bu araç, bugünkü dağınık taşı / ölçekle / döndür tutamaçlarının yerini alır. Üç yoldan açılır:

- seçim çubuğunda "Dönüştür",
- eleman menüsünde "Dönüştür",
- Düzenle menüsünde "Dönüştür".

### Kipler (alt panelin üst satırı)

**Serbest (Freeform):**

- Kenar ve köşe tutamaçlarıyla en ve boy ayrı ayrı ölçeklenir.
- Üstteki döndürme tutamacı serbest döndürür.
- Sürükleyerek taşınır.

**Tekdüze (Uniform):**

- Ölçekleme oranı korunur: köşeden çekince en ve boy birlikte değişir.
- Taşıma ve döndürme Serbest'teki gibi.
- Öntanımlı kip budur.

**Bozma (Distort):**

- Dört köşe ayrı ayrı sürüklenir; perspektif dönüşümü (homografi).
- Kenar ortasından çekince o kenar paralel kayar (eğme).
- Perspektif kılavuzu açıksa köşeler kaçış çizgilerine yaslanır.

**Çarpıtma (Warp):**

- 3×3 ağ: köşe, kenar ve iç noktalar sürüklenir. Ağ çizgileri eğri olarak bükülür.
- **"Gelişmiş ağ"** seçeneği 5×5 ağ açar (ücretsiz).
- Ağın içine dokunup sürükleyince yumuşak bir bölge kayar.

### Alt panelin ikinci satırı (her kipte aynı)

- **Yaslama** (Snapping) ve ayarları:
  - **Mıknatıslar:** döndürme 15°'nin katlarına, taşıma yatay ve dikey eksene, ölçekleme tam oranlara kilitlenir.
  - **Yaslama:** tuval kenarına ve merkezine, öteki elemanların kenar ve merkezine, ızgaraya ve perspektif
    kılavuzuna yaslanır. Yaslanınca mavi kılavuz çizgileri görünür.
  - **Mesafe:** yaslama yakınlığı.
  - **Hız:** hızlı sürüklemede yaslamanın devre dışı kalma eşiği.
- **Yatay çevir** ve **Dikey çevir:** seçimin merkezine göre.
- **45° döndür:** her dokunuşta 45° (uzun basınca 90°). Açı göstergesine dokununca sayıyla açı girilir.
- **Tuvale sığdır:** seçim, oranı korunarak tuvale (ya da çizgi romanda bulunduğu panele) sığacak kadar büyür veya
  küçülür ve ortalanır.
- **Ara değerleme:** resimler için En yakın, Çift doğrusal, Çift kübik.
- **Sayısal giriş:** konum, en, boy ve açı değerlerine dokunarak yazılır.
- **Sıfırla:** dönüşümün başındaki hale döner.
- **Bitti:** dönüşüm uygulanır ve tek bir geri alma adımı olur.

### Yerleşim

- **Telefon dikey:** alt panel iki satır. Üstte dört kip düğmesi; altta yatay kaydırılan seçenekler.
- **Telefon yatay ve tablet:** alt panel tek satır. Tablette sürüklenebilir (v45 tablet pencereleri).
- **Arapça:** düğme sırası aynalanır; çevirme ve döndürme yönleri değişmez.
- **Çocuk modu:** Dönüştür paneli yok (çocuk modunda yalnız simetri var).
- **Not alma:** Dönüştür paneli yok; mevcut taşı/ölçekle/döndür tutamaçları olduğu gibi kalır.

### Model

- **Vektör çizgi, şekil ve yazı:** dönüşüm noktalara uygulanır. Bozma'da homografi, Çarpıtma'da ağ enterpolasyonu.
  Kalıcı ve düzenlenebilir.
  - Yazı ve şekiller Bozma/Çarpıtma'da yola çevrilir; kullanıcı önce uyarılır.
- **Resim:** `quad` (Bozma) ve `mesh` (Çarpıtma) alanları; asıl resim değişmez, sonradan yeniden düzenlenebilir.
- **Katman ve seçim içeriği:** içerikteki her eleman için aynı dönüşüm. Raster fırça izleri (v51 fırçaları) resim olarak
  dönüştürülür.
- **Yaslama ayarları:** kullanıcı tercihlerinde, mod başına.

### Dosyalar

- Android: `components/V56TransformPanel.kt`, `state/TransformController.kt`, `core/content/.../transform/` (homografi,
  ağ, yaslama).
- iOS: `Features/CanvasEdit/TransformPanelView.swift`, `State/TransformController.swift`,
  `Core/Geometry/Homography.swift`, `WarpMesh.swift`, `Snapping.swift`.

### Testler

- **Homografi:** dört köşe → doğru eşleme; bilinen noktalar ≤ 0,5 px hata.
- **Çarpıtma:** düz ağ kimlik dönüşümü verir; köşe noktası kayınca yalnız o bölge değişir.
- **Yaslama:**
  - 15° adımları; tuval merkezine ve öteki elemanın kenarına yaslanma.
  - Mesafe ve hız eşikleri.
- **Çevir ve döndür:**
  - Çift çevirme kimlik dönüşümü verir.
  - 8 × 45° kimlik dönüşümü verir.
- **Tuvale sığdır:** oran korunur, ortalanır. Çizgi roman panelinde panel kutusuna sığar.
- **Geri alma:** bir dönüşüm tek adım; Sıfırla ve iptal belgeyi değiştirmez.
- **Arayüz:** her kip düğmesi, alt panel seçenekleri, telefon dikey ve tablet ekran görüntüleri, RTL sırası.

## 2B. 3B Çizim modu ve 3B şekiller

_Kaynak: Ahmet'in öteki sohbette hazırladığı iki plan: "3B çizim modunu yenileme planı" ve onun yerine geçen "3B şekil
modunu 3B çizime dönüştürme planı" (ikisi de 2026-11-17). Temel ikinci plandır; birincisinden video incelemesi,
akıllı düzlem, toon görünüm, katmanlar ve başarım hedefleri alındı. Bu planlar v56–v59'a dağılmıştı; burada tek
sürüme (1.13.0) toplandı._

### Özet

- Ana ekrandaki **3B Şekillerim** düzenleyicisi tamamen bir **3B Çizim** moduna dönüşür:
  - ızgaralı düzlemlere kalemle çizim;
  - kapalı alanı düz renkle doldurma;
  - düz ve eğri yüzeye boyama;
  - toon görünüm;
  - ışık.
- **Çıktı resimdir.** 3B moddan GLB çıkmaz. Çizim ayrıca düzenlenebilir eskiz olarak saklanır.
- **Öteki modlarda** (Resim, Basit resim, Çizgi roman) "3B şekil ekle" kalır. Yalnız iki kaynağı olur:
  - uygulamanın **3B şekil kütüphanesi**;
  - **dışarıdan GLB dosyası**.
- **Tuvaldeki 3B şeklin "Düzenle"si kalkar.** Şekil, v56 Dönüştür paneliyle (2A) taşınır, ölçeklenir, döndürülür,
  çevrilir ve silinir.
- **Hepsi ücretsiz** (1. bölümdeki erişim kararı).
  - Abonelikteki `model3d.export` (GLB dışa aktarma) ve `model3d.suggest` (çizgiden cisim önerisi) özellikleri
    ortadan kalkar; yerlerine yeni ücretli anahtar konmaz.
  - Sunucudaki `Plans.java` değişmez: eski sürümler bu anahtarları sormaya devam ettiği için listede kalırlar.
  - Bu yüzden sunucu 54.0.0'da kalır.

### Video incelemesinden alınan çalışma biçimi

Videoda (iPad ve Apple Pencil, 49 sn) bir sokak köşesi çiziliyor. Kullanıcı:

- ızgaralı bir düzleme 2B çizer;
- çizgiler 3B uzayda kalır;
- kapalı alanları düz renkle doldurur;
- sahneyi döndürüp başka açıdaki bir düzleme geçer;
- yüzeylerin üstüne (tabela yazısı, berber direği şeridi) boyar;
- en sonda ışığı ayarlar ve dönen bir kamerayla sunar.

Görünüm: hafif gölgeli düz renkler, koyu mürekkep kenarlar, zeminde yumuşak gölge.

Yalnız bu **çalışma biçimini** alıyoruz. Simgeler, renkler ve yerleşim bizim tuval arayüzümüzden gelir; başka bir
uygulamanın görünümü kopyalanmaz.

### Kapsam: bugün → v56

| Alan | Bugün (v55) | v56 |
|---|---|---|
| Ana ekranda "3B Şekillerim" | Modelleme düzenleyicisi: Seç, Düzenle, Çiz, Boya, Ekle | **3B Çizim** modu; liste ekranının adı "3B Çizimlerim" olur |
| 3B moddaki GLB | İçe aktar (Ekle paneli, liste ekranı), dışa aktar ve paylaş | **Kalkar** |
| 3B moddaki hazır şekiller | Liste ekranında sekme, Ekle panelinde katalog | **Kalkar** (kütüphane yalnız tuvale eklemede) |
| 3B moddan çıktı | GLB + isteğe bağlı resim | **Resim** (Resimlerim, galeri, paylaş, tuvale ekle); isteğe bağlı **dönen video (MP4/GIF)** |
| Tuvalde "3B şekil ekle" | 3B Şekillerim + hazır şekiller + GLB + "Yeni 3B şekil" | **Kütüphane + GLB dosyası**; eklemeden önce açı seçimi |
| Tuvaldeki 3B şeklin düzenlenmesi | Kalem ya da "Düzenle" düzenleyiciyi açar | **Kalkar**; Dönüştür paneli (2A) ve "Açıyı değiştir" |
| Tuvaldeki izometrik "3B çizim" kılavuzu (v21–v22) | `GuideSpec.draw3d`, `Draw3dBar` | **Kalkar**; "3B çiz" düğmesi yeni 3B Çizim moduna götürür |

### Yeni 3B Çizim ekranı

Ekran, tuval arayüzünün parçalarını aynen kullanır. Kullanıcı 2B'de öğrendiği düğmeleri aynı yerde bulur.

| Tuvaldeki parça | 3B Çizim'deki karşılığı |
|---|---|
| Alt mod çubuğu | **Çiz · Düzlem · Seç · Ekle · Görünüm** |
| Üst araç satırı `EditorToolRow` | Geri al / yinele · fırça (kalem, mürekkep, markör, geniş boya) · silgi · **dolgu** · Elemanlar / Katmanlar / Özellikler |
| Fırça seçici, `BrushTuningControls` | Aynı bileşen; boyut ekran pikseli olarak alınır, dünya birimine çevrilir |
| `ColorPickSheet` ve hızlı renkler | Aynı bileşen; renk çizgiye, dolguya ve boyaya uygulanır |
| Sağ yan panel `EditorSidePanel` | Elemanlar = çizgiler, dolgular, cisimler · Katmanlar = 3B katmanlar (göster, kilitle) · Özellikler = seçili aracın ayarları |
| Tanıtım, `ToolHelp` | Yeni 3B Çizim adımları |
| Kaydetme, Resimlerim | Aynı akış; çıktı resim |

Bu ekranda v56'nın 2B özelliklerinden şunlar **yok**: hızlı fırça seçici (yalnız Resim ve Basit resimde),
2B katman karışım modları, kement dolgu.

**Modlar:**

1. **Çiz.**
   - **Kalem:** etkin düzleme basınçlı çizgi çizer. Basılı tutunca düz çizgiye dönüşür.
   - **Dolgu:** kapalı çizginin içine dokununca düz renkli yüzey olur. Kement gibi çizerek de alan doldurulur.
   - **Geniş boya:** dokunulan yüzeyin (düz ya da eğri) boya katmanına çizer.
   - **Silgi:** çizgi, dolgu ve boyayı siler.
2. **Düzlem.**
   - Görünüm: ızgaralı, yarı saydam, kenarı belli bir düzlem.
   - Hazır yönler: Ön, Yan, Üst, Kameraya dönük.
   - **Yapıştırma:** bir yüze dokununca düzlem o yüze oturur (`Pick3`). Bir çizginin ucuna ya da iki çizginin
     kesişimine dokununca düzlem oradan geçer.
   - Tutamaçlarla taşınır ve döndürülür; döndürme 15° adımlarla mıknatıslıdır.
   - Düzlem kameraya 75°'den fazla yatıksa uyarı rengi çıkar ve "kameraya çevir" önerilir.
   - Kalem düzlemin kenarını aşarsa çizgi düzlemin uzantısında devam eder.
3. **Seç.** Çizgi, dolgu ya da cisim seçilir; kopyalanır, silinir, gruplanır.
   - Dönüştürme v56 Dönüştür panelinin (2A) yerleşimiyle yapılır, 3B seçeneklerle:
     - **kipler:** Taşı, Döndür, Ölçekle;
     - **seçenekler:** Yaslama (15°, eksen kilidi, ızgara), Çevir, Sıfırla, Bitti.
   - Bitti tek bir geri alma adımıdır.
4. **Ekle.** Yalnız temel cisimler: küp, silindir, küre, koni, düzlem (`Primitives`). Çizime yardımcı bloklardır.
5. **Görünüm.**
   - Işık yönü topçuğu; gölge ve ortam ışığı aç/kapa (`SceneSettings.light`).
   - Görünüm tarzı: **Toon**, Yumuşak, Sadece çizgi.
   - Işık yayan malzeme (`MaterialKind.GLOW`).
   - Arka plan rengi, zemin ızgarası, hazır kamera açıları.

**Jestler:**

- Kalem çizer.
- Tek parmak sahneyi döndürür; iki parmak kaydırır ve yakınlaştırır; iki parmakla dokunma geri alır.
- Kalem ile parmak kesin ayrılır: avuç ekrana değse de çizim bozulmaz.
- Kalemsiz cihazlar için Özellikler'de "Parmakla çiz" anahtarı var. Açıkken döndürme iki parmağa geçer.

**Yerleşim:**

- **Önce tablet ve kalem:** ekran, jestler ve testler önce tablet (yatay ve dikey) için yapılır.
- **Sonra telefon, aynı sürümde:** yan panel alttan açılır, araçlar tek satıra iner; kalem yoksa "Parmakla çiz"
  öntanımlı açıktır.
- Arapçada düzen aynalanır.

### Resim (ve video) olarak kaydetme

- **Kaydet:** çizimi düzenlenebilir eskiz olarak saklar ve son görünümün resmini Resimlerim'e yazar (tuvallerdeki
  gibi).
- **Resim olarak kaydet:** bugünkü resim dışa aktarma penceresi ana kayıt yolu olur. Android'de
  `Model3DImageExport`, iOS'ta `ImageExportSheet3`.
  - Bugün sunduğu seçenekler: boyut, oran, saydam ya da renkli arka plan, gölge, kenar çizgileri, açı, PNG/JPEG,
    Resimlerim, Paylaş.
  - Yeni: cihaz galerisine kaydetme (Android MediaStore, iOS PHPhotoLibrary; iOS'ta yalnız "ekleme" izni).
  - Yeni: **"Bir tuvale resim olarak ekle"**.
  - Kullanıcının kendi 3B çizimi tuvale **yalnız resim olarak** girer.
- **Dönen video:** hazır "etrafında dön" kamera izi. Oynat çubuğunda 0.5x / 1x / 2x hız.
  - **MP4:** H.264, 1080p, 30 fps. Android'de `MediaCodec` + `MediaMuxer`, iOS'ta `AVAssetWriter`.
  - **GIF:** 480 px.
  - Elle anahtar kareli kamera (`CameraTrack`) veri modelinde var; arayüzde yalnız hazır izler ve "Görünümü ekle".

### Tuvalde "3B şekil ekle" (Resim, Basit resim, Çizgi roman)

**Ekleme penceresi** (`Model3DPickerSheet`):

| Bugün | v56 |
|---|---|
| "3B Şekillerim" sekmesi | **Kalkar** (kullanıcının 3B çizimleri resimdir, resim olarak eklenir) |
| "Hazır şekiller" (`Catalog3D`) | **Kütüphane** sekmesi; ilk sekme, kategoriler ve arama |
| GLB içe aktar ve "3B Şekillerim'e de kaydet?" sorusu | **GLB dosyası ekle**: dosya seç, ayrıntı düzeyi (`Budget3D`), tuvale ekle. Soru kalkar |
| Kalem simgesi, "Yeni 3B şekil" | **Kalkar** |
| Sabit açıyla ekleme | Önizlemede parmakla çevirip **açı seç**, sonra ekle |

**Tuvaldeki 3B şekil:**

- "Düzenle" kalkar: Android `CanvasEditScreen` `model3d_edit`, `edit3dAsk`, `open3dAsk`; iOS eylem çubuğundaki
  karşılıkları.
- Yeni **"Açıyı değiştir"** seçeneği. Model değişmez, yalnız bakış açısı ve yeniden çizim değişir; tek geri alma
  adımıdır.
- Taşıma, ölçekleme, döndürme ve çevirme v56 Dönüştür paneliyle (2A) yapılır; ayrıca kopyalama, katman sırası ve
  silme var.
  - 3B şekilde Bozma ve Çarpıtma kapalıdır: şekil yeniden çizilen bir görüntü olduğu için bu kipler anlamsız kalır.
- `Model3DController`: `insertCatalog` ve `importGlb` kalır. `insert(modelId)` ve `prepareEdit` silinir; GLB'deki
  `save` parametresi kalkar.
- Gezinme: `Routes.MODEL_EDIT` içindeki `canvasId` ve `strokeId` parametreleri ile `Model3DResults` akışı kalkar.
- Eski tuvallerde 3B Şekillerim'e bağlı şekiller olduğu gibi görünür; yalnız "Düzenle" düğmesi çıkmaz.
- **Not alma ve çocuk modu:** yeni özellik eklenmez. Ancak düzenleyiciye giden yol her yerde kalktığı için ekleme
  penceresinin sadeleşmesi bu modlarda da geçerlidir (zorunlu temizlik). Yeni özellik sayılmaz.

### Veri ve geçiş

- **Biçim:** 3B çizimler kompakt **D3 v2** (`Model3DCodec`) biçiminde saklanır. Bugün kayıtlar GLB'dir:
  `Model3DStore`, düzenleme bilgisi glTF "extras" alanında. Yeni alanlar:
  - `Stroke3`: nokta başına kalınlık (basınç), opaklık, fırça türü, bağlı düzlem ya da yüz kimliği, katman kimliği;
  - `Fill3`: düzlem çerçevesi (merkez, u, v, normal), 2B çokgenler ve delikler, malzeme; üçgenlenmiş ağ önbelleği;
  - `PaintLayer3`: bir düzleme ya da parçanın UV'sine bağlı boya dokusu. Boyutu cihaz sınıfına göre 512–2048 px;
    lowRam'da 512;
  - `Layer3`: ad, görünür, kilitli;
  - `CameraTrack`: anahtar kareler;
  - düzlem durumu.
- **Kurallar:**
  - v1 dosyalar okunmaya devam eder.
  - Android'de çizilen eskiz iOS'ta açılır; bayt biçimi aynıdır.
  - `models3d` tablosu ve eşitleme aynı kalır, yalnız içeriğin biçimi değişir.
  - Boya dokuları dosyayı büyüttüğü için eşitleme sınırı kontrol edilir; doku PNG olarak sıkıştırılır ve model
    başına üst sınır konur.
- **Eski 3B Şekillerim kayıtları silinmez.** İlk açılışta GLB'den D3 v2'ye çevrilir. Parçalar **kilitli cisim**
  olur: taşınır, boyanır, üstüne çizilir, ama ağı düzenlenmez. Asıl GLB bir sürüm boyunca yedek olarak saklanır.
- **Tuvaldeki şekiller:** `Model3DLink(modelId, dataAssetId)` korunur. Yeni eklemelerde `modelId` kütüphane kimliği
  ya da "glb" olur. Model verisi tuvalin `canvas_assets` kaydında kalır; ölçeklenince keskin yeniden çizilir.
- **Eski izometrik kılavuzla çizilmiş tuvaller** (`Space3D` izleri) bozulmaz. İzler bugünkü görünümleriyle düz 2B
  çizgi olarak kalır; yalnız kılavuz düğmesi yoktur.

### Çizici

- **Android:** `GlRenderer3` (OpenGL ES 2.0) içine yeni gölgelendiriciler:
  - toon ışık (2–3 basamak);
  - düzlem ızgarası;
  - dolgu ağları;
  - boya dokusu katmanı;
  - basınçlı çizgi şeritleri (kameraya dönük, yuvarlak uçlu);
  - mürekkep kenar geçişi (derinlik ve normal farkı, çerçeve tamponu). LOW sınıfında kapalıdır.
- **iOS:** **sıfırdan yeni Metal çizici** yazılır (karar).
  - Bugün 3B düzenleyici her kareyi yazılımla çiziyor (`Renderer3D`, `CGImage`); bu akıcı çizime yetmez.
  - Yeni dosyalar: `MetalRenderer3.swift`, `Shaders3.metal` ve bir `MTKView` sarıcısı (`Metal3DView`).
  - Kapsam: ağlar (parçalar, dolgular, temel cisimler), basınçlı çizgi şeritleri, düzlem ızgarası, toon ışık,
    gölge haritası, mürekkep kenar geçişi, boya dokuları, seçim vurgusu, ışık yayan malzeme.
  - Aynı çizici tuvaldeki 3B şeklin önizlemesinde ve "Açıyı değiştir"de de kullanılır.
  - Simülatörde Metal çalıştığı için CI'daki XCUITest ve duman testleri Metal ile koşar.
  - `Renderer3D` resim kaydı, testler ve Metal'siz durumlar için yedek olarak kalır.
- Yazılım çizicileri (Android `Renderer`, iOS `Renderer3D`) aynı toon görünümü resim kaydı ve testler için de üretir.

### Silinecek modelleme kodu

- Düzenle modu (nokta, kenar, yüz).
- Katı cisim işlemleri (`Csg`) ve `MeshTopo`.
- Parçalama ve ağ sadeleştirme paneli.
- Yüz boyama kapsamları.
- 3B fırça cisimleri (tüp, duvar, torna, şişir; `BrushShapes`).
- Çizgiden cisim önerisi (`Sketch3`; Naive Bayes ve küçük sinir ağı).
- `ModelEdit` kısmen silinir. `BrushErase` yeni çizgi, dolgu ve boya için yeniden yazılır.
- **Kalanlar:**
  - `MeshSimplifier`: GLB eklerken ayrıntı düşürür.
  - `Gltf3.import`: tuvale GLB ekleme için yeni bir sınıfa taşınır.
  - `Catalog3D`.
  - `Pick3`, ışık ve gölge.
- Yaklaşık 3–4 bin satır Kotlin ve karşılığı Swift silinir.
- Silmeden önce her sınıfın tuvaldeki GLB ve kütüphane çiziminde kullanılmadığı doğrulanır.

### Dosyalar

| İş | Android | iOS |
|---|---|---|
| Ekleme penceresi | `canvasedit/components/Model3DPickerSheet.kt`, `CatalogGrid.kt` | `Features/CanvasEdit/Model3DPickerSheet.swift`, `V53ComicWindows.swift` |
| Tuvalde düzenlemeyi kaldırma | `canvasedit/state/Model3DController.kt`, `CanvasEditScreen.kt`, `CanvasEditViewModel.kt` | `State/Model3DController.swift`, `EditorViewModel.swift` |
| Gezinme | `navigation/NavGraph.kt` (`MODEL_EDIT`), `data/library/Model3DResults.kt` | `App/DrawGuidanceApp.swift`, `EditorDependencies.swift` |
| Liste ekranı → 3B Çizimlerim | `screens/model3d/Model3DListScreen.kt` | `Features/Model3D/Model3DExtras.swift`, `Home/HomeView.swift` |
| Depo ve geçiş | `data/library/Model3DRepository.kt`, `Model3DStore.kt`, `Model3DCodec.kt` (v2) | `Data/Model3D/Model3DLibrary.swift`, `Model3DStore.swift`, `Core/Content/Model3D/Model3DCodec.swift` |
| Yeni ekran | `screens/sketch3d/` (`Sketch3DScreen`, `Sketch3DViewModel`, `PlaneGizmo`, `Sketch3DPanels`, `PlaybackBar`) | `Features/Sketch3D/` (aynı adlar) |
| Yeni çekirdek | `model3d/Fill3.kt`, `Triangulate2.kt`, `PaintLayer3.kt`, `PlaneSnap.kt`, `CameraTrack.kt` | Swift eşleri |
| Çizici | `gl/GlRenderer3.kt`, `gl/GlShaders.kt`, `Renderer.kt` | **Yeni** `Metal/MetalRenderer3.swift`, `Shaders3.metal`; `Renderer3D.swift` |
| Video | `MediaCodec`, `MediaMuxer` | `AVAssetWriter` |
| Kaldırılacak kilitler | `PremiumFeatures.kt`: `MODEL3D_EXPORT`, `MODEL3D_SUGGEST` artık sorulmaz | StoreKit özellik listesi |
| Eski kılavuz | `Draw3dBar.kt`, `Scene3D.kt`, `Iso3D.kt`, `GuideRenderer.kt` (draw3d dalı) | `Draw3dBar.swift`, `Editor/Scene3D.swift` |
| Metinler | `strings_v56.xml` (7 dil) | `Android.strings` (convert.py ile) |

### Testler

- **Çekirdek:**
  - Üçgenleme: kulak kesme, delikli çokgen, kendini kesen çizgide hata vermeme.
  - D3 v1 → v2 okuma, v2 gidiş-dönüş; Android ve iOS aynı bayt.
  - Düzleme yapıştırma: yüz, çizgi ucu, kesişim; 15° mıknatıs.
  - Boyanın UV'ye çevrilmesi, dikiş yerinde taşma.
  - Eski GLB → kilitli cisim çevrimi.
- **Arayüz:**
  - 3B Çizim'de çiz → doldur → resim olarak kaydet → Resimlerim'de görünür.
  - Tuvale kütüphaneden ve GLB'den şekil ekleme; açı seçimi.
  - Tuvalde "Düzenle" yok, "Açıyı değiştir" var.
  - Hiçbir ekrandan modelleme düzenleyicisine yol kalmamış.
  - Not alma ve çocuk modunda 3B'ye yeni özellik yok.
- **Çizici:**
  - Android `GlRendererTest` (LWJGL).
  - iOS Metal duman testi.
  - Metal ile yazılım çizicinin ekran görüntülerinin karşılaştırılması (fark eşiği).
- **Başarım:**
  - 500 çizgi ve 50 dolguda döndürme: iPad'de ve orta sınıf Android'de 60 fps.
  - LOW cihazda boya bellek kullanımı 64 MB altında.
  - 10 sn 1080p video orta sınıf cihazda 1 dakikadan kısa sürede çıkıyor.
- **Kabul senaryosu:** videodaki bina cephesi (dış hat, dolgu, tente, tabela yazısı) 10 dakikada çizilip resim olarak
  kaydedilebiliyor.

### Kararlar (Ahmet onayladı, 2026-11-17)

| Soru | Karar |
|---|---|
| Tuvaldeki eski 3B çizim modu (izometrik kılavuz, v21–v22) | **Kaldırılır.** Eski izler 2B çizgi olarak kalır; "3B çiz" düğmesi yeni 3B Çizim moduna götürür |
| Kendi 3B çizimi tuvale nasıl girer? | **Yalnız resim olarak** |
| İlk hedef cihaz | **Önce tablet ve kalem, sonra telefon; ikisi de aynı sürümde.** Arayüz önce tablet için yapılır ve test edilir, telefon düzeni aynı oturumun sonunda eklenir |
| iOS çizici | **Sıfırdan Metal çizici** (`MetalRenderer3`); yazılım çizici yalnız resim kaydı, test ve yedek |
| Ücret | Hepsi ücretsiz (yüzeye boya, yüksek çözünürlük ve video dahil) |
| Tuvaldeki şeklin açısı | Eklerken seçilir, sonra "Açıyı değiştir" ile de değişir |
| GLB dışa aktarmayı kaybeden aboneler | Sürüm notunda açıkça yazılır; mağaza açıklaması güncellenir |
| Oturum | Bütün değişiklikler **tek oturumda** yapılmaya çalışılır (bkz. 6) |

## 2C. Canlı yayın düzeltmeleri, yayın onayı ve sayfalar arası kes-yapıştır

_Ahmet'in 2026-11-17 bildirdiği sorunlar. "3B şekil ekleme penceresinde Ekle düğmesi yok" maddesi Ahmet'in isteğiyle
bu bölümün dışında. Bu pencere zaten aşama 10'da yeniden yazılıyor; bulunan neden aşağıda not olarak duruyor._

### 2C-1. Yayını alan cihaz canlı ilerlemeyi görmüyor

**Belirti:** yayını alan cihaz tuvalin ilk alınan halini görüyor, sonraki değişiklikler gelmiyor.

**Kodda bulunan neden adayları** (Android `ShareManager`, `CanvasEditRoute.LiveBroadcastBridge`; iOS
`ShareManager.swift`, `EditorViewModel`, `ShareView`):

1. **Yayın kendiliğinden "ara verildi" durumunda kalıyor. En güçlü aday.**
   - Yayın kabul edilince gönderen tarafta `ShareEvent.OpenBroadcast` aynı tuvalin düzenleyicisini yeniden açıyor.
   - Yeni düzenleyici yayını sürdürüyor (`resumeLive`).
   - Hemen ardından kapanan eski düzenleyicinin `DisposableEffect.onDispose` kodu `pauseLive` çağırıyor.
   - Son çağrı "ara ver" olduğu için `publishLive` hiçbir kareyi göndermiyor.
   - Alan tarafta yalnız `LIVE_PAUSE` geliyor ve ekran ilk halde kalıyor.
   - Aynı yarış ekran döndürülünce, pencere açılıp kapanınca ve uygulama arka plana gidip gelince de oluşabilir.
2. **Çizim sürerken hiçbir şey gitmiyor.** `publishLive` yalnız `vm.canvas` değişince, yani çizgi bitince
   çalışıyor. Kalem ekrandayken yarım çizgi gönderilmiyor.
3. **Her karede tuvalin tamamı gidiyor.** `livePackage`, tuvalin bütün sayfalarını her 250 ya da 900 ms'de baştan
   kodluyor. Çok sayfalı ya da büyük tuvalde kare gecikiyor. Alan taraf da her karede veritabanına yazıp yeniden
   okuyor (`install` → `refreshLiveCanvas`).

**Çözüm:**

- **Ara ver / sürdür sayacı:** `pauseLive` ve `resumeLive` sahip kimliğiyle (düzenleyici örneği) çalışır. Bir
  düzenleyici yalnız kendi verdiği arayı kaldırabilir; yayın, ara veren kalmayınca sürer. Eski düzenleyicinin geç
  gelen `onDispose` çağrısı yeni düzenleyicinin yayınını durduramaz. iOS'ta aynı kural `ShareManager.swift`'te.
- **`OpenBroadcast`:** gönderen zaten o tuvalin düzenleyicisindeyse düzenleyici yeniden açılmaz.
- **Canlı mürekkep (yeni ileti `LIVE_INK`):**
  - Çizilmekte olan çizginin yeni noktaları yaklaşık 60 ms'de bir küçük paketle gönderilir: çizgi kimliği,
    fırça, renk, nokta parçası.
  - Alan taraf bu noktaları veritabanına yazmadan, ekranda geçici bir katmanda çizer.
  - Çizgi bitince normal `LIVE_UPDATE` gelir ve geçici katman temizlenir.
  - Eski sürümler bu ileti türünü tanımadıkları için yok sayar (`when` dalı yok); uyumluluk bozulmaz.
- **Fark paketi:** `LIVE_UPDATE` yalnız değişen sayfayı ve değişen çizgileri taşır (`pageIndex`, eklenen,
  değişen ve silinen kimlikler). Her 20 karede bir ya da sayfa değişince tam paket gönderilir.
  - Alan taraf farkı bellekteki tuvale uygular. Veritabanına yazma 2 sn'de bir toplu yapılır.
- **Alan taraftaki ekran:** `LiveCanvasScreen` tuvali küçük resimle (`CanvasThumbnail`) değil, düzenleyicinin
  salt-okunur çiziciyle gösterir; yakınlaştırılabilir ve sayfa takip edilir.
- **Ara verildi göstergesi:** gönderen 5 sn'den uzun ara verirse alan tarafta "Yayıncı başka bir ekranda" yazısı
  çıkar.

**Testler:**

- Android:
  - Robolectric: düzenleyici yeniden açılınca yayın ara verilmiş kalmıyor.
  - Sıralı `pause(A)`, `resume(B)`, `dispose(A)` sonrasında yayın sürüyor.
  - `LIVE_INK` gidiş-dönüş.
  - Fark paketi uygulanınca tam paketle aynı tuval çıkıyor.
- iOS: aynı testlerin XCTest karşılıkları.
- İki cihaz arası: Android→iOS ve iOS→Android yayında çizilen çizgi alan tarafta 0,5 sn içinde görünüyor.
  - Bu test elle yapılır ve devir notuna yazılır.

### 2C-2. Yayın önce alana sorulmalı, doğrudan şifre vermemeli

**Bugün:**

- Yayın yapan bir cihaz seçince alan cihaz bağlantıyı kendiliğinden kabul ediyor (`onRequest` →
  `transports.accept`) ve ilk eşleşmede 6 haneli şifreyi hemen ekranda gösteriyor.
- Yayın teklifinin sorulması bir ayara bağlı (`askBeforeLive`); bu ayar kapalıysa yayın sorulmadan başlıyor.

**Yeni akış (her zaman):**

1. Yayın yapan cihaz alan cihazı seçer.
2. Alan cihazda önce **onay penceresi** çıkar: "<cihaz adı>, <tuval adı> tuvalini size canlı yayınlamak istiyor."
   Seçenekler: **Kabul et** / **Reddet**. Şifre bu aşamada gösterilmez.
3. **Kabul edilirse:**
   - İlk eşleşmede şifre ancak şimdi gösterilir, yayın yapan cihazda girilir. Daha önce eşleşmiş cihazlarda şifre
     adımı atlanır.
   - Ardından yayın başlar.
4. **Reddedilirse ya da 60 sn içinde yanıt gelmezse:** bağlantı kapanır; yayın yapan cihazda "Reddedildi" ya da
   "Yanıt yok" yazar.

**Kurallar:**

- Canlı yayın teklifi **her zaman** sorulur. `askBeforeLive` ayarı yayın için kaldırılır, onun yerine "Her zaman
  sorulur" bilgisi yazar. Tuval ve resim gönderme için `askBeforeReceive` ayarı olduğu gibi kalır.
- Onay penceresi uygulama hangi ekranda olursa olsun çıkar (`ShareHost`). Uygulama arka plandaysa bildirim
  gönderilir. Kilit ekranında kabul edilemez; uygulama açılınca sorulur.
- Aynı cihaz 1 dakikada 3'ten fazla reddedilirse 5 dakika boyunca istek gönderemez.
- **Protokol:** şifreleme (`SharePairing` v2) değişmez. Değişen, `onRequest` sonrasında `accept` çağrısının
  kullanıcı onayına bağlanması ve şifrenin onaydan sonra üretilip gösterilmesidir.
  - Eski sürümlerle uyumlu: eski sürümden bağlanan cihaz yalnız biraz daha uzun bekler.
- iOS'ta aynı akış: `ShareManager.swift`, `ShareView.swift`.

**Testler:**

- Onaysız şifre gösterilmiyor.
- Reddet, bağlantıyı kapatıyor.
- 60 sn zaman aşımı.
- Eşleşmiş cihazda şifre adımı yok ama onay var.
- `askBeforeLive=false` olsa bile yayın soruluyor.
- Android ve iOS birim testleri; şifrenin onaydan önce üretilmediği `SharePairing` düzeyinde de test edilir.

### 2C-3. Seçilen şekilleri sayfalar arasında kes, kopyala ve yapıştır

**Bugün:** seçili elemanlar için pano yok. Yalnız aynı sayfada çoğaltma (`duplicate`) ve vektör noktası kesme
(`cutSelected`) var.

**Yeni:**

- **Seçim çubuğu ve eleman menüsü:** **Kes**, **Kopyala**, **Yapıştır**.
  - Yapıştır pano doluyken her sayfada görünür.
  - Uzun basma menüsünde "Buraya yapıştır" seçeneği de var.
- **Kapsam:** çizgi, şekil, yazı, resim, tablo, diyagram, 3B şekil, çizgi roman balonu, grup ve çoklu seçim.
- **Hedef:**
  - Aynı tuvalin başka sayfası (öncelik). Sayfa şeridinden (v55 `PageStripView`) sayfa değiştirilip
    yapıştırılır.
  - Başka bir tuval. Pano uygulama içidir; tuval kapansa da uygulama açık kaldıkça durur.
- **Davranış:**
  - **Kes:** elemanlar kaynak sayfadan çıkar. Geri al, kesmeyi geri getirir.
  - **Yapıştır:**
    - Elemanlar aynı konuma konur.
    - Hedef sayfa daha küçükse ya da konum boşsa yapıştırılan elemanlar görünür alanın ortasına gelir.
    - Elemanlar seçili gelir ve Dönüştür paneli (2A) açılır.
    - Aynı yere art arda yapıştırmada 16 px kaydırılır.
  - Yapıştırılan elemanlara yeni kimlik verilir. Gruplar ve bağlantılar (diyagram okları) kendi aralarında korunur.
  - Katman: hedef sayfada etkin katmana yapıştırılır. Elemanların v56 katman özellikleri (kırpma maskesi,
    `clip`) taşınır.
  - **Resim ve doku varlıkları:** başka tuvale yapıştırınca `CanvasAssetStore`'a kopyalanır; aynı tuvalde
    paylaşılır.
- **Modlar:** Resim, Basit resim, Çizgi roman ve **Not alma**. Not almada sayfalar arası taşıma en çok gereken
  yer olduğu için bu düzeltme not almaya da girer; not almaya yeni araç ekleme yasağının (1. bölüm) bilinçli
  istisnasıdır.
  - Çocuk modunda yok.
- **Uygulama:**
  - Android: yeni `state/ClipboardController.kt` ve uygulama düzeyinde `data/clipboard/ElementClipboard.kt`.
    Saf kopyalama ve kimlik yenileme `core/content`'te (`ElementClip.kt`).
  - iOS: `State/ClipboardController.swift`, `Data/ElementClipboard.swift`.
  - Pano içeriği V11 içerik biçimiyle kodlanır.

**Testler:**

- Kes → başka sayfa → yapıştır: kaynakta yok, hedefte aynı biçimde.
- Kopyala → yapıştır: yeni kimlikler, gruplar korunuyor.
- Geri al ve yinele.
- Başka tuvale resimli yapıştırma: varlık kopyalanıyor.
- Not alma ve çizgi roman sayfalarında çalışıyor; çocuk modunda menüde yok.
- Android Robolectric ve iOS XCTest.

### Not: 3B şekil ekleme penceresindeki Ekle düğmesi (plan dışı, bilgi için)

- **Android'deki neden:** `Model3DPickerSheet`'te kaydırılan gövde `sheetBodyHeight(540.dp)` kullanıyor. Pencere
  tam ekranken (dikey ekran ya da yerleşik pencere) bu `fillMaxHeight()` olur ve alt çubuktaki Ekle düğmesini
  ekranın dışına iter.
- **Doğrusu:** gövdeye `weight(1f)` verilmesi.
- Aşama 10'da pencere yeniden yazılırken bu hata tekrarlanmaz. Testi "Ekle düğmesi her ekran yönünde görünür".

## 2D. Ahmet'in 2026-11-20 ekleri (madde 2–6)

_Ahmet 2026-11-20'de v56 planına beş madde ekledi. Hepsi ücretsiz (1. bölümdeki erişim kararı). Kapsam tablosuna 18–22
olarak girer._

| # | Özellik | Modlar | Erişim |
|---|---|---|---|
| 18 | Huawei AppGallery, Xiaomi GetApps, Samsung Galaxy Store uyumluluğu (abonelik ve giriş) | Hepsi (Android) | Free |
| 19 | E-posta ve şifreyle giriş; Google ve Apple ile giriş korunur | Hepsi (Android + iOS) | Free |
| 20 | SVG, PSD ve DXF olarak dışa aktarma | Resim, Basit resim, Çizgi roman, Not alma; **çocuk modunda yok** | Free |
| 21 | Özellikler penceresinde seçili katmana Ton/Doygunluk/Parlaklık (HSL) ve Renk Dengesi; katmanı çoğalt; birden çok katmanı birleştir | Resim, Basit resim, Çizgi roman | Free |
| 22 | Hızlı fırça seçicide renk düğmesi; renk seçici penceresinde damlalık kalemi; damlalıkta kalemin çevresinde canlı renk dairesi | Resim, Basit resim (hızlı fırça); damlalık dairesi her damlalık kullanan modda | Free |
| 23 | Alıştırmalardaki ödüllü reklam hatası | Alıştırma olan modlar | — (düzeltme) |

Not alma kuralı (1. bölüm) bu maddeler için şöyle: 20 (dışa aktarma) bir araç değil kaydetme seçeneği olduğu için not
almaya da girer; 21 ve 22 not almaya girmez.

### 2D-1. Alternatif mağazalar: Huawei AppGallery, Xiaomi GetApps, Samsung Galaxy Store (madde 18)

**Sorun:** Uygulama bugün Google Play'e bağlı: abonelik Play Billing, Google ile giriş Google Play Hizmetleri (GMS)
ister, freeRASP (`TalsecGuard`) Play dışından kurulumu "güvensiz kaynak" sayar (`supportedAlternativeStores(emptyArray())`).
Huawei cihazlarda GMS yok; Xiaomi ve Samsung cihazlarda GMS var ama bu mağazalar kendi ödeme sistemlerini ister.

**Çözüm: mağaza başına derleme türü (flavor).** Tek kod tabanı, `store` boyutu:

| Flavor | Mağaza | Abonelik | Giriş | Reklam |
|---|---|---|---|---|
| `play` | Google Play | Play Billing (bugünkü) | E-posta/şifre, Google, Apple | AdMob |
| `huawei` | AppGallery | **Huawei IAP** (HMS IAP Kit, abonelik) | E-posta/şifre, Apple, (HMS varsa) Huawei ID; GMS yoksa Google düğmesi gizlenir | AdMob GMS'siz cihazda reklam vermez; reklamsız çalışır (Huawei Ads Kit sonraki sürüm) |
| `samsung` | Galaxy Store | **Samsung IAP** (IAP SDK 6.x, abonelik) | E-posta/şifre, Google, Apple | AdMob |
| `xiaomi` | GetApps | **Play Billing** (Ahmet'in kararı, 2026-11-20); cihazda Play yoksa "Google Play gerekli" uyarısı | E-posta/şifre, Google, Apple | AdMob |

- **Ortak abonelik arayüzü:** `data/billing/StoreBilling` (ürünleri getir, satın al, geri yükle, durum). Uygulamaları:
  `PlayBilling` (bugünkü kod taşınır), `HuaweiBilling`, `SamsungBilling`, `XiaomiBilling`. Koin'de flavor'a göre seçilir.
  Premium hakkı yine sunucunun `/me` yanıtından gelir; mağaza yalnız satın alma kanıtını verir.
- **Sunucu (54.0.0 → 56.0.0, tek değişiklik paketi):**
  - `POST /api/v1/subscriptions/verify-huawei` (purchaseToken + subscriptionId; Huawei Subscription API ile doğrulanır),
    `POST /api/v1/subscriptions/verify-samsung` (purchaseId; Samsung IAP sunucu API'si ile doğrulanır),
    Xiaomi Play Billing kullandığı için bugünkü Play doğrulamasından geçer (store alanı `XIAOMI_GETAPPS`).
  - Mağaza bildirimleri: `POST /api/v1/billing/huawei-notifications` (Huawei abonelik bildirimi v2),
    Samsung için düzenli durum sorgusu (Samsung sunucuya bildirim göndermiyor).
  - `subscriptions` belgesindeki `store` alanına `HUAWEI_APPGALLERY`, `SAMSUNG_GALAXY`, `XIAOMI_GETAPPS` eklenir.
  - Gizli anahtarlar (Huawei client secret ve abonelik genel anahtarı, Samsung service account) Swarm secret olur;
    depoya ve zip'e girmez.
- **freeRASP:** `supportedAlternativeStores` flavor'a göre: `com.huawei.appmarket`, `com.xiaomi.mipicks` ve
  `com.xiaomi.market`, `com.sec.android.app.samsungapps`. İmza sertifikası değişmez (her mağazaya aynı yükleme anahtarı;
  Samsung ve Xiaomi APK'yı kendimiz imzalarız, AppGallery'de "App Signing" kapalı tutulur).
- **GMS'siz cihaz:** `GoogleApiAvailability` kontrolü; GMS yoksa Google ile giriş ve AdMob gizlenir, uygulama çökmeden açılır.
  Firebase Auth e-posta/şifre ve Apple (tarayıcı akışı) GMS'siz çalışır.
- **Uygulama içi güncelleme** (`app-update-ktx`) yalnız `play` flavor'da; ötekilerinde mağaza sayfasına bağlantı.
- **Derleme:** `bundlePlayRelease` (AAB, Play), `assembleHuaweiRelease`, `assembleSamsungRelease`, `assembleXiaomiRelease`
  (APK). Huawei için `agconnect-services.json` gerekir (gizli değil ama depoya konmaz, PC'de `yayin-anahtari\`).
- **Ahmet'ten gerekenler:** üç mağazada geliştirici hesabı ve uygulama kaydı; Huawei AppGallery Connect'te IAP açılıp
  abonelik ürünü (`premium.monthly`); Samsung Seller Portal'da IAP ürünü ve "Item ID"; Xiaomi kararı.

**Xiaomi notu:** Xiaomi'nin uygulama içi ödeme hizmeti (Mi IAP) küresel GetApps'te herkese açık değil; çoğu uygulama
GetApps'te Google Play Billing'i (Xiaomi cihazlarda GMS var) ya da kendi web ödemesini kullanıyor. Önerim: `xiaomi`
flavor'ı Play Billing'i kullanır (cihazda Play varsa), yoksa satın alma düğmesi "Google Play gerekli" uyarısı verir.
**Karar (Ahmet, 2026-11-20): Play Billing.**

**Testler:** flavor başına Koin grafiği kurulumu; `StoreBilling` sahte uygulamalarla satın alma → sunucu doğrulama →
Premium; GMS'siz Robolectric profilinde Google düğmesi yok ve uygulama açılıyor; freeRASP mağaza listesi; sunucuda
Huawei ve Samsung doğrulamalarının imzalı örnek yanıtlarla birim testleri.

### 2D-2. E-posta ve şifreyle giriş (madde 19)

- **Kayıt, giriş, şifre sıfırlama, doğrulama e-postası ve şifre saklama tamamen Firebase Auth'ta** (e-posta/şifre
  sağlayıcısı Firebase konsolunda açılır). Uygulama sunucuya bugünkü gibi Firebase ID belirteci gönderir.
- **Sunucuda hiçbir şey değişmez (Ahmet'in kararı, 2026-11-20):** sunucu belirteci bugünkü gibi doğrular, kullanıcı kaydı,
  abonelik, paylaşım ve uçlar aynı kalır; şifre sunucuya hiç gelmez. Bugünkü kod `sign_in_provider` listesinde olmayan
  `password` için `signInProvider` alanını boş bırakıyor; bu da değiştirilmez (alan yanıtta yok, işleyişi etkilemiyor).
- Hesap ekranı (Android `AccountUi`, iOS `AccountView`): **Giriş yap** / **Hesap oluştur** sekmeleri, e-posta, şifre
  (en az 8 karakter, göster/gizle), **Şifremi unuttum** (Firebase sıfırlama e-postası), kayıttan sonra doğrulama
  e-postası; doğrulanmamış hesapla giriş yapılır ama abonelik satın alma ve paylaşım için doğrulama istenir.
- Google ve Apple düğmeleri aynı ekranda kalır (GMS yoksa Google gizli). Aynı e-postayla farklı sağlayıcı: Firebase
  "hesap var" hatası yakalanır, kullanıcıya önceki sağlayıcıyla giriş yapıp hesapları bağlama (`linkWithCredential`) sunulur.
- Hesap silme (App Store ve Play şartı) e-posta hesaplarında şifre yeniden sorularak yapılır.
- Çocuk hesabı kuralları (doğum tarihi, KVKK onayı) bugünkü gibi.
- Metinler `strings_v56.xml` (7 dil).
- **Testler:** geçersiz e-posta, kısa şifre, yanlış şifre, var olan hesap, sıfırlama e-postası, doğrulanmamış hesapta satın
  alma engeli (sahte Firebase ile); iOS XCTest karşılıkları.

### 2D-3. SVG, PSD ve DXF dışa aktarma (madde 20)

Kaydet penceresine (`UnifiedSaveSheet`, iOS `SaveSheetView`) üç biçim eklenir. Çocuk modunda bu biçimler görünmez; 3B Çizim
modunda yok (çıktı resim).

- **SVG** (aşama 9'daki SVG ile aynı iş): vektör çizgiler `path`, şekiller ve yazılar vektör, resimler gömülü PNG
  (`data:`), katmanlar `<g id inkscape:label>` grupları, opaklık ve karışım `mix-blend-mode`, kırpma `clipPath`, seçim
  maskesi `mask`. Raster fırça izleri (v51 fırçaları) katman başına PNG olarak gömülür. Çok sayfalı tuvalde sayfa başına
  dosya (zip) ya da seçili sayfa.
- **PSD** (katmanlı, Photoshop/Krita/GIMP/Photopea açar): her katman ayrı raster katman (8 bit RGBA, RLE sıkıştırma),
  katman adı, görünürlük, opaklık, karışım modu anahtarı (`norm`, `mul `, `scrn`, `over`, `sLit`, `colr`, `lum `...),
  alfa kilidi, alttakine kırpma (`clipping` bayrağı); kâğıt rengi en altta "Arka plan" katmanı; birleşik önizleme resmi.
  Kendi yazıcımız (`core/content/.../export/PsdWriter`), dış kütüphane yok. En çok 8000 px kenar; düşük RAM'li cihazda katman
  katman yazılır (bellek tepe değeri tek katman).
- **DXF** (AutoCAD/LibreCAD, ASCII DXF R12/AC1009 + R2000 seçeneği): vektör çizgiler `LWPOLYLINE` (ya da `SPLINE`), daire ve
  elips `CIRCLE`/`ELLIPSE`, dikdörtgen ve çokgen kapalı polyline, yazı `TEXT`/`MTEXT`, katmanlar DXF `LAYER` tablosu (ad, renk),
  ölçü birimi mm (sayfa boyutundan), Y ekseni ters çevrilir. Resimler ve raster fırça izleri DXF'e girmez; pencerede
  "Resimler ve boya izleri DXF'e aktarılmaz" uyarısı. Diyagram okları `LINE` + ok ucu çokgeni.
- Kaydedilenler ekranında yeni dosya rozetleri (SVG, PSD, DXF) ve paylaş/aç.
- **Testler:** SVG XML şemaya uygun ve yeniden okunabilir; PSD başlık, katman sayısı, adlar, karışım anahtarları ve piksel
  içeriği (kendi okuyucumuzla gidiş-dönüş + bilinen örnek bayt dizisi); DXF grup kodları geçerli, katman tablosu, çizgi
  noktaları mm cinsinden doğru; çocuk modunda biçimler yok; Android ve iOS aynı belgeden aynı SVG ve DXF metnini üretir.

### 2D-4. HSL ve Renk Dengesi Özellikler penceresinde; katman çoğalt ve birleştir (madde 21)

- **Yer:** Özellikler penceresinde (eleman seçili değilken katman özellikleri gösterilir) yeni **"Katman renkleri"** bölümü.
  Etkin (seçili) katmana uygulanır. Plan 7. maddesindeki seçim çubuğu yolu ("Renk ayarları") da kalır; ikisi aynı denetimleri
  kullanır.
  - **Ton/Doygunluk/Parlaklık (HSL):** Ton −180…+180°, Doygunluk −100…+100, Parlaklık −100…+100; "Renklendir" seçeneği.
  - **Renk Dengesi:** Gölgeler / Ara tonlar / Parlak alanlar sekmeleri; her birinde Camgöbeği–Kırmızı, Eflatun–Yeşil,
    Sarı–Mavi kaydırıcıları (−100…+100); "Parlaklığı koru".
  - Kaydırırken canlı önizleme (yarım çözünürlük, `lowRam`'da çeyrek); **Uygula** tek geri alma adımı, **Sıfırla**.
- **Uygulama:** vektör çizgi, şekil ve yazı renkleri yeni renge çevrilir (vektör kalır, yıkıcı değil ama geri alınabilir);
  resimler ve raster izler piksel olarak işlenir (v52 fotoğraf düzenleyicisinin süzgeç kodu ortak `ColorAdjust`'a taşınır).
  Kilitli katmanda bölüm pasif.
- **Katmanı çoğalt:** katman menüsünde ve Katmanlar panelindeki satır menüsünde "Çoğalt". Yeni katman hemen üstte, adı
  "<ad> kopya", bütün elemanlara yeni kimlik, katman özellikleri (opaklık, mod, alfa kilidi, kırpma) aynen.
- **Birden çok katmanı birleştir:** Katmanlar panelinde "Seç" düğmesiyle çoklu seçim (onay kutuları), **Birleştir**:
  seçilen katmanlar, sıraları korunarak en üstteki seçili katmanın yerinde tek katman olur. Hepsi Normal mod ve opaklık 1 ise
  elemanlar vektör olarak taşınır; değilse görünüm korunsun diye modlu/opak olmayan katmanlar resme çevrilerek birleştirilir
  (kullanıcıya bir kez sorulur). "Aşağıyla birleştir" kısayolu da eklenir. Tek geri alma adımı.
- **Dosyalar:** Android `state/LayerController.kt` (duplicate, merge), `components/V56LayerColorSection.kt`,
  `core/content/.../color/ColorAdjust.kt`; iOS `State/LayerController.swift`, `Features/CanvasEdit/LayerColorSection.swift`.
- **Testler:** HSL ve renk dengesinin bilinen renklerde sonucu (Android ve iOS aynı, ≤ 1/255); çoğaltmada yeni kimlikler;
  birleştirmede sıra ve görünüm (önce/sonra PNG aynı, ≤ 2/255); geri al/yinele; not almada bölüm yok.

### 2D-5. Hızlı fırça seçicide renk düğmesi, damlalık ve canlı renk dairesi (madde 22)

- **Renk düğmesi:** hızlı fırça seçicinin (2. bölüm) açık şeridinde, "Tüm fırçalar"dan önce, seçili rengi gösteren yuvarlak
  **renk düğmesi**. Dokununca bugünkü renk seçici penceresi (`ColorPickSheet` / iOS `ColorPickerSheet`) açılır. Kapalı
  düğmenin kenar halkası zaten seçili rengi gösterir; halkaya uzun basmak da pencereyi açar. Test: düğme her zaman var,
  basınca pencere açılıyor.
- **Damlalık kalemi renk seçici penceresinde:** Çiz menüsündeki damlalık (`tool_eyedropper`) renk seçici penceresinin üst
  satırına da konur. Basınca pencere geçici olarak kapanır, damlalık etkin olur; tuvale dokunulup renk alınınca renk seçilir ve
  pencere aynı sekmeyle yeniden açılır (hızlı fırçadan açıldıysa şerit açık kalır). Çiz menüsündeki düğme yerinde kalır.
- **Canlı renk dairesi:** damlalık etkinken parmak/kalem tuvale değdiği andan bırakılana kadar dokunma noktasının çevresinde
  (parmakla kullanımda parmağın görmediği yerde, 60 dp yukarıda) **halka** gösterilir:
  - halkanın **üst yarısı** o an altındaki renk (`ColorSampler`, her harekette), **alt yarısı** önceki renk;
  - ortada küçük artı işareti ve 1 px'lik nokta; halkanın dış kenarı açık ve koyu çift çizgi (her zeminde görünür);
  - halka kenarlara yaklaşınca ekranda kalacak şekilde kayar; RTL'de değişmez;
  - bırakınca renk uygulanır ve halka 150 ms'de solar; parmak tuval dışına çıkarsa iptal.
  - Damlalık kullanan her modda (çocuk modu dahil, çocuk modunda daha büyük halka) çalışır.
- **Dosyalar:** Android `components/V56EyedropperLoupe.kt`, `DrawingCanvas.kt` (damlalık dokunuşu `down/move/up`'a bölünür),
  `ColorPickSheet.kt`; iOS `EyedropperLoupeView.swift`, `EditorCanvasView.swift`, `ColorPickerSheet.swift`.
- **Testler:** sürüklerken halka rengi altındaki renge eşit; bırakınca fırça rengi o renk; iptal; pencereden açılan damlalık
  sonrası pencerenin geri gelmesi; ekran görüntüsü testi (telefon dikey, tablet yatay).

### 2D-6. Alıştırmalardaki ödüllü reklam (madde 23)

**Bulunan nedenler:**

1. **Android:** 20 sn'lik süre sayacı ancak `MobileAds.initialize` bittikten sonra kuruluyordu. Başlatma takılırsa (yavaş ağ,
   ilk açılış) dönen simge sonsuza kadar kalıyordu.
2. **iOS:** süre sınırı abonelik sorgusunu (`SubscriptionStore.refresh`) kapsamıyordu; reklam, kapanmakta olan kilit penceresinin
   (alert) üstüne sunulmaya çalışılıyordu ve sunum başarısız oluyordu. Hata gelince pencere yeniden açılmadığı için "yüklenemedi"
   yazısı da görünmüyordu.
3. **İki platform:** reklam ancak düğmeye basınca yüklenmeye başlıyordu; yeni reklam biriminde ya da düşük doluluk oranında
   ilk istek çoğunlukla "envanter yok" (kod 3 / iOS 1) dönüyor ve kullanıcı hep "yüklenemedi" görüyordu.

**Düzeltme (uygulandı, aşama 0A):**

- Tek sonuç ve 25 sn'lik toplam süre sınırı (başlatma, onay ve abonelik sorgusu dahil).
- Alıştırma listesi ya da kilit penceresi açılınca ödüllü reklam **önceden yüklenir**.
- "Envanter yok" ya da iç hatada 1,5 sn sonra bir kez yeniden denenir.
- Yine reklam yoksa ya da süre dolarsa (`NO_FILL`) alıştırma **yine açılır** ve "Şu an gösterilecek reklam bulunamadı.
  Alıştırma senin için açıldı." bilgisi gösterilir. Yalnız ağ hatasında "bağlantını kontrol et" uyarısı kalır.
- iOS'ta reklam, kilit penceresinin kapanması bittikten sonra sunulur; hata olursa pencere hata yazısıyla yeniden açılır.
- **Ahmet'in AdMob'da kontrol etmesi gerekenler:** uygulamanın AdMob'da mağaza kaydına bağlı ve "Onaylandı" durumunda olması;
  `app-ads.txt` (drawguidance.ahmetkar.com/app-ads.txt) doğrulanmış olması; ödüllü birimlerin (Android `/7207499530`, iOS
  `/2072778264`) etkin olması. Bunlar eksikse gerçek reklam gelmez, ama artık alıştırmalar kilitli kalmaz.

### 2D iş planına eklenenler

| Aşama | İş | Bağımlılık |
|---|---|---|
| 0A | Ödüllü reklam düzeltmesi (2D-6) | — (yapıldı) |
| 4 | Hızlı fırça seçici **+ renk düğmesi, renk penceresinde damlalık, canlı renk dairesi** (2D-5) | — |
| 7 | Renk ayarları **+ Özellikler penceresinde HSL ve Renk Dengesi, katman çoğalt ve çoklu birleştir** (2D-4) | 2, 3 |
| 9 | Dışa aktarma **+ PSD ve DXF** (2D-3); SVG zaten bu aşamada | 2–7 |
| 9A | **E-posta/şifre girişi** (2D-2, Android + iOS) | — (paralel) |
| 9B | **Mağaza flavor'ları, Huawei ve Samsung abonelik, freeRASP mağaza listesi, sunucu 56.0.0** (2D-1) | 9A |

Kontrol noktalarına etkisi: K0'a aşama 0A eklenir; K3 (2B tamam) aşama 9A ve 9B'yi de kapsar. Sunucu değiştiği için 9. bölümdeki
"Sunucu değişmez (54.0.0)" maddesi "Sunucu 56.0.0: Huawei ve Samsung abonelik doğrulaması" olur. Tahmini büyüklük
yaklaşık 6–8 bin satır artar (en büyük kalemler: PSD/DXF yazıcıları, mağaza ödeme katmanları, sunucu doğrulamaları).

## 3. Veri modeli (tek değişiklik paketi)

`ContentCodec` **V11** (V10'dan). Eski belgeler değişmeden açılır; yeni alanların öntanımlıları eski davranışı verir.

| Model | Yeni alanlar |
|---|---|
| `Layer` | `opacity` (1), `blend` (`NORMAL`), `alphaLock` (false), `clipToBelow` (false) |
| `Stroke` | `clip: ClipMask?`: çokgen noktaları, `feather` px, `inverted`; seçimin içine boyama için |
| `Stroke` (yeni tür) | `BrushType.LASSO_FILL`: kapalı yol, yalnız dolgu, isteğe bağlı doku |
| `BrushSettings` | `grain: GrainSpec?`: doku kimliği, ölçek, güç, kayar/sabit. `imageBrushAssetId` |
| Resim elemanı | `quad: [4 nokta]?` (bozma), `mesh: [16 nokta]?` (çarpıtma), `wave: WaveSpec?` (yansıma bozulması) |
| Belge | `symmetry: SymmetrySpec?` (tür, kol sayısı, eksen) |

**Kurallar:**

- Room şeması değişmez; hepsi belge içeriğinde.
- `CatalogInfo` değişmez.
- Yeni varlık türü "fırça resmi": `CanvasAssetStore`. v55 resim güvenliğinden geçer ve 512 px'e küçültülür.
- **Paylaşım ve canlı yayın:**
  - Protokole `codecVersion` eklenir.
  - Karşı taraf V11 bilmiyorsa belge gönderilmeden önce düzleştirilir (katmanlar resme çevrilir) ve kullanıcıya
    bilgi verilir.
- Sunucu etkilenmez; çizimler sunucuya gitmez.

## 4. Çizim motoru

**Katman birleştirme:**

- Opaklığı 1 ve modu Normal olmayan, ya da alfa kilitli veya kırpmalı katmanlar ekran dışı tampona çizilir.
  - Android: `saveLayer` + `Paint.blendMode`.
  - iOS: `CGContext` transparency layer + `setBlendMode`.
- Öteki katmanlar bugünkü gibi doğrudan çizilir; performans değişmez.
- Kaplama, Yumuşak Işık, Renk ve Parlaklık her iki platformda yerel karışım modları. Android'de API 29 öncesi için
  `PorterDuff` yedeği; olmayan modlar için çalışma zamanı uyarısı yerine en yakın mod.

**Diğer:**

- **Seçim maskesi:** sert seçimde `clipPath`. Yumuşatılmış seçimde maske tamponu: bulanıklaştırılmış alfa + `DstIn`.
- **Alfa kilidi:** `SrcAtop`. **Alttakine kırpma:** katman tamponu, alttaki katmanın alfa'sıyla `DstIn`.
- **Gren:** fırça damgaları doku ile çarpılır. "Kayar" seçenekte doku tuvale sabitlenir.
- **Bozma ve çarpıtma:**
  - Vektör çizgilerin noktalarına homografi ya da ağ enterpolasyonu uygulanır; sonradan değiştirilebilir.
  - Resimlerde Android `drawBitmapMesh`, iOS Core Image (`CIPerspectiveTransform` / ağ için Metal'siz üçgen çizimi).
- **Yansıma:** çoğalt, dikey çevir, alfa gradyanı, dalga (sinüs kaydırma) ya da dalgacık. Ayarlanabilir, kalıcı
  parametre (`wave`).
- **Sıvılaştır:** raster katmanda (resme çevrilmiş) ağ deformasyonu. İt, döndür, büzüştür, şişir. Geri alınabilir.
- **Simetri:** canlı çizgi n kopyaya aynalanır; kaydedilen çizgiler ayrı elemanlar olur.
- **Renk ayarları:** katman ya da seçim resme çevrilir (yıkıcı, geri alınabilir). v52 fotoğraf düzenleyicisinin
  süzgeçleri kullanılır.

**Bellek (v55 `lowRam`):**

- Düşük RAM'li cihazlarda en çok 4 tamponlu katman.
- Sıvılaştır ve çarpıtma önizlemesi yarım çözünürlükte.

## 5. Arayüz yerleşimi

- **Katmanlar paneli:** her satırda küçük resim, ad, mod seçici, opaklık çubuğu, alfa kilidi simgesi, "alttakine kırp"
  simgesi. Kırpılmış katmanlar girintili ve ok işaretli.
- **Seçim çubuğu** (seçim varken tuvalin altında yüzer):
  - İçine boya, Ters çevir, Yumuşat (kaydırıcı), Genişlet/Daralt, Doldur, Dokuyla doldur.
  - Dönüştür (alt panelde Serbest / Tekdüze / Bozma / Çarpıtma ve seçenekler; bkz. 2A), Yansıma, Desen yap, Renk
    ayarları, Seçimi kaldır.
- **Çiz menüsü:**
  - "Kement dolgu" ve "Sıvılaştır" araçları.
  - Fırça Özellikleri'ne "Gren" bölümü.
  - "Resimden fırça oluştur".
- **Ekle menüsü:** "Işık katmanı", "Gölge katmanı" (hazır ayarlar). "Doku" (hazır paket).
- **Kılavuzlar:** "Simetri" (dikey, yatay, dörtlü, radyal n).
- **Hızlı fırça seçici:** bkz. 2.
- **Eğitim modu:**
  - v56 yenilik grubu: seçimle boyama, katman modları, kement dolgu, hızlı fırça seçici.
  - `TutorialScripts.VERSION = 56`.
  - Yenilik grubu not alma ve çocuk modunda gösterilmez; hızlı fırça adımı yalnız Resim ve Basit resimde.
- **Metinler:** `strings_v56.xml` (7 dil) ve iOS `Android.strings` (convert.py ile).

## 6. İş planı (tek sürüm, aşamalı geliştirme)

Bütün özellikler aynı dalda (`claude/v56`) geliştirilir ve tek sürümde yayımlanır. **Hedef: tek oturum.**

**Tek oturum düzeni:**

- Her aşama ayrı commit ve push. Ayrıca K0–K7 kontrol noktalarında durulur, zip ve devir notu verilir (aşağıda).
  Oturum kesilirse sonraki oturum son devir notundan devam eder.
- Android önce yazılır ve yerelde test edilir (Robolectric, birim testleri, LWJGL). iOS karşılığı hemen arkasından
  yazılır.
- iOS CI denemeleri birikmiş değişikliklerle seyrek koşturulur, çünkü her deneme 30–90 dk sürüyor. Koşturma
  noktaları:
  - yayın düzeltmeleri bittiğinde (aşama 0);
  - 2B motor bittiğinde (aşama 3);
  - Metal çizicinin ilk hali çalıştığında (aşama 11);
  - 3B tamamlandığında (aşama 13);
  - yayın derlemesi (aşama 15).
- CI beklerken bir sonraki aşamanın Android kısmı yazılır.
- Tablet önce: 3B aşamalarında (11–13) arayüz ve ekran görüntüsü testleri önce tablet için yapılır; telefon düzeni
  aşama 13'ün sonunda eklenir.
- Oturum sonunda ulaşılamayan iş kalırsa `V56Flags` ile kapatılır, `v56-durum.md`'ye yazılır ve kalan iş açıkça
  bildirilir; yarım özellik yayına girmez.

### Kontrol noktaları (checkpoint)

Çalışma Opus 5.5 (high) ile, haftalık kullanım sınırı sıfırlandıktan sonra başlar. Sınıra oturumun ortasında
takılıp iş yarıda kalmasın diye belirli noktalarda **durulur**. Her durakta teslim edilen dosyaların hepsi kendi
başına devam etmeye yeter.

Claude kalan kullanım sınırını göremez. Bu yüzden duraklar sık tutulur ve her biri iş kaybı olmadan bırakılabilecek
bir yerdir.

| Durak | Ne zaman | Durumu |
|---|---|---|
| **K0** | Aşama 0 bitti: canlı yayın ara-verme hatası, canlı mürekkep, yayın onayı (Android + iOS) | Bildirilen yayın hataları düzeldi |
| **K1** | Aşama 1–3B bitti: V11 veri modeli, katman motoru, seçim maskesi, **sayfalar arası kes-yapıştır**, yayın fark paketi (Android + iOS) | 2B temeli |
| **K2** | Aşama 4–6 bitti: hızlı fırça seçici, gren ve doku, Dönüştür paneli, yansıma, sıvılaştır | 2B araçlarının çoğu |
| **K3** | Aşama 7–9 bitti: renk ayarları, simetri, dışa aktarma; **2B tamam**, iOS CI yeşil | 2B özellikleri tamam |
| **K4** | Aşama 10 bitti: 3B temizlik ve tuvalde yeni ekleme akışı | Eski 3B düzenleyiciye giden yollar kapalı |
| **K5** | Aşama 11 bitti: 3B Çizim temeli ve **Metal çizicinin ilk hali**, iOS CI yeşil | 3B çizim çalışıyor |
| **K6** | Aşama 12–13 bitti: dolgu, toon, yüzeye boya, video; telefon düzeni | 3B tamam |
| **K7** | Aşama 14–15 bitti: eğitim, metinler, tam test, imzalı AAB/APK ve IPA | Yayına hazır |

**Her durakta yapılanlar:**

1. **Testler:** Android birim ve Robolectric testleri çalıştırılır. K0, K3, K5 ve K7'de iOS CI de çalıştırılır.
   Sonuçlar not edilir; kırmızı test varsa nedeni yazılır.
2. **Kayıt:** commit edilir ve `claude/v56`'ya push edilir.
3. **Zip, iki tane:**
   - `DrawGuidance-v56-K<n>-degisen.zip`: önceki duraktan bu yana değişen ve eklenen dosyalar, repo yapısıyla.
     İçinde `SILINEN.txt` bulunur: silinen dosyaların listesi. Silme işlemi PC'de taşıma olarak yapılır.
   - `DrawGuidance-v56-K<n>-toplam.zip`: `claude/v55`'ten bu yana değişen bütün dosyalar. Önceki zip'ler
     kaybolsa bile tek başına yeter.
   - Gizli dosyalar (`yayin-anahtari\`, imza anahtarları, `.env`) zip'e girmez.
4. **Devir notu:** `v56-devir-notu.md` güncellenir. İçeriği:
   - biten aşamalar;
   - sıradaki adım (dosya ve fonksiyon düzeyinde);
   - açık hatalar;
   - son CI çalışmasının numarası ve sonucu;
   - `V56Flags`'te açık ve kapalı olanlar;
   - yeni oturum için başlangıç komutları;
   - verilecek ilk mesaj.
   Not projeye `claude/v56-devir-notu.md` olarak yazılır. `v56-durum.md` de güncellenir.
5. **Teslim:** iki zip ve devir notu sohbette gönderilir. PC'ye yazma yalnız siz söylerseniz yapılır;
   `_eski_v55\` yedeğiyle, eski kural geçerlidir.
6. **Durma:** Claude durur ve "devam" demenizi bekler. Kullanım sınırı yaklaşmış görünüyorsa, yani oturum çok
   uzamışsa ya da yanıtlar yavaşlıyorsa, bir sonraki durağı beklemeden ara bir durak (K<n>a) verilir.

**Yeni oturumda devam:**

- Yeni sohbete devir notundaki ilk mesaj yapıştırılır.
- Claude dalı (`claude/v56`) GitHub'dan çeker. Push yapılamamışsa toplam zip'ten dalı yeniden kurar.
- Notu ve bu planı okuyup sıradaki aşamadan devam eder.

İç sıra, bağımlılığa göre:

| Aşama | İş | Bağımlılık |
|---|---|---|
| 0 | **Canlı yayın düzeltmesi ve yayın onayı** (2C-1, 2C-2): ara ver / sürdür sayacı, `LIVE_INK`, önce onay sonra şifre | — (ilk iş, bağımsız) |
| 1 | V11 veri modeli, codec, paylaşım sürüm kontrolü, düzleştirme | — |
| 2 | Katman birleştirme motoru: opaklık, modlar, alfa kilidi, kırpma (Android + iOS) | 1 |
| 3 | Seçim maskesi, seçim işlemleri, kement dolgu, seçim çubuğu | 1 |
| 3A | **Sayfalar arası kes, kopyala, yapıştır** (2C-3) | 1, 3 |
| 3B | Canlı yayının fark paketi (2C-1), V11 içerikle | 0, 1 |
| 4 | Hızlı fırça seçici | — (paralel) |
| 5 | Gren ve resimden fırça, doku dolgusu, hazır doku paketi | 2, 3 |
| 6 | Dönüştür paneli (Serbest, Tekdüze, Bozma, Çarpıtma, Yaslama, Çevir, Döndür, Tuvale sığdır), yansıma, sıvılaştır | 3 |
| 7 | Renk ayarları (katman/seçim), ışık ve gölge hazır ayarları | 2, 3 |
| 8 | Simetri ve desen yap | 1 |
| 9 | Dışa aktarma: PNG, JPEG, PDF (CMYK raster yedek), SVG (`mix-blend-mode`, `clipPath`, `mask`), CBZ | 2–7 |
| 10 | 3B temizlik ve tuvalde yeni ekleme akışı: Düzenle ve Yeni 3B şekil kalkar, Kütüphane + GLB, açı seçimi, Açıyı değiştir, 3B Çizimlerim listesi, GLB dışa aktarma kalkar | 6 (Dönüştür paneli) |
| 11 | 3B Çizim temeli: tuval bileşenleriyle ekran, görünür düzlem ve yapıştırma, basınçlı çizgi, katmanlar, D3 v2, eski kayıtların çevrimi, modelleme kodunun silinmesi; **iOS Metal çizici** | 10 |
| 12 | Dolgu ve üçgenleme, toon ve mürekkep kenarları, Görünüm modu, temel cisimler, resim olarak kaydet / galeri / tuvale ekle | 11 |
| 13 | Yüzeye boya (düz ve eğri, UV), boya silgisi, dönen video (MP4/GIF) | 12 |
| 14 | Eğitim, metinler (7 dil); 3B Çizim tanıtım adımları dahil | hepsi |
| 15 | Tam test takımı (Android + iOS CI), yayın derlemeleri | hepsi |

**Tahmini büyüklük:**

- 2B özellikler (kapsam tablosunda 1–12; aşama 1–9): yaklaşık 9–12 bin satır yeni kod; Android ve iOS yarı yarıya.
- 3B (kapsam tablosunda 13–14; aşama 10–13): yaklaşık 12–16 bin satır yeni kod, 3–4 bin satır silinir. En büyük
  kalemler: iOS Metal çizici, dolgu ve üçgenleme, yüzeye boya.
- Toplam yaklaşık 21–28 bin satır yeni kod.
- **Hedef tek oturum (karar).** Bu, bugüne kadarki en büyük tek oturum olur. Gerçekçi değerlendirme: tek oturumda
  tamamlanması mümkün ama garanti değil. Darboğaz iOS CI süresi ve Metal çizicinin ilk hatalarıdır.
- Kesilirse kontrol noktası düzeni geçerli: son duraktaki zip'ler ve devir notuyla yeni oturumda kaldığı yerden
  devam edilir. En kötü durumda bir duraklık iş kaybolur.
- Öteki plan aynı 3B işini tek geliştiriciyle Android'de 6, iOS'ta 5,5 hafta olarak tahmin ediyordu.

## 7. Testler

- **Birim:**
  - Codec V10↔V11 gidiş-dönüş.
  - Eski belge açılışı.
  - Düzleştirme.
  - Her karışım modunun bilinen renklerle piksel sonucu.
  - Alfa kilidi, kırpma.
  - Yumuşatılmış seçim alfa'sı.
  - Homografi ve ağ enterpolasyonu.
  - Simetri kopyaları.
  - Gren dokusunun yerleşimi.
  - Hızlı fırça seçici deposu.
- **Arayüz (Robolectric ve XCUITest):**
  - Kementle seç → İçine boya → seçim dışı boş.
  - Katman modunu değiştir → önizleme ve dışa aktarma aynı.
  - Kement dolgu.
  - Dönüştür paneli: dört kip ve alt panel seçenekleri (2A).
  - Yansıma oluştur.
  - Hızlı fırça seçiciden fırça değiştir.
  - Not alma tuvalinde v56 öğelerinin hiçbiri görünmüyor (menüler, katman paneli, seçim çubuğu).
- **Platformlar arası:** Aynı V11 belgesi Android ve iOS'ta aynı PNG'yi üretmeli (renk farkı ≤ 2/255).
- **Performans:**
  - 20 katmanlı 3000 px belge, düşük RAM simülasyonu.
  - Kare süresi < 16 ms (Normal katmanlar), < 33 ms (modlu katmanlar).

## 8. Riskler

| Risk | Önlem |
|---|---|
| Tek sürümde büyük kapsam: hata olasılığı yüksek | Özellik bayrakları (`V56Flags`); sorunlu bir özellik yayından önce gizlenebilir |
| Eski sürümle paylaşım | Sürüm kontrolü + düzleştirme + kullanıcı uyarısı |
| Performans (düşük RAM, iOS 16 cihazları) | Tampon sınırı, yarım çözünürlüklü önizleme, `lowRam` dalı |
| SVG/PDF'te mod farkları | SVG `mix-blend-mode`; PDF'te desteklenmeyen modlar için raster yedek |
| iOS derleme döngüsü yavaş (CI) | Önce Android, sonra iOS; iOS'ta derleme hatalarını toplu düzeltme |
| 3B modelleme araçlarının kalkması | Eski modeller kilitli cisim olarak açılır, resim olarak kaydedilir; sürüm notunda açıkça yazılır |
| Sıfırdan Metal çizici | 11. aşamanın başında; önce düz ağ + çizgi + ızgara, sonra toon, gölge ve kenar. Yazılım çiziciyle ekran görüntüsü karşılaştırması. Metal hata verirse `Renderer3D` yedeğine düşer |
| Tek oturumda bitmeme ya da kullanım sınırına takılma | K0–K7 kontrol noktaları: her durakta commit, push, iki zip (değişen ve toplam) ve devir notu; gerekirse ara durak |
| Eski Android (OpenGL ES 2.0) | LOW sınıfında mürekkep kenar geçişi kapalı, boya dokusu 512 px |
| Boya dokularının belleği ve eşitleme boyutu | Doku havuzu, PNG sıkıştırma, model başına sınır, `lowRam` dalı |
| Telefonda kalemsiz 3B çizim | "Parmakla çiz", düz çizgi düzleştirme, büyük yakınlaştırma |
| Benzer uygulamalara fazla benzeme | Kendi tuval arayüzümüz, simgelerimiz ve renklerimiz; yalnız çalışma biçimi benzer |
| Yayın değişikliğinin eski sürümlerle uyumu | `LIVE_INK` eski sürümde yok sayılır; fark paketini bilmeyen karşı tarafa (`HELLO.app` < 1.13.0) tam paket gönderilir; onay akışı protokolü değiştirmez |
| Yanlışlıkla kilit | Test: ücretsiz kullanıcıda v56 özelliklerinin hiçbiri kilitli ya da rozetli değil |

## 9. Teslim

- Android: imzalı AAB (Play) ve APK.
- iOS: TestFlight ve imzalı IPA (Xcode 26).
- Her kontrol noktasında değişen ve toplam zip ile `v56-devir-notu.md` (bkz. 6).
- Proje klasörüne değişen dosyalar (yalnız siz söyleyince, `_eski_v55\` yedeğiyle), `v56-durum.md` ve son devir notu.
- Sunucu 56.0.0: Huawei ve Samsung abonelik doğrulaması (2D-1). `Plans.java`'daki `model3d.export` ve `model3d.suggest` eski sürümler için kalır.
- Mağaza açıklamaları ve ekran görüntüleri: 3B Çizim modu ve GLB dışa aktarmanın kalkması.
- K0'da yayın düzeltmeleri ayrı bir küçük durak olduğu için istenirse yalnız onlarla ara sürüm (1.12.2) de
  çıkarılabilir. Öneridir, karar sizde.
