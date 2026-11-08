# DrawGuidance v30 — durum

_2026-10-12 · Room v11 (şema değişmedi; yapay zekâ ayarı bir kez açıldı) · minSdk 21 – targetSdk 36_

## İstekler ve yapılanlar

1. **Resimde arka plan kaldırma ve efektler (bütün modlarda).**
   - **Nereden açılır:**
     - "Resmi düzenle" üst menüsündeki **Arka planı kaldır** ve **Efektler**.
     - Resmin bağlam menüsü.
     - Resim, not alma ve çizgi roman tuvallerinin hepsinde var.
   - **Arka planı kaldır:**
     - Kenardaki renkler Lab uzayında kümelenir; kenara değen konunun renkleri ayıklanır.
     - Arka plan, renk uzaklığı ve yerel renk adımıyla büyütülür. Böylece gradyanlı zemin izlenir, keskin kenarda durulur.
     - İsteğe bağlı olarak iç boşluklar da kaldırılır (harf içleri, kol arası).
     - Ayarlar: tolerans, kenar yumuşaklığı, otomatik bul.
     - Önizlemeye dokunarak bölge kaldırılır ya da geri getirilir. Son dokunuş geri alınabilir.
     - Kenar renginden arka plan rengi ayrıştırılır, beyaz hale kalmaz.
     - Önizleme damalı zeminde canlı güncellenir ve kaldırılacak payı gösterir.
   - **Efektler:**
     - 16 hazır görünüm, küçük resimleriyle: Özgün, Siyah-beyaz, Sepya, Negatif, Eski fotoğraf, Soğuk, Sıcak, Kara film, Canlı, İki ton, Afiş, Piksel, Karakalem, Kabartma, Yağlı boya (Kuwahara), Parıltı.
     - 10 ayar: parlaklık, karşıtlık, doygunluk, sıcaklık, ton, bulanıklık, keskinlik, vinyet, gren, solukluk.
     - Saydamlık korunur.
   - **İşleme:**
     - Arka planda yapılır. Büyük resimler cihaz sınıfına göre küçültülür.
     - İşlerken şeffaf yükleme perdesi görünür.
     - Sonuç yeni resim verisi olarak saklanır. Geri alma tek adımdır.
2. **Seçilen elemanlardan istenenleri gizleme.**
   - **Gizle** şu yerlerde: Seç üst menüsü, bağlam menüsü, Elemanlar paneli.
   - Tek eleman seçiliyse doğrudan gizlenir. Birden çoksa hangilerinin gizleneceği sorulur (hepsi işaretli açılır).
   - Gizlenen elemanlar:
     - Çizilmez; dışa aktarmada ve küçük resimde görünmez.
     - Seçilemez; yeni çizimlerle birleşmez.
     - Kayıtta korunur.
   - Yeniden gösterme:
     - Elemanlar panelindeki her elemanın ve grubun **göz** düğmesi.
     - **Tümünü göster**.
     - Gizle penceresindeki "Gizlenenler" listesi.
3. **Yazı rengi ilk değişiklikte değişmiyordu: düzeltildi.**
   - **Sebep:** Yazı düzenlenirken üstteki renk/yazı satırından açılan fırça panelinde seçilen renk yalnız kalemi değiştiriyordu. Düzenlenen yazı eski renginde kalıyordu.
   - **Ek sorun:** Renk seçici dokunma işleyicisinde eski geri çağrıyı tutuyordu.
   - **Düzeltme:** İkisi de giderildi. Renk artık ilk seçimde yazıya geçiyor.
   - Regresyon testi var. Düzeltme geri alınınca test başarısız oluyor.
4. **Tuvalin yapay zekâ ayarı gerçekten uygulanıyor.**
   - Ayar kapalıysa o tuvalde hiçbir öneri yapılmaz: çizim, yazı ve şekil önerisi, seçimde öneri, "Düzeltme öner", tuvalden açılan 3B düzenleyicinin önerileri.
   - Düzenleyicinin ⋮ menüsünde **Yapay zekâ (bu tuval)** ile açılıp kapatılır.
   - Yeni tuval formunda ayar öntanımlı **açık**.
   - Önceki tuvallerin hepsinde ayar bir kez açıldı (veritabanı v10 → v11).
