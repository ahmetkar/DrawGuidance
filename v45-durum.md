# DrawGuidance v45 — durum

_2026-10-31 · versionCode **45** (versionName 1.3.0) · Room **v23** (değişmedi) · minSdk 23 – targetSdk 36 · sunucu değişmedi (44.0.0) · 7 dil_

Ahmet'in 30 Ekim v45 isteğinin bütün maddeleri (1–14.1) yapıldı. Değişen dosyalar `git reset --soft e7ce7d1` ile
hazırlık alanında (staged); commit yok. Sunucu (`backend/`) ve web (`web/`) bu sürümde değişmedi.

Teslim: imzalı `DrawGuidance-v45.apk` ve `DrawGuidance-v45.aab` (yükleme anahtarıyla imzalı, apksigner ile doğrulandı).

---

## 1. Çocuk modu (madde 1, 1.1, 1.2)

- **Boya fırçası** (`Boya`): parmağın geçtiği yer boyanır. Fırça bir şeklin ya da kalemle çizilmiş kapalı bir şeklin
  içinde başlarsa boya o alanın dışına taşmaz (kovanın bulduğu alanın maskesiyle kırpılır); boş kâğıtta keçeli kalem
  gibi serbest boyar. Her darbe tek geri alma adımı.
  - Kod: `editor/KidsBrush.kt`, `state/KidsBrushController.kt`, `components/KidsBrushTool.kt`,
    `core/content/.../fill/RegionClip.kt`.
  - Boya şeklin içinde `VectorEdit.BRUSH_PAINT` etiketli dolgu parçaları olarak saklanır (şekille taşınır, görevde
    boyanmış alan sayılır). Kalemle çizilmiş kapalı şekilde ayrı dolgu şekli (`kbrush-` önekli).
  - Fırça boyu: İnce / Orta / Kalın.
- **Yeni çocuk menüsü** (`components/KidsToolbar.kt`): daha büyük, renkli karolar, çocuk teması; geri al ve ileri al
  solda sabit (kaydırılınca kaybolmaz), kalan araçlar kayan satırda, dörderli gruplar ve ayırıcılar.
- **Adlar** (`strings_v45_kids.xml`, 7 dil): Kalem, Silgi, Boya, Kova, Parçalar, Renkler, Şekiller, Görev, Zemin, Resim,
  Yazı, 3B, Seç, Renk al, Cetvel, Kalıp, Büyüt. Açıklamalar ve görev ipuçları da çocuk diliyle yeniden yazıldı.

## 2. Çizgi roman kapağı (madde 2)

- Ana sayfadan yeni çizgi roman: **kapak + bir boş sayfa**, boş sayfada açılır.
- Kapak şablonu: tam taşan panel (resim alanı), üstte büyük başlık (Bangers, büyük harf, beyaz kontur; tuval adı ya da
  "Başlık"), altta isteğe bağlı "Yazar adı" (Comic Relief Bold). Üçü de seçilir, taşınır, düzenlenir, silinir.
- Kapakta sayfa numarası, üst bilgi ve alt bilgi yok: düzenleyicide, vektör/resim PDF'de, resim dışa aktarmada, CBZ'de.
  Numara kapaktan sonraki sayfada 1'den başlar. Sayfa şeridinde kapak "Kapak", sonrakiler 1, 2, …
- Eski çizgi romanlar aynı numaralarla açılır. Sayfa çipine uzun basınca **"Kapak ekle"** (geri alınabilir).
- Saklama: `PageBackground.cover` (sayfa arka planı JSON'unda; false iken yazılmaz). Room göçü yok.

## 3. Gelişmiş modlarda düğme adları (madde 3)

- ART, NOTES, COMIC'te dikeyde de (telefon ve tablet) üst menünün her düğmesinde ad, simgenin yanında düğmenin içinde
  (`V45InlineLabels.kt`). Basit modlar ve yatay görünüm v38'deki gibi.
- Düğmeye dokununca tuvalin köşesine yazı çıkmaz. Basılı tutunca açıklama yine görünür.

## 4. Basit not (madde 4)

- SIMPLE_NOTES, NOTES'un şekil seçici kategorilerinin tamamını kullanır (aynı kimlikler; şekiller, grafikler, eğriler,
  bilim şekilleri, diyagram, formül, 3B). Düzenleme NOTES'la aynı yoldan: Seç → Şekli düzenle.

