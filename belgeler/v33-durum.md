# DrawGuidance v33 — durum

_2026-10-18 · Room v12 (olay tablosu ve cihazın kullanıcısı eklendi) · minSdk 21 – targetSdk 36 · sunucu 33.0.0_

## İstekler ve yapılanlar

1. **Şekil düzenleme menüsü sadeleşti; bütün düğmeler görünür.**
   - Üst satır: Geri al, Yinele, **Noktalar**, **Çizgiler**, **Alan boya**, [Grafik], Döndür, Ölçekle, Katmanlar. 411 dp telefonda kaydırmadan sığar.
   - Nokta düzenleme ayarları alttaki pencerede: **Taşı / seç**, **Nokta ekle**, **Çizgi çiz** (Noktalar seçiliyken açılır). Parça biçimi, Kes, Sil ve Bitti alt çubukta.
   - Benzer karmaşıklıktaki menüler ve öneriler aşağıda ("Öneriler" bölümü).
2. **Sayfa sınırı:** çizgi roman 1000, not alma (ve basit not) 200 sayfa. Sınırda Sayfa ekle/çoğalt pasif, "Sayfa sınırına ulaşıldı (N)" bildirimi; yeni tuval formu da sınırı gösterir.
3. **Araç adları ve açıklamaları (çizim alanındaki bütün menüler).**
   - Dokununca düğmenin adı, **2 sn basılı tutunca** ne işe yaradığı çizim alanının köşesindeki kutuda görünür (kutu ad için ~1,5 sn, açıklama için ~6 sn açık kalır; dokununca kapanır).
   - Üst satırlar, mod seçici, mod çubuğu, bilgi çipleri, yakınlık çipi, fırça göstergesi, sayfa şeridi, kılavuz çubuğu, başlıktaki düğmeler. Basılı tutmak düğmeyi çalıştırmaz.
   - ~115 açıklama (`strings_v33.xml`); her menü düğmesinin açıklaması olduğu testle denetleniyor.
4. **Basit çizim / gelişmiş çizim.**
   - Ana sayfada iki oluşturma düğmesi: **Basit çizim** (Basit resim, Basit not alma, Çocuklar için çizim) ve **Gelişmiş çizim** (Resim, Not alma, Çizgi roman, **3B şekil oluşturma**). Alt modlar açılan pencerede seçilir.
   - **3B şekil oluşturma** tuval kategorileri arasında: adı sorulur, 3B düzenleyici açılır; kaydedilince 3B Şekillerim'de görünür, Tuvallerim'de görünmez.
   - Tuvallerim'deki "Yeni Tuval" formunda türler iki grup halinde (gelişmiş ve basit).
   - **Basit çizimde üstte tek menü** (mod seçici yok): Fırça, Silgi, Şekil ekle, Resim ekle, Yazı ekle, 3B şekil ekle, Arka plan boyama, **Şekil boyama**, Elemanlar, Seç; basit notta ayrıca Diyagram ekle ve Formül ekle. Yalnız seçili aracın adı yazar, ötekilerin simgesi görünür; sığmazsa satır kayar; 2 sn basılı tutunca açıklama. Araçların alttaki pencereleri korunur (fırça seçici, seçim çubuğu: Taşı, Kopyala, Sil, Bitti).
   - Sınırlı katalog: 10 şekil (basit notta 9 şekil + koordinat ekseni), 10 diyagram, 10 formül, 10 hazır 3B şekil; fırçalarda 3 kategori.
   - **Çocuklar için çizim:** internetteki boyama sayfası ve çocuk çizimi kaynaklarından seçilen konularda **50 özgün boyama şekli** (Hayvanlar 9, Deniz 7, Taşıtlar 7, Meyve ve yiyecek 7, Doğa ve gökyüzü 7, Uzay 6, Oyuncak ve eşyalar 7). Kalın çizgi, her parça kapalı ve beyaz: **Şekil boyama**da parçaya dokunmak onu seçili renge boyar. Büyük 12 renkli palet; sade fırça paneli (fırça, kalınlık, palet); arka plan boyama yalnız renk; fırçalar Kalem / Geniş / Damga.
