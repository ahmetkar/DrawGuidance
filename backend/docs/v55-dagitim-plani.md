# Backend güncelleme planı: 44.2.0 → 54.0.0

Üretim sunucusu (Docker Swarm, `https://drawapi.ahmetkar.com`) şu an **44.2.0** sürümünde. Bu plan, depodaki güncel
`backend/` klasörüne (sürüm **54.0.0**) **tek seferde** geçirir.

- Ara sürüm (50.0.0) ayrıca kurulmaz.
- 50 ve 54 rehberlerindeki adımlar burada birleştirildi:
  - `v50-sunucu-guncelleme.md`
  - `v54-sunucu-guncelleme.md`
  - `v50-app-store-abonelik.md`

Uygulama sürümleri:

- Android 1.12.1 (55) ve iOS 1.12.1 (55) bu sunucuyla tam çalışır.
- Eski sunucuyla da açılırlar. Yalnız iOS aboneliğinin sunucuda doğrulanması, cihaz devralma koruması ve yönetim
  şifresi çalışmaz.

Sunucuda `docker` `sudo` ile çalışıyorsa bütün `docker` komutlarının başına `sudo` ekleyin.

**Sıra önemli:** önce iki sır (adım 1), sonra imaj (adım 2), sonra dağıtım (adım 3). Sırlar yoksa `stack deploy`
`secret not found` hatasıyla durur.

---

## 44.2.0 → 54.0.0 arasında sunucuda değişenler

| Sürüm | Konu | Değişiklik | Sizden istenen |
|---|---|---|---|
| 50 | İstisna hesabı kaldırıldı | `ExemptAccounts` ve `BILLING_EXEMPT_EMAILS` silindi. Premium artık yalnız doğrulanmış abonelikle açılır. | `ahmetkar8040@gmail.com` hesabında Premium **kapanır**. |
| 50 | Play aboneliği tazelik denetimi | `/api/v1/subscriptions/me`, 12 saatten eski Play kaydını Google Play'den yeniden okur (kaçan iade ve iptaller yakalanır). | Yok |
| 50 | App Store abonelikleri (StoreKit 2) | `POST /api/v1/subscriptions/verify-apple` (Firebase jetonu ister) ve `POST /api/v1/billing/apple-notifications` (Apple imzalı, herkese açık). Bir platformda alınan Premium diğerinde de açılır. | Sır `apple_iap_key` + App Store Connect bildirim adresi |
| 50 | Yeni kütüphane | `app-store-server-library 5.2.0`. Apple Root CA G3 imajın içinde. | Yok |
| 50 | Giriş sağlayıcısı | Firebase jetonundaki sağlayıcı (google.com / apple.com) `users.signInProvider` alanına yazılır. | Yok |
| 54 | Yönetim şifresi | Bütün `cli` komutları PBKDF2 şifre doğrulamasıyla çalışır. Şifre gizli sorulur; 3 yanlış denemede çıkar. | Sır `cli_password_hash` |
| 54 | Gelen kutusu sahipliği | `GET /api/v1/inbox` cihazın sürümünü yalnız kayıtlı hesapla okur. Başka hesap `404` alır. | Yok |
| 54 | Cihaz devralma koruması | `PUT /api/v1/devices/me` `deviceSecret` alır; sunucu yalnız SHA-256 özetini saklar. Başka hesaba geçiş ancak aynı anahtarla olur, yoksa `409 device_owned`. Anahtarı olmayan eski kayıtlar anahtarı ilk kayıtta alır. | Yok |
| 54 | Hata raporu | `POST /api/v1/reports` doğrulanmış jeton yoksa `401`. | Yok |
| 54 | Taban paket resim güvenliği | `taban-paket --yukle` her resmi denetler: imza, yapı, gömülü betik veya arşiv, en çok 20 MB ve 8192 px. | Yok |
| — | Veritabanı | **Göç yok.** Yeni alanlar kendiliğinden oluşur: `users.signInProvider`, `subscriptions.store` (eski kayıtlar Google Play sayılır), `devices.secretHash`, planlarda `appleProductIds`. | Yine de yedek alın |

