# v49 — Backend ve Google Cloud Storage rehberi

_Ahmet için adım adım. v49'daki istemci değişiklikleri (menü, çizgi roman arka planı, eğitimler, freeRASP, dpt-shell) **sunucu şemasını değiştirmez**; sunucu sürümü **44.0.0** kalır. Bu rehber iki şeyi kapsar: (A) v49'un backend/GCS açısından ne gerektirdiği (çok az), (B) önceki sürümlerden (v43/v44) **hâlâ bekleyen** GCS + dağıtım kurulumu ve kovanın güvenli yapılandırması. Komutlar `gcloud`/`gsutil` içindir; projeniz `drawguidance`, kova `dgpackets`._

---

## A. v49 backend/GCS açısından ne gerektiriyor?

**Kısa cevap: yeni bir sunucu/GCS değişikliği gerektirmez.** Nedeni:

- **freeRASP (Talsec Community)** tamamen istemci tarafı bir SDK'dır. Community sürümü **sunucu bileşeni gerektirmez**; tehdit raporları isteğe bağlı olarak Talsec'in kendi bulut portalına gider (aşağıda "Talsec portalı"). Sizin backend'inize ya da GCS'ye bir şey yazmaz.
- **dpt-shell** bir paketleme aracıdır; yalnız yerel makinede APK üzerinde çalışır. Sunucu/GCS ilgisi yoktur.
- **Menü, çizgi roman arka planı, eğitimler** tamamen istemci UI değişiklikleridir.

Yani v49 için **zorunlu** backend/GCS işi yok. İki **isteğe bağlı** nokta:

### A.1 (İsteğe bağlı) Talsec portalı — tehdit telemetrisi

`TalsecGuard.WATCHER_MAIL` (şu an `ahmetkar.1258@gmail.com`) yalnız Talsec'in ücretsiz geliştirici portalına kaydolursanız anlam taşır; o zaman root/hook/kurcalama olaylarının e-posta/panel özetini alırsınız. Kaydolmak:

1. https://talsec.app → ücretsiz hesap.
2. Uygulama paketini (`com.karapp.drawguidance`) ve aynı `watcherMail` adresini girin.
3. Ek kod gerekmez; SDK zaten gömülü. İstemiyorsanız bu adımı atlayın — koruma yine çalışır, yalnız telemetri gelmez.

### A.2 (İsteğe bağlı ama önerilir) Play App Signing hash'lerini doğrulayın

freeRASP'ın bütünlük denetimi, uygulamanın **imza sertifikası** Base64 SHA-256 hash'lerini bilmek zorundadır. `TalsecGuard.SIGNING_HASHES` içine 4 hash konuldu: release/yükleme anahtarınız + verdiğiniz 3 Play App Signing SHA-256. Play, yüklediğiniz AAB'yi **kendi anahtarıyla yeniden imzalar**; bu yüzden Play anahtarları şarttır, yoksa Play'den inen her kurulum "kurcalanmış" sanılıp (kapatma politikası yüzünden) kendini kapatır.

