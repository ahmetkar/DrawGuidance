# DrawGuidance v44 — durum

_2026-10-26 · versionCode **44** (versionName 1.2.0) · Room **v23** · minSdk 23 – targetSdk 36 · sunucu **44.0.0** · 7 dil_

Ahmet'in v44 isteklerinin hepsi yapıldı: 1–10. maddeler. Değişen dosyalar `git reset --soft e7ce7d1` ile hazırlık
alanında (staged); commit yok.

## 1. Kalem aracı

- Kalem penceresindeki vektör düzenleme ayarları (nokta seçme, silme, eğri/düz, düzenle) kaldırıldı.
- Kalan tek düzenleme: iki nokta arasındaki çizginin ortasından tutup sürükleyerek o çizgiyi elle eğmek.
  - Çizgi parmağın geçtiği yerden geçer (ikinci derece eğri).
  - Noktaların yeri değişmez.
  - Noktaya yakın sürükleme çizgiyi tutmaz.
  - Boş yere dokunmak yine nokta ekler.
  - Geri alınan noktayla o çizginin eğimi de gider.

### 1.1–1.5 Adlar ve simgeler

Simgelerin hepsi uygulamada başka bir yerde kullanılmaz. Serbest seçimin kement simgesi uygulamaya özel çizildi.

| Yer | Ad | Simge |
|---|---|---|
| Seç menüsü (gelişmiş) | Dikdörtgen seçim | kesik çizgili seçim kutusu |
| Seç menüsü (gelişmiş) | Serbest seçim (eski adı "Kalemle alan") | kement |
| Basit modlar, üst menü | Dikdörtgenle seçim yap · Serbest seçim · Damlalık | aynı simgeler |
| Resim düzenle / Düzenle | Arkaplan ile Uyumla | ton dairesi |
| Resim düzenle / Düzenle | Nesneyi Mekâna Yerleştir | dağ ve zemin |
| Şekil kalıbı | — | beşgen kalıp |

- Basit modlarda Seç penceresinde bu araçlar artık yok.
- Çocuk modunun üst menüsünde dikdörtgen ve serbest seçim yok; damlalık var.

## 2–4. Premium ve Free

| Konu | v44 |
|---|---|
| 3B mod | GLB dışa aktarma ve başka uygulamalarla paylaşma **Premium** (yeni hak `model3d.export`; menüde kilit rozeti). |
| Çizgi roman karakterleri | Kovboy (Rıza), genç büyücü (Efe), samuraylar (Kenji, Aiko), elf okçu (Silvan) **Premium**. |
| Mekânlar | Terk edilmiş fabrika (çizgi roman + manga), yıkık bina merdiveni, kutup araştırma üssü (çizgi roman + manga), okul çatısı **Free**. |
| Nesneler | Bilim kurgu, askeri (silah/zırh) ve fantastik (büyü) kategorilerinden 4'er nesne **Free**. |
| Efektler | Portal ve büyü, enerji ve aura, atış ve namlu alevi kategorilerinden 2'şer efekt **Free**. |
| Çizgi roman şablonları | Hepsi **Free**. |
| Resim filtreleri | Manga filtresi **Free** (çizgi roman filtresi zaten Free). |
| Resim efektleri | Parıltı, yağlı boya, karakalem, kabartma, piksel, iki ton, afiş **Free**. Soğuk, sıcak, eski fotoğraf (Noir ve Canlı ile birlikte) **Premium**. |
| Resim ayarları | Bulanıklık, keskinlik ve solukluk **Free**. Kenar karartma ve gren Premium kaldı. |

## 5. İstisna hesabı (geçici)

`ahmetkar8040@gmail.com` abonelik ayarları dışında her şeye erişir: Premium'un bütün hakları ve reklamsız.

- **Uygulama.** Girişli ve e-postası doğrulanmış hesap bu adresse haklar açılır. Planı değiştirilmez; abonelik
  ekranı gerçek durumu gösterir.
- **Sunucu.** `/billing/me` ve `/billing/verify` yanıtlarında haklar Premium'un hepsi olur. Firebase jetonundaki
  **doğrulanmış** e-postaya bakılır.
  - Ayar: `BILLING_EXEMPT_EMAILS`.
  - Kaldırmak için değer boş verilir ve uygulamada `ExemptAccounts.EMAILS` boşaltılır.

