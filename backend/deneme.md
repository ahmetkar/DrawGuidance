Hem **yerel Windows geliştirme bilgisayarınızda (Docker Desktop)** hem de **canlı Google Cloud VM (Linux)** sunucunuzda tüm yönetim komutlarını saniyeler içinde ve son derece güvenli bir şekilde çalıştırabilmeniz için kapsamlı bir rehber hazırladım.

Bu rehberde; komutları kısaltan pratik Windows ve Linux betiklerini, her komutun ne anlama geldiğini, aldığı parametrelerin detaylarını ve gerçek kullanım senaryolarını bulacaksınız.

---

# 📖 DrawGuidance API CLI Yönetim Rehberi (v43/v44)

---

## 🛠️ 1. Kolay Erişim Betikleri (Kısayollar)

Uzun Java bellek ve kütüphane parametrelerini [18, 60] her seferinde yazmamak için işletim sisteminize uygun betiği bir kez oluşturun.

### A. 🪟 Windows (Yerel Bilgisayarınız İçin)
Çalışma dizininizde (`c:\Users\ahmet\Documents\DrawGuidance\backend\docker\swarm`) veya projenin kök dizininde **`dg-cli.bat`** adında bir dosya oluşturup içine aşağıdaki kodları kaydedin:

```batch
@echo off
:: Windows Local Docker Desktop Kısayol Betiği
for /f "tokens=*" %%i in ('docker ps -q -f name=drawguidance_api -f status=running') do set CONTAINER_ID=%%i

if "%CONTAINER_ID%"=="" (
    echo [HATA] Calisan aktif 'drawguidance_api' konteyneri bulunamadi!
    exit /b 1
)

set ARGS=%*
if "%ARGS%"=="" set ARGS=help

:: Belleği kısıtlı tutarak ve Zstd kütüphane yollarını mapleyerek çalıştırır
docker exec -it %CONTAINER_ID% sh -c "exec java -Xms16m -Xmx64m -XX:+UseSerialGC -DZstdNativePath=/app/native/libzstd-jni.so -Dorg.xerial.snappy.lib.path=/app/native -Dorg.xerial.snappy.lib.name=libsnappyjava.so -jar /app/app.jar cli %ARGS%"
```

### B. 🐧 Google Cloud VM (Linux Canlı Sunucu İçin)
VM üzerinde `dg-cli.sh` dosyası oluşturup aşağıdaki komutları kaydedin [91]:

```bash
#!/bin/bash
CONTAINER_ID=$(docker ps -q -f name=drawguidance_api -f status=running | head -n 1)

if [ -z "$CONTAINER_ID" ]; then
    echo "❌ Hata: Çalışan aktif drawguidance_api konteyneri bulunamadı!"
    exit 1
fi

ARGS="$@"
if [ -z "$ARGS" ]; then ARGS="help"; fi

docker exec -it "$CONTAINER_ID" sh -c "exec java \
  -Xms16m -Xmx64m -XX:+UseSerialGC \
  -DZstdNativePath=/app/native/libzstd-jni.so \
  -Dorg.xerial.snappy.lib.path=/app/native \
  -Dorg.xerial.snappy.lib.name=libsnappyjava.so \
  -jar /app/app.jar cli $ARGS"
```
*Dosyayı kaydettikten sonra VM terminalinde şu komutla çalıştırma yetkisi verin:* `chmod +x dg-cli.sh`

> **Pratik Kullanım:** Aşağıda anlatılan tüm test komutlarını Windows'ta `./dg-cli.bat <komut>`, Linux'ta ise `./dg-cli.sh <komut>` şeklinde doğrudan koşturabilirsiniz!

---

## 📦 2. PAKETLER (COMIC, NOTES, ART)

Uygulamanın ana modlarına (Çizgi Roman, Not Defteri, Sanat) ait çizim şablonu paketlerini yönetir [395].

### A. Modları ve Pencereleri Görme
Uygulamada hangi pencerelerin (katmanların) aktif olduğunu gösterir [396]:
```bash
./dg-cli.sh paket pencereler
```
*Çıktı Örneği:* `COMIC -> objects, effects, backgrounds, balloons, characters...` [18]

### B. Paket Listeleme ve İnceleme
```bash
# Sadece COMIC modundaki paketleri listele
./dg-cli.sh paket listele --mod COMIC

# Belirli bir paketin detayını ve öğelerini (taslak/yayınlanmış durumunu) göster
./dg-cli.sh paket goster uzay-macerasi
```

