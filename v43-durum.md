# DrawGuidance v43 — durum

_2026-10-26 · versionCode **43** (versionName 1.1.0) · Room **v22** · minSdk 23 – targetSdk 36 · sunucu **43.0.0**, kapı 4747 · 7 dil_

Ahmet'in v43 isteklerinin hepsi yapıldı: yerel sunucu hatası ve 1–15. maddeler.

Bildirim ve içerik dağıtımı **Firebase olmadan** çalışır ve yüksek trafiğe göre tasarlandı. Değişen dosyalar
`git reset --soft e7ce7d1` ile hazırlık alanında (staged); commit yok.

## Yerel sunucu başlatma hatası

**Neden.** Windows PowerShell 5.1, Docker'ın hata akışına yazdığı ilerleme satırlarını `ErrorActionPreference=Stop`
altında hata sayıp betiği durduruyordu.

**Düzeltme.**

- Docker çağrıları kendi içinde `Continue` ile çalışır.
- Her çalışmanın kaydı `logs\son-calisma.log` dosyasına yazılır.
- Eski `.env` dosyalarına eksik anahtarlar kendiliğinden eklenir.

**Yeni.** `Yonetim.bat` ile paket, bildirim, duyuru ve sürüm yönetimi. Cloud Storage anahtarı
`yayin-anahtari\gcs-service-account.json` dosyasından `sirlar\gcs.json`'a kopyalanır.

## 1. Eşitleme ve içerik dağıtımı

### Uygulama

- **Eşitleme her zaman açık.** Otomatik/elle eşitleme ayarı ve "Şimdi eşitle" kaldırıldı. Ağ türü ve günlük
  sınırlar kaldı.
- **Günlük indirme sınırı.** Her 16 KB'lık parçadan önce denetlenir. Sınır dolunca indirme bırakılır ve yarım paket
  silinir; ertesi gün paket baştan iner.
- **Olay tablosu (`sync_events`) iki yönlü.**
  - Giden işler: gönderildi / gönderilmedi.
  - Gelen teslimatlar: yeni, bilgi onaylandı, veri alındı, işlendi (gösterildi / kuruldu / ana sayfada),
    teslim onaylandı, kapatıldı.
- **Paket kurulumu.** Paket önce geçici klasöre iner. Tamamlanınca tek adımda `files/packs/<mod>/<paket>/` klasörüne
  taşınır.
  - Çizgi romanın nesne, efekt, mekân ve karakterleri kendi pencerelerinde, paketin adını taşıyan kategoride görünür.
  - Diyalog kutuları, şekiller ve diyagramlar **Ekle → Paket içerikleri**'nde.
- **Gösterim.** Bildirimler Android bildirimi olur. Duyurular ana sayfada kapatılabilir kart olarak çıkar.

### Sunucu (Spring Boot + MongoDB)

- **Koleksiyonlar.** Paketler (mod başına pencereler), bildirimler, duyurular, sürümler, cihazlar, gönderimler,
  teslimatlar.
- **Komut satırı.** Ekleme, güncelleme, görüntüleme ve gönderme komutları var; hedef herkes ya da seçili
  kullanıcılardır (`Yonetim.bat yardim`).
- **İşçiler.** Önce "yeni içerik var" haberini gönderir, sonra veriyi. Cihaz her aşamayı onaylar:
  `QUEUED → INFO_SENT → INFO_RECEIVED → DATA_SENT → DELIVERED`.
- **Yüksek trafik.**
  - Değişmeyen gelen kutusu yoklaması 304 döner: tek indeksli okuma.
  - Uygulama açıkken 25 sn'lik uzun yoklama yapılır, bildirim anında gelir. Bekleyenler sanal iş parçacıklarında
    tutulur.
  - Örnek başına tek izleyici var; bekleyen sınırı dolunca istekler bekletilmez.
  - `X-Poll-After` yoğunlukta 15 dakikadan 45 dakikaya uzar; cihaz buna rastgele sapma ekler.
  - Teslimatlar toplu upsert ile açılır ve toplu onaylanır.
  - Aynı içerik için hazırlanan veri 30 dk bellekte tutulur.
  - Resimler sunucudan geçmez: imzalı Cloud Storage adresinden doğrudan iner.
