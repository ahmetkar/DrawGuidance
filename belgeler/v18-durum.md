# DrawGuidance v18 — Moda göre sade üst menüler, grafik deseni ve kağıt uyumu, Android 5.0→16 desteği, tablet kalemi

_2026-09-28 · Room v8 (değişmedi). Kayıt biçimi ContentCodec v5, yalnızca basınç ya da eğim içeren çizimlerde kullanılır; öteki kayıtlar v4 olarak yazılır. Eski kayıtlar aynen açılır._

## İstekler ve yapılanlar

1. **Çiz** menüsü: fırça, silgi, boya kutusu, elemanlar ve fırça ayarlarından oluşur. Kırp ve + (ekle) açılır menüsü kaldırıldı. Eklenecekler yalnızca **Ekle** modunun üst satırında durur.
2. **Seç** menüsü: kopyala, sil ve elemanlar. Kırp, boya, döndür ve ölçekle bu menüden çıktı.
3. **Düzenle** menüsü: döndür, ölçekle, kırp, boya kutusu ve elemanlar.
   - Şekil, resim ve diyagram düzenlemede de döndür ve ölçekle ilk sırada yer alır.
   - Döndür, Ölçekle ve Kırp bitince Düzenle'ye dönülür.
4. **Taşı** menüsü: yalnızca taşımayla ilgili seçenekler bulunur.
   - Seçimi 1 ya da 10 px adımla kaydıran yön okları.
   - "Sayfada ortala".
   - Koordinat çipi ("X · Y"): dokununca konum penceresi açılır ve seçimin sol üst köşesi tam koordinata taşınır.
   - Adım çipi (1 px / 10 px).
5. **Genişlet** menüsü: yalnızca genişletme ayarları bulunur.
   - Sol, üst, alt ve sağ kenarı %10 genişletme ve "Sığdır".
   - Boyut çipi ("1240 × 1600 px"): dokununca Kağıt boyutu penceresi açılır. Genişlik ve yükseklik 64–8192 px arasında girilir; 3×3 düğmeyle sabit kalacak yer seçilir.
   - Kağıt büyütülürken çizimler seçilen yere göre kayar, küçültülürken karşı kenarlardan kırpılır.
6. **Komut yaz** kutusu kaldırıldı. "Boşlukları doldur" artık iki parmakla uzun basınca açılan bağlam menüsünde (çizim ve karışık seçimde).
7. **Grafik deseni formülü değiştirir.** Fonksiyon grafiklerinde desen artırılınca eğri sıklaşır ve formül de buna göre değişir: sin(x) → sin(2x), x² → (2x)². Desen 1–8 arasındadır.
   - Eksenler aynı kalır.
   - Formül elle yazılınca desen 1'e döner.
8. **Grafik arka planı kağıda uyar**: ızgara, yardımcı çizgiler, formül kutusu ve nokta içleri kağıt rengine göre çizilir.
   - Kağıt rengi değişince tüm grafikler aynı geri alma adımında yeniden çizilir.
   - Tuval açılırken eski grafikler de uyarlanır.
9. **Geniş Android desteği**: Android 5.0 (API 21) ile Android 16 (API 36) arasında çalışır.
   - Kütüphaneler minSdk 21'i destekleyen son sürümlerde tutuldu.
   - Yeni Java API'leri için desugaring eklendi.
   - Sürüme göre değişen yerler `Compat` ile seçilir:
     - donanım bitmap;
     - StaticLayout;
     - kalem yan tuşu;
     - yüksek yenileme hızı (API 23+).
   - `DeviceCaps` cihazın belleğine göre dışa aktarma boyutunu (1024–4096) ve resim önbelleğini ayarlar; bellek azalınca önbelleği boşaltır.
   - Android 7.1 ve öncesi için PNG simgeler eklendi. Android 13+ için tek renkli simge eklendi.
10. **Tablet kalemi**:
    - **Algılama**: bağlı kalem aygıtı (Bluetooth dahil) algılanır ve Ayarlar'da "Kalem bağlı / kullanılıyor" olarak görünür.
    - **Anında çizim**: kalem değer değmez dokunma eşiği beklenmeden çizer ve ara örnekleri de kullanır.
    - **Basınç**: çizgiyi inceltip kalınlaştırır; kurşun kalem, kömür ve pastelde koyuluğu da değiştirir. Tüm fırça motorları basınca duyarlı.
    - **Eğim**: kurşun kalem, kömür ve pastelde çizgiyi genişletip açar.
    - **Silme**: kalemin silgi ucu ya da yan tuşu seçili fırçayı değiştirmeden siler.
    - **Avuç**: kalemle çizerken avuç ve parmaklar yok sayılır. Kalem havadayken gelen dokunuşlar 350 ms boyunca yok sayılır. Sistem dokunuşu avuç sayıp iptal ederse çizgi kaydedilmez.
    - **Parmak kaydırır**: kalem kullanılınca parmak çizmez, kaydırır ve iki parmak yakınlaştırır. Fırça göstergesinin altındaki "Kalem modu" çipiyle parmakla çizim açılıp kapatılır.
    - **Fırça boyu imleci**: kalem havadayken fırça boyunda bir halka görünür.
    - **Hareket tahmini**: Android 14+'ta sistemin MotionPredictor'ı, öncesinde doğrusal tahmin kullanılır. Tahmin geçici olarak gösterilir, kaydedilmez.
    - **Düşük gecikmeli çizim** (Android 10+): ön tampon (graphics-core `CanvasFrontBufferedRenderer`) kullanılır. Olaylar tamponlanmadan alınır ve yüksek yenileme hızı istenir.
    - **Ayarlar → Tablet kalemi**: altı ayrı anahtar (parmak kaydırsın, basınç, eğim, yan tuş silgi, tahmin, düşük gecikme).

