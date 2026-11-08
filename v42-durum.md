# DrawGuidance v42 — durum

_2026-10-25 · versionCode **42** (versionName 1.0.0) · Room **v21** · minSdk 23 – targetSdk 36 · sunucu **39.0.0**, kapı 4747 · 7 dil_

Ahmet'in 25 Ekim isteklerinin (1–13) hepsi, Ayarlar çökmesi için tanılama ve geçici sunucu seçimi (yerel ağ / uzak)
yapıldı. Değişen dosyalar `git reset --soft e7ce7d1` ile hazırlık alanında (staged), commit yok.

## İstekler ve yapılanlar

### 1. Başlık menüsünde cetvel ve kalıp yok

- Üst menüdeki cetvel ve şekil kalıbı seçenekleri kaldırıldı; ikisi Çiz satırında ayrı düğmeler.

### 2. Çizgi roman mekânları diğer modların arka planlarından ayrı

- Hazır mekân resimleri yalnız çizgi roman **Ekle → Mekân arka planı** penceresinde. Öteki modların kağıt, desen ve
  resimleri kendi Arka plan pencerelerinde. Çizgi romanın Arka plan penceresinde resim yok (`BackgroundPicker(showImages)`).

### 3. Sayılar Tuvallerim'de

- Ana sayfadaki tuval/not/çizgi roman sayıları kaldırıldı; Tuvallerim'de "resim, not, çizgi roman" sayıları.

### 4. Kullanım şartları: çizgi romanların dağıtımı ve ticari kullanımı

- `terms_tr.txt` / `terms_en.txt` ve web `terms.tsx`'e **6. Çizgi romanlarınızın dağıtımı ve ticari kullanımı**:
  kullanıcının eser sahipliği (FSEK 5846), dağıtma ve ticari kullanma hakkı, hak devrinin yazılı şekli (FSEK m.52),
  uygulamayla gelen varlıklar için dünya çapında, süresiz, bedelsiz, münhasır olmayan ve geri alınamaz lisans
  (FSEK m.21–25), varlıkların tek başına yeniden dağıtılamaması, marka kullanımı yasağı, kullanıcı sorumluluğu
  (KVKK 6698), basılı yayın yükümlülükleri (ISBN, bandrol FSEK m.81, 6279 sayılı derleme), CMYK prova önerisi,
  yapay zekâ yardımı notu, isteğe bağlı atıf, talep üzerine imzalı lisans belgesi. Yürürlük 25 Ekim 2026.

### 5 ve 8.2. Resim modlarının şekilleri

- Gelişmiş ve basit resimde temel şekiller **14**'e indirildi (çizgi, ok, çift ok, daire, elips, kare, dikdörtgen,
  yuvarlak köşeli dikdörtgen, üçgen, dik üçgen, eşkenar dörtgen, beşgen, altıgen, kalp).
- **129 genel şekil** eklendi (`core/content/.../library/general/GeneralShapes.kt`), 7 kategori:
  çizgiler ve oklar (16), blok oklar (15), yıldızlar ve patlamalar (17), afişler ve kurdeleler (16),
  akış şeması (28), konuşma ve açıklama kutuları (17), semboller ve çerçeveler (20). Basit resimde akış şeması yok.
- Renksiz (kalem rengi; "İçi dolu" açıksa dolgu); kutuya serbest yerleşir (daire/yıldız kare, çizgiler iki nokta
  arasında). Adlar 7 dilde.
- **Nesneler** (hayvan, taşıt, eşya, çizim yardımcıları) resim modlarının şekil seçicisinden tamamen kalktı. Çizgi
  romanın seçicisi değişmedi.

### 6. Yol üzerinde yazı (text on path)

- Yazı düzenleyicisinde **Yol üzerinde yazı** düğmesi: yazı kaydedilir ve kalem aracıyla çizilmiş bir yolun (ya da
  başka bir vektör çizginin) üstüne yerleşir. Sayfada tek yol varsa doğrudan; birden çok yol varsa yollar işaretlenir,
  dokunulan yol seçilir. Yol yoksa düğme kapalıdır ("önce kalem aracıyla bir yol çiz").
- Seçili yazının bağlam menüsünde: yola yerleştir, yolun altına/üstüne al, yönü çevir, yoldan çıkar.
- Hizalama (sol/orta/sağ), yazı tipi, kenarlık, gölge, büyük harf ve harf aralığı yolda da çalışır. Yazı taşınır,
  döndürülür ve ölçeklenir; yol yazının içinde kopya olarak saklanır (yol silinse de yazı bozulmaz).