- **Gizlilik.** Cihaz kimliği HMAC-SHA256 ile saklanır. Model, üretici, Android sürümü ve dil AES-256-GCM ile
  şifrelenir; anahtar `DEVICE_DATA_KEY`.
- Ayrıntılar: `backend/docs/v43-dagitim.md`.

### 1.1 Taban paketler (Google Cloud Storage)

- Uygulamadaki bütün pencere resimleri paket mimarisine göre dışa aktarıldı ve **`dgpackets`** kovasına yüklendi
  (2099 dosya):

  | Paket | Öğe | İçerik |
  |---|---|---|
  | `base-comic` | 1723 | nesne, efekt, mekân, karakter, diyalog kutusu, şekil |
  | `base-notes` | 230 | şekil, diyagram |
  | `base-art` | 146 | şekil |

- Sunucu açılışta bu paketlerin kayıtlarını kendiliğinden oluşturur.
- Cihaz taban paketleri kurulu sayar, yeniden indirmez.

## 2. Şekil kalıbında öneri yok

- Kalıbın üstünde çizilen çizgiye yapay zekâ önerisi yapılmaz. Bu çizgiler sonraki önerilerde de dikkate alınmaz.
- "Üstünde" ölçütü: noktalarının en az %60'ı kalıbın kutusunda (kutu kalıbın boyunun %8'i kadar genişletilir).
- Kalıbın dışında öneriler her zamanki gibi.

## 3. Basit modlarda şekil ve resmi boyutlandırma

- Seç ve Taşı'da seçili tek şekil ya da resmin dört köşesinde tutamak görünür.
- Köşeden sürükleyince karşı köşe sabit kalır ve oran korunur. Geri alınabilir.

## 4. İlk açılışta izinler

- Android'in izin penceresi ilk açılışta açılır (Android 13+ bildirim izni).
- Sonuç veritabanına yazılır (`app_permissions`). Açılışta ve Ayarlar'da sistemin güncel durumuyla eşitlenir; kalıcı
  reddedilmişse uygulama ayarları açılır.

## 5. Kalem penceresinde nokta ve çizgi düzenleme

- Çizerken noktalar görünür. Nokta ya da eğri tutamağı sürüklenerek taşınır; noktaya dokunmak onu seçer.
- Seçili nokta silinir ya da köşe ↔ eğri yapılır.
- **Bitir ve düzenle** yolu tamamlar ve şekil düzenlemede bütün nokta araçlarıyla açar.

## 6. Arkaplan ile Nesneyi Uyumla

Gelişmiş resim ve çizgi romanda **Düzenle** menüsünde (resim düzenlemede de).

- **Pencere.** Seçenekler: gölge, renk ve ışık, kenar yumuşatma, konum/ölçek, yoğunluk. Değişiklikler tuvalde
  önizlenir. **Uygula** tek bir geri alınabilir adım; **İptal** özgün hâle döndürür; **Uyumu kaldır**.
- **Yıkıcı değil.** Resmin dosyası değişmez. Ayarlar elemanın `harmony` alanında saklanır ve çizimde uygulanır.
  Taşıma, ölçekleme ve döndürmede korunur.
- **Cihazda.** Büyük model, bulut ya da LLM yok; OpenCV gerekmedi. Arka plan nesnenin çevresinde en çok 256 piksele
  küçültülerek çözümlenir.

Algoritmalar:

