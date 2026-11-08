# DrawGuidance v19 — Resim tuvalinde formülsüz bağlam menüsü, PDF kaydetme penceresi, Katmanlar üst menüde

_2026-09-28 · Room v8 (değişmedi) · Kayıt biçimi aynı (ContentCodec v4/v5)_

## İstekler ve yapılanlar

1. **Resim tuvalinin bağlam menüsünde formül yok.** İki parmakla uzun basınca (seçim yokken) açılan Ekle menüsünde "Formül ekle" yalnız not tuvalinde görünür. Resim tuvalinin Ekle modunda da formül zaten yoktu.
2. **PDF olarak kaydet penceresi.** ⋮ → PDF olarak kaydet artık hemen kaydetmiyor; alttan açılan bir pencerede şunlar seçilir:
   - **Dosya adı:** altında `.pdf` uzantılı hâli görünür.
   - **Sayfalar:** tüm sayfalar, bu sayfa ya da aralık (ilk–son). Geçersiz aralıkta Kaydet kapalıdır.
   - **Kağıt boyutu:** tuval oranı (kısa kenar A4 genişliği), A4, A5 ya da Letter.
   - **Yön:** sabit kağıtta otomatik, dikey ya da yatay. Tuval kağıda sığdırılıp ortalanır.
   - **Kenar boşluğu:** yok, dar (18 nokta) ya da normal (36 nokta).
   - **Kalite:**
     - Keskin (vektör): yazı, şekil ve formül keskin kalır. Silgi kullanılan sayfalar yine resim olur.
     - Resim: tüm sayfalar resim olarak yazılır.
     - Resim çözünürlüğü: standart 1×, yüksek 2× ya da çok yüksek 3×.
   - **Arka plan rengi:** kapalıyken beyaz kağıt (yazdırmak için).
   - **Kılavuz çizgileri:** tuvalde kılavuz varsa.
   - **Sayfa numarası:** altta "2 / 5" gibi. Kağıt koyuysa açık gri yazılır.
   - **Özet satırı:** "2 sayfa · A4 yatay · 842 × 595 nokta" gibi.
   - **Kaydedileceği yer:** İndirilenler/DrawGuidance.

   Son kullanılan ayarlar oturum boyunca hatırlanır; dosya adı ve sayfa seçimi her açılışta yenilenir.
3. **Katmanlar üst menüde.** Başlıktan kaldırıldı; her modun üst satırının sonunda duruyor (Çiz, Seç, Ekle, Düzenle, Taşı, Genişlet).
   - Düğmeler sığarsa satıra eşit aralıkla yayılır; sığmazsa satır parmakla yana kaydırılır.
   - Kaydırılabilen kenarda hafif bir solma görünür.
   - Düğme gölgeleri kaydırma alanında kesilmez.
   - Telefonda (411 dp) Çiz satırı kaydırmadan sığıyor. Ekle satırı ve dar ekranlar kaydırılıyor.

## Düzeltilen hata

- "PDF kaydedildi" ve "Resim kaydedilemedi" bildirimleri görünür görünmez kapanıyordu. Sebep: durum sıfırlanınca bildirimi gösteren efekt yeniden başlıyordu. Artık bildirim ekranın kapsamında gösteriliyor ve açık kalıyor.

## Mimari

- `data/images/PdfOptions.kt` (yeni):
  - `PdfOptions`, `pageIndices`;
  - enum'lar `PdfPages`, `PdfPaper`, `PdfOrientation`, `PdfMargin`, `PdfQuality`;
  - `PdfLayout.of` (sayfa boyu, tuvalin yeri ve ölçeği, sayfa numarası şeridi).
- `data/images/PdfSink.kt` (yeni): PDF yazıcı arayüzü. Uygulamada `AndroidPdfSink` (PdfDocument) kullanılır; testlerde sayfalar resme çizilir.
- `ImageExporter`:
  - `exportPdf(canvas, options)`, `renderPdf`, `pdfLocationLabel`;
  - `render(..., background)`: beyaz kağıt için.
- `ImagesRepository.defaultPdfOptions`.
- `ImageController`: `openPdfDialog`, `closePdfDialog`, `exportPdf(options)` ve son ayarların hatırlanması.
- `ui/components/PdfOptionsSheet.kt` (yeni).
- `EditorToolRow`:
  - `ToolRowFrame`: sığarsa yayar, sığmazsa kaydırır; kenarda solma; gölge payı (`bleed`);
  - `SpreadOrScroll` yerleşimi;
  - Katmanlar düğmesi.

## Doğrulama

- Yeni testler:
  - `V19PdfLayoutTest`: sayfa seçimi ve kağıt, yön, kenar boşluğu, sayfa numarası yerleşimi.
  - `V19PdfRenderTest`: sayfa sayısı ve boyu, beyaz kağıt ya da tuval rengi, içerik ortada, kenar boşluğu temiz, sayfa numarası, keskin/resim/silgili sayfa, dosyaya yazma.
  - `V19PictureCanvasTest`: formülsüz bağlam menüsü; Katmanlar her modda üst satırda ve Çiz satırına sığıyor.
  - `V19NarrowRowTest`: 330 dp'de satır kaydırılınca Katmanlar'a ulaşılıyor.
  - `V19PdfSheetTest`: pencerenin tüm ayarları, geçersiz aralık, kaydetme ve bildirim, ayarların hatırlanması; not tuvalinde formül bağlam menüsünde.
  - `V19ShotsTest`: ekran görüntüleri.
- Güncellenen test: `V17ContextMenuTest` (resim tuvalinde bağlam menüsünde artık formül yok).
- Tam koşu: 307 testin tamamı geçti (uygulama 122 sınıf / 202 test; core 105 test). Lint'te hata yok. `assembleDebug` başarılı (minSdk 21, targetSdk 36).

## PC'ye yazarken

- Stage: `outputs/v19src/android/`. v18'e göre 14 dosya (7 yeni, 7 değişen).
- Bilgisayardan silinecek dosya yok.
- Bilgisayardaki proje v18'de (md5 ile doğrulandı).
