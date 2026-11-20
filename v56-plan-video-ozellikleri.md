# v56 planı — üç videodan çıkan, uygulamada olmayan özellikler

_2026-11-17. Yalnız plan, kod değişikliği yok. Android ve iOS birlikte. Taban: `claude/v55`._

## 1. Videoların analizi (sırasıyla)

### Video 1 (`downloaded-file_2.mp4`, 34 sn): seçimle şekil boyama, sert ve yumuşak kenar

Bilgisayar kalem tableti, Photoshop benzeri bir program. Çizer yaptığını anlatıyor; aşağıdaki özet ekrandaki altyazılardan (ses dinlenmedi).

- **Teknik:** kement (lasso) ile yüz, saç ve gölge gibi bir şekil seçiliyor. Seçimin içi grenli, dokulu bir yumuşak
  fırçayla (airbrush) boyanıyor.
  - Seçimin kenarı boya için **sert ve net** bir sınır oluyor.
  - Fırçanın seçim içindeki kenarı **yumuşak ve solgun** kalıyor.
  - Sonuç: bir yanı keskin, öbür yanı yumuşak gölge ve ışık şekilleri.
- **Diğer:** seçim kenarında yürüyen karıncalar (kesik çizgi) görünüyor. Seçim tersine çevrilip dış taraf da
  boyanıyor. Fırçada belirgin bir gren dokusu var.
- Altyazıdaki anlatım: "derinliği, şekilleri seçip içini doldurarak veriyorum; bu bana bir tarafta çok net, öbür tarafta yumuşak ve
  solgun kenarlar veriyor; sert ve yumuşak kenarı karıştıran bir stil."

### Video 2 (`downloaded-file_1.mp4`, 43 sn): eskizden su altı yansımasına

Photoshop; altyazısız. Akış (ekran görüntülerinden çıkarıldı):