5. **Tuvallerim ve 3B Şekillerim'de anlık arama** (başlıkta): büyük/küçük harf ve Türkçe harfler (ı/i, ş/s, ğ/g, ü/u, ö/o, ç/c) fark etmez; bütün kelimeler başlıkta geçmeli. Kökte klasörlerdeki tuvaller de aranır; 3B listede hazır şekillerde bütün kategoriler aranır.
6. **Çevrimdışı çalışma (olay tablosu).**
   - Uygulama sunucu olmadan tam çalışır. Sunucuyla ilgili her iş yerel veritabanındaki **`sync_events`** tablosuna yazılır: yapılanlar (geçmiş, son 200) ve yapılacaklar. O an yapılamayan iş bekler; **bağlantı gelince, açılışta ve WorkManager ile arka planda** yapılır. Geçici hatalar üstel beklemeyle (30 sn … 6 sa) yeniden denenir, kalıcı hatalar FAILED olur.
   - Ayarlar → **Sunucu eşitleme**: bekleyen iş sayısı, katalog sürümü, son denetim, "Şimdi eşitle".
7. **Katalog sunucuda; günlük eşitleme; sunucudaki öncelikli.**
   - Hazır şekil şablonları (yapay zekâ eşleştirme tablosu, 465), vektör şekiller (290) ve modların temel sinir ağları (3) sunucunun MongoDB'sinde (`catalog_*` koleksiyonları). Tohum dosyası uygulamanın kataloğundan üretilir (`backend/src/main/resources/catalog/catalog.json.gz`, sürüm 33000).
   - Uygulama günde bir manifesti sorar (ETag; değişmediyse gövde inmez). Sunucunun sürümü yüksekse şablonlar, vektörler ve ağlar inip uygulanır; aynı anahtarlı yerleşik kayıtların yerine geçer. Kişisel öğrenme (sayaçlar, kullanıcı örnekleri, cihazda eğitilen ağ) cihazda kalır.
   - **Kullanıcı tuvalleri, 3B şekilleri, resimleri sunucuda yok**: sunucudaki tuval/klasör/dosya/varlık/eşitleme uçları ve koleksiyonları kaldırıldı; uygulamadaki bulut eşitleme kodu ve CRUD testi kaldırıldı.