| Adım | Yöntem |
|---|---|
| Sahne çözümlemesi | Nesnenin altındakiler (kağıt, arka plan, sırada önceki elemanlar) çizilir. Nesne kutusunun dışındaki halkadan ortalama renk, parlaklık, karşıtlık (std), doygunluk ve keskinlik (Laplace varyansı) hesaplanır. |
| Işık yönü | Parlaklığın ortalamadan sapmasıyla ağırlıklı yön vektörü (halka merkezine göre). Zayıfsa sol-üst varsayılır. |
| Gölge | Siluet (alfa maskesi) ayak çizgisinden ışığın tersine yatırılır (eğme) ve basılır (ışık tepedeyse kısa), `BlurMaskFilter` ile yumuşatılır. Ayak altına radyal temas gölgesi eklenir. Gölge rengi arka planın koyulaştırılmış rengi; opaklık karşıtlığa göre. |
| Renk ve ışık | Kanal kazançları: arka planın renk tonuna %35 yaklaşır (0,75–1,3 arasında). Parlaklık kayması ve doygunluk oranı da kısmen aktarılır. |
| Kenar | Kenar yumuşatma: alfa, iki geçişli kutu bulanıklığıyla (O(n)) bulanıklaştırılmış alfayla çarpılır. Arka plan nesneden bulanıksa daha çok yumuşatılır. Işık sarması: kenarın iç bandına arka plan rengi karışır. |
| Konum | Nesnenin alt kenarı çevresinde yatay kenar gücü en büyük satır zemin sayılır; yakınsa ayaklar ona oturtulur. |
| Bellek | İşlenmiş resim en çok 1024 piksel, 32 MB'lık LRU önbellekte. Çözümleme arka plan iş parçacığında. Bütün API'ler minSdk 23 ile uyumlu. |

## 7. Nesneyi Mekâna Yerleştir

- **Ufuk ve kaçış noktası.** Arka plan 320 piksele küçültülür. Sobel kenarları üzerinde Hough dönüşümü (1°, yerel en
  büyükler) çalışır.
  - Eğik çizgilerin kesişimlerinin ortancası kaçış noktasıdır; kesişimlerin yarısı yakınsa ufuk olarak kabul edilir.
  - Kaçış noktası yoksa en güçlü uzun yatay çizgi ufuk sayılır.
  - O da yoksa göz hizası %40 varsayılır (düşük güven) ve kullanıcıya "elle ayarlayın" denir.
- **Perspektif ölçeği.** Zemindeki nesnenin ekrandaki boyu ayağın ufka uzaklığıyla orantılıdır:
  `boy = k × (ayak − ufuk)`.
  - `k` sahnedeki referans nesnelerden (zemindeki öteki resimler, ortanca) ya da nesnenin şimdiki hâlinden alınır.
  - Ölçek 0,25–4 arasında sınırlı; oran korunur.
  - Ayak ufkun üstündeyse nesne zemine indirilir.
- **Elle düzeltme.** Ufuk, ayak hizası ve ek ölçek (×0,5–2) kaydırıcıları.
- **Akış.** Önizleme → Uygula/İptal. Yalnız dönüşümdür (taşıma ve ölçek); geri alınabilir; cihazda çalışır.

## 9. Google Play güncellemeleri

- **Sürüm duyurusu.** Yeni sürüm sunucuya `surum ekle --kod … --ad …` ile kaydedilir. Eski sürümdeki cihazlara
  güncelleme haberi kendiliğinden gönderilir.
- **Cihazda.**
  - Bildirim gösterilir. Bildirime dokununca ya da uygulama açılınca güncelleme penceresi çıkar.
  - **Şimdi güncelle**, Play içi güncellemeyi (Play Core In-App Updates; zorunluysa IMMEDIATE, değilse FLEXIBLE)
    başlatır. Desteklenmiyorsa Play mağaza sayfası açılır.
- Uygulamanın sürüm sorgusu da en yeni kaydı kullanır.
- **Play'e yükleme.** İmzalı `DrawGuidance-v43.aab` hazır. Play Console'a yükleme senin onayınla yapılır.

## 10. Geniş ekranda yan panel

- Yatay ve geniş ekranlarda Fırça, Elemanlar ve Katmanlar paneli başlığındaki tutamaktan sürüklenir.
- Bırakınca en yakın kenara (sol ya da sağ) yerleşir ve orada kalır.

