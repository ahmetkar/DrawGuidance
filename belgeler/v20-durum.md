# DrawGuidance v20: fırça ayarları, sabitleme, çizim hızı, kalem eğimi, üç parmakla geri al, gerçek 3B fırça

_2026-09-29 · Room v8 (değişmedi) · Kayıt biçimi ContentCodec v6 (yeni ayar ya da kalem yönü içeren izlerde; diğerleri v4/v5)_

## İstekler ve yapılanlar

1. **Çizim önerileri kapatılabilir.**
   - Öneri kartında göz simgeli "Önerileri kapat" düğmesi var.
   - Kapatınca bir bildirim çıkar: "Çizim önerileri kapatıldı (⋮ menüsünden açılabilir)".
   - Yeniden açmak için iki yol var: ⋮ menüsündeki "Çizim önerileri" işaret kutusu ya da Ayarlar'daki anahtar.
   - Ayar kalıcıdır; uygulama kapansa da hatırlanır.
   - Kart artık ekrana sığacak kadar sola kayıyor. Kapatma düğmeleri kaymayan kısımda olduğundan hep görünür.
2. **Fırça boyutu 1–380 px.**
   - Kaydırıcı üstel çalışır: küçük boyutlar hassas, büyükler hızlı ayarlanır.
   - Değere (ör. "18 px") dokununca alttan bir pencere açılır ve boyut yazılabilir. Aralık dışı bir değer girilirse Uygula kapalı kalır.
   - Her fırça kendi boyutunu hatırlar.
3. **Aralık (spacing).**
   - Damgalarla çizen fırçalarda (sert/yumuşak fırça, damgalar, yarım ton, sıçratma, desenler) "Aralık" kaydırıcısı var: fırça genişliğinin %2'si ile %300'ü arası.
   - Varsayılandan farklıysa bir "Varsayılan" düğmesi çıkar.
   - Aralık ize kaydedilir; kayıtlı çizim aynı görünür.