5. **Karmaşıklığa göre algoritmalar; sinir ağı yalnız çok elemanlı şekillerde.**
   - **Kademeler:**
     - **Basit tek çizgi:** geometrik uydurma.
     - **Tek çizgi:** LSH, kosinüs, kNN, yön dizisi, chamfer, $Q, IDM.
     - **İki çizgi:** tek çizgidekiler ve parça grafiği.
     - **Üç ve daha çok çizgi:** yukarıdakiler ve modun **sinir ağı**.
   - Sinir ağı 1–2 çizgide hiç çağrılmaz ve yalnız 3+ çizgili geri bildirimden öğrenir. Az elemanlıda yalnız kabul/ret sayaçları güncellenir.
   - 3B düzenleyicide ağ yalnız 3+ köşeli izlerde (üçgen, dörtgen, yıldız) çalışır. Basit izlerde Naive Bayes kullanılır.
   - **Her modun ağı internetten geniş bir eğitim setiyle eğitildi.** Kaynak: Google "Quick, Draw!" veri kümesi (CC BY 4.0).
     - Mod şablonlarıyla eşleşen 181 kategoriden ~127 bin el çizimi kullanıldı.
     - Her şablonun el çizimi benzetimleri de eklendi.
     - Ağ yapısı: 83 → 256 → şablonlar, int8.

     | Mod | Boyut | Eğitim çizimi | İnternet testi (3+ çizgi): ilk-1 | ilk-3 |
     |---|---|---|---|---|
     | Resim | 133 KB | 155 bin | %69,3 | %86,3 |
     | Not | 70 KB | 59 bin | %79,9 | %92,8 |
     | Çizgi roman | 136 KB | 154 bin | %68,7 | %86,0 |

   - **Tam arama, 3+ çizgili internet çizimlerinde:**
     - Resim: ilk-1 %13,5'ten **%52,2**'ye çıktı.
     - Not: %18,7'den **%53,7**'ye çıktı.
     - Şablon benzetimlerinde doğruluk %100 kaldı.
   - **Araştırmayla eklenen şablonlar:**
     - **Not modu (görsel not tutma dağarcığı, 7):** şerit başlık, gölgeli kutu, kesik çizgili ok, yıldız insan, madde listesi, yön tabelası, uyarı üçgeni.
     - **Çizgi roman (McCloud'un "Lexicon of Comicana" sözlüğü, 9):** toz bulutu, bayılma gözleri, buhar, parlama, ışık çizgileri, küfür simgeleri, titreme çizgileri, sallanma yayları, uçuşan ter damlaları.
     - Biçimler yeniden çizildi.
   - Ayarlar → "Moda özel yapay zekâ" bölümünde kademeler ve veri kaynağı görünür.
   - Eğitim betikleri `android/tools/modenet/` klasöründe.
6. **Geniş cihaz desteği, hız.**
   - minSdk 21 korunuyor.
   - **Bellek ve yük:**
     - Modun ağı yalnız o modda yüklenir.
     - Önizlemeler küçük kopya üstünde hesaplanır.
     - Ağır işler arka planda ve küçültülmüş resimle yapılır.
   - **Ağ:**
     - İstemci kütüphanesi bağlantı havuzu, ETag önbelleği, yeniden deneme ve akışla yükleme/indirme kullanır.
   - **Sunucu:**
     - Sanal iş parçacıkları kullanır.
     - 400 eşzamanlı istek ~1,9 sn'de tamamlandı.
7. **Karşılama ekranı, yükleme perdesi, Ana sayfa.**
   - **Açılış:**
     - Logo kendini çizer: kalem kesik çizgili kılavuzu izler. Ad ve "Rehberli çizim, akıllı öneriler" yazısı belirir.
     - Dokununca hemen geçilir.
     - Android 12+ sistem açılış ekranı da yeni simgeyle, aynı koyu zeminde açılır. Beyaz parlama olmaz.
   - **Şeffaf yükleme perdesi** şu durumlarda görünür:
     - Tuval açılırken.
     - Resim işlenirken.
     - PDF hazırlanırken ve resim kaydedilirken.
     - 3B şekil açılırken.
     - Ana sayfa yüklenirken.
     - Kısa işlerde görünmez.
   - **Ana sayfa:**
     - Saate göre karşılama ("Günaydın"…).
     - **Kaldığın yerden devam et** kartı: en son bırakılan tuvalin önizlemesi ve "… önce bıraktın".
     - Yeni oluştur kısayolları: Resim, Not, Çizgi roman, 3B şekil.
     - **Bırakıp kapattıkların** ve **Son çizimlerin** şeritleri, "Tümü" ile Tuvallerim'e geçiş.
     - Sayaçlar.
   - Alt menüde **Ana sayfa en solda**. Geniş ekranda sol rayda.
8. **Sunucu baştan Spring Boot + MongoDB.** Eski Node.js sunucusunun yerini alır; kapı 3000 ve `/api/health` aynı.
   - **Servisler:**
     - Kapsam: tuvaller (içerik ≤4 MB belgede, üstü GridFS), klasörler, kaydedilen resimler, 3B şekiller, tuval varlıkları (aynı içerik bir kez), eşitleme değişiklik akışı, kullanıcı profili.
     - Kullanıcı, Google Play girişine kadar `X-User-Id` başlığıyla seçilir.
     - Davranış: iyimser kilit (`If-Match`/409), yumuşak silme, imleçli liste, toplu yazma.
   - **MongoDB yüksek trafik ayarları:**
     - 200 bağlantılık havuz, kısa zaman aşımları, yeniden denenen okuma/yazma.
     - zstd/snappy/zlib sıkıştırma.
     - `majority` yazma onayı; listeler ikincil üyelerden okunabilir.
     - Her sorgu `ownerId` ile başlayan dizinden.
     - Değişiklikler tek gidiş-dönüşte (`findAndModify`).
   - **Docker:**
     - Varsayılan: tek üyeli kopya kümesi ve API. Anahtar dosyalı kimlik doğrulama; kullanıcılar ilk açılışta oluşturulur.
     - `rs3` profili: üç üye. `tools` profili: tarayıcıdan yönetim.
     - Ayrı dosya: **parçalı küme** (yapılandırma sunucusu, iki parça, mongos). Koleksiyonlar `ownerId` karma anahtarıyla parçalanır.
     - MongoDB bilgisayardan `127.0.0.1:27017` ile erişilir; API bütün arayüzlerde dinler.
   - **Uygulama tarafı:**
     - CRUD kütüphanesi `RemoteStore`. Telefondaki tuvali içeriği ve resimleriyle yükleyen `CanvasUploader`.
     - Ayarlar → Sunucu:
       - **Ağda ara:** yerel ağı tarar, sunucuyu bulup kaydeder.
       - **Veritabanı testi:** oluştur → oku → içerik → değiştir → listele → sil.
       - Sunucu sürümü, veritabanı durumu ve küme türü gösterilir.
   - Tam eşitleme arayüzü Google Play girişiyle eklenecek; altyapı hazır.
9. **Yeni uygulama simgesi (rehberli çizim).**
   - Çizim: mor gradyanlı zemin, kesik çizgili kılavuz eğrisi, kılavuzu izleyen sarı→pembe çizgi, kalem ve yapay zekâ parıltısı.
   - Biçimler:
     - Uyarlanabilir simge (Android 8+).
     - Android 13+ tek renkli temalı sürüm.
     - Eski sürümler için 5 yoğunlukta PNG (kare ve yuvarlak).
   - Aynı logo uygulamanın içinde de kullanılıyor: karşılama ekranı, Ana sayfa, yükleme perdesi.
   - Üretici betik: `android/tools/icon/make_icon.py`.

## Doğrulama

- **Tam koşu: 524 test** — uygulama 322, core/content 169, core/model 33. Hepsi geçti.
  - Atlanan 6 test bilerek atlanır; yalnız istenince çalışırlar: ağ eğitimi, veri dışa aktarma, değerlendirme, katalog dökümü, bir görsel kontrol.
  - Yapay zekâ ayarı artık uygulandığı için bir eski görsel testin tuvali "yapay zekâ açık" yapıldı (V20).
  - Yeni tuval formunun öntanımlısı "açık" oldu; ilk akış testi buna göre güncellendi.
- **Sunucu testleri:** 9 uçtan uca test, gerçek MongoDB 7 kopya kümesine karşı (Testcontainers). Hepsi geçti.
  - Kapsadıkları: CRUD, sürüm çakışması, imleçli liste, içerik (satır içi ve GridFS), kullanıcı ayrımı, dosyalar, yinelenmeyen varlıklar, toplu yazma, eşitleme, iz temizliği, eşzamanlılık.
  - 400 eşzamanlı istek ~1,9 sn'de tamamlandı.
- **Docker (bulutta kurulup denendi):**
  - **Kopya kümesi:**
    - Anahtar dosyası, kullanıcılar, birincil seçimi ve API sağlıklı.
    - Bilgisayardan yönetici bağlantısı ve dizinler çalışıyor.
    - Yeniden başlatmada veriler korunuyor.
  - **Parçalı küme:**
    - İki parça eklendi; API parçalamayı doğruladı.
    - 300 kullanıcının tuvalleri parçalara 149/151 dağıldı.
    - 5 MB içerik GridFS'e yazılıp aynen okundu.
- **Uygulama sunucu testleri:** gerçek sunucuya karşı.
  - `RemoteStore` ile CRUD, ETag önbelleği, 250'lik toplu yazma, 1 MB dosya yükleme/indirme.
  - Telefondaki tuvalin yüklenmesi; ikinci yüklemede varlık atlanıyor.
  - Ağda arama ve Ayarlar'daki veritabanı testi.
- **Lint:** 0 hata. `assembleDebug` başarılı (minSdk 21, targetSdk 36).
- **Yeni testler:**
  - `V30TextColorTest`: fırça panelinden ve Yazı biçimi panelinden ilk renk değişikliği.
  - `V30HideTest`: istenenleri gizleme, göz düğmesi, "Tümünü göster", geri alma.
  - `V30AiTest`: tuvalde yapay zekâ kapalıyken öneri yok.
  - `V30ImageToolsTest`: resim ve not tuvalinde arka plan kaldırma ve efekt, saydamlık, geri alma.
  - `V30HomeTest`: karşılama, Ana sayfa, kaldığın yerden devam.
  - `V30RemoteTest`.
  - `V30ModelTest`: gizli eleman kaydı.
  - `ImageToolsTest`.
  - `ModeShapesTest`: ağ yalnız 3+ çizgide, mod ağları, geri bildirim.
  - `Sketch3Test`: 3B ağ yalnız çok parçalı izde.
  - Veritabanı göçü v11.

## Sahnede tutulanlar (bilgisayara yazılmadı)

- `v30src/` (v29'a göre değişen Android dosyaları, yeni `backend/` ve `windows/` betikleri) ve `v30-kaynak.tar.gz`.
- Bilgisayara kurulum istenince yapılacaklar:
  - Dosyalar yazılacak.
  - Eski Node.js sunucusu (`server\`) yerine Docker'daki sunucu kullanılacak.
  - `Sunucuyu-Baslat.bat` Docker Desktop'ı başlatıp kapsayıcıları kuracak. Docker Desktop kurulu olmalı.

## Kaynaklar

- Quick, Draw! veri kümesi: https://github.com/googlecreativelab/quickdraw-dataset (CC BY 4.0)
- Çizgi roman simgeleri: https://en.wikipedia.org/wiki/The_Lexicon_of_Comicana
- Görsel not tutma dağarcığı: https://en.wikipedia.org/wiki/Sketchnoting