- İçerik biçimi **v9**: yalnız yol yazısı içeren belgeler v9 yazılır (eski sürümler bozuk okumak yerine açmaz);
  ötekiler eski biçimde kalır.

### 7. Yeni mekân arka planları

- Gönderilen arka plan zip'indeki 20 arka plan (manga 12, çizgi roman 8) klasörlerine göre kategorilere eklendi; yeni
  kategoriler: Orman, Sahil, Çöl. Toplam **51** mekân.

### 8 ve 8.1. Efektler, nesneler ve tema paketleri

- `effects.zip` ve `items.zip` sayfaları parçalara bölündü, bozuk/yarım/yinelenen parçalar atıldı:
  **471 efekt resmi** (8 kategori) ve **479 nesne** (12 kategori), WebP ~14 MB.
- Çizgi romanda **Ekle → Nesneler** penceresi (yeni) ve **Efektler → Resimli efektler** sekmesi: Manga / Çizgi roman,
  tema paketi ve kategori süzgeci. Eklenen resim taşınır, döndürülür, kırpılır. Free'de patlama, duman, simge ve ışık
  efektleri ile belge, mobilya, yiyecek, ışık ve teknoloji nesneleri açık; ötekiler Premium.
- **Tema paketleri:** 14 tema (yeni: Macera, Kıyamet Sonrası). Her paketin karakterleri, mekânları, efektleri ve
  nesneleri var. Veritabanında (Room v21) `theme_packs`, `comic_assets` ve iki yabancı anahtarlı
  `theme_pack_assets` (CASCADE) tabloları; açılışta katalogdan yazılır (değişmediyse yazılmaz). Elle eklenen
  bağlantılar (`ThemePackRepository.link`) korunur. Karakterler, Mekân ve Nesne/Efekt pencerelerinde tema
  çipleri bu bağlantılardan gelir.
- Yeni varlık eklerken: `tools/comic_assets/manifest.py` (karakter `CHAR_EXTRA_THEMES`, mekân `BG_THEMES`) ve
  `props_manifest.py` (sayfa başına temalar) düzenlenir, `build_assets.py` sonra `props_build.py` çalıştırılır.

### 9. Resimlerdeki bozukluklar

- 617 karakter çeşidi tek tek incelendi. Düzeltilemeyen **22 çeşit atıldı**: iki figür tek resimde (5), başı silinmiş
  gövde (3), parıltı/hologramın içine işlemiş dama deseni (9), omuz arkasında gölge lekesi (4), köşede dama kırıntısı (2).
  Kalan **595** çeşitte beyaz saçak ve kopuk kırıntılar giderildi, iki resimde dama kalıntısı silindi
  (`tools/comic_assets/fix_chars.py`, `build_assets.py` içinde de uygulanıyor).
- Efekt ve nesne parçaları sayfa sayfa incelendi (yarım, iç içe ve tekrar edenler atıldı; ulusal amblemli sancak atıldı).

### 10. Tek kaydetme penceresi

- Çizgi romanda ve not almada ⋮ menüsündeki CBZ/PDF kaldırıldı; başlıktaki **Kaydet** düğmesi tek pencere açar:
  çizgi romanda PDF / CBZ / Resim dosyası sekmeleri (öntanımlı **PDF, CMYK**), not almada PDF / Resim dosyası
  (öntanımlı **PDF, CMYK**). Öteki birleşimler seçilebilir.

### 11. Metinler

- Google ile giriş açıklaması, Hesap ayarlarındaki giriş düğmesinin üstündeki yazı ve Ana sayfadaki Premium önerisi
  istenen metinlerle (7 dil).

### 12. Kaydedilenler

- "Resimlerim" → **Kaydedilenler**, simgesi yer imi. PNG, JPEG, WebP, CMYK ve **PDF / CBZ** belgeleri listelenir
  (belgeler tür simgesiyle; önizlemede **Aç** ve paylaş). İki sekme: **Varsayılan klasör** ve **Diğer konumlar**
  ("Konum seç" / "Farklı kaydet" ile kaydedilenler). Eski kayıtların türü ve konumu adreslerinden bulunur.

### 13. Web sitesi (Next.js)

