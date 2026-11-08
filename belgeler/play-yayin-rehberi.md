# Çizim Rehberi — Google Play'de yayınlama rehberi

_Paket adı `com.karapp.drawguidance` · sürüm adı 1.0.0 · minSdk 23 (Android 6.0) · targetSdk 36_

> **Önemli:** Paket adı Play'de bir kez kullanılınca **asla değiştirilemez**. Uygulamayı açarken tam olarak `com.karapp.drawguidance` olacak (bunu ilk yüklediğin .aab belirler).

## 0. Elinde olanlar

- **`.aab` dosyası**: Play'e yüklenecek dosya, yükleme anahtarıyla imzalı. Hazır olunca ayrıca vereceğim; bu rehberin 1, 2 ve 4–8. adımlarını şimdiden yapabilirsin.
- **Yükleme anahtarı:** `yayin-anahtari/drawguidance-upload.jks`, takma adı `upload`. Şifresi aynı klasördeki `keystore.properties` dosyasında.
  - Bu iki dosyayı **mutlaka yedekle**: bir USB belleğe ve bir şifre yöneticisine.
  - Başkasıyla paylaşma, sohbete yapıştırma.
  - Kaybolursa Play Console'dan yükleme anahtarı sıfırlama istenebilir, ama bu günler sürer.
- **Sunucu:** yayın sürümü `https://drawapi.ahmetkar.com/` adresine bağlanıyor.

## Senden beklediklerim

- Play Console'da uygulamayı `com.karapp.drawguidance` için oluşturduğunu ve ilk .aab'yi yüklediğini haber ver.
- 3. adımdaki Play imza parmak izlerini Firebase'e ekleyip yeni `google-services.json` dosyasını gönder.
- 5. adımdaki hizmet hesabı JSON dosyasını sunucuya kendin koy; sohbete yapıştırma.

## 1. Play Console hesabı

1. https://play.google.com/console adresinde geliştirici hesabı aç. Tek seferlik 25 USD ücret var.
   - Geliştirici adı: **KarApplication** (web sitesindeki `DEVELOPER_NAME` ile aynı).
   - Herkese açık iletişim e-postası: **ahmetkar.1258@gmail.com**.
2. Kimlik doğrulamasını tamamla.
3. Kişisel hesaplarda kapalı test zorunlu: en az 12 test kullanıcısı, 14 gün boyunca (bkz. 6. adım).
4. Abonelik satabilmek için **Ödeme profili** oluştur (Ayarlar → Ödeme profili).

## 2. Uygulamayı oluştur

1. **Uygulama oluştur** düğmesine bas.
   - Ad: Çizim Rehberi (İngilizce mağaza kaydında: DrawGuidance)
   - Varsayılan dil: Türkçe
   - Tür: Uygulama
   - Ücret: Ücretsiz
2. **Uygulama içeriği** bölümünü doldur:
   - **Gizlilik politikası URL'si.** Uygulamadaki Ayarlar → Yasal metnini `drawapi.ahmetkar.com/gizlilik` gibi bir sayfada yayınla.
   - **Reklamlar:** "Evet, reklam içeriyor".
   - **Hedef kitle:** uygulamada 13 yaş altı çocuk modu var.
     - Hedef yaş grubunda 13 yaş altı seçilirse uygulama **Aileler politikasına** girer.
     - Bu durumda reklamlar yalnız Aileler sertifikalı ağlardan ve çocuğa yönelik işaretlenmiş olmalı.
     - Kod bunu yapıyor: 13 yaş altı hesaplarda ve çocuk tuvallerinde reklam gösterilmiyor; reklam isteği `tagForChildDirectedTreatment` ile gidiyor.
   - **Veri güvenliği formu:**
     - Toplanan veriler: e-posta ve ad (Google ile giriş), kullanıcı içeriği (eşitlenen çizimler), satın alma geçmişi, reklam kimliği (AdMob), hata raporları.
     - Hepsi aktarım sırasında şifreli. Kullanıcı hesabını ve verilerini silebiliyor.
   - Diğer formlar: içerik derecelendirme anketi, haber uygulaması değil, devlet uygulaması değil.

