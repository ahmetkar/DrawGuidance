# App Store yükleme rehberi — DrawGuidance iOS 1.12.1 (55)

Bu rehber, TestFlight'a yüklenen imzalı derlemeyi App Store'da yayımlamak için App Store Connect'te yapılacakları
sırayla anlatır.

| Bilgi | Değer |
|---|---|
| Uygulama | DrawGuidance (Çizim Rehberi) |
| Paket kimliği | `com.karapp.drawguidance` |
| Apple ID | 6834536997 |
| Takım | G3LKL7QH6D |
| Sürüm | **1.12.1**, derleme **55** |
| En düşük iOS | **16.0** (iPhone 8 / X ve sonrası, iPad 5. nesil ve sonrası) |
| Cihazlar | iPhone ve iPad |

> Derleme GitHub Actions ile imzalanıp App Store Connect'e yüklenir (`ios.yml`, `signed=true upload=true`).
> Yükleme bittikten sonra Apple derlemeyi 10–30 dakika işler. İşlem bitince e-posta gelir ve derleme TestFlight'ta
> görünür.

---

## 1. Ön koşullar (bir kez; yapıldıysa geçin)

1. **Sözleşmeler:** App Store Connect → **İşletme** → **Sözleşmeler** → "Ücretli Uygulamalar" sözleşmesi **Etkin**
   olmalı. Banka ve vergi formları da dolu olmalı. Bu olmadan abonelik satılmaz.
2. **Abonelik ürünü:** **Para Kazanma → Abonelikler** → grup `Premium`. Ürün kimliği
   `com.karapp.drawguidance.premium.monthly`, durumu "Gönderilmeye hazır" ya da "Onaylandı" olmalı.
   - Ürünün görünen adı ve açıklaması Türkçe ve İngilizce girilmiş olmalı.
   - Ürüne **inceleme ekran görüntüsü** eklenmiş olmalı (abonelik sayfasının görüntüsü).
   - İlk abonelik **uygulama sürümüyle birlikte** incelemeye gönderilir. Bunun için 5. adımda sürüm sayfasındaki
     "Uygulama İçi Satın Alımlar ve Abonelikler" bölümünden ürünü seçin.
3. **Sunucu bildirimleri:** **Uygulama Bilgileri → App Store Server Notifications**.
   - Üretim ve Sandbox adresi: `https://drawapi.ahmetkar.com/api/v1/billing/apple-notifications`
   - Sürüm: **Version 2**
   - Sunucu 54.0.0 olmalı (`claude/v55-backend-dagitim-plani.md`).

## 2. TestFlight'ta kontrol

1. **TestFlight** sekmesi → **iOS** → derleme **55**, durumu "Test edilmeye hazır" olmalı.
2. **Şifreleme sorusu:** Uygulama `ITSAppUsesNonExemptEncryption = NO` bildirir. Ek belge sorulmaz, uyumluluk sorusu
   çıkmaz. Çıkarsa "Yalnız işletim sisteminin şifrelemesi / muaf" seçin.
   - Paylaşımdaki şifreleme CryptoKit'tir, yani sistem kütüphanesi.
3. **Dahili test:** "Dahili Test" grubuna kendinizi ekleyin. TestFlight uygulamasından yükleyip şunları deneyin:
   - açılış, Google ve Apple ile giriş;
   - her mod;
   - çizgi roman / not alma: 4 sayfalı tuval oluşturun; alttaki sayfa şeridinden sayfalar arasında geçin, sayfa ekleyin,
     uzun basıp çoğaltın ve silin;
   - büyük ve detaylı bir fotoğraf ekleyin: artık reddedilmemeli, büyükse küçültülerek eklenmeli;
   - yeni tuval → Çizgi roman → şablonlar: "Manga: Avlu düellosu" görünmeli, "Okul macerası" görünmemeli;
   - Premium satın alma (Sandbox): TestFlight satın almaları ücretsizdir.
4. iOS 16 cihazınız varsa (iPhone 8 / X) orada da açılışı deneyin.

## 3. Sürüm sayfası