## 5–9. Bilim: formüller, grafikler, kimya, fizik

- **Grafiklerde formül (5):** Düzenle'de grafiğin üstünde "Formül" çipi. Fonksiyon/eksen/koordinat düzleminde en çok
  4 eğri (`y = x²; y = 2x + 1`), 3B eksende `z = f(x, y)`, sayı doğrusunda çözüm kümesi (`−1 ≤ x < 3`), çizelgelerde
  f(1), f(2)… örnekleri. Geçersiz formül uygulanmaz, kırmızı hata konumu söyler. "Derece" çipi (`GraphSpec.angleDeg`).
  Ortak ayrıştırıcı: `core/content/.../math/MathExpr.kt`.
- **Formülle şekil düzenleme (6):** parametreli şekillerde Düzenle → **"Değerler"** aracı (PARAMS): formül satırı
  (`v = 30; θ = 45°`, türetilmiş değer `R = 50` geri çözülür, eğri `y = 2x² − 3` uydurulur), adım düğmeleri, şekil
  üstünde sürüklenen tutamaçlar, **"Vektör düzenle"** düğmesi (NODES'a geçer). Formül sol üst köşede, her değişiklikte
  yeniden yazılır. Değerler `ShapeSpec.params` (boşken yazılmaz; eski kayıtlar öntanımla açılır).
  Şekiller: üçgen, çember (`x² + y² = 9` da okunur), düzgün çokgen, parabol, lojistik nüfus büyümesi.
- **Sayılarla hesap (7):** formül elemanında bilinmeyenler sayı olunca sonuç kesin kesir olarak eklenir
  (`6/4 = 3/2`); yanlış bağıntı doğrusuyla değiştirilir (`3² < 10`); sıfıra bölmede "= tanımsız" ve kırmızı uyarı.
- **Kimya (8):** ad, Hill formülü, yarı açık formül (`CH3CH(NH2)COOH`) ya da SMILES. Yaklaşık 95 bilinen molekül;
  değerlik denetimi ve açık hata iletileri; tabloda olmayan formüle **"örnek yapı"** etiketli bir izomer. Görünümler:
  açık, yarı açık, iskelet, 3B top-çubuk (VSEPR). Organik hazırlar: propan, eten, etanol, asetik asit, fenol, toluen,
  glisin, alanin, glikoz.
- **Fizik (9):** eğik atış, enine dalga, yay-kütle, sarkaç, ince ve kalın kenarlı mercek, eğik düzlem, Ohm grafiği;
  her birinde formül etiketi, türetilmiş değerler, tutamaç. Eski kimlikler korundu.

## 10. Not alma: Panel ekle (madde 10)

- "Panel ekle" kendine özgü simgeyle (`V45Icons.NotesPanels`). NOTES'ta Ekle satırında Diyagram'dan sonra,
  SIMPLE_NOTES'ta üst menüde Formül'den sonra.
- 13 yerleşim (sayfa oranında önizleme): 10 dikdörtgen (2/3 sütun, 2/3 satır, 2×2, 2×3, ana+yan, yan+ana, Cornell,
  başlık+2 sütun) ve 3 kare (2×2 kare, 3×3 kare, kare+not alanı). Her alan ayrı `notes.panel` şekli.

## 11–12. Üst menü düzeni

- **ART'ta sıra:** Çiz, Ekle, Seç, Düzenle, Taşı, Genişlet (ART Ekle Çiz'in yanında; Seç ile yer değişti). NOTES ve
  COMIC'te Seç Ekle'den önce kaldı.
- **Dörderli gruplar:** her üst menü 4'lü gruplara ayrılır, aralarında boşluk ve dikey ayırıcı (yatay yan rayda yatay).
  Seçilen düğme tam görünmüyorsa satır onu en az kaydırmayla görünür yapar (seçim başına bir kez; kullanıcı
  kaydırırsa bir sonraki seçime kadar dokunulmaz; döndürmede korunur). `V45ToolRow.kt`.

## 13. Yeni tuval menüleri (madde 13, 13.1)

