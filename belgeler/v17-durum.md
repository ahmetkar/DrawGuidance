# DrawGuidance v17 — Seç+İncele birleşimi, Ekle/Düzenle modları, bağlam menüsü, alan boyama, kenar vektörleştirme, çizim yardımcıları

_2026-09-28 · Veritabanı sürümü değişmedi (Room v8; yalnız yeni sorgular) · Kayıt biçimi aynı (ContentCodec v4); boyalı alanlar şekil parçası olarak (`tag = "region"`) saklanır, eski kayıtlar aynen açılır_

## İstekler ve yapılanlar

1. **+ (Ekle) düğmesi**: son seçilen öğenin simgesini gösterir (köşede küçük +); dokununca menü açılır, son seçilen menüde işaretli.
2. **Formüller elemanlarına ayrılır**: her sayı, harf ve Yunan harfi ayrı düzenlenebilir yuvadır. Aynı harfin her geçişi ayrıdır; birine verilen değer diğerlerini değiştirmez. Eski formüller ilk düzenlemede kendiliğinden ayrılır. "Formülü yaz" alanında formül düz hâliyle görünür.
3. **Resim vektörleştirme kenar çizgisiyle**: sonuç dolgu değil, düzenlenebilir çizgilerdir. Her çizginin vektör noktaları köşelerde ve eğrinin dönüm yerlerindedir.
   - Çizgi bulma türleri: **Otomatik**, **Kenarlar** (fotoğraf; Canny) ve **Çizimin çizgileri** (karakalem/mürekkep; çizginin ortası).
   - Ayarlar: ayrıntı, yumuşatma, kısa çizgileri atma, çizgi kalınlığı, çizgi rengini resimden alma, resmi koruma.
   - Kesişen çizgiler kopmaz: çizgi kavşaktan düz devam eder, daire kapalı kalır. Basamak artıkları atılır.
4. **Grafik formülü değişince grafik yeniden çizilir**. Bu, hazır eğrilerde de geçerlidir: formül yazılınca hazır eğri bırakılır, formül silinince hazır eğri geri gelir.
5. **Resim tuvali seçicisi**: eski hazır renkli çizimler ve temel çizimler (manzara/portre) seçiciden kalktı; eski kayıtlar açılır. Geometrik şekiller duruyor.
   - Yeni **Çizim yardımcıları** bölümü, 90 renksiz özgün çizim:
     - Portreler (10): önden, 3/4 ve profil yüz, çocuk yüzü, kafa kurgusu, göz, burun, dudak, kulak, saç.
     - İnsan (6): 8 kafa oranlı manken, yürüme, koşma, oturma, çocuk, el.
     - Taşıtlar (10), Yapılar (10; perspektif ev dahil) ve Doğa (12: bulut, güneş, ay, ağaç, çam, palmiye, çalı, çimen, çiçek, dağ, yaprak).
     - Hayvanlar (10), Meyveler (10), Sebzeler (10) ve Ev eşyaları (12).
   - Oran/kılavuz çizgileri ince ve kesik; alan boyamada sınır sayılmaz.
6. **Alan alan renklendirme**: şekil düzenlemede **Alan boya** aracı. Renk seçilip çizgilerle çevrili bir alana dokunulur.
   - İçteki adacıklar (ör. göz) delik kalır. Dolgu çizginin iç kenarında durur: çizgiyi örtmez, arada boşluk da kalmaz.
   - Boyalı alana başka renkle dokunmak rengini değiştirir; **Temizle** rengi kaldırır. Küçük boşluklar kendiliğinden kapatılır.
   - Kapalı olmayan yerde bildirim çıkar. Çizgiler düzenlenince boyalı alanlar yeni çizgilere uyar.
7. **Kalemle çizim düzeltme önerisi**: Çiz modunda çizip kısa süre durunca "Çiziminiz şöyle düzeltilebilir:" önerisi çıkar ve düzeltilmiş hâl tuvalde kesik çizgiyle önizlenir.
   - Şekiller: düz çizgi, düz kenarlar, daire, elips, üçgen, dikdörtgen, kare, çokgen. El yazısında yazı önerisi.
   - Onaylanırsa uygulanır; şekilde aynı fırçayla düzgün çizilir, tek adımda geri alınır.
   - Ayarlar'daki ve başlıktaki (⋮) el yazısı ayarı kaldırıldı; öneri hep açık. Seçili çizim için bağlam menüsünde **Düzeltme öner** de var.
8. **Seç ve İncele birleşti**: elemana dokunmak seçer, boş yere dokunmak seçimi kaldırır. Boş yerde sürükleme kaydırır, iki parmak yakınlaştırır, boş yere çift dokunmak kağıdı sığdırır.
   - Seçim yokken altta yalnız kısa yönerge durur. İş bitince seçimsiz Seç moduna geçilir.
9. **Ekle ve Düzenle modları** (çizimin hemen üstündeki mod menüsü: Çiz, Seç, Ekle, Düzenle, Taşı, Genişlet):
   - Ekle: dokunulan yerde "Buraya ekle" menüsü açılır; yazı, şekil, formül, diyagram ve resim oraya eklenir.
   - Düzenle: dokunulan elemanın düzenleyicisi açılır (şekil, diyagram, resim, yazı, formül; fırça çiziminde çizim araçları).