- Kurumsal mobil uygulama SaaS tasarımı, mobil uyumlu (390 px'te yatay kaydırma yok, açılır menü):
  Ana sayfa (karşılama, rakamlar, 6 özellik, vitrin, SSS, çağrı), **Hakkımızda**, **İndir** (Google Play, Windows, iOS
  kartları; Windows ve iOS "Yakında"), **İletişim**. Yasal sayfalar ve destek dar sütunda.
- **Yönetim paneli** `/admin`: Ana sayfa, Hakkımızda ve İndirmeler metinleri (TR/EN), indirme bağlantıları,
  görsel yuvaları (https adresi; boşken yer tutucu), iletişim mesajları, passkey'ler, oturumlar, işlem kaydı.
- **Veritabanı:** Vercel Neon Postgres (`db/schema.sql`, `npm run db:migrate`). İletişim formu buraya; destek ve
  hesap silme formları uygulamanın sunucusuna (değişmedi).
- **Güvenlik:** Passkey (WebAuthn) girişi, `__Host-` HttpOnly Secure SameSite=Strict oturum çerezi (8 saat / 30 dk),
  her işlemde veritabanı oturum denetimi, köken (CSRF) denetimi, istek sınırları, parametreli sorgular, düz metin
  içerik + kaçırılmış JSON-LD + CSP, yalnız https adresler, `noindex`/`no-store`, işlem kaydı.
- **SEO:** başlık/açıklama, kanonik, hreflang, Open Graph/Twitter (otomatik paylaşım görseli), JSON-LD
  (Organization, WebSite, MobileApplication, FAQPage, BreadcrumbList, AboutPage, ContactPage), site haritası, robots.
- Kurulum adımları `web/README.md` → "Panel kurulumu".

### Ayarlar çökmesi (Android 10) — tanılama

- Yerelde yeniden üretilemedi (Robolectric API 29, Türkçe yerel ayar, yayın varyantı R8'siz geçiyor). R8 iyileştirmeleri
  ve tam mod kapatıldı (`-dontoptimize`, `android.enableR8.fullMode=false`).
- Hata raporu penceresinde **Kopyala** ve **Paylaş** (e-posta) düğmeleri: giriş ya da sunucu gerekmeden tam rapor
  (sürüm, cihaz, bellek, hata izi, son ekranlar).
- Beklenmedik kapanma (çökme raporu bile yazılamadan) bir sonraki açılışta yakalanır: son ekran, bellek bilgisi ve
  logcat (crash ve uyarılar) rapora eklenir.

### Geçici sunucu seçimi ve yerel sunucu paketi

- Ayarlar → Bağlantı → **Sunucu (geçici)**: Uzak sunucu (`https://drawapi.ahmetkar.com`) ya da Yerel ağ (adres yaz
  ya da "Ağda ara"); "Bağlantıyı dene" sürüm/veritabanı bilgisini gösterir. Düz HTTP yalnız özel ağ adreslerine.
- `yerel-sunucu\`: Docker Desktop ile MongoDB + hazır sunucu (`drawguidance-server.jar`). `Yerel-Sunucuyu-Baslat.bat`
  ile kurulur; `BENIOKU.md`'de adımlar.

## Doğrulama

- **Android birim/arayüz testleri (Robolectric):** tam takım 733 test + `core:content` 254 + `core:model` 34. İlk turda
  11 test eski davranışı bekliyordu (resim modunda çizim yardımcıları, ⋮ menüsünden kaydetme, Room 20, Ayarlar kart
  sırası, yatay telefonda kayan Çiz satırı); güncellendi, düzeltilen ve ilgili 31 sınıf (107 test) yeniden çalıştırıldı:
  **hepsi geçti**. V42 için 8 yeni test dosyası (tema paketleri ve yabancı anahtarlar, efekt/nesne pencereleri, genel
  şekiller, yol üzerinde yazı, Kaydedilenler sekmeleri, tek kaydetme penceresi, sunucu seçimi, hata raporu).
- Basit resimde yapay zekâ çizim karşılaştırması artık seçicideki genel şekilleri de kullanıyor (oklar, yıldızlar…).
- **Web:** `npm run lint`, `tsc --noEmit`, birim testleri (10/10), `npm run build` başarılı. Yönetim paneli uçtan uca
  (Playwright + sanal passkey, yerel Postgres + Neon HTTP vekili): kurulum, içerik düzenleme + XSS denemesi,
  `javascript:` adres reddi, görsel yuvaları, iletişim mesajı, çıkış/giriş, sahte oturum, CSRF ve yanlış kurulum
  anahtarı reddi — **hepsi tamam**.
- **Yerel sunucu:** Docker ile MongoDB + sunucu ayağa kalktı, `http://127.0.0.1:4747` sağlık denetimi tamam.
- **APK:** imzalı yayın APK'sı (apksigner doğruladı), versionCode 42, targetSdk 36, ~61 MB (efekt ve nesne resimleri
  ~14 MB ekledi).

## Teslim

- `DrawGuidance-v42.apk` (imzalı yayın APK'sı), `yerel-sunucu-v42.zip`.
