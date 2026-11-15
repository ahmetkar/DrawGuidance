# DrawGuidance v52 devir notu

**Durum:** bütün maddeler bitti; teslim dosyaları `/mnt/project-files/drawguidance/v52/` içinde.
**Dal:** `claude/project-thread-db34s3` (GitHub `ahmetkar/DrawGuidance`), v51 `ios/v51-entegrasyon` üstüne.
**Sürüm:** versionCode 52 / 1.10.0 (Android `build.gradle.kts` ve iOS `project.yml` yükseltildi).
**Zip tabanı:** `16bbbc2` (Android v49 kaynakları). Zip, o günden bu yana eklenen/değişen bütün dosyaları içerir; PC kök klasörüne üzerine yazarak açılır. Silinen 2 dosya `SILINEN-DOSYALAR.txt` içinde listelenir.

## Biten maddeler (Android + iOS)
1–11. Önceki oturumlarda bitti (ayrıntılar git geçmişinde).
15. Not almada "Şekli düzenle" gelişmiş çizimde açılır; o sırada yayına ara verilir.
16. 3B düzenleme eksen rayı.
17. Canlı yayın yalnız çizim / not / çizgi roman tuvallerinde (çocuk modunda yok).
18. Paylaş ve Canlı yayın, çizim sayfasının "Diğer" menüsünde ve Ayarlar'da.
19. Not almada ayrı **Grafik ekle** düğmesi ve penceresi; yeni **Tablo ekle**: satır/sütun sayısı, başlık satırı/sütunu, zebra, kenarlık türleri, 6 hazır tema; tablo düzenleme penceresinde her hücre, her satır, her sütun ve bütün tablo için renk, yazı rengi, kalın, hizalama, genişlik/yükseklik, satır/sütun ekleme-silme. Tablo tek şekil olarak saklanır (iki platform arasında aynı biçim).
21. Düzenleyiciden başka ekrana geçince (ya da uygulama arka plana alınınca) yayına ara verilir, dönünce sürer. Çizim ekranında küçük yayın bilgi çubuğu: canlı/ara verildi, alıcı cihaz, geçen süre, tuval adı, durdur.

## Biten maddeler (devam)
20. **Foto düzenleme modu**
   - Android: bitti. Tam ekran düzenleyici, 7 menü: Kırp ve döndür (90° döndürme, çevirme, eğim düzeltme, oranlı kırpma çerçevesi), Işık (pozlama, vurgular, gölgeler, beyazlar, siyahlar, netlik, parlaklık, karşıtlık), Renk (doygunluk, canlılık, sıcaklık, ton, solukluk), Filtreler (16 görünüm), Efektler (çizgi roman filtreleri, bulanıklık, keskinlik, vinyet, gren), Arka plan (otomatik kaldırma, dokunarak kaldır/geri getir), Rötuş fırçaları (aç, koyulaştır, cilt yumuşat, bulanıklaştır, boya, geri getir). Üstte geri al, yinele, önce/sonra (basılı tut), sıfırla, kaydet. Kaydedince resim geldiği yere konur (oran değiştiyse çerçeve uyar). Not almada ve çizgi romanda resim düzenlenirken "Foto düzenlemede / Gelişmiş çizimde / (çizgi romanda) Burada düzenle" seçilir; gelişmiş/basit çizimde Resmi düzenle satırında "Foto düzenle" düğmesi. Foto düzenleme açıkken yayına ara verilir.
   - iOS: aynı özellikler Swift ile (PhotoEdit.swift aynı hesap sırası, PhotoEditorView tam ekran, düzenleme yeri seçimi, Resmi düzenle satırında ve basit menüde "Foto düzenle"). iOS'ta daha önce resim filtre/efekt/arka plan pencereleri yoktu; bunlar artık foto düzenlemenin menülerinde.
14. Ödüllü reklam: kod düzeltmesi bitti (aşağıdaki açıklama).

12. Not alma eğitimlerine **"Bilim şekilleri ve hesaplama"** grubu (Android + iOS): formülden molekül (H2O → CO2 → CH4 → etanol), atom numarasından Bohr atomu (6 → 8 → 11), hız ve açıdan eğik atış, formül yuvalarına değer yazınca sonucun eklenmesi (daire alanı r = 3, F = m·a) ve değer değişince sonucun yenilenmesi.

13. **Mağaza görselleri** (`magaza/DrawGuidance-v52-magaza-gorselleri.zip`): 5 sahne × 7 dil × 6 boyut = 210 PNG (Google Play telefon / 7" / 10" tablet; App Store iPhone 6.9" / 6.5" / iPad 13"). Sahneler: formülden bilim şekilleri, adım adım alıştırma, çocuk görevi, yakındaki cihaza paylaşım, iki cihaz arası canlı yayın (yayın yapan ve izleyen yan yana). Ham görüntüler `ham/` içinde. Üretim: `V52StoreShotsTest` (Robolectric, `DG_SHOTS`, `DG_LANG`) → `tools/store-shots/compose.py` (`DG_SETS=v52`).
- Ek düzeltme: paylaşım ekranındaki bağlantı/aktarım hata metinleri her dilde Türkçe çıkıyordu (v51'den); artık 7 dilde.

## Teslim dosyaları
- `DrawGuidance-v52.apk` (test / doğrudan kurulum, upload anahtarıyla imzalı, R8) ve `DrawGuidance-v52.aab` (Play Console), `DrawGuidance-v52-mapping.txt.gz`.
- `DrawGuidance-v52-ios-unsigned.ipa` (Device Farm, 1.10.0 / 52; CI çalıştırması 108).
- İmzalı iOS .ipa için Apple Distribution sertifikası (.p12 + parolası) ve App Store provisioning profili gerekir; gelince CI'a gizli değişken olarak eklenip imzalı derleme alınır.
- `DrawGuidance-v52-degisen-dosyalar.zip`: v49'dan (`16bbbc2`) bu yana değişen bütün dosyalar.

## Ödüllü reklam neden bazen gelmiyordu
1. Yükleme bayrağı takılı kalıyordu: bir istek yarıda kalınca sonraki istekler hemen "başarısız" dönüyordu. Düzeltildi (zaman aşımı + bayrak sıfırlama).
2. Debug derlemeler Google test kimliklerini kullanır; gerçek reklam yalnız release derlemede gelir.
3. Yeni reklam birimleri ilk saatlerde (bazen 1–2 gün) reklam vermez; uygulama AdMob'da onaylı olmalı ve `app-ads.txt` siteye konmalı (drawguidance.ahmetkar.com/app-ads.txt).
4. UMP onay penceresinde izin verilmezse kişiselleştirilmemiş reklam da gelmeyebilir.
5. Çocuk kullanıcılara hiç reklam gösterilmez (bilerek).
6. Hata kodları artık kayda yazılır: 3 = doldurulamadı (no fill), 2 = ağ, 1 = geçersiz istek.

## Gizli bilgiler
Anahtarlar, keystore ve hizmet hesapları depoya konmadı; PC'de `yayin-anahtari\` klasöründe kalır.