**Uygulamalar → DrawGuidance → (sol üst) iOS Uygulaması → "+" Sürüm** → `1.12.1`.

| Alan | Ne girilir |
|---|---|
| Bu sürümdeki yenilikler (TR) | Bu sürümde: çizgi roman ve not almada çok sayfalı tuval ve sayfa şeridi, eklenen resimlerde boyut ve detay sınırı kalktı, örnek manga şablonu, iOS 16 desteği ve performans iyileştirmeleri. |
| What's New (EN) | Multi-page comics and notes with a page strip, no more size or detail limits for added images, a sample manga template, iOS 16 support and performance improvements. |
| Tanıtım metni, açıklama, anahtar kelimeler | Önceki sürümden kalır; gerekirse güncelleyin. Açıklamanın sonunda Kullanım Şartları ve Gizlilik bağlantıları olmalı (abonelik kuralı 3.1.2). |
| Destek URL'si | `<site>/tr/support` |
| Pazarlama URL'si (isteğe bağlı) | `<site>/tr` |
| Telif hakkı | `2026 KarApplication` |

`<site>` = web sitenizin canlı adresi. Uygulamadaki Premium ekranı gizlilik bağlantısı için `https://drawguidance.ahmetkar.com` kullanıyor; App Store'daki bağlantılar da aynı adres olmalı.

**Derleme:** "Derleme" bölümünde **+** → **55** seçin.

## 4. Ekran görüntüleri

- Zorunlu: **iPhone 6,9"** (1320 × 2868 ya da 1290 × 2796).
- iPad desteklendiği için zorunlu: **iPad 13"** (2064 × 2752 ya da 2048 × 2732).
- Apple daha küçük boyutları bunlardan üretir.
- Hazır mağaza görselleri PC'de `tanitim\` ve `magaza52` klasörlerinde (7 dil × 6 boyut). Her dil için en az 3, en çok 10
  görüntü yükleyin.
- Yeni sayfa şeridini gösteren bir görüntü eklemek isterseniz TestFlight'taki uygulamadan ekran görüntüsü alın.

## 5. Uygulama bilgileri ve inceleme formları

1. **Uygulama Gizliliği** (sol menü → Uygulama Gizliliği). Uygulamanın gerçekten topladıkları:
   - **İletişim bilgisi → E-posta adresi:**
     - Hesap için (Firebase giriş); kullanıcıyla ilişkili; izleme için değil.
     - "Uygulama işlevi" amacıyla.
   - **Tanımlayıcılar → Kullanıcı kimliği:** hesap; uygulama işlevi; kullanıcıyla ilişkili.
   - **Satın almalar → Satın alma geçmişi:** abonelik doğrulaması; uygulama işlevi; kullanıcıyla ilişkili.
   - **Teşhis → Kilitlenme verileri / diğer teşhis verileri:**
     - Hata raporları (yalnız oturum açıkken gider); uygulama işlevi.
     - Kullanıcıyla ilişkili.
   - **Reklam verileri / cihaz kimliği:**
     - Google Mobile Ads, ATT istemez; IDFA okunmaz.
     - Reklamlar kişiselleştirilmemiştir, ama SDK teşhis ve etkileşim verisi toplar.
     - "Üçüncü taraf reklamcılığı" amacıyla **Ürün etkileşimi** ve **Diğer teşhis verileri** işaretleyin; "izleme için
       kullanılır" **Hayır**.
   - **Kullanıcı içeriği:** çizimler cihazda kalır; sunucuya gitmez.
     - Wi-Fi paylaşımı cihazdan cihaza doğrudan yapılır, Apple'ın tanımında "toplama" sayılmaz. Bu yüzden
       işaretlemeyin.
   - Gizlilik politikası URL'si: `<site>/tr/privacy`.
