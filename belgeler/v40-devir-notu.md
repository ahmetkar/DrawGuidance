# DrawGuidance v40: devir notu (yeni Claude oturumu için)

_Son güncelleme: 2026-10-24. Önce `YENI-SOHBET-DEVIR.md` (v38 dönemi genel bilgi), sonra bu not okunmalı._

## 1. Projenin güncel kopyası nasıl açılır

- `DrawGuidance-v40-proje.bundle`, git deposunun tamamıdır: bütün dallar ve geçmiş.
  - Açmak için: `git clone DrawGuidance-v40-proje.bundle DrawGuidance-proje`, ardından `git branch -a`.
  - Devam eden işler ayrı dallardadır (bkz. 4. bölüm). Ana dal `master`'dır.
- Git'e girmeyen sırlar `yayin-anahtari/` klasöründedir:
  - yükleme anahtarı (`drawguidance-upload.jks` ve `release.properties`);
  - `sunucu.env`;
  - `play-service-account.json`.

  Bu dosyalar kimseyle paylaşılmaz, içerikleri yazdırılmaz.
  - `android/app/keystore/` klasörüne `drawguidance-upload.jks` ve `release.properties` konur.
  - `backend/docker/.env` dosyası `sunucu.env`'den oluşturulur.
  - `backend/docker/secrets/play.json` dosyası `play-service-account.json`'dan oluşturulur.
- Google kitaplıkları çevrimdışı derleme için `gmaven-v39/` klasöründedir (dl.google.com kapalıydı).

## 2. Sabit bilgiler

- **Paket ve ad:**
  - Paket `com.karapp.drawguidance`; uygulama adı "Çizim Rehberi" (TR) ve "DrawGuidance" (EN).
  - versionCode 40; Play'e yeni yükleme 41 olmalı.
  - minSdk 23. Yayın derlemesinde R8 küçültme açık.
- **Sunucu ve site:**
  - Sunucu `https://drawapi.ahmetkar.com` (Spring Boot 3.5 ve MongoDB, `prod` profili).
  - Site `drawguidance.ahmetkar.com`: `web/` klasöründeki Next.js, Vercel'de çalışır.
- **Abonelik:** ürün `drawguidance_premium`, temel plan `premium-monthly`. RTDN: Pub/Sub push ve OIDC.
- **AdMob:** uygulama `ca-app-pub-4962587185426397~4108507569`, geçiş reklamı birimi `/2112899614`.
- **Docker Swarm:** `backend/docker/swarm/` (rehber README.md).
  - ACME e-postası ahmetkar.1258@gmail.com.
  - İmajlar GitHub Container Registry'de (`ghcr.io/<github-kullanıcı-adı>`, kullanıcı adı henüz alınmadı).
  - Önde CDN yok.
- **Room şeması:**
  - v18: çocuk görevleri.
  - v19: `canvas_time`, süre istatistiği.
  - v20: hesap/eşitleme dalında.
  - Göçler `ALL_MIGRATIONS` içinde ve zincir kopmamalı.
- **Derleme:** `android/` klasöründen çalıştırılır.
  - Hızlı kontrol: `gradle --offline -q :app:compileDebugUnitTestKotlin`.
  - Paket: `:app:bundleRelease` ya da `:app:assembleRelease`, `-x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease` ile.
  - Bellek az; paralel derlemede `--max-workers=2` kullanılmalı.

## 3. Ahmet'in kalıcı istekleri

- Türkçe yanıt verilir.
- **Git ve teslim:**
  - Değişen bütün dosyalar git'te hazır (staged) tutulur. Teslimde `git reset --soft e7ce7d1` uygulanır.
  - Push, PR ve canlı ortam değişikliği sorulmadan yapılmaz.
  - Zip yalnız istenince hazırlanır.