## 11 ve 11.1. Alanla seçim ve damlalık

- **Seç satırında üç araç.** Hiçbiri tuvale çizmez.
  - **Dikdörtgen.** Kutunun içindekiler seçilir, sonra Ölçekle açılır ve seçim köşelerden elle boyutlanır.
  - **Kalemle alan.** Çizilen kapalı çizginin içindekiler seçilir.
  - **Damlalık.**
- **Damlalık ayrıca** Fırça ayarlarının renk bölümünde ve geniş ekranda Çiz satırında. Dokunulan yerin 3×3 piksel
  ortalaması fırça rengi olur.

## 12. RGB/HSV renk seçici

- Bütün renk seçicilerde HSV alanının altında **HSV / RGB** kaydırıcıları (ton 0–360°, doygunluk/parlaklık %,
  R/G/B 0–255) ve **#RRGGBB** kod alanı var.

## 13. Mekân, nesne ve efekt pencereleri

- **"Meslek" kategorisi** mekân, efekt ve nesne pencerelerinden kaldırıldı; meslek karakterleri kaldı.
- **Adlar düzeltildi.** Örnekler: "Işık ve ateş", "Atış ve kesik".
- **Yanlış kategoriler.** Silah, zırh, sancak ve patlama efektleri doğru kategorilere taşındı.
- **Mekânlar.** Manga sekmesindeki renkli mekânlar gri tonlamaya çevrildi ya da çizgi roman stiline alındı;
  dört mekânın adı düzeltildi.
- **Yinelenenler.** Manga/çizgi roman nesneleri tek listede. Gerçek kopyalar atıldı (3 nesne, 1 efekt, 1 mekân).

## 14. Şekil ekle → Nesneler

- Sekme ve nesne kütüphanesinin resimleri silindi. Katalog sürümü 43000 oldu; sunucunun katalog tohumu yeniden üretildi.

## 15. Abonelik

- Ayarlar'daki abonelik satırı yalnız giriş yapılmışken görünür.

## Doğrulama

- **Android (Robolectric).** Tam takım 749 test + `core:content` 250 + `core:model`.
  - İlk turda 31 test eski davranışı bekliyordu (Room 21, abonelik satırı, nesne kütüphanesi, eşitleme anahtarı,
    sunucu sürümü, meslek teması); güncellendi.
  - Düzeltilen 30 sınıf (77 test) ve gerçek sunucu testleri yeniden çalıştırıldı: **hepsi geçti**.
  - `V25Model3DViewModelTest` yük altında bir kez ortamdan kaynaklı kararsız çıktı; tek başına geçiyor.
- **Yeni testler:**
  - `V43InboxTest`: uçtan uca gelen kutusu, sınırda yarım paketin silinmesi, bozuk resim, bekletme, taban paket
    süzme.
  - `V43EditorTest`: kalıp, boyutlandırma, kalem düzenleme, yan panel, alanla seçim, damlalık, RGB/HSV.
  - `V43SceneTest`: ışık/renk/zemin çözümlemesi, yıkıcı olmayan çizim, kaçış noktası, perspektif ölçeği,
    uygula/iptal/geri al.
  - `V43ShapePickerTest`.
- **Sunucu.** 155 test (Testcontainers MongoDB), hepsi geçti. Cloud Storage canlı testi (`dgpackets`: yükleme, MD5,
  imzalı indirme) geçti.
- **APK.** İmzalı yayın APK'sı (apksigner doğruladı), versionCode 43, ~62 MB. AAB ~66 MB.

## Teslim

| Dosya | İçerik |
|---|---|
| `DrawGuidance-v43.apk` | Doğrudan kurulum için imzalı yayın APK'sı |
| `DrawGuidance-v43.aab` | Play Console için imzalı paket |
| `yerel-sunucu\` | `yerel-sunucu.ps1`, `Yonetim.bat`, `docker-compose.yml`, `.env.ornek`, `BENIOKU.md`, yeni `drawguidance-server.jar` |
