# v50 sunucu güncelleme rehberi

Bu rehber üretim sunucusunu (Docker Swarm, `https://drawapi.ahmetkar.com`) v44.2.0'dan **v50.0.0**'a günceller.
v50 uygulaması (Android 1.8.0 / versionCode 50 ve iOS 1.8.0 / 50) bu sunucuyla tam çalışır. Eski sunucuyla da açılır,
ama iOS aboneliğinin sunucuda doğrulanması ve Android'deki Apple ile giriş kaydı çalışmaz.

Sıra önemli: **önce sır (1), sonra imaj (2), sonra dağıtım (3)**. 1. adım atlanırsa `stack deploy` başarısız olur.

## v50'de sunucuda değişenler

| Konu | Değişiklik |
|---|---|
| Sürüm | `50.0.0` (`/api/health`). |
| İstisna hesabı kaldırıldı | `ExemptAccounts` ve `BILLING_EXEMPT_EMAILS` silindi. Artık Premium yalnızca doğrulanmış abonelikle açılır. `stack.yml`'deki satır da kaldırıldı. |
| Play aboneliği tazelik denetimi | `/api/v1/subscriptions/me`, 12 saatten eski Play kaydını Google Play'den yeniden okur. Böylece kaçan iade ya da iptal bildirimi yakalanır. Play jetonu tanımazsa hak kapanır. |
| App Store abonelikleri (StoreKit 2) | Yeni uç `POST /api/v1/subscriptions/verify-apple` (Firebase jetonu ister). Yeni uç `POST /api/v1/billing/apple-notifications` (herkese açık; Apple imzalı, IP sınırından muaf). iOS'ta alınan Premium Android'de, Android'de alınan Premium iOS'ta açılır. Ayrıntı: `docs/v50-app-store-abonelik.md`. |
| Yeni kütüphane | `com.apple.itunes.storekit:app-store-server-library:5.2.0`. Apple Root CA G3 sertifikası imajın içinde. |
| Giriş sağlayıcısı | Firebase jetonundaki `firebase.sign_in_provider` (google.com / apple.com) `users.signInProvider` alanına yazılır. Ayar gerekmez. |
| Yığın (`stack.yml`) | Yeni ortam değişkenleri: `APPLE_APP_APPLE_ID=6834536997`, `APPLE_IAP_ISSUER_ID`, `APPLE_IAP_KEY_ID=MTT897CZ3J`, `APPLE_IAP_PRIVATE_KEY_FILE=/run/secrets/apple_iap.p8`. Yeni sır: `apple_iap_key`. |
| Veritabanı | Göç yok. Plan tohumu `appleProductIds` alanını ekler; abonelik belgelerine `store` alanı gelir (eski kayıtlar Google Play sayılır). |

## 0. Hazırlık (PC)

1. GitHub'dan birleşik dalı alın:
   ```bash
   git fetch origin && git checkout ios/v49-port && git pull
   ```
   Sunucu kodu `backend/` klasöründedir.
2. App Store anahtarını bulun: `yayin-anahtari\SubscriptionKey_MTT897CZ3J.p8`. Bu dosyayı yönetici düğüme kopyalayın,
   örneğin `scp yayin-anahtari\SubscriptionKey_MTT897CZ3J.p8 <kullanici>@<yonetici>:/tmp/`.

## 1. Bir kezlik: Apple anahtarı sırrı

Yönetici düğümde:

```bash
docker secret create apple_iap_key /tmp/SubscriptionKey_MTT897CZ3J.p8
shred -u /tmp/SubscriptionKey_MTT897CZ3J.p8   # kopyayı silin; asıl dosya PC'de kalır
docker secret ls                              # apple_iap_key görünmeli
```

Öteki sırlar (`mongo_*`, `play_service_account`, `slack_webhook`, `ip_hash_salt`, `gcs_service_account`,
`device_data_key`) önceki kurulumdan kalır. Bunlara dokunmayın.

## 2. İmajı derleyip göndermek

`backend/` klasöründe:

```bash
./gradlew test                      # Testcontainers için Docker açık olmalı
export REGISTRY=ghcr.io/ahmetkar API_TAG=50
echo "$GHCR_PAT" | docker login ghcr.io -u ahmetkar --password-stdin
docker build -t "$REGISTRY/drawguidance-server:$API_TAG" .
docker push "$REGISTRY/drawguidance-server:$API_TAG"
```

