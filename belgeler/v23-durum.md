# DrawGuidance v23: arka plan desen/resimleri, yazı kutusu, 3B düzenleme penceresi, ayrı yazı/şekil önerisi

_2026-10-04 · Room v10 (MIGRATION_9_10) · Kayıt biçimi ContentCodec v8: yazı kutusu ya da 3B şekil içeren tuvallerde; diğerleri v4–v7 · minSdk 21 – targetSdk 36 · Rapor: `DrawGuidance-v23-Rapor.docx`_

## İstekler ve yapılanlar

0. **Arka plan resmi ve deseni.**
   - Kağıt renginin yanında **Desen** ve **Resim** seçimi (Ekle menüsü → "Arka plan", ayrıca yeni tuval / tuval ayarları formunda).
   - **Desenler:** not tuvali için 30 (çizgili, kareli, milimetrik, noktalı, izometrik, Cornell, müzik porteği, haftalık plan…), resim tuvali için 30 (puantiye, ekose, zikzak, balık pulu, yıldızlar, çiçekler…). Hepsi kodla üretilir; renk, ölçek ve opaklık ayarlanır.
   - **Resimler:** not ve resim tuvali için 8 + 8 hazır resim (kraft, keten, pirinç kağıdı, suluboya kağıdı, mermer, ahşap…) ve galeriden resim. Sığdırma: kapla, sığdır, uzat, döşe.
   - **Veritabanı:** `paper_assets` tablosu. Desenler sıkıştırılmış ikili (70 desenin toplamı ~10,5 KB), resimler opaksa WebP (API 30+ `WEBP_LOSSY`), saydamsa PNG. Katalog sürümü değişmedikçe yeniden yazılmaz.
   - **Çizim:** desen bir kez karo bitmap'e çizilir (2'nin kuvvetine nicemlenir), `BitmapShader` ile döşenir; dışa aktarmada (PNG/PDF) vektör çizilir.
1. **Şekil veritabanı genişletildi.** ~140 sık çizilen şekil eklendi (toplam 396 şablon). Benzerliğin yanında **popülerlik önseli** (en çok kullanılan şekillere benzerlik) ve karışık çizimlerde **iç içe alt şekil araması** (SimHash LSH → 80 boyutlu kaba kNN → 320 boyutlu kNN → DTW ile yeniden sıralama). Tek şekilde ilk öneri ~%99, karışık çizimde ~%61 → ~%79 doğru, arama ~0,9 ms.
1.1 **Yazı kutusu (not tuvali).**
   - Taşı modunda sağ kenar (en), alt kenar (boy) ve köşe tutamakları; kutu istenildiği kadar genişler, yazı kutu enine göre kayar.
   - Kutu arka planı: renk, 10 kutu deseni (+ not desenleri) ve 10 hazır **dokuz parçalı** resim (yapışkan not, spiral defter, bantlı kağıt, yırtık kenar…). Köşeler/süsler esnemez; kenarlar tam sayıda tekrarlanır, tasarım bozulmaz. Yazı resmin iç boşluğundan başlar.
2. **3B düzenleme penceresi.**
   - Tuvalde 3B şekle yalnız genel işlemler: ekle, seç, taşı, ölçekle (bağlam menüsünde "3B düzenle").
   - Düzenleme ayrı pencerede (`model3dEdit` rotası). Açmadan önce her seferinde "Kaydet ve aç / Kaydetmeden aç" sorulur.
   - Araçlar: Döndür, Taşı, Çevir, Ölçekle (eksen kilidi), Köşe, Yüz (yükselt, içeri al, böl, sil), Birleştir/ayır, Boya (yüz ya da tüm parça), Çiz (ön/yan/üst düzlemlere fırçayla 3B çizgi). Malzeme (renk, parlaklık, metal), 31 hazır doku (veritabanından), ışık ve zemin ayarları. Geri al / yinele.
   - Sahne tek parmakla döndürülür, iki parmakla yakınlaştırılır.
   - Kaydedilen model `models3d` tablosuna yazılır, **3B Şekiller** sekmesinde ve şekil seçicinin "3B Şekillerim" bölümünde ayrı şekil olarak görünür. Tuvalden açılan model kaydedilince tuvaldeki resmi yenilenir.
   - Kütüphane: yazılım z-tamponlu çizici (OpenGL yok); etkileşimde ~420 px tek örnek, durunca ~760 px 2× süper örnekleme.
   - Eski tuval üstü 3B çizim çubuğu kaldırıldı (pencereye taşındı).
3. **Yazı ve şekil önerisi ayrıldı.** `TextSuggester` (satır zinciri, el yazısı + Türkçe sözlük, sonuç önbelleği) ve `ShapeSuggester` (InkShapes + şablon dizini) ayrı çalışır; iki ayrı balon, Ayarlar'da iki ayrı anahtar. Güçlü tek çizgi şekilse (ör. büyük çember) yazı önerisi bastırılır.
4. **Not tuvalinde ekleme sonrası Taşı.** Yazı, formül ve şekil eklenince Taşı moduna geçilir.
5. **İki parmakla uzun basma.** Seçili olmayan elemana basılırsa önce o (grubuyla) seçilir, sonra bağlam menüsü açılır; seçili elemana basılırsa seçim korunur. Boş yerde normal bağlam menüsü: seçim varsa seçimin menüsü, yoksa Ekle menüsü. Her eleman menüsünde ilk seçenek **Taşı**.
6. **Sıralama.** Elemanlar panelinde ve bağlam menüsünde En üste / Bir üste / Bir alta / En alta (`ZOrder`, katman içinde). Katmanlar panelinde her satırda yukarı/aşağı düğmeleri.
7. **Bu şekilden ok çıkar** (not tuvali, şekil menüsü): ok şeklin kenarından parmağın bırakıldığı yere çizilir.
8. **Geniş cihaz desteği** (minSdk 21 – targetSdk 36): WebP sürüme göre, resim önbelleği cihaz belleğine göre, sınırlı önbellekler (desen karoları 8 MB, 3B dokular 24 MB), ağır işler `Dispatchers.Default`'ta, 3B'de etkileşimde düşük çözünürlük.
9. **Word raporu:** `DrawGuidance-v23-Rapor.docx` (21 sayfa, 35+ ekran görüntüsü, 3 şema): kütüphaneler, tuval akışı, arka plan ve yazı kutusu, öneri algoritmaları, 3B düzenleyici, veritabanı, cihaz desteği, testler.