## Mimari

- Derleme:
  - AGP 8.13.2, Gradle 8.14.3, Kotlin 2.2.21 (compose derleyici eklentisi), KSP 2.2.21-2.0.5.
  - compileSdk ve targetSdk 36, minSdk 21, JDK 21 (hedef 17), desugar 2.1.5.
  - Compose BOM 2025.11.01, Room 2.7.2, lifecycle 2.9.4, navigation 2.9.8, graphics-core 1.0.4.
- `platform/` (yeni):
  - `DeviceCaps`;
  - `Compat`;
  - `Stylus`: durum, ayarlar, aygıt dinleyici, yan tuş ve iptal tespiti; aynı dosyada `StrokePredictor`.
- Model:
  - `StrokePoint.pressure` ve `tilt` (JSON'da varsayılanlar yazılmaz).
  - `GraphSpec.freq` ve `paper`.
  - `ContentCodec` v5 (`EXT_DYNAMICS`: nokta başına 1 bayt basınç ve 1 bayt eğim).
- Grafik (`core/content`):
  - `GraphParams.freq/paper`, `GraphPalette.of(paper)`.
  - `FormulaText.scaleX`, `Graphs.editableFormula`, `patternRange`.
- Çizim:
  - `BrushDynamics` (fırça başına eğriler).
  - `StrokeStream`: basınç ve eğim dizileri; tüm motorlarda genişlik ve opaklık çarpanı.
  - `DabBuckets` opaklık seviyeleri.
  - `PathStream`: dinamik çizgide parça parça çizim.
  - `LiveStrokeLayer.start(front = …)`.
  - `FrontBufferInk` (API 29+), `GraphPaper` (yeniden renklendirme).
- Editör:
  - `ToolRowKind.MOVE`; Döndür, Ölçekle ve Kırp Düzenle ailesinde.
  - `PenInput.kt`: `PenSession`, `penStroke`, `FrontBufferSurface`.
  - `DrawingCanvas`: kalem oturumu, havada imleç, yakınlık filtresi, parmakla kaydırma.
  - `FrameSheets.kt`: `PositionSheet`, `CanvasSizeSheet`.
  - `CanvasEditViewModel`: `moveSelectionTo`, `centerSelection`, `expandSide`, `setCanvasSize`, `fillGaps`.
  - `CommandBar` ve `EditorCommands` kaldırıldı.

## Doğrulama

- Yeni testler:
  - `V18ModelTest`: codec v5/v4, JSON.
  - `V18GraphPatternTest`.
  - `V18TopRowTest`, `V18MoveTest`, `V18ExpandTest`, `V18GraphTest`.
  - `V18StylusTest`: kalem olaylarıyla basınç, eğim, silgi ucu, yan tuş, avuç iptali, parmakla kaydırma ve çip.
  - `V18PenRenderTest`: basınç tüm motorlarda inceltir, kömürü açar; eğim kurşun kalemi genişletir.
  - `V18Api21SmokeTest` ve `V18Api24SmokeTest`: eski Android'de açılış, çizim ve kayıt.
  - `V18ShotsTest` (ekran görüntüleri).
- Tam koşu: 298 testin tamamı geçti (uygulama 116 sınıf / 193 test; core 105 test).
  - `TracePreviewTest.timings` paralel yük altında bir kez süreyi aştı (213 ms > 150 ms). Tek başına 59 ms ile geçti; kod değişmedi.
- Lint'te hata yok. `assembleDebug` başarılı. APK: minSdk 21, targetSdk 36.
- **Gerçek cihazda denenmedi**: kalem basıncı, eğim, düşük gecikme ve hareket tahmini. Sorun çıkarsa her biri Ayarlar → Tablet kalemi'nden kapatılabilir.

## PC'ye yazarken

- Stage: `outputs/v18src/android/`. v17'ye göre 109 dosya (24 yeni, 85 değişen).
- Bilgisayardan silinecek dosya: `android/app/src/main/java/com/ahmet/drawguidance/ui/screens/canvasedit/components/CommandBar.kt` (listesi `v18src/SILINECEK-DOSYALAR.txt`).
- İlk derlemede Gradle 8.14.3 ve yeni kütüphaneler indirilir.
- Android SDK Platform 36 gerekir. Eksikse Gradle kendisi indirir; bunun için SDK lisanslarının kabul edilmiş olması gerekir.