## 6. Yerel sunucu kaldırıldı, yayına hazır

- **Uygulamadan kaldırılanlar:**
  - Sunucu seçimi kartı (uzak / yerel).
  - Yerel ağ taraması (`LanScanner`).
  - Yerel adres kaydı, "diğer sunucu ayarı" kısmı.
  - Bağlantı sınaması ve sunucu ayarları kodu.
- **Bağlantı sekmesi.** Ayarlar → Bağlantı'da yalnız eşitleme kartı kaldı.
- **Yayın derlemesi:**
  - Adres her zaman `https://drawapi.ahmetkar.com/`; kayıtlı hiçbir adres okunmaz.
  - Düz HTTP'ye hiçbir adres için izin yok (`network_security_config.xml`).
  - https olmayan bir hedefle uygulama açılmaz.
  - Eski `server_config` kaydı yedeğe girmez.
- **Hata ayıklama derlemesi.** Testler ve emülatör için `server_url` parametresi bellekte çalışır (diske yazılmaz).
- **Bilgisayardaki `yerel-sunucu` paketi** isteğin gereği olarak yerinde bırakıldı.
- **Yayın denetimi.** Bütün bulgular incelendi; düzeltme gerektiren bir şey çıkmadı:
  - Gradle'daki parola yalnız herkese açık hata ayıklama anahtarının parolası.
  - Yükleme anahtarı ve `release.properties` git'e girmez.
  - R8 ve kaynak küçültme açık.
  - Yedekleme kuralları tanımlı.
  - targetSdk 36.
  - Yalnız INTERNET, ACCESS_NETWORK_STATE ve POST_NOTIFICATIONS izinleri var.

### 6.1

Eşitleme kartının açıklaması (7 dilde): "Uygulamadaki çizim nesneleri uzak sunucumuzdan güncellemeler alır".

## 7. Çocuk kilidi

- **Kendiliğinden açılma.** Girişli hesabın yaşı 12'den küçükse kilit kendiliğinden açılır. Bu her hesap için bir
  kez olur: ebeveyn kapatırsa aynı hesapta yeniden açılmaz. Ebeveynden 4 haneli şifre istenir.
- **Ayarlar → Genel → Çocuk kilidi.** Yaş ne olursa olsun açılır ve kapanır.
  - Açarken ebeveyn şifre belirler; şifre iki kez girilir.
  - Kapatırken şifre sorulur. Şifre değiştirilebilir (eski şifreyle).
- **Kilit açıkken:**
  - Ana sayfada yalnız çocuk çizimi kartı (kapatılamaz) ve görev kartı görünür. Kaldığın yer ve en çok zaman
    listelerinde yalnız çocuk tuvalleri var.
  - Tuval oluşturma seçenekleri, tanıtım ve Premium kartı gizlenir.
  - Tuvallerim'de yalnız çocuk tuvalleri görünür. "Yeni" doğrudan çocuk tuvali açar.
  - Alt çubukta Resimlerim ve 3B modeller yok.
  - Başka türden bir tuval ya da 3B, resim ve abonelik sayfası açılmaya çalışılırsa geri dönülür. Ayarlar'da
    abonelik satırı yok.
- **Şifrenin saklanması.** Yerel veritabanında (`kids_lock` tablosu, Room 22 → 23) yalnız PBKDF2-HMAC-SHA1 özeti
  (20 000 tur, 16 baytlık rastgele tuz) tutulur.
  - 5 yanlış denemeden sonra 30 sn bekleme; bekleme her yanlışta iki katına çıkar.
  - Şifre girişi uygulamanın kendi tuş takımıyla yapılır.

## 8. Çocuk modu ve tuvaller

- **8.** Çocuk modunun üst menüsünde **Fırça ayarları** var. Elemanlar ve Fırça ayarları boyamanın hemen ardına
  (sola) taşındı. Başka araç seçilince fırça penceresi kapanır.
- **8.1.** Kalemle (fırçayla) çizilmiş **kapalı** şeklin içi boyanabilir.
  - Nereden: boyama aracı (dokunulan kapalı çizgi), boya kutusu ve "Renk ver".
  - Nasıl: dolgu, çizginin hemen altına aynı grupta ayrı bir şekil olarak konur. Çizginin kendisi değişmez; birlikte
    taşınır ve döndürülür. Yeniden boyamada aynı dolgunun rengi değişir.
  - Geri alma tek adımdır. Açık çizgiler boyanmaz.