## 3. Play App Signing ve ilk yükleme

1. **Test ve yayın → Kurulum → Uygulama imzalama:** Google'ın uygulama imzalama anahtarını kullanmayı kabul et (varsayılan).
2. **Test → Dahili test → Yeni sürüm** ile .aab dosyasını yükle. Dahili teste kendini testçi olarak ekle; bağlantıyla telefonuna kur.
   - İlk yüklemede yükleme anahtarın kaydedilir; sonraki her .aab aynı anahtarla imzalanmalı.
3. **Firebase'e Play imza parmak izini ekle** (Firebase → Proje ayarları → `com.karapp.drawguidance` → SHA parmak izi ekle):
   - Hata ayıklama ve yükleme anahtarı SHA-1'leri zaten ekli (gönderdiğin dosyada ikisi de var).
   - Eksik olan: **Uygulama imzalama anahtarı** SHA-1 ve SHA-256. Play Console → **Test ve yayınlama → Uygulama bütünlüğü → Uygulama imzalama** sekmesinde, "Uygulama imzalama anahtarı sertifikası" altında yazar. İlk .aab bir sürüme yüklenip kaydedilince görünür. İkisini de ekle.
   - Sonra yeni `google-services.json` dosyasını indirip bana gönder; bir sonraki .aab'ye koyarım.
   - Bu yapılmazsa Play'den indirilen uygulamada Google ile giriş çalışmaz (dahili test kurulumu da Play'den indirildiği için etkilenir).

## 4. Abonelik ürünü (Premium)

**Para kazanma → Ürünler → Abonelikler → Abonelik oluştur:**

- **Ürün kimliği:** `drawguidance_premium`. Sonradan değiştirilemez; uygulama ve sunucu bu adı kullanıyor.
- **Ad:** Premium
- **Temel plan:** kimlik `premium-monthly`, otomatik yenilenen, 1 ay.
- **Fiyat:** Türkiye için **100 TL**. "Fiyatları dönüştür" ile diğer ülkelere Play otomatik yerel fiyat ve para birimi atar.
  - Uygulama fiyatı Play'den aldığı için her kullanıcı kendi ülkesinin fiyatını görür. Play'e ulaşılamazsa "100 TL" yazar.
- **Avantajlar:** reklamsız; gelişmiş efektler; arka plan kaldırma; gelişmiş şekil, karakter ve nesneler; 3B şekil önerileri.
- Etkinleştir.

## 5. Sunucunun abonelikleri doğrulaması

Sunucu, satın alma jetonunu Google Play Developer API ile doğruluyor ve Google'ın abonelik bildirimlerini (RTDN) işliyor.

1. **Google Cloud Console → Play'e bağlı proje:**
   - **Google Play Android Developer API**'yi etkinleştir.
   - Bir **hizmet hesabı** oluştur ve JSON anahtarını indir.
2. **Play Console → Kullanıcılar ve izinler:** hizmet hesabının e-postasını davet et. Yetkiler: "Finansal verileri görüntüle" ve "Siparişleri ve abonelikleri yönet".
3. JSON dosyasını sunucuya koy, sohbete yapıştırma. `backend/docker/.env` dosyasına yolunu yaz:
   - `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON=/run/secrets/play.json`
   - `GOOGLE_PLAY_PACKAGE_NAME=com.karapp.drawguidance`
4. **Pub/Sub:**
   - Konu oluştur, örneğin `play-rtdn`.
   - `google-play-developer-notifications@system.gserviceaccount.com` hesabına bu konuda **Yayıncı** rolü ver.
   - Bir **push aboneliği** ekle. Uç: `https://drawapi.ahmetkar.com/api/v1/billing/rtdn`. **Kimlik doğrulamayı etkinleştir** (OIDC) ve bir hizmet hesabı seç; hedef kitle olarak uç adresini yaz.
   - Aynı değerleri `.env` dosyasına yaz: `PUBSUB_PUSH_AUDIENCE=https://drawapi.ahmetkar.com/api/v1/billing/rtdn` ve `PUBSUB_PUSH_SERVICE_ACCOUNT=<seçtiğin hizmet hesabının e-postası>`.
