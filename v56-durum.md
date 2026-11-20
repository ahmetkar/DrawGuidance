# v56 durum

Son güncelleme: 2026-11-20, K0 durağı.

| Durak | Durum | Not |
|---|---|---|
| Aşama 0A (ödüllü reklam, madde 23) | Bitti | Android + iOS |
| **K0** (aşama 0: canlı yayın) | **Bitti, "devam" bekleniyor** | Android + iOS |
| K1 (V11 model, katman motoru, seçim maskesi, fark paketi) | Başlamadı | |
| K2 (hızlı fırça seçici, renk butonu, damlalık + büyüteç, Dönüştür paneli…) | Başlamadı | |
| K3 (renk ayarları, HSL/Color Balance, katman çoğalt/birleştir, SVG/PSD/DXF, e-posta girişi, mağaza flavor'ları, sunucu 56.0.0) | Başlamadı | |
| K4–K6 (3B Çizim) | Başlamadı | |
| K7 (eğitim, metinler, tam test, imzalı derlemeler) | Başlamadı | |

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

## Testler

- Android: `V56LiveShareTest` 9/9 geçti (ara-ver sahipleri, şifre kapısı, onay kuralları, mürekkep gidiş-dönüş ve
  birleştirme). Derleme hatasız.
- iOS: `V56Tests` (aynı testler + Android JSON çözümü). CI #145: Release derleme başarılı, testler iptal (duraklarda derleme yok).

## Açık konular

- Ödüllü reklamda AdMob tarafı: uygulamanın AdMob'da "onaylı" görünmesi ve app-ads.txt kontrolü Ahmet'te.
- Canlı yayın fark paketi (aşama 3B) K1'de.
- `V56Flags` henüz yok; K1'de V11 modeliyle eklenecek.