- **8.2.** Çocuk görevi (challenge) sürerken çizim önerisi verilmez: çizgi, şekil, yazı ve seçim önerileri.
  Görev bitince öneriler döner.

## 9. Play ekran görüntüleri

`play-ekran-goruntuleri\` klasöründe, uygulamanın kendi çizimleriyle (özgün karakter, mekân ve efekt resimleri,
şablonlar, eğitimlerin çizdiği sayfalar):

- **Telefon** 1233×2193.
- **7" tablet** 1200×1920.
- **10" tablet** 1600×2560.

Her biri 6 görüntü: çizgi roman sayfası, resim sahnesi, ders notu, çocuk resmi, basit resim, 3B düzenleyici.

## 10. Sunucu ve yayın

- **Sürüm.** Sunucu `44.0.0`; yeni hak `model3d.export`; istisna ayarı `exempt-emails`.
- **Yığın (`stack.yml`).** v43 dağıtımının üretimde zorunlu ayarları eksikti; eklendi:
  - Cloud Storage kovası `dgpackets`.
  - Hizmet hesabı sırrı `gcs_service_account`.
  - Cihaz bilgisi anahtarı sırrı `device_data_key`.
  - Sır komutları Swarm README'sinde.
- **Rehber.** `backend/docs/v44-yayin-rehberi.md`: yeni sırlar, imaj, dağıtım, denetim, sürüm kaydı, Play'e
  yükleme, ekran görüntüleri, Aile politikası notu, geri alma.

## Doğrulama

- **Android (Robolectric).** Tam takım 766 test (13'ü yalnız `DG_SHOTS` ile çalışan ekran görüntüsü testi).
  - İlk turda 26 test eski davranışı bekliyordu: Room 22, sunucu seçimi kartı, yedek kuralları, düz HTTP izni, 21 hak, çocuk kartının kapatılması, araç sırası, sunucu sürümü.
  - Bunlar v44'e göre güncellendi. `V42ServerChoiceTest` kaldırılan özelliği sınadığı için silindi.
  - Düzeltilen sınıflar ve gerçek sunucu testleri (`CanvasFlowTest`, `V33BackendTest`) yeniden çalıştırıldı: **hepsi geçti**.
  - `V25Model3DViewModelTest`, `V37SyncPolicyTest` ve `V40KidsChallengeSuggestTest` yük altında ortam kaynaklı düştü; tek başına geçiyorlar.
  - `core:content` 250 ve `core:model` 34 test geçti.
- **Yeni testler:**
  - `V44RulesTest`: şifre, bekleme, yaş kuralı, 22→23 göçü, Premium ayrımı, istisna hesabı, sunucu ayarının kalkması, kalem dolgusu.
  - `V44EditorTest`: basit üst menü araçları, çocuk menüsü sırası ve Fırça ayarları, boyama aracıyla kapalı kalem şekli, görevde öneri yok.
  - `V44KidsLockUiTest`: Ayarlar'dan şifreyle açma/kapama, ana sayfa, alt çubuk, Tuvallerim; yaşla açılan kilitte ebeveyn şifresi.
  - `V43EditorTest`: kalemde çizgi eğme.
  - `V44PlayShotsTest`: ekran görüntüleri.
- **Sunucu.** 159 test (Testcontainers MongoDB), hepsi geçti; 1 test yalnız canlı GCS anahtarıyla çalışır. Yeni testler `ExemptAccountsTest` ve `V44StackConfigTest` (yığında sırlar, düz değer yok).
- **Paketler.**
  - İmzalı yayın APK'sı: apksigner doğruladı (CN=Ahmet Kar). versionCode 44 / 1.2.0, hata ayıklanamaz, ~64 MB.
  - AAB: jarsigner doğruladı, ~69 MB.

## Teslim

| Dosya | İçerik |
|---|---|
| `DrawGuidance-v44.apk` | Doğrudan kurulum için imzalı yayın APK'sı |
| `DrawGuidance-v44.aab` | Play Console için imzalı paket |
| `play-ekran-goruntuleri\` | Telefon, 7" ve 10" tablet ekran görüntüleri |