- **Seçili eleman:** elemanın üst köşesinde yarı saydam yatay menü: Taşı, Ölçekle, Kopyala, Sil, Düzenle (Arapçada
  sol üst). Yer yoksa altına geçer, hep ekranda kalır. Arka plan seçiliyken de aynı menü.
- **Düzenle:** eski bağlam menülerindeki türe özel işlemlerin dikey listesi + öne/arkaya, gizle, seçimi kaldır.
  Şekillerde ilk satır "Şekli düzenle" (bilim şekillerinde Değerler düzenleyicisini açar).
- **Boş yere dokunma:** Seç'te seçim yokken, Ekle'de her dokunuşta ya da iki parmakla boş yere basılı tutunca, dokunulan
  yerde dikey **ekleme menüsü**. Eski tuval bağlam menüsü ve "buraya ekle" açılır menüsü kaldırıldı.
- Çift dokunma yine kâğıdı sığdırır (ikinci dokunuş menüyü kapatır). Çocuk modunda bu menüler yok.
- **Tablette sürüklenen pencereler (13.1):** en küçük genişlik ≥ 600 dp olunca üst araç satırı, mod seçici, yan panel ve
  alt pencereler (yüzen pencere olarak) tutamaçla sürüklenir, ekranda kalır, konum oturumlar arası saklanır,
  "Konumu sıfırla" düğmesi. Telefon değişmedi. `V45TabletWindows.kt`, `V45CanvasMenus.kt`.

## 14. DEX iyileştirmesi (madde 14)

- **Bulgu:** v40–v44'te `proguard-android.txt` kullanılıyordu; o dosyada `-dontoptimize` var. Yani yayında kod
  iyileştirme hiç açık değildi, yalnız küçültme ve ad karartma vardı.
- **Android 10 Ayarlar çökmesi** v40'ta AGP'nin öntanımlı **R8 tam kipinden** geliyordu (tam kip yansımayla kullanılan
  sınıfların yapıcılarını ve imzalarını siler). v42'de `android.enableR8.fullMode=false` ile düzelmişti. Bu çıkarımdır;
  burada Android 10 cihazı yok, cihazda doğrulanmadı.
- **v45:** `proguard-android-optimize.txt` (iyileştirme açık), uygulama kurallarından `-dontoptimize` çıkarıldı,
  **tam kip kapalı kaldı** (`gradle.properties`'te neden yazıyor). Testi: `V45R8ConfigTest` (4 test) bu üç ayarı ve
  yansıma/çökme raporu kurallarını korur.
- **Ölçüm (v44 → v45 iyileştirilmiş):** DEX 24,4 → 18,75 MB, sınıf 33618 → 26847, APK 64,0 → 62,3 MB, R8 uyarısı yok.
  Yansımayla adı aranan sınıf dizgileri taraması: v44'te olup v45'te kaybolan sınıf adı **0** (`/home/claude/r8cmp/scan.py`,
  dexdump).

## 15. Diğer değişiklikler

- versionCode 45, versionName 1.3.0.
- Kataloğa bağlı olmayan 5 çizgi roman resmi ve küçük resimleri çıkarıldı (V40ComicCatalogTest bunları yakalıyordu):
  `effects/fx_c1_41`, `objects/it_armory_37`, `objects/it_light_24`, `objects/it_armory_03`,
  `backgrounds/bg_yikik_merdiven_m` (.webp ve küçük resimleri, 236 KB).
- Ölü test `V36ObjectPickerTest.kt` silindi (v43'te kaldırılan `content.library.objects` paketine bağlıydı; yerini
  V43ShapePickerTest tutuyor).
- Çevrimdışı derleme için `gmaven-v45` deposu (Play içi güncelleme kütüphanesi) `settings.gradle.kts`'e eklendi.

## 16. Testler

Çekirdek: model 34/34, içerik 313 (0 hata). Uygulama: 898 test, 3 kırmızı (2'si gerçek sunucu ister, 1'i v44'te de kırmızı). Yeni v45 testleri: 40 sınıfta 177 test, hepsi geçti. Ayrıntı: `v45-devir-notu.md` → "Test durumu".

## 17. Bilinen sorunlar ve öneriler

Bkz. `v45-devir-notu.md` → "Bilinen sorunlar".
