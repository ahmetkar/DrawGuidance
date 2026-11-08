# DrawGuidance v38 — durum

_2026-10-20 · Room **v15** · minSdk 21 – targetSdk 36 · katalog **38000** · sunucu **38.0.0**, kapı 4747 · 7 dil_

## İstekler ve yapılanlar

### 1. Resim tuvalinde Karakterler yok

- Ekle menüsündeki **Karakterler** düğmesi ve penceresi artık yalnız çizgi roman tuvalinde. Resim ve not tuvallerinde görünmüyor.

### 2. Yatay ekranda düğme adları

- Ekran yatayken (genişlik > yükseklik) üst menüdeki her düğmenin adı simgesinin altında **dokunmadan** yazıyor.
  - **Profesyonel mod:** en üstteki menünün bütün düğmeleri (Çiz satırı ve Seç, Ekle, Düzenle gibi moda özel satırlar), ⋮ (**Diğer**) ve **Resmi kaydet**.
  - **Basit ve çocuk modları:** bütün düğmeler. Seçili araç hap yerine çökük ve vurgu renginde, adı altında.
- Uzun adların parantez içi atılıyor ("Kalem aracı (yol)" → "Kalem aracı"). ⋮ ve kaydet için kısa adlar eklendi (7 dilde).
- Menü kalan yere sığmazsa yana kayıyor (kenarda solma). ⋮ ve kaydet her zaman ekranda kalıyor.
- Dikey ekranda eskisi gibi: yalnız seçili ya da dokunulan düğmenin adı.
- Kod: `LocalButtonLabels` ve `ButtonLabel` (`ui/components/NeuComponents.kt`), `SimpleToolRow` (`EditorToolbar.kt`).

### 3. Yapay zekâ önerileri sıfırdan

Her modda üç ayrı öneri var: **yazı, şekil ve çizgi**. Her birinin ayrı algoritması, ayrı balonu, ayrı aç/kapa ayarı ve **tek düzelt düğmesi** var. Düğme, dikkate alınan bütün çizgileri birlikte düzeltiyor ve tek adımda geri alınıyor. Kurallar `SuggestRules` içinde toplandı.

- **Tek çizgiye öneri yok.** Öneriler yalnız birbirine yakın birden çok çizgi için çıkıyor.
- **Yazı (sinir ağı):** en az **2** yakın kalem çizgisi, aynı satırda. Önce hızlı bir eleme, sonra `HandwritingNet` üzerinde yeni "bu yazı mı?" kapısı, sonra tanıyıcı. Yazı sayılan çizgilere şekil ve çizgi önerisi yapılmıyor.
- **Şekil (sinir ağı):** en az **4** yakın öğe. Her modun kendi şekil ağı ve şablon veritabanı kullanılıyor; uzak çizgiler gruplanmıyor.
- **Çizgi (klasik makine öğrenmesi, sinir ağı yok):** en az 2 yakın çizgi.
  - Her çizgi `InkShapes` ile oturtuluyor; oturmazsa Gauss ile yumuşatılıyor.
  - Yeni `InkLines`: yakın uçlar kümelenip birleştiriliyor (iki düz kenar kesişme noktasında buluşuyor). Yaylar Kåsa en küçük kareler çember uydurmasıyla düzgün yaya dönüyor. Aynı çembere düşen birkaç yay ortak çembere oturuyor; iki yayla çizilen daire tek daire oluyor.
  - Düzeltilen ya da reddedilen çizgiler bir daha dikkate alınmıyor.
- **Seçimde:** en az 2 çizgi seçiliyken üç öneri de çalışıyor. Tek seçili çizgiye "düzeltme önerisi" artık yok.
- **Çocuk maskotu:** öncelik şablon, sonra yazı, sonra çizgi.
- **Kaldırılanlar:** parça önerileri, tek çizgi şekil düzeltmesi, "hepsini düzelt" (`SelectionSuggester` silindi).

**Yeniden eğitim** (eğitim verisi öncekiyle aynı yoldan üretiliyor):

- **Şekil ağları (4 mod):** gizli katman 256 → 320, v37 ağlarından başlanarak. 4 ve daha çok öğeli çizimlerle, sınıf başına 60 örnek, 14 tur, küme 64, Adam (öğrenme hızı 1.5e-3 çıkış / 6e-4 gizli), etiket yumuşatma 0.05, L2 1e-5.

  | Mod | Ağ ilk tahmin (v37 → v38) | Tam arama ilk tahmin (v37 → v38) |
  |---|---|---|
  | Resim | %70.7 → %95.1 | %68.0 → %75.4 |
  | Not | %67.3 → %95.4 | %67.0 → %73.9 |
  | Çizgi roman | %69.0 → %93.6 | %67.1 → %74.5 |
  | Çocuk | %77.6 → %93.4 | %74.1 → %80.4 |

- **Yazı kapısı:** donmuş el yazısı katmanının üstünde iki doğrusal baş, Adam 3e-3, 60 tur. Ayrılmış veride doğruluk %96.8, yazıyı yakalama %95.3, çizimi yazı sanma %1.0. Tam yazı önerisinde çizimi yazı sanma **%27.8 → %0.2**; yazıyı yakalama %88.8 → %84.0.
- Katalog sürümü **38000**, `HandwritingNet.VERSION` 2. Sunucunun hazır katalog dosyası (`catalog.json.gz`) yeniden üretildi.