**`stack.yml` farkı (44.2.0 → 54.0.0):**

```yaml
# kaldırıldı
BILLING_EXEMPT_EMAILS: ahmetkar8040@gmail.com
# eklendi (api servisi ortamı)
APPLE_APP_APPLE_ID: "6834536997"
APPLE_IAP_ISSUER_ID: 9c6a0a17-8b74-4124-a44d-0e2101126b6a
APPLE_IAP_KEY_ID: MTT897CZ3J
APPLE_IAP_PRIVATE_KEY_FILE: /run/secrets/apple_iap.p8
CLI_PASSWORD_HASH_FILE: /run/secrets/cli_password_hash
# eklendi (servis sırları ve en alttaki secrets bölümü)
apple_iap_key     -> /run/secrets/apple_iap.p8
cli_password_hash -> /run/secrets/cli_password_hash
```

Öteki sırlara dokunulmaz; önceki kurulumdan kalırlar:

- `mongo_*`
- `play_service_account`
- `slack_webhook`
- `ip_hash_salt`
- `gcs_service_account`
- `device_data_key`

---

## 0. Hazırlık (yaklaşık 15 dk)

1. **Geri dönüş etiketini not edin** (yönetici düğümde):
   ```bash
   docker service inspect drawguidance_api --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}'
   curl -fsS https://drawapi.ahmetkar.com/api/health        # "version":"44.2.0"
   ```
2. **MongoDB yedeği.** Göç yok, ama sürüm atlaması büyük olduğu için yedek alın. Kimlik doğrulama açık olduğu için
   yedek kullanıcısı `dgbackup` kullanılır. Parolası `mongo1` kapsayıcısındaki Docker sırrından okunur; komut satırına
   ya da geçmişe yazılmaz:
   ```bash
   C=$(sudo docker ps -qf name=drawguidance_mongo1 | head -1)
   sudo docker exec "$C" bash -c 'umask 077; printf "password: \"%s\"\n" "$(cat /run/secrets/mongo_backup_password)" > /tmp/dump.yml; mongodump --quiet --config=/tmp/dump.yml --uri="mongodb://dgbackup@localhost:27017/?authSource=admin&directConnection=true" --oplog --gzip --archive=/tmp/v44-oncesi.gz; rc=$?; rm -f /tmp/dump.yml; exit $rc'
   sudo docker cp "$C":/tmp/v44-oncesi.gz ./v44-oncesi-$(date +%F).gz && sudo docker exec "$C" rm -f /tmp/v44-oncesi.gz
   ls -lh ./v44-oncesi-*.gz
   ```
   Ayrıca `mongo-backup` servisi her gece 03:00 (UTC) yedek alır. Son gecenin yedeğini görmek için:
   ```bash
   sudo docker exec $(sudo docker ps -qf name=drawguidance_mongo-backup | head -1) ls -lh /backups
   ```
3. **Kod:** PC'deki proje klasöründeki `backend/` (54.0.0) ya da GitHub `claude/v55` dalı. Yönetici düğüme güncel
   `backend/docker/swarm/stack.yml` kopyalanacak.
4. **App Store anahtarı:** `yayin-anahtari\SubscriptionKey_MTT897CZ3J.p8` dosyasını yönetici düğüme geçici olarak
   kopyalayın:
   ```bash
   scp yayin-anahtari\SubscriptionKey_MTT897CZ3J.p8 <kullanici>@<yonetici>:/tmp/
   ```

## 1. Sırlar (bir kez)

**1a. App Store Server API anahtarı (v50):**

```bash
docker secret create apple_iap_key /tmp/SubscriptionKey_MTT897CZ3J.p8
shred -u /tmp/SubscriptionKey_MTT897CZ3J.p8      # asıl dosya PC'de kalır
```

**1b. Yönetim komut satırı şifresi (v54).** İmaj 2. adımda gönderildikten sonra yapın:

```bash
docker run --rm -it --entrypoint java ghcr.io/ahmetkar/drawguidance-server:54.0.0 -jar /app/app.jar cli sifre-ozeti
printf '%s' 'pbkdf2-sha256:...' | docker secret create cli_password_hash -
docker secret ls                                   # apple_iap_key ve cli_password_hash görünmeli
```

- İlk komut şifreyi iki kez gizli sorar ve bir özet satırı yazar. Şifre en az 10 karakter olmalı.
- İkinci komutta tırnak içine bu özet satırını yazın (şifrenin kendisini değil).
- Şifreyi parola yöneticisinde saklayın. Ne şifreyi ne özeti bir sohbete ya da dosyaya yazın.

## 2. İmaj

`backend/` klasöründe (Dockerfile'ın bulunduğu yer). Klasör `sudo` ile kopyalandıysa önce sahipliği alın:
`sudo chown -R "$USER":"$USER" <klasör>`.

```bash
export REGISTRY=ghcr.io/ahmetkar API_TAG=54.0.0
sudo docker build -t "$REGISTRY/drawguidance-server:$API_TAG" .
# Birden çok düğüm varsa ya da imaj ghcr'den çekiliyorsa:
echo "$GHCR_PAT" | sudo docker login ghcr.io -u ahmetkar --password-stdin
sudo docker push "$REGISTRY/drawguidance-server:$API_TAG"
```

- Dockerfile kaynağı kendi içinde derler (`bootJar -x test`). Sunucuda Java ya da Gradle gerekmez.
- `./gradlew test` isteğe bağlıdır. Testcontainers ile MongoDB başlattığı için Docker'a doğrudan erişim ister; kullanıcı
  docker grubunda değilse düşer. Testleri Docker Desktop'lı bir geliştirme makinesinde çalıştırın.

Ardından 1b'yi yapın.

## 3. Dağıtım

Yönetici düğümde, güncel `stack.yml` ile:

```bash
export REGISTRY=ghcr.io/ahmetkar API_TAG=54.0.0 ACME_EMAIL=ahmetkar.1258@gmail.com
docker stack deploy -c stack.yml --with-registry-auth --detach=false drawguidance
docker service ps drawguidance_api
```

Kopyalar birer birer güncellenir. Sağlık denetimi geçmezse Swarm kendiliğinden eski sürüme döner.

## 4. Doğrulama (yaklaşık 10 dk)

```bash
curl -fsS https://drawapi.ahmetkar.com/api/health                                        # "version":"54.0.0"
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://drawapi.ahmetkar.com/api/v1/subscriptions/verify-apple   # 401
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://drawapi.ahmetkar.com/api/v1/reports \
     -H 'Content-Type: application/json' -d '{}'                                         # 401
curl -s -X POST https://drawapi.ahmetkar.com/api/v1/billing/apple-notifications \
     -H 'Content-Type: application/json' -d '{}'                                         # 400 bad_request (uç açık)
docker service logs --since 10m drawguidance_api | grep -iE "error|exception|App Store|Planlar" | head -20
```

**Günlükte beklenenler:**

- `Planlar hazır: ... App Store [com.karapp.drawguidance.premium.monthly]` satırı **görünmeli**.
- `App Store anahtar dosyası okunamadı` satırı **görünmemeli**. Görünürse `apple_iap_key` sırrı yanlış ya da boş.

**Yönetim komut satırı:**

```bash
C=$(docker ps -q -f name=drawguidance_api | head -1)
docker exec -it "$C" sh -c 'exec java $JAVA_OPTS -jar /app/app.jar cli surum listele'
```

Önce şifre sorulmalı. Yanlış şifre 3 denemede çıkmalı.

**Uygulamadan kontrol:**

| Kontrol | Beklenen |
|---|---|
| Android 1.12.1 / iOS 1.12.1: Google ve Apple ile giriş, eşitleme, gelen kutusu | Normal çalışır |
| Eski uygulama (1.12.0 ve öncesi) | Normal çalışır |
| `ahmetkar8040@gmail.com` (aboneliği yoksa) | Premium **kapalı** (istisna hesabı kaldırıldı) |
| iOS'ta TestFlight sandbox satın alma | Premium açılır; Android'de aynı hesapla da açık görünür |

## 5. App Store Connect: sunucu bildirim adresi (bir kez)

App Store Connect → Uygulama → **Uygulama Bilgileri** → **App Store Server Notifications**:

- **Üretim** ve **Sandbox** adresi: `https://drawapi.ahmetkar.com/api/v1/billing/apple-notifications`
- Sürüm: **Version 2**
- Kaydedip **Test bildirimi gönder**. Sunucu günlüğünde 200 görünmeli.

Abonelik ürünü (`com.karapp.drawguidance.premium.monthly`), Ücretli Uygulamalar sözleşmesi ve vergi/banka bilgileri
hazır değilse önce onları tamamlayın: `v50-app-store-abonelik.md`.

## 6. Web sitesi (Vercel)

**Settings → Environment Variables → Production:**

- **`IP_HASH_SALT`:** en az 32 karakter rastgele değer.
  - v54'ten beri üretimde zorunlu. Yoksa ya da 16 karakterden kısaysa form uçları hata verir.
- **`TRUSTED_PROXY_HOPS`:** yalnız Vercel'in önünde ayrıca bir ters vekil (ör. Cloudflare proxy) varsa onların sayısı.
  Yoksa boş bırakın.

Ardından **Redeploy**. Ana sayfayı, iletişim formunu ve yönetim girişini deneyin.

## 7. Uygulama sürümünü kaydetmek (mağazalarda yayımlanınca)

```bash
C=$(docker ps -q -f name=drawguidance_api | head -1)
docker exec -it "$C" sh -c 'exec java $JAVA_OPTS -jar /app/app.jar cli surum ekle --kod 55 --ad 1.12.1 --ileti "Geniş cihaz desteği, sınırsız resim ekleme ve örnek manga şablonu"'
```

Önce şifre sorulur. Haber göndermek istemiyorsanız `--gonderme` ekleyin.

---

## Geri alma

```bash
export REGISTRY=ghcr.io/ahmetkar API_TAG=<0. adımda not edilen etiket>
docker service update --image "$REGISTRY/drawguidance-server:$API_TAG" --with-registry-auth drawguidance_api
```

Eski `stack.yml` ile tam dönüş için o dosyayla `docker stack deploy` yapın.

- Yeni sırlar (`apple_iap_key`, `cli_password_hash`) ve ortam değişkenleri eski sürüme zarar vermez, kalabilir.
- Veritabanında göç olmadığından yedeğe dönmek gerekmez. Eski sürüm yeni alanları görmezden gelir.
- Eski sürüme dönünce istisna hesabı yeniden açılır (`BILLING_EXEMPT_EMAILS` öntanımlısı eski kodda).
- Web için: Vercel → **Deployments** → önceki dağıtım → **Promote to Production**.

## Sık hatalar

| Belirti | Sebep / çözüm |
|---|---|
| `stack deploy`: `secret not found: apple_iap_key` ya da `cli_password_hash` | 1. adım eksik. |
| `verify-apple` 503 `billing_not_configured` | `.p8` okunamadı ya da `APPLE_IAP_*` eksik. |
| Apple bildirimi 401 ya da imza hatası | App Store Connect'te Version 1 seçili; Version 2 olmalı. |
| `cli` komutu hemen çıkıyor | `cli_password_hash` boş ya da biçimi yanlış. Özet `pbkdf2-sha256:` ile başlamalı. |
| Uygulamada `409 device_owned` | Cihaz kimliği başka hesapta kayıtlı ve gizli anahtar uyuşmuyor. Koruma beklenen biçimde çalışıyor; cihazın kendi anahtarıyla gelen istekler etkilenmez. |
| Web formları 500 | Vercel'de `IP_HASH_SALT` eksik ya da kısa. |

## Dikkat

- Hız sınırı sayaçları kopya başına bellekte tutulur. Yığında API iki kopya çalıştığından gerçek sınır ayarlananın en
  çok iki katıdır. Tam sınır gerekirse merkezi sayaç (Redis) eklenmeli.
- Uygulama içi satın almanın `ASC_*` değişkenlerine dokunulmaz.