- **Bilgisayar klasörü** (`C:\Users\ahmet\Documents\DrawGuidance`):
  - Bu klasöre yalnız Ahmet söyleyince yazılır.
  - Sürüm belgeleri klasörün köküne konur.
  - Eski dosyalar silinmez, `_eski_vNN\` klasörüne taşınır.
  - Windows ayarları değiştirilmez.
- **Kod ve içerik:**
  - Fazladan duran `CanvasDto.kt` dosyasına dokunulmaz.
  - Yalnız istenen şey silinir.
  - Telifli karakterler ve internetten bulunan resimler çizime kopyalanmaz.
  - Her özelliğe test yazılır.
- **Sırlar:** gizli anahtarlar yazdırılmaz.

## 4. Durum (yapılanlar ve yapılacaklar)

### master'a birleştirilenler
- Paket adı değişikliği, Play rehberi, web sitesi ve sunucu uçları, yayın anahtarı ve `.env`.
- Çocuk görevi: 50 seviye, son 15'i Premium; ilk 5 girişte maskot öneriyor.
- Güvenlik denetimi, Swarm dosyaları, R8.
- CMYK, PDF ve CBZ dışa aktarma; sayfa numarası, üst ve alt bilgi; kayıt konumu seçimi; not düzeltmeleri.
- 5 sn basılı tutunca Taşı, basit resimde Taşı düğmesi, arka planı taşıma ve boyutlandırma, yazı kutusu boyutu, cihaz hızına göre açılış.
- Çizgi roman karakterleri (zip'ten kesilmiş) ve arka planlar; `yikik_kopru` ve `okul_cati2` çıkarıldı.
- Cetvel (düz, iletki, iki gönye, pergel, elips, pistole) ve şekil kalıbı.
- Gölgesiz, kenarlıklı kutular; sade ana sayfa; tuval sayıları Tuvallerim'de; moda göre tuval boyutu ve özel boyut.
- Açık, Koyu ve Sistem teması ile yüksek karşıtlık; en çok vakit geçirilen çizimler.

### Devam eden dallar
`.claude/worktrees/<dal>` klasörlerinde.

**`v40-comic-assets`** (2026-10-24 isteği):
- Arka planlar, tuval oluşturma ekranından düzenleyicideki "mekan arkaplanı ekle" penceresine taşınır. Bırakıldığı şablon paneline sığar ya da bütün sayfayı kaplar.
- Arka plan çiftlerinin biri Manga, biri Çizgi roman kategorisine konur.
- Şekil ekleme ve diyagramlar tamamen ücretsiz olur.
- Yalnız şu karakterler Premium kalır: yeniçeriler, moğol atlı, ajan nova, komando, samuray, buz büyüsü, uzaylı büyücü, siborg, ateş yumruk, gezgin kılıççı ve maskeli hırsız.
- Şu karakterler MANGA kategorisine alınır: demirci emi, uzaylı büyücü, öğrenci ece, zeynep, nine fatma, büyücü leyla, kılıççı doruk, elf büyücü luara, elf okçu silvan, samuray aiko ve kılıççı lale.
- Karakterlerin kişi adları kaldırılır; yalnız meslek, yaş ve rol yazılır.

**`v40-ruler`:**
- Cetvel ve Şekil kalıbı ayrı düğmeler olur.
- Bilgi çubuğu ekranın altında görünür.
- Cetvelin türü, birimi, ölçeği ve Bitir düğmesi cetvelin üstündeki tutamaç düğmelerinden değiştirilir.

**`v40-account-sync`:**
- Giriş sonucu penceresi; notlu hata penceresi; mod bazında geri bildirim; güncelleme ve hareketsizlik bildirimleri.
- Eşitleme izni dilimleri (1000'erli gruplar, 24 saat içinde 10 dakikalık aralıklar).
- Abonelik başlangıç, bitiş ve otomatik yenileme bilgisi; rapor için giriş zorunluluğu. Room v20.
- Yeni işler:
  - Android 10'da yayın APK'sında Ayarlar'a girince uygulama kapanıyor; bu hata çözülecek.
  - Abonelik ekranı SaaS tarzına çevrilecek: plan kartları ve başlıklar altında yönetim düğmeleri.

**`v40-tutorials`** (WIP 6d2744e):
- Bütün modların eğitimleri yeni özelliklere göre yenileniyor; basit modlara da eğitim ekleniyor.
- Öteki dallar birleştirildikten sonra bitirilecek; cetvel düğmesi etiketleri değişecek.

### Son adımlar
1. Dalları sırayla master'a birleştir, her seferinde derlemeyi kontrol et:
   - comic-assets;
   - ruler;
   - account-sync (Room 20);
   - tutorials.
2. Uygulama test takımının tamamını sunucu açıkken çalıştır (CanvasFlowTest 4747 portunu ister), ardından sunucu testlerini çalıştır.
3. Çeviri denetimini yap (7 dil). Hız testlerini ve geniş uyumluluk denetimini yap. Play mağazası için her modun ekran görüntüsünü al.
4. versionCode'u 41 yap.
5. İmzalı `.aab` ve telefona kurulabilen imzalı APK üret. Çizgi roman varlıkları büyük olduğu için APK yaklaşık 43 MB tutar.
6. Belgeleri yaz: `belgeler/v40-durum.md` yazılır, `YENI-SOHBET-DEVIR.md` güncellenir.
7. `git reset --soft e7ce7d1` ile her şeyi hazır (staged) bırak.

### Ahmet'ten beklenenler
- GitHub kullanıcı adı (Swarm imaj adresi için).
- Play geliştirici adı (sitede şimdilik "karApp" yazıyor) ve herkese açık iletişim e-postası.
- `web/` klasörünü bilgisayarına kopyalama izni.