4. **Çizim hızı ("Mürekkebin kaleme yetişme hızı").**
   - %10 ile "Anında" arası ayarlanır.
   - Düşük değerde mürekkep parmağı gecikerek ve yumuşakça izler (en çok ~0,24 sn).
   - Parmak dururken de mürekkep yetişmeye devam eder (16 ms'de bir). Parmak kalkınca son noktaya tamamlanır.
5. **Kalem eğimi** (eğim bildiren tablet kalemleri için):
   - Eğimle kalınlaşma (%0–200) ve eğimle açılma (%0–100).
   - Eğim eşiği (0–60°): kalem bu açıdan fazla yatınca etki başlar.
   - Yassı uçlu fırçalarda (kaligrafi) "Uç kalemin yönüne dönsün" seçeneği. Kalemin yönü her noktaya kaydedilir.
6. **Sabitleme** (fırça başına açılır):
   - Gücü %5–100 arası ayarlanır.
   - Çekilen ip yöntemi (en çok 26 px) ile kayan ortalama birlikte çalışır; zikzaklı, titrek çizgiler düzleşir.
   - Kaydedilen noktalar düzleştirilmiş hâlidir.
7. **Üç parmakla geri al / yinele.**
   - Sola kaydırma geri alır, sağa kaydırma yineler. Bildirimler: "Geri alındı", "Yinelendi", "Geri alınacak adım yok", "Yinelenecek adım yok".
   - Hareket 60 dp'yi geçince ve yatay hareket dikeyin en az 1,5 katıysa bir kez çalışır.
   - Bu sırada tuval kaymaz ve yakınlaşmaz, çizgi oluşmaz.
8. **3B fırça yeniden yapıldı.** Artık gerçek bir tüp gibi görünüyor:
   - 16 bant içe doğru daralır ve ışığa doğru kayar: kenarlar koyu, ışığa bakan yüz aydınlık, en üstte parlama.
   - Uçlar yarım küre gibi. Keskin dönüşlerde dirsek yuvarlak. Kalem basıncı kalınlığı değiştirir.
   - Kullanıcı ayarları:
     - **Işık yönü:** 0–359°, kadranla gösterilir.
     - **Kabartma** ve **parlaklık**.
     - **Malzeme:** mat, plastik, metal (yansıma bantları, renkli parlama) ya da cam (yarı saydam, karşı yanda ikinci parlama).
     - **Zemin gölgesi** ve **gölge yumuşaklığı:** gölge ışığın tersine düşer.
   - "Bu fırçanın ayarlarını sıfırla" düğmesi var.
   - Tüm ayarlar fırça başına saklanır ve ize kaydedilir.

## Mimari

- **core/model**
  - `Stroke`: `params: BrushParams?` alanı eklendi.
  - `StrokePoint`: `orient` alanı eklendi.
  - `BrushParams`: `spacing`, `tiltWidth`, `tiltLight`, `tiltThreshold`, `nibFollow`, `tube`.
  - `TubeParams`: `lightAngle`, `relief`, `gloss`, `material`, `shadow`, `shadowSoftness`.
  - `ContentCodec` v6: nokta başına kalem yönü (1 bayt) ve varsayılan olmayan ayarların JSON'u.
- **Ayarlar ve durum**
  - `data/settings/BrushTuning.kt` (yeni): fırça başına ayarlar; `params(type)` yalnız varsayılandan farklı olanları döndürür.
  - `EditorPrefsStore`: `brushTunings` ve `brushSizes` eşlemeleri.
  - `BrushController`: yükleme ve kaydetme, `updateTuning`, `resetTuning`; boyut 1–380 aralığına sınırlanır.
- **Çizim girdisi**
  - `ui/drawing/InkFilter.kt` (yeni): sırasıyla çekilen ip, kayan ortalama ve zaman takipçisi (`tick` ile).
  - `DrawTool` filtreyle çizer.
  - `PenInput` ve `DrawingCanvas`'ın parmak döngüsü, filtre açıkken 16 ms'de bir ilerler.
  - `Stylus.orientOf` kalemin yönünü okur.
  - `DrawingCanvas`: üç parmak dalı (`onThreeFingerSwipe`).
- **Fırça motoru**
  - `BrushDynamics`: eğim ayarları ve eşik.
  - Damgalı motorlarda `spacing` ayarı. `RibbonStream` ucu kalemin yönüne döndürebilir.
- **TubeStream (3B)**
  - Gölge ayrı katmanda: opak siyah çizilip saydamlıkla birleştirilir, izin altında kalır.
  - Canlı çizimde her parça, son örneklerin tüpünü (önceki parçanın bant uçlarını kapsayacak kadar geriden) yeniden çizer:
    - bölgeye değen tüm dörtgenler (çizginin eski kısımları dahil) kayıtlı çizimdeki bant sırasıyla kendi katmanında çizilir;
    - karodaki iz bu bölgede SRC ile değiştirilir.
  - Sonuç olarak canlı ve kayıtlı çizim piksel piksel aynıdır: birleşim yerlerinde kenar koyulaşması ya da kesik kenar olmaz, dar kıvrımlarda delik kalmaz.
  - Gölge çizerken yaklaşıktır; parmak kalkınca tamamı yeniden çizilir.
  - `LiveStrokeLayer`: gölge karoları; `needsOwnLayer` ile kuyruk yalnız kendi katmanını değiştirir.
- **Arayüz**
  - `BrushTuningControls.kt` (yeni): `BrushSizeControl` (üstel kaydırıcı ve yazma penceresi), `BrushTuningSection`, `LightDial`.
  - `EditorSidePanel` ve `BrushPreview` ayarları gösterir.
  - Öneri kartı (`HandwritingSuggestionChip`) ve ⋮ menüsü güncellendi. Ayarlar'da öneri anahtarı var.
  - Metinler `strings_v20.xml` dosyasında.

## Doğrulama

- Yeni testler:
  - `V20ModelTest`: codec v6 ve varsayılanlar.
  - `V20InkFilterTest`: sabitleme, gecikme, bitişte yetişme.
  - `V20EngineTest`:
    - aralık, eğim ve eşik, uç yönü;
    - tüpün ışık tarafı ve gölgesi, malzemeler;
    - canlı = kayıtlı (dalga: 1 pikselde fark > 30);
    - dar zikzak ve halkada delik yok, canlı = kayıtlı.
  - `V20SuggestionsOffTest`: karttan kapatma, bildirim, kapalıyken kart çıkmaması, menüden açma.
  - `V20ThreeFingerSwipeTest`: geri al ve yinele, bildirimler, dikey hareketin etkisizliği, yakınlaştırmanın değişmemesi.
  - `V20BrushPanelTest`:
    - 300 px yazılır, 999 uygulanamaz;
    - aralık yalnız damgalı fırçada ve ize kaydediliyor;
    - sabitleme titremeyi azaltıyor;
    - ayarlar fırça başına.
  - `V20Brush3DPanelTest`: Metal, ışık 90° ve gölge 0 ize kaydediliyor ve hatırlanıyor; sıfırlama çalışıyor.
  - `V20TubeShotTest` ve `V20ShotsTest`: ekran görüntüleri.
- Güncellenen testler:
  - `CanvasFlowTest`, `EditorElementCountTest`, `EditorEraserImageTest`: boyut kaydırıcısı artık üstel.
  - `StrokeStreamTest`: canlı benzetim gölgeyi de birleştiriyor. 3B fırçada ortalama fark 0,016.
  - `V18PenTest`.
- Tam koşu: 327 testin tamamı geçti (uygulama 130 sınıf / 220 test; core 107 test). Lint'te hata yok. `assembleDebug` başarılı (minSdk 21, targetSdk 36).

## Bilinmesi gerekenler

- Kalem eğimi ve uç yönü ayarları gerçek bir tablet kalemiyle denenmedi; testler taklit edilmiş olaylarla yapıldı.
- v20 ile yeni ayar ya da kalem yönü içeren izler codec v6 ile yazılır. Eski uygulama sürümleri bu tuvalleri açamaz.

## PC'ye yazarken

- Stage: `outputs/v20src/android/`. v19'a göre 36 dosya (9 yeni, 27 değişen). Silinecek dosya yok.
- Bilgisayardaki proje v18'de; v19 ve v20 yalnız stage'de. PC'ye yazılırken önce `v19src`, sonra `v20src` kopyalanmalı (ya da v18 → v20 birleşik küme çıkarılır).