10. **Döndür ve Ölçekle** mod menüsünden üst menüye taşındı.
11. **Üst menü moda göre değişir**:
    - Çiz: fırça, silgi, boya kutusu, +, kırp ve paneller.
    - Seç/Taşı: döndür, ölçekle, boya kutusu, kopyala, sil, kırp, elemanlar.
    - Ekle: eklenecekler.
    - Düzenle: şekil düzenlemede Noktalar / Nokta ekle / Çizgi çiz / Çizgiler / Alan boya; resim düzenlemede Kırp / Vektörleştir / Kalem.
    - Genişlet/Kırp: kağıt araçları.
12. **İki parmakla uzun basma bağlam menüsü**. İçeriği seçime göre değişir:
    - Seçim yoksa: yazı, formül ve şekil ekle (basılan yere).
    - Yazı: düzenle, ayarlar, renk.
    - Şekil: düzenle, alan boya, dolgu, döndür, ölçekle.
    - Çizim: renk, incelt, kalınlaştır, yumuşat, düzeltme öner.
    - Formül: katsayıları düzenle, formülü yaz.
    - Resim, diyagram ve karışık seçimin de kendi seçenekleri var; hepsinde kopyala ve sil.
13. **Resimlerim klasörleri gerçek klasör**: klasör oluşturunca kayıt yerinde de oluşur. Kayıt yeri Ayarlar'da seçilen izinli klasör ya da Galeri'de Pictures/DrawGuidance olabilir.
    - Resim taşınınca dosyası da o klasöre taşınır.
    - Klasör adı değişince dosyaları yeni adlı klasöre geçer; klasör silinince boşalan klasör de kalkar.
    - Not tuvalinin sayfaları tuvalin klasörüne kaydedilir.

## Mimari

- `core/content`:
  - `formula/Formula.kt`: `atomize`, `isAtomized`, `plain`.
  - `trace/EdgeTracer.kt` (yeni).
  - `fill/RegionFill.kt` (yeni; alan bulma, delikler köprüyle tek kapalı yol).
  - `ink/InkShapes.kt` (yeni; el çiziminden şekil tanıma).
  - `library/sketch/*` (yeni çizim yardımcıları); `LibCategory.listed` (seçicide görünür mü).
- `EditMode`:
  - `VIEW` kalktı; `ADD` ve `EDIT_PICK` eklendi.
  - `family` alanı: mod menüsünde hangi düğme seçili görünür.
  - `rowKind` / `ToolRowKind`: üst menünün türü. `SelectionKind`: bağlam menüsü için.
  - `finishAction`: seçim kalkar, Seç'e geçilir.
- Menüler ve jestler:
  - `EditorToolRow(items = …)`: moda özel satır. `CanvasMenus.kt`: `CanvasPopupMenu`, `ModeHintChip`.
  - `DrawingCanvas`: iki parmakla uzun basma (`TWO_FINGER_MENU_MS` 550 ms).
  - `CanvasTools`: `SelectTool` (birleşik), `AddTool`, `EditPickTool`.
- Denetleyiciler:
  - `ShapeController`: `ShapeTool.FILL`, alan boyama ve düzenlemede alan yenileme.
  - `HandwritingController`: şekil + yazı önerisi, `ShapeFix`, `suggestForSelection`, `acceptShape`.
  - `ImageEditController.vectorize(EdgeOptions, lineWidth, keepImage)`.
- Resim deposu:
  - `ImageExporter`: `createFolder`, `moveImage`, `deleteFolder`.
  - `ImagesRepository`: `moveToFolder` (dosyayla), `renameFolderOnDisk`, `createFolderOnDisk`.
- `InkSmoothing` (çizim yumuşatma). Eklenen resim de seçili olarak Taşı'ya geçer.

## Doğrulama

- Yeni testler:
  - `core:content`: `V17FormulaAtomTest`, `V17EdgeTracerTest`, `V17RegionFillTest`, `V17InkShapesTest`, `V17SketchesTest` (önizleme sayfalarıyla).
  - `app`: `V17LogicTest`, `V17ModesTest`, `V17ContextMenuTest`, `V17InkCorrectionTest`, `V17VectorizeTest`.
- Güncellenen testler: İncele yerine seçimsiz Seç; Döndür/Ölçekle ve şekil araçları üst menüde; formül yuvaları numaralı; resim tuvali seçicisinde çizim yardımcıları; "Ekle menüsü" adı.
- Tam koşu: 279 testin tamamı geçti (107 uygulama test sınıfı + core). `HandwritingTest.performance` yük altında bir kez zaman aşımına düştü, tek başına çalıştırınca geçti. `assembleDebug` başarılı.

## PC'ye yazarken

- Stage: `outputs/v17src/android/` — v16'ya göre 76 dosya (24 yeni, 52 değişen).
- Bilgisayardan silinecek dosya yok.