8. **Google ile giriş (Firebase Authentication).**
   - Ayarlar'da (yalnız internet varken) **Google ile giriş yap**. Credential Manager ile hesap seçilir, Google kimlik jetonu Firebase'e verilir; oturum kalıcıdır.
   - İlk girişte **doğum tarihi** penceresi (gün/ay/yıl). Google kimliği, ad, soyad, e-posta, e-posta doğrulaması, profil fotoğrafı adresi, hesap oluşturma tarihi ve doğum tarihi **tek birincil kimlikle (UUID) tek satırda**: önce yerel `local_user` tablosuna (yalnız bu cihazın kullanıcısı), sonra olay tablosu üzerinden sunucunun `users` koleksiyonuna (`PUT /api/v1/users/me`, Bearer Firebase jetonu; sunucu jetonu Google'ın anahtarlarıyla doğrular).
   - Giriş yapılmışsa: ana sayfanın üst çubuğunda ad-soyad ve fotoğraf; Ayarlar'da **Hesap ayarları** (ad, soyad, doğum tarihi düzenleme; Google'dan yenile; Google hesabını değiştir; Google'dan alınan bilgiler) ve **en altta Çıkış yap**. İnternet yokken bunların hiçbiri görünmez, uygulama giriş yapılmamış gibi çalışır.
9. **Ortak hata ayıklama anahtarı** (`android/app/keystore/debug.keystore`): her bilgisayarda aynı SHA-1 (`0E:16:DE:72:…:A0:17`); Firebase'deki Android istemcisi bununla kayıtlı.

## Öneriler (benzer karmaşıklıktaki menüler — değiştirilmedi)

| Menü | Düğme | Durum | Öneri |
|---|---|---|---|
| Ekle – çizgi roman | 13 | Belirgin taşma (Perspektif, Resim ekle, Katmanlar ekran dışında) | Balon, Panel düzeni, Efekt yazısı, Hız/odak çizgileri tek "Çizgi roman" düğmesinin alt penceresine; Kılavuz + Perspektif tek "Kılavuzlar" düğmesine (9 düğme) |
| Ekle – not alma | 10 | ~8 dp taşma | Kılavuz Ekle penceresine ya da Formül + Diyagram tek "Not öğeleri" penceresine |
| Düzenle | 10 | ~8 dp taşma | Boya kutusu ve Arka plan yalnız Çiz satırında (8 düğme) |
| Resmi düzenle | 10 | ~8 dp taşma | Filtre + Efektler tek "Efektler" penceresinde iki sekme (9) |
| Seç alt çubuğu (çizim seçili) | 5 yazılı | Etiketler kesiliyor | Şekil düzenlemedeki gibi simge + kısa ad (Renk, İnce, Kalın, Yumuşat, Öner) ya da "Daha fazla" |
| ⋮ menüsü | ~9 | 5'i öneri anahtarı | "Yapay zekâ ve öneriler" alt menüsü |
| Sayfa şeridi | — | 1000 sayfada gezinmek uzun | "Sayfaya git" (numara) ya da sayfa ızgarası |
| Bağlam menüsü | 11–12 | Uzun liste | Öne/Arkaya → "Sıra", Döndür/Ölçekle → "Dönüştür" alt menüleri |

## Firebase / Google Play için notlar

- Firebase projesi `drawguidance`; `android/app/google-services.json` projede. Eklenti dosya varsa uygulanır.
- Uygulama Google Play'e yüklendiğinde **Play App Signing anahtarının SHA-1 ve SHA-256'sı** Firebase Console → Proje ayarları → Android uygulaması → "Parmak izi ekle" ile eklenmeli; yoksa Play'den kurulan sürümde giriş "yapılandırılmamış/başarısız" olur. Yükleme anahtarı (upload key) ile yerelde imzalanan yayın APK'sı için de SHA-1 eklenmeli.
- Firebase Authentication → Sign-in method → Google açık olmalı (açıldı).
- Sunucu jetonları `https://www.googleapis.com/...securetoken...` adresinden aldığı anahtarlarla doğrular: sunucunun internete çıkabilmesi gerekir (yoksa kullanıcı uçları 503 `auth_unavailable`). `docker/.env` içindeki `FIREBASE_PROJECT_ID=drawguidance` olmalı.
- minSdk 21 korunsun diye Firebase Auth 22.3.1 kullanılıyor (23.x minSdk 23 istiyor).

## Doğrulama

- **Tam test takımı:** 574 test (uygulama 365, core:content 176, core:model 33). İlk tam koşuda 3 hata çıktı, üçü de giderildi ve yeniden koşuldu:
  - Veritabanı göç testinin beklediği sürüm 12'ye güncellendi.
  - V18 (Android 7) testi için gereken Robolectric dosyası indirilemiyordu; ortam düzeltildi, test geçti.
  - V32 eğitim testi yük altında zaman aşımına uğradı; yeniden koşulunca geçti.
- **Gerçek sunucuya karşı (Docker'daki Spring Boot + MongoDB 7):** sağlık, katalog manifesti (ETag, 304) ve şablonlar uygulamanınkilerle aynı; kullanıcı ucu jetonsuz reddediliyor.
- **Sunucu testleri:** 28 test, hepsi geçti (Testcontainers MongoDB).
- **Lint:** 0 hata.
- **APK:** ortak anahtarla imzalandı (SHA-1 `0e16de72…a017`, Firebase'deki ile aynı). minSdk 21, targetSdk 36. Firebase kaynakları APK'da.
- **Ekran görüntüleri:** basit resim menüsü, çocuk boyama, ana sayfa (basit/gelişmiş, profil), Ayarlar hesap kartı, şekil düzenleme satırı, araç yardımı, arama.

## Dosyalar

- `v33src/` (v32'ye göre değişen ve yeni dosyalar; silinenler `SILINENLER.txt`), `v33-kaynak.tar.gz`, `DrawGuidance-v33-debug.apk`.