### C. Yeni Çizim Paketi Ekleme (`paket ekle`)
Yeni bir paket oluştururken her bir öğeyi `--oge` parametresi ile tek tek ekleyebilirsiniz [400].
*   **Format:** `pencere_adı|öğe_başlığı|görsel_adresi[|isteğe_bağlı_kategori]` [401]
*   **Modlar:** `COMIC`, `NOTES`, `ART` [395]

```bash
./dg-cli.sh paket ekle \
  --ad "Cadılar Bayramı" \
  --mod COMIC \
  --kimlik cadilar-bayrami \
  --oge "objects|Balkabağı|gs://dgpackets/comics/pumpkin.png|Süsler" \
  --oge "characters|Tatlı Hayalet|gs://dgpackets/comics/ghost.png|Canavarlar" \
  --oge "backgrounds|Korkunç Şato|gs://dgpackets/comics/castle.png"
```
*Önemli:* Görsel adreslerini `https://...` şeklinde doğrudan bir sunucu adresi veya AWS S3/GCP Bucket için `gs://bucket_adi/nesne_yolu` şeklinde belirtebilirsiniz [10, 34, 401].

### D. Paket Güncelleme (`paket guncelle`)
Var olan paketlerin ismini değiştirmek, yeni öğe eklemek ya da silmek için kullanılır [402].
```bash
# Paket adını güncelle ve yeni bir öğe ekle:
./dg-cli.sh paket guncelle cadilar-bayrami --ad "Korkunç Cadılar Bayramı" --oge "objects|Yarasa|gs://dgpackets/comics/bat.png"

# Belirli bir öğeyi ismini yazarak paketten çıkar:
./dg-cli.sh paket guncelle cadilar-bayrami --cikar "Balkabağı"
```

### E. Paketi Yayınlama (`paket yayinla`)
Eklenen tüm paketler varsayılan olarak **TASLAK (DRAFT)** durumundadır ve kullanıcılara doğrudan gitmez [10]. Kullanıcılara dağıtıma hazır hale getirmek için **mutlaka yayınlanmalıdır** [403]:
```bash
./dg-cli.sh paket yayinla cadilar-bayrami
```

---

## 🔔 3. BİLDİRİMLER VE DUYURULAR

Cihazlara push bildirim (Firebase kullanılmaz, cihaz sunucuyu yoklar) ve uygulama içi görsel duyuru kartları ekler [3, 405].

### A. Bildirimler (Düz Metin)
Uygulamadaki bildirim kutusuna düşecek olan mesajlardır [32].
```bash
# 1. Yeni bildirim ekle
./dg-cli.sh bildirim ekle --baslik "Cadılar Bayramı Özel!" --aciklama "Korkunç çizim paketleri yayında, hemen keşfet!"

# 2. Tüm bildirimleri listele (ID'leri ve içerikleri görmek için)
./dg-cli.sh bildirim listele

# 3. Belirli bir bildirimi güncelle
./dg-cli.sh bildirim guncelle <bildirim_id> --baslik "Son Gün! Cadılar Bayramı Kampanyası"
```

### B. Duyurular (Resimli Manşetler)
Uygulama açılışında veya anasayfada çıkan büyük, resimli duyuru kartlarıdır [32].
```bash
# 1. Yeni duyuru ekle (--resim parametresi zorunludur)
./dg-cli.sh duyuru ekle --baslik "v44 Güncellemesi!" --aciklama "Çocuk kilidi ve 3B dışa aktarma modülü eklendi." --resim "https://api.ahmetkar.com/announcements/banner.png"

# 2. Duyuruları listele
./dg-cli.sh duyuru listele
```

---

## 📱 4. UYGULAMA SÜRÜMÜ VE GÜNCELLEMELERİ (RELEASE)

Uygulamanın yeni sürümlerini kaydetmek, güncelleme uyarısı göndermek ve eski sürümleri zorunlu güncellenmeye yönlendirmek için kullanılır [13, 14, 409].

```bash
# 1. Yeni sürüm ekleme
# --kod: versionCode (ör. 44)
# --ad: versionName (ör. 1.2.0)
# --en-eski: Bu kodun altındaki sürümleri kullanan cihazlar uygulamayı açınca "Zorunlu Güncelleme" ekranıyla karşılaşır.
./dg-cli.sh surum ekle --kod 44 --ad 1.2.0 --ileti "Daha kararlı bir deneyim için uygulamayı güncelleyin." --en-eski 40

# 2. Kayıtlı uygulama sürümlerini listele
./dg-cli.sh surum listele
```