**Doğrulama (gerçek cihazda, yayından önce):** iç test kanalına AAB yükleyin, o kanaldan indirip açın; kapanmamalı. Kapanırsa hash listesi eksik/yanlıştır (Günlükler'de `Talsec` etiketi "appIntegrity" yazar).

> Not: Play Console → Yayın → Kurulum → **Uygulama imzalama**'da birden çok sertifika olabilir (ör. anahtar yükseltme). Orada görünen **tüm** SHA-256'ları Base64'e çevirip listeye ekleyin. Dönüştürme:
> ```bash
> echo "AA:BB:...:FF" | tr -d ':' | xxd -r -p | openssl base64
> ```

---

## B. Bekleyen GCS + dağıtım kurulumu (v43/v44)

Devir belgesine göre uzak sunucuya (`https://drawapi.ahmetkar.com`) v43 dağıtım bileşeni **henüz kurulmamış olabilir**. Kurulmazsa uygulama paket yoklamasında 404 alıp sessizce bekler (çökme yok) — ama hazır paketler (base-comic/notes/art ve gelecekteki premium paketler) dağıtılamaz. v49'u yayınlarken bunu tamamlamak mantıklı.

### B.1 GCS kovası `dgpackets`

Zaten varsa atlayın; yoksa:

```bash
gcloud config set project drawguidance

# Tek bölge (ör. Avrupa) — düşük gecikme + maliyet. İstemci imzalı V4 URL kullanır.
gsutil mb -l europe-west3 -b on gs://dgpackets
```

- `-b on` → **Uniform bucket-level access** (nesne-tek-tek ACL yok; erişim yalnız IAM + imzalı URL ile). Güvenlik için şart.
- Kova **özel** kalmalı (herkese açık yapmayın). İstemci, sunucunun ürettiği **imzalı V4 URL**'lerle indirir.

### B.2 Hizmet hesabı (imzalı URL üretimi için)

Sunucu, nesneleri imzalı URL ile sunar; bunun için imza yetkili bir hizmet hesabı gerekir:

```bash
gcloud iam service-accounts create dgpackets-signer \
  --display-name="DrawGuidance packets signer"

# Yalnız bu kovada nesne okuma + imzalama yeter (en az yetki):
gsutil iam ch \
  serviceAccount:dgpackets-signer@drawguidance.iam.gserviceaccount.com:roles/storage.objectViewer \
  gs://dgpackets

# İmzalı URL için anahtar (JSON) üret — bu dosya "gcs-service-account.json" olur:
gcloud iam service-accounts keys create gcs-service-account.json \
  --iam-account=dgpackets-signer@drawguidance.iam.gserviceaccount.com
```

- **V4 imzalı URL** için hizmet hesabının ayrıca `roles/iam.serviceAccountTokenCreator` yetkisi gerekebilir (sunucu imzalamayı IAM API ile yapıyorsa). Kendi JSON anahtarıyla imzalıyorsa gerekmez.
- Bu JSON **gizli**dir, git'e girmez. Sunucuya `GCS_SERVICE_ACCOUNT_JSON` olarak (Docker secret) verilir.

### B.3 Sunucu ortam değişkenleri

`backend/docker/.env` (ya da Swarm'da `stack.yml` sırları):

```
GCS_BUCKET=dgpackets
GCS_SERVICE_ACCOUNT_JSON=/run/secrets/gcs_service_account   # dosya yolu ya da JSON içeriği
DEVICE_DATA_KEY=<openssl rand -base64 32 çıktısı>           # cihaz verisi AES-GCM anahtarı (üretimde zorunlu)
FIREBASE_PROJECT_ID=drawguidance
```

`DEVICE_DATA_KEY` üretimi:
```bash
openssl rand -base64 32
```

Docker Swarm sırları (devir notundaki `stack.yml`):
```bash
printf '%s' "$(cat gcs-service-account.json)" | docker secret create gcs_service_account -
printf '%s' "$(openssl rand -base64 32)"      | docker secret create device_data_key -
```

### B.4 Taban paketleri yükleyin

Uygulama açılışta `base-packages/manifest.json`'u ve taban paketleri (`base-comic`, `base-notes`, `base-art`) kovadan okur. Bunları bir kez yükleyin (sunucu deposundaki üretilmiş paket klasörlerinden):

```bash
# Örnek düzen — gerçek içerik backend/dağıtım betiğinizden gelir:
gsutil -m cp -r base-packages/manifest.json gs://dgpackets/base-packages/manifest.json
gsutil -m cp -r base-comic/   gs://dgpackets/base-comic/
gsutil -m cp -r base-notes/   gs://dgpackets/base-notes/
gsutil -m cp -r base-art/     gs://dgpackets/base-art/
```

> İçerik üretimi backend/CLI tarafındadır (`server/distribution`, `java -jar drawguidance-server.jar cli …`). Devir notu ve `backend/docs/v43-dagitim.md`'ye bakın.

### B.5 Doğrulama

```bash
# Sağlık:
curl -s https://drawapi.ahmetkar.com/api/v1/health

# Taban paket manifesti imzalı URL dönmeli (cihaz başlığıyla):
# (gerçek uç ve başlıklar v43-dagitim.md'de)
```

Uygulamada: yeni cihazda açılışta base paketlerin indiğini ve çizgi roman/not hazır içeriğinin geldiğini doğrulayın.

---

## C. GCS güvenliği (kova sertleştirme)

İstemci güvenliğini (freeRASP) sunucu tarafında da desteklemek için kovayı sıkılaştırın:

1. **Uniform bucket-level access: açık** (B.1). Nesne ACL'si kullanmayın.
2. **Herkese açık erişim engeli:**
   ```bash
   gcloud storage buckets update gs://dgpackets --public-access-prevention
   ```
3. **İmzalı URL ömrü kısa** (ör. 15 dk). Sunucu zaten imzalı V4 üretir; süreyi kısa tutun ki sızan URL işe yaramasın.
4. **Yalnız okuma imzalı URL:** istemci yalnız indirir; yükleme/silme imzalı URL **üretmeyin**.
5. **Sürümleme + yaşam döngüsü** (kaza/bozulmaya karşı):
   ```bash
   gsutil versioning set on gs://dgpackets
   # Eski sürümleri 30 günde temizle (örnek lifecycle.json):
   gsutil lifecycle set lifecycle.json gs://dgpackets
   ```
   `lifecycle.json`:
   ```json
   { "rule": [
     { "action": {"type": "Delete"},
       "condition": {"numNewerVersions": 3, "isLive": false} }
   ]}
   ```
6. **CMEK (isteğe bağlı):** hassas değilse gerekmez; paketler telif-serbest hazır içerik.
7. **Erişim günlüğü / Data Access audit log** (isteğe bağlı): kim indiriyor görünür.
8. **CORS:** istemci native Android (tarayıcı değil), CORS gerekmez. Web yöneticisi kovaya erişmiyorsa CORS ayarı yapmayın.

---

## D. Özet kontrol listesi

- [ ] (v49 için zorunlu) Yok — sunucu 44.0.0 kalır.
- [ ] (Önerilir) Play App Signing SHA-256'ları doğrula → freeRASP imza listesi tam; iç testte Play kopyası açılıyor.
- [ ] (İsteğe bağlı) Talsec portalına kaydol (telemetri).
- [ ] (Bekleyen v43/v44) `dgpackets` kovası, `dgpackets-signer` hizmet hesabı + JSON anahtar.
- [ ] `GCS_BUCKET`, `GCS_SERVICE_ACCOUNT_JSON`, `DEVICE_DATA_KEY` sunucuda tanımlı.
- [ ] Taban paketler + `base-packages/manifest.json` kovada.
- [ ] Kova sertleştirme (uniform access, public-access-prevention, kısa imzalı URL, versioning+lifecycle).
- [ ] Sağlık ve paket yoklaması cihazda doğrulandı.
