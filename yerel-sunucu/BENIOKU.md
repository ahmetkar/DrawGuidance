# DrawGuidance yerel ağ sunucusu (v43)

Uygulamanın sunucusunu kendi bilgisayarında çalıştırır. Telefon aynı Wi-Fi'dayken uygulama bu sunucuya bağlanır.
Uzak sunucu (`https://drawapi.ahmetkar.com`) çalışmadığında ya da denemek istediğinde kullanılır. Kaynak koddan
derleme gerekmez: sunucu hazır derlenmiş olarak gelir (`drawguidance-server.jar`).

## Gerekenler

- Windows 10/11 ve **Docker Desktop**: <https://www.docker.com/products/docker-desktop/>. Kurduktan sonra
  bilgisayarı bir kez yeniden başlat.
- İnternet: yalnız ilk açılışta MongoDB ve Java imajlarını indirmek için. Sunucu, Google girişini doğrulamak için
  Google'ın açık anahtarlarına da ulaşır.
- Telefon ile bilgisayar **aynı Wi-Fi ağında** olmalı.

## Kurulum

1. Bu klasörü bilgisayarda istediğin bir yere kopyala. Örnek: `C:\DrawGuidance\yerel-sunucu`.
   Klasörde `drawguidance-server.jar` bulunmalı; teslim paketinde gelir.
2. **`Yerel-Sunucuyu-Baslat.bat`** dosyasına çift tıkla. Betik sırayla şunları yapar:
   - Docker Desktop'ı açar ve hazır olmasını bekler.
   - İlk seferde `.env` dosyasını rastgele parolalarla üretir. Bu dosyayı kimseyle paylaşma.
   - MongoDB'yi ve sunucuyu başlatır.
   - Telefonda yazılacak adresi gösterir. Örnek: `192.168.1.20:4747`.
3. Windows "erişime izin ver" diye sorarsa **Özel ağlar**'ı işaretleyip izin ver.
4. Telefonda uygulamayı aç: **Ayarlar → Bağlantı → Sunucu (geçici) → Yerel ağ**. Gösterilen adresi yazıp
   **Bu sunucuyu kullan**'a dokun ya da **Ağda ara**'ya dokun. **Bağlantıyı dene** "Bağlantı başarılı" demeli.
5. Uzak sunucuya dönmek için aynı yerde **Uzak sunucu**'ya dokun.

## Diğer düğmeler

| Dosya | Ne yapar |
|---|---|
| `Yerel-Sunucuyu-Durdur.bat` | Sunucuyu durdurur. Veritabanı korunur. |
| `Yerel-Sunucu-Durumu.bat` | Sunucu çalışıyor mu, telefonda hangi adres yazılmalı. |
| `Yerel-Sunucu-Gunlugu.bat` | Sunucunun son kayıtlarını `logs\api.log` dosyasına yazar. |
| `Yerel-Sunucuyu-Sifirla.bat` | Kapsayıcıları **ve veritabanını** siler (onay ister). |
| `Yonetim.bat` | v43 paket, bildirim, duyuru ve sürüm yönetimi (aşağıda). |

Docker Desktop açık olduğu sürece sunucu bilgisayar açılınca kendiliğinden yeniden başlar
(`restart: unless-stopped`).

## Paketler, bildirimler ve duyurular (v43)

Sunucu, cihazlara içerik paketi, bildirim, duyuru ve uygulama güncellemesi gönderir. Firebase gerekmez: telefon
sunucuyu yoklar. Uygulama açıkken sunucu yeni bir şey olana kadar yanıtı bekletir (uzun yoklama), bu yüzden gönderilen
hemen gelir. Uygulama kapalıyken telefon yaklaşık 15 dakikada bir sorar.

**Cloud Storage.** Paket resimleri Google Cloud Storage'daki `dgpackets` kovasındadır. Betik ilk açılışta hizmet
hesabı anahtarını `..\yayin-anahtari\gcs-service-account.json` dosyasından `sirlar\gcs.json`'a kopyalar. Bu
klasörü kimseyle paylaşma. Anahtar başka yerdeyse `.env`'deki `GCS_KEY_SOURCE` satırını değiştir.

**Taban paketler.** Çizgi roman, not alma ve resim modlarının taban paketleri kovaya yüklüdür. Sunucu açılışta
bunların kayıtlarını kendiliğinden oluşturur.

**Cihaz anahtarı.** `.env`'deki `DEVICE_DATA_KEY` cihaz bilgilerini veritabanında şifreler. Betik bu anahtarı ilk
açılışta üretir. Anahtarı değiştirme: değişirse kayıtlı cihaz bilgileri okunamaz.

`Yonetim.bat` komutları çalışan sunucunun içinde çalıştırır. Örnekler:
  
```
Yonetim.bat yardim
Yonetim.bat paket listele
Yonetim.bat bildirim ekle --baslik "Yeni paket" --aciklama "Uzay paketi geldi"
Yonetim.bat gonder bildirim <kimlik> --herkes
Yonetim.bat duyuru ekle --baslik "Hoş geldin" --aciklama "..." --resim https://...
Yonetim.bat gonderim listele
Yonetim.bat teslimat listele --gonderim <kimlik>
```

Dosya isteyen komutlar için dosyayı `yonetim\` klasörüne koy ve yolu `yonetim\dosya.png` diye yaz.

## Telefon bağlanamıyorsa

- Bilgisayar ile telefon aynı Wi-Fi'da mı? Misafir ağları cihazları birbirinden ayırır.
- Windows Güvenlik Duvarı 4747 kapısına izin vermeli. Betik Windows ayarlarını kendisi değiştirmez. Elle izin
  vermek için **yönetici** komut isteminde:

  ```
  netsh advfirewall firewall add rule name="DrawGuidance 4747" dir=in action=allow protocol=TCP localport=4747 profile=private
  ```

- Bilgisayarın tarayıcısında `http://127.0.0.1:4747/api/health` adresi `{"status":"ok",...}` göstermeli.

## Güvenlik

- Telefon ile bilgisayar arasındaki bağlantı **şifresizdir** (HTTP). Bunu yalnız kendi ev ağında kullan.
- Uygulama düz HTTP'yi yalnız yerel ağ adreslerine (192.168.x.x, 10.x.x.x, 172.16–31.x.x) gönderir. İnternetteki
  bir adrese yerel sunucu olarak bağlanmaz.
- Veritabanı yalnız bu bilgisayardan erişilebilir (`127.0.0.1:27017`). Kullanıcı adı ve parola `.env`'dedir.
- Yerel sunucudaki hesaplar ve abonelik kayıtları uzak sunucudakilerden ayrıdır.

## Klasördekiler

- `docker-compose.yml`: MongoDB (tek üyeli kopya kümesi `rs0`) ve Java 21 üzerinde sunucu.
- `mongo/`: kopya kümesini kuran betikler (`backend/docker/mongo`'dan).
- `.env.ornek`: ayarların örneği. `.env` ilk açılışta bundan üretilir.
- `yerel-sunucu.ps1`: düğmelerin çalıştırdığı PowerShell betiği. Son çalışmanın kaydı: `logs\son-calisma.log`.
- `sirlar/`: Cloud Storage anahtarının kopyası. Depoya girmez, paylaşılmaz.
- `yonetim/`: yönetim komutlarına verilecek dosyalar.
- `drawguidance-server.jar`: derlenmiş sunucu. Depoda tutulmaz; `backend` klasöründe `gradlew bootJar` ile
  üretilir (`backend/build/libs/`).