---

## 🚀 5. GÖNDERME VE DAĞITIM (DISPATCH)

Sisteme kaydettiğiniz paket, bildirim, duyuru veya sürüm güncellemelerini kullanıcılara **fiziksel olarak ulaştırma** aşamasıdır [15, 414].

### A. Herkese Gönderme (Tüm Aktif Cihazlar)
Yayınlanmış bir paketi veya duyuruyu sistemdeki tüm kayıtlı cihazlara gönderilmek üzere sıraya ekler [415]:
```bash
# Bir paketi tüm kullanıcılara gönder
./dg-cli.sh gonder paket cadilar-bayrami --herkes

# Bir duyuruyu tüm kullanıcılara gönder
./dg-cli.sh gonder duyuru <duyuru_id> --herkes
```

### B. Belirli Kullanıcılara Gönderme (Hedefli Test)
Canlıya almadan önce sadece belirlediğiniz test hesaplarına (Firebase UID değerlerine) gönderim yaparak cihaz üzerinde test edebilirsiniz [416]:
```bash
./dg-cli.sh gonder paket cadilar-bayrami --kullanicilar "TEST_UID_123,TEST_UID_456"
```

### C. Dağıtım Kuyruğunu Yönetme ve İzleme
```bash
# 1. Gönderilen tüm dağıtım işlerini (Dispatches) ve durumlarını listele
./dg-cli.sh gonderim listele

# 2. Devam eden veya kuyrukta bekleyen bir gönderimi iptal et (Kalan cihazlara gitmez)
./dg-cli.sh gonderim iptal <gonderim_id>

# 3. Cihaz bazlı anlık teslimat durumlarını (Teslim edildi, bilgi gitti vb.) listele
./dg-cli.sh teslimat listele --gonderim <gonderim_id>
```

---

## 👥 6. KULLANICILAR VE CİHAZLAR

Veritabanında kayıtlı profilleri ve HMAC-SHA256 ile şifrelenmiş cihaz bilgilerini görmenizi sağlar [14, 110, 420].

```bash
# 1. Sistemdeki kullanıcıları listele veya ara (DisplayName veya Email ile)
./dg-cli.sh kullanici listele --ara "ahmet"

# 2. Sistemdeki aktif cihazları ve inbox (gelen kutusu) sürümlerini listele
./dg-cli.sh cihaz listele
```

---

## ⚡ 7. DİĞER KOMUTLAR

```bash
# 1. İşçi Turunu Tetikleme (isci tur)
# Normal şartlarda arka plandaki dağıtım işçisi 10 saniyede bir çalışır. 
# Test yaparken bu 10 saniyeyi beklemek istemiyorsanız, işçiyi el ile hemen tetikler:
./dg-cli.sh isci tur

# 2. Taban Paketleri Yükleme (taban-paket)
# Uygulama pencerelerinde varsayılan olarak gelen resimleri GCP Cloud Storage'a yükler ve kaydeder:
./dg-cli.sh taban-paket --klasor /tmp/basepack --yukle
```

---

## 💡 Pratik Bir Test Senaryosu (Örnek İş Akışı)

Sıfırdan bir paketi ekleyip kendi test cihazınızda nasıl test edeceğinizin adımları:

1.  **Paketi ekleyin (Cadılar Bayramı):**
    `./dg-cli.sh paket ekle --ad "Halloween" --mod COMIC --kimlik hw-test --oge "objects|Pumpkin|gs://dgpackets/pumpkin.png"`
2.  **Paketi onaylayın (Yayınlayın):**
    `./dg-cli.sh paket yayinla hw-test`
3.  **Sadece kendi test Firebase UID'nize gönderin:**
    `./dg-cli.sh gonder paket hw-test --kullanicilar "SİZİN_FIREBASE_UID_DEĞERİNİZ"`
4.  **İşçiyi hemen tetikleyin:**
    `./dg-cli.sh isci tur`
5.  **Teslimatı kontrol edin:**
    `./dg-cli.sh teslimat listele`
    *(Cihazınızda uygulamayı açtığınızda paketin hemen indiğini ve çizim pencerelerinde belirdiğini göreceksiniz).*



ahmetkar8040@drawguidance-backend:~$ M=$(docker ps -q -f name=drawguidance_mongo1)
ahmetkar8040@drawguidance-backend:~$ docker exec -it "$M" sh -c 'mongosh -u dgadmin --password "$(cat /run/secrets/mongo_root_password)" --authenticationDatabase admin'