### 4, 5 ve 5.1. Çocuk modunda Çizim Görevi

- Çocuk tuvalinin menüsünde **Çizim Görevi** düğmesi. Mod kapalıyken **Başlat / İlerleme**, açıkken **Modu kapat / İlerleme** seçenekleri çıkıyor.
- **15 seviye**, kolaydan zora, uygulamanın kendi çocuk şekilleriyle:
  - **Üstünden çiz** (kesik çizginin üstünden): güneş, elma, uçurtma, dondurma, balık, şemsiye, kedi, kelebek.
  - **Boya** (şekil kâğıtta, kovayla boyanıyor): ev, balonlar, mantar, roket.
  - **Yerleştir ve boya** (Şekil ekle'den koy, kesik çizgiye taşı, boya): çiçek, araba, şato.
  - Üstte küçük hedef resmi, "Seviye n / 15", ad ve yeşil **Bitti** düğmesi. Renk noktaları hangi rengin nereye gideceğini gösteriyor; doğru boyanınca yeşil tik alıyor.
- **Boyacık ekranın kenarında**, puana göre yüz ifadesiyle: seviyeyi tanıtıyor, ampul düğmesiyle o seviyenin araçları için ipucu veriyor (fırça, silgi, geri al, kova, 2 sn basılı tutma, Şekil ekle, taşıma, "Bitti'ye dokun"). Geçecek kadar yaklaşınca "Bitti'ye dokun" diyor.
- **Puan (0–100):** çizim hedefle nokta nokta karşılaştırılıyor. Başka yerde ya da boyda doğru çizim %95 sayılıyor; karalama cezalı. Boyamada her alanın rengi ölçülüyor. **60** puan geçer; **60 / 75 / 90** için 1 / 2 / 3 yıldız. Değerlendirme Bitti'ye basınca yapılıyor.
- **Sonuç ekranları:** başarıda puan, yıldız, **Sonraki seviye** ve **Moddan çık**. Başarısızlıkta cesaretlendirme, puan, yıldız, **Tekrar dene** ve **Moddan çık**.
- **İlerleme ekranı:** 15 seviye ızgarada; geçilenler yıldız ve yeşil tikle, açık olan "Oyna" ile, kilitliler kilit simgesiyle işaretli. Açık seviyeye dokununca mod başlıyor ya da sürüyor, tuval temizleniyor (geri alınabilir) ve o seviye kuruluyor. Kilitli seviyede hangi seviyenin geçilmesi gerektiği söyleniyor.
- En iyi puanlar `KidsPrefs` içinde saklanıyor, uygulama kapanınca kaybolmuyor.
- Kod: `ui/screens/canvasedit/challenge/` (5 dosya).

## Doğrulama

- **Uygulama testleri:** 514 test. 512'si ilk turda geçti; `V9VectorShapesTest` içindeki 2 test, test ortamının sıfırlanmasından kaynaklanan geçici bir hatayla düştü ve yeniden çalıştırılınca 6/6 geçti. 4 test yalnız elle eğitim komutuyla çalışan testler olduğu için atlandı (eskiden de böyle).
- **Çekirdek testleri:** içerik modülü 251 (20'si yalnız eğitimde çalışan, atlandı), model modülü 34; hepsi geçti.
- **Sunucu testleri:** 58/58 geçti. Uygulamanın sunucuya bağlanan testleri yerel sunucu (38.0.0, MongoDB) açıkken geçti.
- **Çeviriler:** 2249 dize 6 dilde eksiksiz, yer tutucular uyumlu.
- **APK:** `DrawGuidance-v38-debug.apk` derlendi ve imzası doğrulandı.
- Ekran görüntüleriyle denetlendi: yatay telefon ve tablet üst menüsü, basit ve çocuk modu, çizim görevi ekranları.

## Kalan eksikler ve öneriler

- Korece tek heceli kelimeler yazı kapısından yalnız yaklaşık %26 oranında geçiyor.
- Şekil tam aramasının ilk tahmini %74–80; ağın kendisi %93–95.
- Birleştirilen iki yayın buluşma noktası çemberden biraz sapabiliyor (test %6 pay tanıyor).
- 360×640 telefonda çizim görevinin giriş balonu, ilk çizgiye kadar hedefin alt yarısını örtüyor.
- Lint bu ortamda çalışmadı (lint aracı indirilemiyor); yerine çeviri betiği bütün dizeleri 7 dilde denetledi.

## Dosyalar

- `DrawGuidance-v38-debug.apk`, bu rapor.
- Yeni: `ui/screens/canvasedit/challenge/*`, `state/LineSuggester.kt`, `components/LineSuggestionChip.kt`, `content/shapes/SuggestRules.kt`, `content/ink/InkLines.kt`, `strings_v38_labels.xml`, `strings_v38_kids.xml`, `strings_v38_ai.xml` (7 dilde).
- Testler: `V38LandscapeLabelsTest`, `V38KidsChallengeTest`, `V38AiLogicTest`, `V38AiUiTest`, `ModeNetsV38Test`, `TextGateTest`, `InkLinesTest`; güncellenen eski testler yeni kurallara uyarlandı (hiçbiri silinmedi ya da atlanmadı).