## Mimari

- **Veri:** `CanvasEntity.background` (JSON `BackgroundSpec`), `LibraryEntities.kt` (`paper_assets`, `models3d`, `textures3d`), `AppDatabase` v10.
- **data/library:** `PaperRepository` + `PaperLibrary`, `TextureRepository`, `Model3DRepository`, `Model3DResults` (düzenleyiciden tuvale sonuç), `BitmapCodec`.
- **core/content:** `paper/` (Paper, PaperCatalog, PaperImages, PatternPainter), `model3d/` (Model3D, Mesh, Primitives, ModelEdit, View3, Renderer, Textures, Samples, Model3DCodec), `ink/Lexicon.kt`, `shapes/TemplatesCommon.kt`.
- **ui/drawing:** `BackgroundRenderer`, `TextBoxResize`; `TextRenderer` kutu ölçüsü ve iç boşluk.
- **canvasedit:** `Model3DController`, `TextSuggester`, `ShapeSuggester`, `ZOrder`, `BackgroundSheet`, `Model3DPickerSheet`; `SelectionController.selectForContext/reorder`, `ShapeController.startArrowFrom`.
- **ui/screens/model3d:** `Model3DEditorScreen`, `Model3DEditorViewModel`, `Model3DViewport`, `Model3DPanels`, `Model3DListScreen`.
- Metinler `strings_v23.xml`.

## Doğrulama

- Yeni testler: `V23DataTest` (3), `V23ModelTest` (core/model), `Model3DTest` ve `PaperTest` (core/content), arayüz: `V23BackgroundTest`, `V23NotesTextBoxTest`, `V23ContextArrowTest`, `V23OrderTest`, `V23Model3DEditorTest`, `V23Model3DCanvasTest`, `V23No3dBarTest`, `V23SuggestShotsTest`, `V23EditorToolsShotsTest`.
- **Tam koşu:** 394 test (uygulama 153 sınıf / 256 test, core/content 107, core/model 31).
  - v23 davranış değişikliklerine göre güncellenen eski testler: `DatabaseAndExportTest` (v10 kolonları/tabloları), `EditorTestBase.addMenu` (uzayan Ekle satırında kaydırma), `V8ShotsTest`, `V11RecolorFillTest`, `V16TextBarTest` (eklenen yazı Taşı'da), `V17ContextMenuTest`, `V17SketchShotsTest` (iki parmak başka elemana basmasın), `V21TemplateShotsTest` (masa düz kağıtta, yüzden uzakta). Tekrar koşturulduğunda geçti.
  - `EditorCommandTest` bir ürün hatası gösterdi: boş yerde iki parmak seçimi kaldırıyordu (çoklu seçimde "Boşlukları doldur" çıkmıyordu). Düzeltildi: boş yerde seçim korunur.
  - `V9PerformanceTest`'teki "yoğun sayfa karesi 60 ms altında" sınırı bu oturumun 2 çekirdekli makinesinde 62–78 ms ölçüldü; ölçülen çizim kodu (ShapeRenderer, DiagramRenderer) v23'te değişmedi. `TracePreviewTest` süre ölçümü yük altında aşılabiliyor, tek başına geçiyor.
  - `CanvasFlowTest` ve `SettingsFlowTest` yerel backend'i (127.0.0.1:3000) ister; backend açıkken geçti.
- Lint: 0 hata (211 uyarı; v23'ün kullanılmayan metinleri ve yerel ayar uyarıları giderildi). `assembleDebug` başarılı (minSdk 21, targetSdk 36).

## Bilinmesi gerekenler

- **3B çizim çubuğu kalktı.** v21–v22'de tuval üstünde çizilen 3B izler eski tuvallerde görünmeye devam eder; yeni 3B işler 3B düzenleyicide yapılır.
- **Yazılım çizici:** 3B pencere GPU kullanmaz; çok parçalı modellerde eski telefonlarda döndürme sırasında görüntü kısa süre düşük çözünürlüklü kalır.
- **Galeriden arka plan resmi** tuvalin varlık klasörüne kopyalanır; aynı resim başka tuvalde yeniden kopyalanır.
- **Room v10:** eski sürüm uygulama bu veritabanını açamaz (geri dönüş yok).

## PC'ye yazarken

- Stage: `outputs/v23src/android/`: 99 dosya (42 yeni, 57 değişen). Silinecek dosya yok.
- Bilgisayardaki proje v22'deydi (değişen dosyaların bilgisayardaki kopyaları v22 ile aynı çıktı); v23 dosyaları üzerine yazıldı ve MD5 ile doğrulandı.