2. **Yaş derecelendirmesi:**
   - Şiddet, korku gibi sorulara çizgi roman karakterleri ve efektler (kılıç düellosu) için **"Seyrek / hafif çizgi
     film şiddeti"**.
   - "Sınırsız web erişimi": **Hayır**.
   - "Kullanıcılar arası iletişim / kullanıcı içeriği paylaşımı": yerel Wi-Fi paylaşımı için **Evet, sınırlı**.
   - "Reklam": **Evet**.
   - "Uygulama içi kontroller": çocuk kilidi var.
   - Uygulama **"Çocuklar" kategorisinde değil**; Kids kategorisi seçilmemeli (üçüncü taraf reklam SDK'sı var).
3. **Kategori:** Birincil "Grafik ve Tasarım", ikincil "Eğitim".
4. **İçerik hakları:** "Üçüncü taraf içeriği" → yok. Karakter ve mekân görselleri özgün; şablonlar kamu malı ya da özgün.
5. **Hesap silme:** Uygulama içinden hesap silme var: Ayarlar → Hesap bölümü → Hesabı sil (kural 5.1.1(v)).
   Web: `<site>/tr/delete-account`.

## 6. İnceleme bilgileri (App Review Information)

- **Oturum açma bilgileri:** Uygulamanın tamamı girişsiz kullanılabilir. "Oturum açma gerekli" kutusunu **boş** bırakın.
- **İletişim:** ad, telefon, e-posta (`ahmetkar.1258@gmail.com`).
- **Notlar** (İngilizce önerilir):

  > The app works fully without an account. Sign in with Google or Apple is optional and only syncs settings and
  > subscriptions. Premium is an auto-renewable subscription (com.karapp.drawguidance.premium.monthly); it can be
  > purchased in Settings → Premium using a Sandbox account. Local network access is used only for the optional
  > "Share / Live broadcast" feature between the user's own nearby devices. Drawings stay on the device.
  > To see multi-page comics: New canvas → Comic → choose 4 pages → use the page strip below the canvas.

- **Ek:** abonelik sayfasının ekran görüntüsü (isteğe bağlı).

## 7. Gönderme

1. Sürüm sayfasının üstünde **İncelemeye Ekle**.
2. "Sürüm yayını" seçimi:
   - **Onaydan sonra elle yayınla** (önerilir; yayın anını siz seçersiniz), ya da
   - **Otomatik**, ya da
   - **Aşamalı yayın** (7 gün içinde %1 → %100).
3. **İncelemeye Gönder.** İnceleme genelde 24–48 saat sürer.

## 8. Sık reddedilme nedenleri ve çözümleri

| Kural | Neden | Çözüm |
|---|---|---|
| 3.1.2 | Abonelik ekranında fiyat, süre, otomatik yenileme açıklaması ya da Kullanım Şartları ve Gizlilik bağlantıları eksik | Uygulamadaki Premium ekranında hepsi var. Açıklama metninin sonuna da iki bağlantıyı ekleyin. |
| 2.1 | Abonelik ürünü sürümle birlikte gönderilmemiş | 1.2'deki ürünü sürüm sayfasında seçin. |
| 4.8 | Google ile giriş var ama Apple ile giriş yok | Apple ile giriş var; sorun olmamalı. |
| 5.1.1 | Gereksiz izin | v55'te kamera ve tüm galeriyi okuma izni kaldırıldı. Yalnız "galeriye kaydet" ve "yerel ağ" izni var. Yerel ağ izninin açıklaması Info.plist'te yazılı. |
| 2.3.3 | Ekran görüntüleri uygulamayı göstermiyor | Gerçek uygulama ekranları kullanın (mağaza görselleri uygulama ekranlarından üretildi). |
| ITMS-91053 | Gizlilik bildirimi eksik | v55'te `PrivacyInfo.xcprivacy` eklendi: UserDefaults, dosya zamanı, sistem açılış süresi. |

## 9. Yayından sonra

- Sunucuda sürümü kaydedin ki eski sürümdeki kullanıcılara güncelleme haberi gitsin (`v55-backend-dagitim-plani.md`
  §7):
  ```bash
  cli surum ekle --kod 55 --ad 1.12.1 --ileti "…"
  ```
- App Store Connect → **Analiz** ve **Kilitlenmeler**: ilk 48 saatte kilitlenme olup olmadığına bakın. Özellikle iOS 16
  cihazlarında ve düşük bellekli iPhone 8'de.