1. Kurşun kalem eskiz.
2. Dikdörtgen seçimle arka plan seçimi ve renk seçiciden turkuaz dolgu.
3. Figürlerin altına düz, tek renk siluet dolgusu (eskiz üstte kalıyor).
4. Seçili bölgeye **Renk Dengesi** (Color Balance) ayarı. Pencerede gölge, orta ton ve parlak kaydırıcıları var.
5. Yanaklara yumuşak, parlayan pembe ışık (glow). Görüntüden, muhtemelen Ekran ya da Ekle karışım modlu bir katman.
6. Kement ile saç tutamları seçiliyor, içi boyanıyor (video 1'deki teknik).
7. Figürün **kopyası çevrilip yansıma** yapılıyor. Yansıma **dalga, sıvılaştır ve bulaştır** ile bozuluyor; su
   yüzeyinde dalgalı bir görüntü çıkıyor.
8. Son ışık ve parıltı dokunuşları.

### Video 3 (`downloaded-file.mp4`, 51 sn): dokulardan tren sahnesi (Procreate, iPad)

1. **Doku bloğu:** resim tabanlı bir damga fırçasıyla kâğıda kolaj dokusu basılıyor ("add a block of texture").
2. Dokudan yavaş yavaş tren şekilleri oyuluyor: pencereler, kapılar.
3. **Çizim kılavuzu** (ızgara) açılıyor. Zikzak bir çizgi çiziliyor, **çoğaltılıp yatay çevriliyor**. Böylece pencere
   ızgarası deseni çıkıyor ve bu desen pencerelere yerleştiriliyor.
4. **Yeni katmanı dokuyla doldur:** zemin için.
5. **Bozma ve perspektif dönüşümü** ("distort it down below"): dokulu zemin, köşelerinden çekilerek perspektife
   sokuluyor.
6. Ayrıntılar: yazılar, bisiklet, kovalar, kalabalık siluetleri. Ardından karakter eskizi.
7. **Işık katmanı:** turuncu bir ışık hüzmesi karışım moduyla (Ekle / Kaplama) sahnenin üstüne konuyor ve sıcak
   gün batımı ışığı çıkıyor.

## 2. Uygulamamızda zaten olanlar

- Fırçalar:
  - Airbrush ve yumuşak yuvarlak.
  - Doku fırçaları (sünger, tuval, sıçratma).
  - Damga fırçaları (yıldız, kalp, yaprak).
  - Desen fırçaları ve yarım ton fırçaları.
  - v51'de bulanıklaştır, keskinleştir, aç, yak, sünger, klonla, iyileştir, karıştır, akrilik, yağlı boya, bulut.
- **Katmanlar:** görünürlük ve kilit var. Opaklık ve karışım modu yok.
- **Seçim:** dikdörtgen ve kement seçimi var, ama yalnız **elemanları** seçiyor (taşı, kopyala, sil). Seçim boyamayı
  sınırlamıyor.
- **Kılavuzlar ve tuval:** ızgara ve perspektif kılavuzu, gradyan ve desen arka plan, alan boyama (kova).
- **Fotoğraf düzenleme modu (v52):** ışık, renk, 16 filtre, efektler. Yalnız **eklenen resimlere** uygulanıyor, çizim
  katmanlarına değil.
- **Diğer:** çevirme ve döndürme (elemanlarda); efektler ve odak çizgileri (çizgi roman).

## 3. Eksik özellikler ve plan

Öncelik: **A** yüksek (videolardaki tekniğin özü), **B** orta, **C** sonraki sürüm.

| # | Özellik | Video | Öncelik | İş |
|---|---|---|---|---|
| 1 | Seçim maskesiyle boyama ("seçimin içine boya") | 1, 2 | A | Orta |
| 2 | Kement dolgu aracı (çizilen kapalı şekli anında doldur) | 1, 2, 3 | A | Küçük |
| 3 | Katman opaklığı ve karışım modları | 2, 3 | A | Orta |
| 4 | Alfa kilidi ve kırpma maskesi | 2, 3 | A | Orta |
| 5 | Seçimi ters çevir, yumuşat (feather), genişlet/daralt | 1 | B | Küçük |
| 6 | Grenli ve resim tabanlı doku fırçaları, "katmanı dokuyla doldur" | 1, 3 | B | Orta |
| 7 | Çizim katmanına ayarlar (Renk Dengesi, Ton/Doygunluk, Eğriler), seçimle sınırlı | 2 | B | Orta |
| 8 | Bozma, perspektif ve çarpıtma dönüşümü (köşeden çek) | 3 | B | Orta |
| 9 | Yansıma aracı (çoğalt, çevir, dalga ile boz) ve sıvılaştır | 2 | C | Büyük |
| 10 | Işık ve parıltı katmanı hazır ayarları | 2, 3 | B | Küçük (3 ve 4'ün üstüne) |
| 11 | Simetri ve tekrar desen kılavuzu (çiz, çoğalt, çevir, döşe) | 3 | C | Orta |

### A1. Seçim maskesiyle boyama

- **Ne:** Kement ya da dikdörtgen seçim varken fırça yalnız seçimin içine boyar. Seçim kenarı sert, fırça kenarı
  yumuşak kalır (video 1'in tekniği).
- **Arayüz:**
  - Seçim aracında yeni düğme: **"İçine boya"**. Seçim durur, kalem fırçaya geçer, yürüyen karıncalar görünür kalır.
  - Seçim menüsünde **"Seçimi kaldır"**.
- **Model:** `Stroke`'a isteğe bağlı `clipPath` alanı (seçimin çokgeni, tuval koordinatlarında). Çizim sırasında ve
  kaydedilen çizgide `canvas.clipPath` (Android) / `context.clip(to:)` (iOS) kullanılır.
  - `ContentCodec` sürüm yükseltmesi gerekir: V10 → V11, eski belgeler etkilenmez.
  - Vektör yapı bozulmaz: kırpma yolu çizginin parçası olarak saklanır, sonradan düzenlenebilir.
- **Testler:**
  - Seçim dışına taşan çizginin pikselleri boş kalır.
  - Kayıt ve yükleme kırpmayı korur.
  - Paylaşım ve dışa aktarma (PNG, PDF, SVG `clipPath`) kırpmayı içerir.

### A2. Kement dolgu aracı

- **Ne:** Kalemle kapalı bir şekil çizilir, bırakınca içi seçili renkle dolar (Procreate'teki "kement dolgu").
  - Video 2'deki siluet dolgusu ve video 3'teki tren şekillerini oyma için en hızlı yol.
- **Uygulama:** Mevcut kalem/yol aracının "dolgu" seçeneğiyle aynı vektör dolgu yolu; çizgisiz, yalnız dolgu. Fırçalar
  listesinde "Kement dolgu" ve Özellikler'de opaklık.
- İsteğe bağlı: dolgu dokulu olabilir (A1 ve B6 ile birlikte).

### A3. Katman opaklığı ve karışım modları

- **Model:** `Layer`'a `opacity: Float = 1f` ve `blend: LayerBlend = NORMAL` alanları.
  - Modlar: Normal, Çoğalt, Ekran, Ekle (Doğrusal Sulandır), Kaplama, Yumuşak Işık, Renk, Parlaklık.
  - Room şeması değişmez; katmanlar belge içeriğinde (`ContentCodec`).
- **Çizim:** Her katman ayrı bir ekran dışı tampona çizilir ve karışım moduyla birleştirilir.
  - Android: `saveLayer` + `Paint.blendMode`.
  - iOS: `CGContext.setBlendMode`.
  - Performans için yalnız Normal dışındaki katmanlar tamponlanır.
- **Arayüz:** Katmanlar panelinde her satıra opaklık kaydırıcısı ve mod seçici.
- **Testler:** her modun piksel sonucu (bilinen renklerle), dışa aktarmada mod korunur, eski belgeler Normal açılır.

### A4. Alfa kilidi ve kırpma maskesi

- **Alfa kilidi:** Katman kilitliyken yalnız dolu piksellerin üstüne boyanır. Siluetin içine renk ve gölge vermek için
  (video 2, adım 3–5). Uygulama: `SrcAtop` karışımı.
- **Kırpma maskesi:** Katman, altındaki katmanın şekline kırpılır. Işık katmanını yalnız figüre uygulamak için.
- **Arayüz:** Katman satırında iki simge: alfa kilidi ve "alttakine kırp".

### B5. Seçim işlemleri

- Ters çevir, tümünü seç, yumuşat (0–50 px), genişlet/daralt.
- Yumuşak kenarlı seçim A1'de kırpma yerine maske alfa'sı olarak kullanılır.

### B6. Doku fırçaları ve doku dolgusu

- **Gren seçeneği:** Fırçalara **gren (grain)** seçeneği: dokunun ölçeği, gücü ve "kaydırma ya da sabit"
  ayarı. Video 1'deki grenli airbrush. Mevcut `BrushTextures` genişletilir.
- **Resimden fırça:** Kullanıcının seçtiği bir resim damga olarak kullanılır (video 3'teki kolaj dokusu). Resim v55
  güvenlik denetiminden geçer ve 512 px'e küçültülür.
- **"Katmanı dokuyla doldur":** Seçili doku ya da resim, katmanı ya da seçimi döşeyerek doldurur.
- **Hazır doku paketi:** 10–15 özgün doku (kâğıt, beton, kumaş, kolaj). Mevcut paket sistemiyle (`dgpackets`) sunucudan
  da dağıtılabilir.

### B7. Çizim katmanlarına renk ayarları

- v52 fotoğraf düzenleyicisindeki ışık ve renk ayarları (Renk Dengesi, Ton/Doygunluk/Parlaklık, Eğriler) çizim
  katmanına ya da seçime uygulanabilsin.
- **İlk sürüm:** Katman resme dönüştürülerek ayar uygulanır (yıkıcı, geri alınabilir).
- **Sonraki sürüm:** Düzenlenebilir **ayar katmanı**.

### B8. Bozma, perspektif ve çarpıtma dönüşümü

- Seçili elemanlar ya da katman için dönüşüm kutusunda üç kip:
  - **Serbest** (mevcut),
  - **Bozma:** dört köşe ayrı ayrı sürüklenir; perspektif homografisi,
  - **Çarpıtma:** 3×3 ağ.
- Vektör çizgilerin noktalarına matris uygulanır, resimlere `Matrix.setPolyToPoly` (Android) /
  `CIPerspectiveTransform` (iOS).
- Perspektif kılavuzu açıksa köşeler kaçış noktasına mıknatıslanabilir.

### C9. Yansıma ve sıvılaştırma

- **"Yansıma oluştur":** Seçimi çoğaltır, dikey çevirir, saydamlık gradyanı verir, isteğe bağlı dalga ya da dalgacık
  bozulması uygular (ayarlar: genlik, dalga boyu, bulanıklık).
- **Sıvılaştır fırçası:** it, döndür, büzüştür, şişir. Raster katmanda ağ deformasyonu; mevcut "karıştır" fırçasının
  devamı.

### B10. Işık ve parıltı hazır ayarları

- A3 ve A4'ün üstüne tek dokunuşluk hazır ayarlar:
  - "Işık katmanı ekle": Ekle modu, alttakine kırpılmış.
  - "Gölge katmanı ekle": Çoğalt modu, alttakine kırpılmış.
  - "Parıltı fırçası": yumuşak airbrush, Ekran modu.
- Eğitim modunda video 2 ve 3'teki akışın kısa bir örneği.

### C11. Simetri ve tekrar deseni

- **Simetri kılavuzu:** dikey, yatay, dörtlü, radyal (n kol). Çizim anında aynalanır.
- **"Desen yap":** Seçili elemanları yatay ya da dikey çoğaltıp döşer (video 3'teki pencere ızgarası).

## 4. Sıra ve sürümler

| Sürüm | İçerik |
|---|---|
| v56 | A1 seçimin içine boya, A2 kement dolgu, A3 katman opaklığı ve modları, A4 alfa kilidi ve kırpma maskesi, B5 seçim işlemleri, B10 ışık/gölge hazır ayarları |
| v57 | B6 gren ve doku fırçaları, B7 çizim katmanına renk ayarları, B8 bozma ve perspektif dönüşümü |
| v58 | C9 yansıma ve sıvılaştırma, C11 simetri ve desen |

**Free ve Premium önerisi:**

- **Free:** A1, A2, A3 (Normal, Çoğalt, Ekran), A4, B5.
- **Premium:** öteki karışım modları, B6 resimden fırça, B7 eğriler, B8 çarpıtma ağı, C9.

## 5. Teknik riskler

- **Performans:** Karışım modlu katmanlar her karede ekran dışı tampon ister. Büyük tuvalde ve düşük RAM'li cihazlarda
  (v55 `lowRam`) katman sayısı sınırlanmalı ya da tamponlar önbelleğe alınmalı.
- **Uyumluluk:** `ContentCodec` V11 ile eski uygulama yeni belgeyi açamaz.
  - Paylaşım ve canlı yayın protokolüne sürüm kontrolü eklenmeli.
  - Eski sürüme gönderilen belge düzleştirilmeli.
- **Dışa aktarma:** SVG ve PDF'te karışım modları ve kırpma yolları desteklenir (`mix-blend-mode`, `clipPath`). CMYK
  PDF'te karışım modu için raster yedek gerekebilir.
- **İki platform:** Her özellik Android (Compose Canvas) ve iOS (Core Graphics) için ayrı yazılır. Piksel testleri iki
  tarafta aynı sonuçları denetler.
- **Eğitim modu:** Her yeni araç için eğitim adımı eklenir; `TutorialScripts.VERSION` yükseltilir.
