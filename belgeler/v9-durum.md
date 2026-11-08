# DrawGuidance v9 — Durum Notu

_2026-09-18. `durum-ve-mimari.md` dosyasının devamıdır; v1–v8 mimarisi orada._

**Teslim:** 131 kaynak dosya + `DrawGuidance-debug.apk` (20,6 MB) + `v9-onizleme.png`
`C:\Users\ahmet\Documents\DrawGuidance` altına yazıldı. 112/112 test geçti (49 sınıf),
`assembleDebug` başarılı.

## 12 maddenin karşılıkları

1. **Nesne elemanlar bütün siliniyor** — `BrushType.isObject` (şekil, yazı, resim, diyagram):
   `ElementIndexer` bunları hiçbir zaman başka çizimle birleştirmez, `ElementOps.objectsHitBy`
   silginin değdiği nesneyi tamamen kaldırır, `ErasedStrokePruner` piksel ayıklamada bunlara dokunmaz.
2. **Grafikler** — `ui/drawing/GraphShapes.kt` + `Formula.kt`: 3B eksen, fonksiyon grafiği, koordinat
   kağıdı, birim çember, sütun/çizgi/pasta/dağılım. Eksen etiketleri (x, y, z) ve formül eleman
   ayarlarından verilir; formül kendi ayrıştırıcımızla çizilir (`+ - * / ^`, parantez, örtük çarpım,
   sin/cos/tan/…/ln/log, pi/e, Türkçe ondalık virgül). Geçersiz formülde uyarı çıkar, eğri çizilmez.
3. **Diyagramlar** — `BrushType.DIAGRAM`, `DiagramSpec`, `ui/drawing/DiagramRenderer.kt`: akış, süreç,
   mimari, organizasyon, zaman çizelgesi, durum, ilişki, karşılaştırma. Yerleştirince açılan küçük
   panelden (`DiagramTools.kt`) adım ekle/sil/yeniden adlandır, kutu biçimi, ok ekle/sil, ok yazısı.
4. **Daha gerçekçi sanat şekilleri** — kelebek (dört kanat, gövde, antenler, benekler), ağaç, kuş,
   ev (baca, bölmeli pencere), yaprak (damarlar), göz (kirpik, kaş, ışık), çimen, balık, palmiye,
   kaya, yelkenli, elma. Hepsi elle çizildi (telif nedeniyle internetten görsel alınmadı).
5. **Tuval içi yazı** — `InlineTextEditor.kt`: pencere yok; dokunulan yere aralıklı noktalarla çevrili
   yazma alanı açılır, altta geçici font çubuğu durur. Aynı ayarlar yazı seçiliyken Elemanlar
   panelinde de görünür.
6. **Vektör bilgisi veritabanında** — `ShapeFactory.kt` / `ShapeRenderer.kt`: şekil tek eleman
   (`BrushType.SHAPE`); noktalar alt yollar hâlinde (`SubPath`), biçim (`ShapeStyle`) ve grafik
   bilgisi (`GraphSpec`) elemanın içinde saklanır.
7. **Eleman biçimi** — `ElementStyleTools.kt`: hizalama (sola/ortaya/sağa), dolgu rengi ve opaklığı,
   kenar rengi ve kalınlığı, köşe yuvarlaklığı; şekil, diyagram ve yazı kutusu için. Ucu açık
   grafiklerde (eksen, fonksiyon grafiği, koordinat kağıdı, sayı doğrusu, 3B eksen) sunulmaz.
   Yazı kutusu `TextSpec.box` olarak saklanır (yazı stil baytının 5. biti; eski kayıtlar etkilenmez).
8. **Sayfalar** — `CanvasUi.pages` + `currentPage`, kayıt biçimi `VERSION_PAGES = 2`, Room v7
   (`currentPage`, `guide` kolonları, `MIGRATION_6_7`). Çalışma/Not tuvali oluşturulurken sayfa sayısı
   (1/2/4/8/16); çizim alanının hemen altında sayfa şeridi (`PageStrip.kt`): geçiş, ekle, uzun basınca
   çoğalt/sil. Her sayfanın çizimleri kendine ait.
9. **Şekil düzenleme modu** — `EditMode.SHAPE_EDIT`, `ShapeEditor.kt`: parçala, dilimle (çizilen doğru
   boyunca), kenarı kaldır, yolu kes, nokta birleştir, yolu kapat. Tutamaklar tuvalde görünür.
10. **Kılavuz ve perspektif** — `GuideRenderer.kt`, `GuideSpec`: kareli, çizgili, noktalı, izometrik,
    1/2/3 nokta perspektif. Ayarlar eklenirken açılan pencereden (aralık, görünürlük, renk, ufuk
    çizgisi, kaçış noktaları, ışın sayısı, dışa aktarmaya dahil et). Tuvale kaydedilir.
11. **Tasarım** — mod çubuğunda elemana özel düzenleme düğmeleri kendi satırında (etiketler kırpılmıyor);
    diyagram panelinde kutu biçimi çipleri yalnızca istenen adım için açılıyor; sayfa şeridi hap biçimli.
12. **Optimizasyon** — şekil yolu önbelleği, diyagram yazı düzeni önbelleği, görünür alan dışındaki
    şekil/diyagram/yazıların hiç çizilmemesi, çizim başına kodlanmış bayt önbelleğinin sayfalara yayılması.

## Ölçümler (masaüstü CPU, yazılım tuvali)

- 60 vektör şekil + 6 diyagram + 20 yazı + 100 fırça izi: ilk kare 129 ms, önbellekli 25 ms,
  görünür alan dar iken **3,0 ms**.
- 16 sayfalık aynı içerik: ilk kodlama 151 ms, önbellekli 14 ms, çözme 121 ms, 133 KB.

## Yeni dosyalar

`ui/drawing/`: ShapeFactory.kt, ShapeRenderer.kt, ShapeEditor.kt, GraphShapes.kt, Formula.kt,
DiagramRenderer.kt, GuideRenderer.kt
`ui/screens/canvasedit/components/`: DiagramTools.kt, ElementStyleTools.kt, GuideSheet.kt,
InlineTextEditor.kt, PageStrip.kt
Testler: V9VectorShapesTest, V9PagesTest, V9DiagramTest, V9GuideTest, V9ShapeEditTest, V9GraphTest,
V9PerformanceTest, V9ShotsTest (V9NotesShotsTest / V9ArtShotsTest)

## Notlar

- İlk açılışta veritabanı v7'ye geçer; eski tuvaller tek sayfalı kalır, kılavuz kapalı başlar.
- "Resim olarak kaydet" açık sayfayı kaydeder; çok sayfalı dışa aktarma henüz yok.
- Backend v9'da değişmedi (yalnızca `/api/health`).