5. **Play Console → Para kazanma kurulumu → Gerçek zamanlı geliştirici bildirimleri:**
   - Konu adını gir: `projects/<proje>/topics/play-rtdn`.
   - **Test bildirimi gönder** ile dene. Sunucuda `billing_webhook_events` koleksiyonunda bir kayıt görünmeli.
6. Sunucuyu yeni sürüme güncelle (Docker Swarm dosyalarını ayrıca hazırlıyorum).

## 6. Kapalı test ve lisans testçileri

1. **Ayarlar → Lisans testi:** kendi Gmail adresini ekle. Bu hesapla yapılan abonelikler ücretsiz test satın alması olur ve 5 dakikada bir yenilenir.
2. **Kapalı test** kanalı aç, en az 12 testçi ekle ve 14 gün beklet.
3. Sonra **Üretime ilerle** ile üretim sürümü oluştur.

## 7. AdMob

1. AdMob'da uygulamayı Play mağaza kaydına bağla. Uygulama yayınlandıktan sonra **Uygulama ayarları → Mağaza ekle**.
2. Uygulama kimliği `ca-app-pub-4962587185426397~4108507569` ve geçiş reklam birimi `…/2112899614` yayın sürümüne gömülü. Hata ayıklama sürümü Google'ın test reklamlarını gösterir.
3. **Gizlilik ve mesajlaşma → GDPR / Avrupa onay mesajı** oluştur. Uygulama bu mesajı UMP ile gösteriyor.
4. Sunucunun kök adresinde `app-ads.txt` dosyası yayınla:
   - İçerik: `google.com, pub-4962587185426397, DIRECT, f08c47fec0942fa0`
   - Adres, Play mağaza kaydındaki web sitesinin kökü olmalı.
5. Hedef kitlede 13 yaş altı seçtiysen AdMob'da **Uygulama ayarları → Çocuklara yönelik** ayarını da aç.

## 8. Mağaza kaydı

- **Ana mağaza kaydı → Uygulama ayrıntıları:**
  - Uygulama adı (en çok 30 karakter): `Çizim Rehberi`
  - Kısa açıklama (en çok 80 karakter), örnek: `Çiz, not al, çizgi roman yap: yapay zekâ destekli çizim rehberin.`
  - Tam açıklama (en çok 4000 karakter): modları (resim, not, çizgi roman, 3B, çocuk) ve yapay zekâ önerilerini anlat.
- **Çeviriler:** Ana mağaza kaydı → **Çeviri ekle** → İngilizce (ABD) seç, adı `DrawGuidance` yap. Diğer diller isteğe bağlı.
- **Grafikler:** uygulama simgesi 512×512 PNG, öne çıkan görsel 1024×500. En az 2 telefon ekran görüntüsü (en az 320 px, en çok 3840 px; 9:16 önerilir), 7" ve 10" tablet görüntüleri önerilir. Her moddan ekran görüntülerini ben hazırlayıp vereceğim.
- **Mağaza ayarları:** kategori Sanat ve Tasarım, iletişim e-postası `ahmetkar.1258@gmail.com`, web sitesi (`https://ahmetkar.com` gibi; `app-ads.txt` bu sitenin kökünde olmalı).

## 9. Sonraki sürümler

1. `android/app/build.gradle.kts` içinde `versionCode` her yüklemede 1 artmalı. `versionName` isteğe bağlı.
2. `gradle :app:bundleRelease` ile derle. Anahtar `app/keystore/` klasöründe olmalı; çıktı `app/build/outputs/bundle/release/app-release.aab`.
