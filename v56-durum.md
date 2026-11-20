# v56 durum

Son güncelleme: 2026-11-20, K1 durağı.

| Durak | Durum | Not |
|---|---|---|
| Aşama 0A (ödüllü reklam, madde 23) | Bitti | Android + iOS |
| K0 (aşama 0: canlı yayın) | Bitti | Android + iOS |
| **K1** (V11 model, katman motoru, seçim maskesi, kes/kopyala/yapıştır, fark paketi) | **Bitti, "devam" bekleniyor** | Android + iOS |
| K2 (hızlı fırça seçici, renk butonu, damlalık + büyüteç, Dönüştür paneli…) | Başlamadı | |
| K3 (renk ayarları, HSL/Color Balance, katman çoğalt/birleştir, SVG/PSD/DXF, e-posta girişi, mağaza flavor'ları, sunucu 56.0.0) | Başlamadı | |
| K4–K6 (3B Çizim) | Başlamadı | |
| K7 (eğitim, metinler, tam test, imzalı derlemeler) | Başlamadı | |

## K1'de yapılanlar

- **Aşama 1, V11 içerik:** katmana opaklık, 17 karışım modu, alfa kilidi, alttakine kırpma; çizgiye kırpma maskesi,
  simetri, resim dönüşümü (dörtgen/ağ/dalga), gren ve kement dolgu alanları. Eski sürüme paylaşırken içerik
  düzleştirilir ve uyarı çıkar (`V11Flatten`). Yeni özellikler `V56Flags` ile açılıp kapanır. Android ile iOS aynı baytları
  üretir (test var).
- **Aşama 2, katman birleştirme:** opaklık, karışım modları, alttakine kırpma ve alfa kilidi çizimde uygulanır; katman
  önbelleği yalnız değişen katmanda yenilenir; çizilmekte olan çizgi de katmanın ayarıyla görünür; katman panelinde
  ayarlar.
- **Aşama 3, seçim:** tümünü seç, seçimi ters çevir, gruba çift dokunup içine girme, sihirli değnek (renk / kontur /
  görünüm, tolerans çubuğu), seçim alanı (maske: ters, yumuşatma, genişlet/daralt; alan varken yeni çizgiler yalnız
  içine boyanır), kement dolgu, kırpma maskesi yap/kaldır.
- **Aşama 3A, kes/kopyala/yapıştır:** uygulama içi pano sayfalar ve tuvaller arasında çalışır. Yapıştırılan öğeler yeni
  kimlik alır; gruplar ve kırpma maskesi korunur; resim varlıkları kopyalanır (pano, kopyalama anındaki resmi saklar,
  kaynak tuval silinse de yapıştırılır). Aynı yere üst üste yapıştırma 16 px kaydırır; boş yere uzun basınca oraya
  yapıştırır. Çocuk modunda kapalı.
- **Aşama 3B, canlı yayın fark paketi:** yayıncı artık her karede bütün tuvali değil, yalnız değişen sayfanın değişen
  çizgilerini gönderir (`LIVE_DIFF`, tür 16). 20 karede bir, sayfa ya da ayar değişince tam paket gider. Alan taraf bir
  kare kaçırırsa `LIVE_RESYNC` (tür 17) ister. Alan taraf farkı bellekte uygular, 2 sn'de bir diske yazar, ayrılınca
  hemen yazar. Fark yalnız içerik sürümü 11 olan cihazlara gider; eskilere tam paket sürer.

## Testler (K1)

- Android: tam birim test takımı çalışıyor (sonuç K1 raporunda). Yeni: `V56ModelTest`, `V56LayerCompositeTest`, `V56SelectionTest`,
  `V56ClipboardTest` 5/5, `V56LiveDiffTest` 2/2, `ShareProtocolTest` 4/4. Derleme hatasız.
- iOS: aynı adlı Swift testleri yazıldı (`V56ModelTests`, `V56LayerTests`, `V56SelectionTests`, `V56ClipboardTests`,
  `V56LiveDiffTests`). Buradaki ortamda Swift derleyicisi yok; duraklarda CI çalıştırılmadı (Ahmet'in kararı).

## K0'da yapılanlar

- **Ödüllü reklam (alıştırmalar):** tüm adımları kapsayan 25 sn süre sınırı (eskiden başlatma ve abonelik sorgusu
  takılınca dönen simge kalıyordu), liste açılınca ön yükleme, envanter yoksa bir kez yeniden deneme, yine yoksa
  alıştırma açılır ve bilgi gösterilir (NO_FILL). iOS'ta reklam artık kapanmakta olan kilit penceresinin üstüne sunulmaya
  çalışılmıyor.
- **Canlı yayın ara-verme hatası:** her düzenleyici sahip kimliğiyle kaydolur; kapanan eski düzenleyicinin geç gelen
  "ara ver"i yeni düzenleyicinin yayınını artık durdurmaz. Fotoğraf düzenleyici, geçici düzenleme ve arka plan araları
  sahibine bağlı.
- **Yayın onayı önce, şifre sonra:** canlı yayın isteği karşı cihazda her zaman önce sorulur; eşleşme şifresi ancak
  kabulden sonra üretilip gösterilir (eşleşmiş cihazda şifre yok, onay yine sorulur). 60 sn yanıt yoksa geri çevrilir;
  1 dakikada 3 ret 5 dakika engeller; bağlanırken verilen onay teklifte yeniden sorulmaz. "Yayından önce sor" anahtarı
  kalktı, yerine bilgi satırı geldi. Bağlanan cihaz ret nedenini görür (reddedildi / yanıt yok / engellendi).
- **Canlı mürekkep (LIVE_INK, tür 15):** çizilmekte olan çizgi ~60 ms'de bir küçük paketle gider, alan tarafta
  tuvalin üstünde çizilir; kare gelince ya da ara verilince silinir. Eski sürümler bilinmeyen türü yok sayar.
- **Alan taraf:** canlı tuvalde iki parmakla yakınlaştırma/kaydırma, çift dokunuşla sıfırlama; ara 5 sn'yi geçince
  "Yayıncı başka bir ekranda" yazısı.

## K0 testleri

- Android: `V56LiveShareTest` 9/9 geçti (ara-ver sahipleri, şifre kapısı, onay kuralları, mürekkep gidiş-dönüş ve
  birleştirme). Derleme hatasız.
- iOS: `V56Tests` (aynı testler + Android JSON çözümü). CI #145: Release derleme başarılı, testler iptal (duraklarda derleme yok).

## Açık konular

- Ödüllü reklamda AdMob tarafı: uygulamanın AdMob'da "onaylı" görünmesi ve app-ads.txt kontrolü Ahmet'te.
- Yapıştırınca Dönüştür paneli açılmıyor; panel K2'de gelecek.
- Seçim alanı varken çizilmekte olan çizginin önizlemesi maskelenmiyor (bırakınca maskelenir).
- Tuval küçük resimleri katman karışım modlarını henüz yansıtmıyor.
- `DocExtras` (belge düzeyi simetri vb.) modelde var, düzenleyiciye K2/K3'te bağlanacak.