Etiket sabittir (`50`). Geri alma için eski etiket (`44` ya da en son kullandığınız) saklı kalır.

## 3. Dağıtım

`backend/docker/swarm/` klasöründe (yönetici düğüme güncel `stack.yml` kopyalanmış olmalı):

```bash
export REGISTRY=ghcr.io/ahmetkar API_TAG=50 ACME_EMAIL=ahmetkar.1258@gmail.com
docker stack deploy -c stack.yml --with-registry-auth --detach=false drawguidance
docker service ps drawguidance_api
```

Kopyalar birer birer güncellenir. Sağlık denetimi geçmezse Swarm eski sürüme kendisi döner.

## 4. Denetim

```bash
curl -s https://drawapi.ahmetkar.com/api/health                                    # "version":"50.0.0"
curl -s -X POST https://drawapi.ahmetkar.com/api/v1/billing/apple-notifications \
     -H 'Content-Type: application/json' -d '{}'                                   # 400 bad_request (uç açık)
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
     https://drawapi.ahmetkar.com/api/v1/subscriptions/verify-apple                 # 401 (jeton ister)
docker service logs --since 10m drawguidance_api | grep -iE "error|exception|App Store|Planlar" | head -20
```

Günlükte şunlar görünmeli:

- `Planlar hazır: ... App Store [com.karapp.drawguidance.premium.monthly]`
- "App Store anahtar dosyası okunamadı" **görünmemeli**. Görünürse `apple_iap_key` sırrı yanlış ya da boştur.

Ayrıca eski istisna hesabıyla (ahmetkar8040@gmail.com) giriş yapıp abonelik almadıysanız, uygulamada Premium'un
**kapalı** olduğunu doğrulayın.

## 5. App Store Connect'te bildirim adresi

App Store Connect → Uygulama → **Uygulama Bilgileri** → **App Store Server Notifications**:

- **Üretim** ve **Sandbox** için: `https://drawapi.ahmetkar.com/api/v1/billing/apple-notifications`
- Sürüm: **Version 2**
- Kaydettikten sonra **Test bildirimi gönder**. Sunucu günlüğünde 200 görünmeli.

Abonelik ürünü (`com.karapp.drawguidance.premium.monthly`), Ücretli Uygulamalar sözleşmesi ve vergi/banka bilgileri
hazır değilse önce onları tamamlayın: `docs/v50-app-store-abonelik.md` → "App Store Connect'te yapılacaklar".

## 6. Uygulama sürümünü kaydetmek (mağazaya çıktıktan sonra)

Android 1.8.0 Play'de yayımlanınca, eski sürümdeki cihazlara güncelleme haberi gitmesi için:

```bash
C=$(docker ps -q -f name=drawguidance_api | head -1)
docker exec -it "$C" sh -c 'exec java $JAVA_OPTS -jar /app/app.jar cli surum ekle --kod 50 --ad 1.8.0 --ileti "Resimden çizim eğitimi, çizim alıştırmaları, Apple ile giriş ve daha fazlası"'
docker exec -it "$C" sh -c 'exec java $JAVA_OPTS -jar /app/app.jar cli surum listele'
```

Haber göndermek istemiyorsanız `--gonderme` ekleyin.

## Geri alma

```bash
export REGISTRY=ghcr.io/ahmetkar API_TAG=44   # ya da önceki etiket
docker service update --image "$REGISTRY/drawguidance-server:$API_TAG" --with-registry-auth drawguidance_api
```

`apple_iap_key` sırrı ve yeni ortam değişkenleri eski sürüme zarar vermez, kalabilirler. Not: eski sürüm istisna
hesabını yeniden açar (`BILLING_EXEMPT_EMAILS` öntanımlısı koddadır).

## Sık hatalar

| Belirti | Sebep |
|---|---|
| `stack deploy`: `secret not found: apple_iap_key` | 1. adım yapılmadı. |
| `verify-apple` 503 `billing_not_configured` | `.p8` okunamadı ya da `APPLE_IAP_*` eksik. Cihazdaki Premium yine çalışır, ama sunucuya bağlanmaz. |
| Apple bildirimi 401 / imza hatası | Bildirim adresinde Version 1 seçilmiş; Version 2 olmalı. |
| Android'de Google ile giriş hatası 10 | İmza anahtarının SHA-1'i Firebase'e ekli değil (Play App Signing SHA-1'i de eklenmeli). |
