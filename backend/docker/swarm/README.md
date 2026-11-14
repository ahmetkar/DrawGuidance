# DrawGuidance üretim kurulumu (Docker Swarm, v40)

`stack.yml` şu servisleri çalıştırır:

| Servis | Görevi |
|---|---|
| `traefik` (v3) | 80/443'te dinler. Let's Encrypt sertifikası alır (TLS-ALPN). HTTP'yi HTTPS'e yönlendirir. HSTS ekler, IP başına istek sınırı ve eşzamanlı istek sınırı uygular. Panosu kapalıdır. |
| `socket-proxy` | Traefik'in Docker API'sini yalnız okuyabildiği süzgeç. Traefik Docker soketine doğrudan bağlanmaz. |
| `api` ×2 | Spring Boot sunucusu (`prod` profili). Root olmayan kullanıcıyla, salt okunur dosya sistemiyle çalışır. Güncellemelerde önce yeni kopya başlar (start-first); sağlık denetimi geçmezse otomatik geri alınır. |
| `mongo1` | MongoDB 7 kopya kümesi `rs0`. Kimlik doğrulama ve anahtar dosyası açıktır, kalıcı birimde durur ve yalnız `drawguidance.db=true` etiketli düğümde koşar. |
| `mongo-backup` | Her gün 03:00'te (UTC) `mongodump --oplog` arşivi alır ve 14 gün saklar. |

Ağlar:

- Bütün overlay ağlar şifrelidir (IPsec).
- `dg_backend` (api ↔ mongo) ve `dg_socket` (traefik ↔ socket-proxy) `internal` ağlardır, yani dışarıya çıkışları yoktur.
- Dışarıya yalnız Traefik açıktır.
- Yalnız `https://drawapi.ahmetkar.com/api/...` yolları yönlendirilir. `/actuator` dışarıdan erişilemez.

Sırların hepsi Docker sırrıdır (`external: true`). Uygulama sırları `*_FILE` değişkenleriyle `/run/secrets/...` altından okur. Hiçbir parola `stack.yml`'de, ortam değişkeninde ya da imajda bulunmaz.

## 0. Ön koşullar

- Docker Engine 24+ kurulu Linux düğümler. İlk kurulum için tek düğüm de yeterlidir.
- DNS: `drawapi.ahmetkar.com` için bir A/AAAA kaydı, Traefik'in koştuğu düğümün (`drawguidance.edge=true`) genel IP'sini göstermeli.
- İmaj kayıt defteri: GitHub Container Registry. Aşağıda `REGISTRY=ghcr.io/ahmetkar` olarak geçer (GitHub kullanıcı adı `ahmetkar`, küçük harf). Giriş için GitHub'da `write:packages` ve `read:packages` yetkili bir *classic* kişisel erişim belirteci (PAT) oluşturun. Paket ilk gönderimden sonra varsayılan olarak gizlidir; gizli kalsın, düğümler `--with-registry-auth` ile çeker.
- Sunucunun önünde CDN ya da başka bir proxy yoktur; Traefik istemciye doğrudan açıktır ve istemcinin gönderdiği `X-Forwarded-*` başlıklarına güvenmez.

## 1. Güvenlik duvarı

| Kapı | Kimden | Neden |
|---|---|---|
| 80/tcp, 443/tcp | Herkes | Traefik. 80 yalnız HTTPS'e yönlendirme ve ACME içindir. |
| 22/tcp | Yalnız yönetici IP'leri | SSH |
| 2377/tcp | Yalnız Swarm düğümleri | Küme yönetimi |
| 7946/tcp+udp | Yalnız Swarm düğümleri | Düğüm keşfi |
| 4789/udp | Yalnız Swarm düğümleri | Overlay (VXLAN) |
| IP protokolü 50 (ESP) | Yalnız Swarm düğümleri | Şifreli overlay trafiği |

Örnek `ufw` kuralları (düğüm IP'leri `<DÜĞÜM_IP>`):

```bash
ufw default deny incoming
ufw allow 80/tcp && ufw allow 443/tcp
ufw allow from <YÖNETİCİ_IP> to any port 22 proto tcp
for ip in <DÜĞÜM_IP_1> <DÜĞÜM_IP_2>; do
  ufw allow from "$ip" to any port 2377 proto tcp
  ufw allow from "$ip" to any port 7946
  ufw allow from "$ip" to any port 4789 proto udp
  ufw allow from "$ip" proto esp
done
ufw enable
```

> Docker, `ports:` ile yayımlanan kapılar için iptables kurallarını kendisi yazar ve ufw'yi atlar. Bu yığında yalnız 80/443 yayımlanır. MongoDB'nin kapısı hiçbir zaman yayımlanmaz.

## 2. Kümeyi kurmak

```bash
docker swarm init --advertise-addr <YÖNETİCİ_DÜĞÜM_IP>
# Başka düğümler için: docker swarm join-token worker  (çıktıdaki komutu diğer düğümde çalıştırın)

# Traefik'in koşacağı yönetici düğüm ve veritabanı düğümü
docker node update --label-add drawguidance.edge=true <YÖNETİCİ_DÜĞÜM_ADI>
docker node update --label-add drawguidance.db=true   <VERİTABANI_DÜĞÜM_ADI>
docker node ls
```

`mongo1` ve `mongo-backup` yalnız `drawguidance.db=true` etiketli düğümde koşar. Verilerin durduğu birim o düğümdedir; etiketi başka bir düğüme taşımak veriyi taşımaz.

## 3. Sırlar

Sırlar yalnız yönetici düğümde, bir kez oluşturulur. Gerçek değerlerin yerel kopyaları ana çalışma kopyasında git'e girmeyen şu dosyalardadır:

- `backend/docker/.env`
- `backend/docker/secrets/play.json`

Bu dosyalar asla commit edilmez. Aşağıdaki komutlarda `<...>` yer tutucudur.

Komut geçmişine sır yazılmaması için komutların başına bir boşluk koyun (`HISTCONTROL=ignorespace`) ya da değeri `read -rs` ile okuyun:

```bash
read -rs V && printf '%s' "$V" | docker secret create <ad> - ; unset V
```

```bash
# MongoDB parolaları: rastgele ve uzun
printf '%s' '<MONGO_ROOT_PAROLASI>'   | docker secret create mongo_root_password -
printf '%s' '<MONGO_APP_PAROLASI>'    | docker secret create mongo_app_password -
printf '%s' '<MONGO_YEDEK_PAROLASI>'  | docker secret create mongo_backup_password -
#   Üretmek için: openssl rand -base64 33 | tr -d '\n' | docker secret create mongo_app_password -

# Kopya kümesi üyelerinin ortak anahtar dosyası (6–1024 base64 karakter)
openssl rand -base64 756 | tr -d '\n' | docker secret create mongo_keyfile -

# Google Play hizmet hesabı JSON'u (drawguidance-backend@drawguidance.iam.gserviceaccount.com).
# api içinde /run/secrets/play.json olarak bağlanır.
docker secret create play_service_account <YOL>/backend/docker/secrets/play.json

# Slack gelen webhook adresi
printf '%s' '<SLACK_WEBHOOK_URL>' | docker secret create slack_webhook -

# IP özeti tuzu (rastgele; değişirse eski özetler eşleşmez)
openssl rand -hex 32 | tr -d '\n' | docker secret create ip_hash_salt -

# v43 Cloud Storage hizmet hesabı (dgpackets kovası: paket resimleri ve imzalı adresler).
# api içinde /run/secrets/gcs.json olarak bağlanır.
docker secret create gcs_service_account <YOL>/gcs-service-account.json

# v43 cihaz bilgilerinin şifreleme anahtarı (base64, 32 bayt). Değişirse kayıtlı cihaz bilgileri okunamaz: bir kez
# üretin ve güvenli bir yerde saklayın.
openssl rand -base64 32 | tr -d '\n' | docker secret create device_data_key -

# v50 App Store Server API anahtarı (App Store Connect → Kullanıcılar ve Erişim → Entegrasyonlar → Uygulama İçi
# Satın Alma). api içinde /run/secrets/apple_iap.p8 olarak bağlanır. Bu sır yoksa yığın dağıtılamaz: henüz anahtar
# yoksa geçici olarak boş bir dosyayla oluşturun (App Store doğrulaması 503 billing_not_configured döner).
docker secret create apple_iap_key <YOL>/SubscriptionKey_XXXXXXXXXX.p8
#   Kimlikler gizli değil; dağıtım kabuğunda verilir:
#   export APPLE_APP_APPLE_ID=<sayı> APPLE_IAP_ISSUER_ID=<uuid> APPLE_IAP_KEY_ID=<10 karakter>

docker secret ls
```

Uygulama sonundaki satır sonlarını kırpar. Yine de `printf '%s'` kullanmak, değeri tam olarak kaydeder.

**Sır değiştirme.** Docker sırları değiştirilemez. Yeni bir adla oluşturun, örneğin `slack_webhook_v2`. Sonra `stack.yml`'nin en altındaki `secrets:` bölümünde dış adı belirtin:

```yaml
slack_webhook: { external: true, name: slack_webhook_v2 }
```

Ardından yığını yeniden dağıtın.

MongoDB parolasını değiştirirken yeni sırla dağıtım yeterlidir. `mongo1` her açılışta uygulama kullanıcısının parolasını sırdaki değerle eşitler (`rs-init.js`). Root parolası ise yalnız ilk kurulumda kullanılır; değiştirmek için `mongosh` içinde `db.changeUserPassword` çalıştırın.

## 4. İmajı derleyip göndermek

`backend/` klasöründe:

```bash
export REGISTRY=ghcr.io/ahmetkar API_TAG=44
echo "$GHCR_PAT" | docker login ghcr.io -u ahmetkar --password-stdin
docker build -t "$REGISTRY/drawguidance-server:$API_TAG" .
#   ya da önceden derlenmiş jar ile: ./gradlew bootJar && docker build --target prebuilt -t ... .
docker push "$REGISTRY/drawguidance-server:$API_TAG"
```

Etiketi sabit tutun (`40`, `40.1`...). `latest` kullanmayın; geri alma işlemi etiket üzerinden yürür.

## 5. Dağıtım

`backend/docker/swarm/` klasöründe:

```bash
export REGISTRY=ghcr.io/ahmetkar API_TAG=44 ACME_EMAIL=ahmetkar.1258@gmail.com
docker stack deploy -c stack.yml --with-registry-auth --detach=false drawguidance
docker stack services drawguidance
```

İlk açılış şöyle ilerler:

1. `mongo1` kopya kümesini kurar.
2. Kullanıcıları oluşturur:
   - `dgadmin`: root;
   - `drawguidance`: yalnız `readWrite`;
   - `dgbackup`: `backup` rolü.
3. api kopyaları sağlıklı olunca Traefik onları yönlendirmeye başlar.
4. İlk HTTPS isteğinde sertifika alınır.

Denetim:

```bash
curl -sI https://drawapi.ahmetkar.com/api/v1/catalog/manifest   # 200 ve Strict-Transport-Security
curl -sI http://drawapi.ahmetkar.com/api/v1/catalog/manifest    # 301 → https
curl -s -o /dev/null -w '%{http_code}\n' https://drawapi.ahmetkar.com/actuator/health   # 404 (dışarıya kapalı)
```

### Google Play RTDN (Pub/Sub itme aboneliği)

`prod` profilinde OIDC doğrulaması zorunludur. Kitle (audience) ya da hizmet hesabı tanımlı değilse uç 503 döner.

Abonelik şöyle ayarlanır:

```bash
gcloud pubsub subscriptions update <ABONELİK> \
  --push-endpoint=https://drawapi.ahmetkar.com/api/v1/billing/rtdn \
  --push-auth-service-account=drawguidance-backend@drawguidance.iam.gserviceaccount.com \
  --push-auth-token-audience=https://drawapi.ahmetkar.com/api/v1/billing/rtdn
```

Pub/Sub hizmet aracısının (`service-<PROJE_NO>@gcp-sa-pubsub.iam.gserviceaccount.com`) bu hizmet hesabı üzerinde `roles/iam.serviceAccountTokenCreator` yetkisi olmalı.

Bu iki değer `stack.yml`'de düz ortam değişkenidir; gizli değildir.

## 6. Güncelleme ve geri alma

```bash
# Yeni sürüm: imajı yeni etiketle gönderip yeniden dağıtın.
# Kopyalar birer birer, önce yeni başlayarak güncellenir.
API_TAG=41 docker stack deploy -c stack.yml --with-registry-auth --detach=false drawguidance

# Yalnız api imajını değiştirmek
docker service update --with-registry-auth --image ghcr.io/ahmetkar/drawguidance-server:41 drawguidance_api

# Elle geri alma (sağlık denetimi geçmezse Swarm bunu kendisi yapar)
docker service rollback drawguidance_api
docker service ps drawguidance_api      # görev geçmişi ve hata nedenleri
```

`traefik-dynamic.yml`, `backup.sh` ya da `../mongo/*` değiştiğinde Swarm config'leri değiştirilemediği için yeni bir ad gerekir. `CONFIG_VERSION` değerini artırarak dağıtın:

```bash
CONFIG_VERSION=2 docker stack deploy -c stack.yml --with-registry-auth drawguidance
```

Eski config'ler kullanılmadığında `docker config rm` ile silinebilir.

## 7. Günlükler

```bash
docker service logs -f --since 10m drawguidance_api
docker service logs -f drawguidance_traefik        # JSON erişim günlüğü (istek başlıkları yazılmaz)
docker service logs drawguidance_mongo1
docker service logs drawguidance_mongo-backup
```

- Günlükler `json-file` sürücüsüyle hizmet başına 5×10 MB'tan fazla tutulmaz.
- Uygulama günlüklerinde jeton, e-posta ya da sır yazılmaz. IP adresleri yalnız özet (hash) olarak saklanır.

## 8. Yedek ve geri yükleme

Yedekler `mongo-backup` servisinin `mongo-backups` biriminde, veritabanı düğümünde durur. Dosya adı `drawguidance-YYYYMMDD-HHMMSS.archive.gz` biçimindedir.

Hemen bir yedek almak için:

```bash
docker service update --env-add BACKUP_ON_START=1 --force drawguidance_mongo-backup
```

Yedekleri sunucu dışına taşıyın (S3, başka bir makine). Düğüm kaybında yedek de kaybolur. Bunlar veritabanı düğümünde çalıştırılır:

```bash
B=$(docker ps -q -f name=drawguidance_mongo-backup)
docker exec "$B" ls -lh /backups
docker cp "$B":/backups/<ARŞİV> ./
```

**Geri yükleme.**

1. Önce api'yi durdurun ve yazmaları kesin:

   ```bash
   docker service scale drawguidance_api=0
   ```

2. Arşivi `mongo1` kapsayıcısına kopyalayın ve root kullanıcısıyla geri yükleyin. Parola komut satırına değil, geçici bir config dosyasına yazılır:

   ```bash
   M=$(docker ps -q -f name=drawguidance_mongo1)
   docker cp ./<ARŞİV> "$M":/tmp/restore.archive.gz
   docker exec -it "$M" bash -c '
     umask 077; printf "password: \"%s\"\n" "$(cat /run/secrets/mongo_root_password)" > /tmp/r.yml
     mongorestore --config=/tmp/r.yml --username=dgadmin --authenticationDatabase=admin \
       --oplogReplay --gzip --archive=/tmp/restore.archive.gz --drop
     rm -f /tmp/r.yml /tmp/restore.archive.gz'
   ```

   `--oplogReplay` yetki hatası verirse (MongoDB bunun için ayrıca `anyAction` yetkili bir rol ister), komutu `--oplogReplay` olmadan çalıştırın. Bu durumda yalnız döküm sırasında yapılan son yazmalar kaybolabilir.

3. api'yi yeniden açın:

   ```bash
   docker service scale drawguidance_api=2
   ```

Geri yüklemeyi düzenli olarak ayrı bir makinede deneyin.

## 9. Üç üyeli kopya kümesine geçiş (önerilir)

Tek üyeli küme düğüm kaybına dayanıklı değildir.

1. Üç düğümü `drawguidance.db.mN=true` gibi ayrı etiketlerle işaretleyin.
2. `stack.yml`'ye `mongo2` ve `mongo3` servislerini `mongo1` kopyası olarak ekleyin. Her birinin birimi ve kısıtı ayrı olmalı, `RS_INIT` ve `CREATE_USERS` verilmemeli.
3. `mongo1` içinde üyeleri ekleyin:

   ```js
   rs.add("mongo2:27017"); rs.add("mongo3:27017")
   ```

4. `api`'nin `MONGODB_URI` değerini `mongodb://mongo1:27017,mongo2:27017,mongo3:27017/drawguidance?replicaSet=rs0&authSource=drawguidance` yapın.
5. `mongo-backup`'ın `MONGO_HOSTS` değerini de aynı şekilde güncelleyin.

## 10. Güvenlik notları

- TLS Traefik'te sonlanır (TLS 1.2+, yalnız ileri gizlilikli AEAD şifreleri, `sniStrict`).
- api, `X-Forwarded-*` başlıklarına yalnız `dg_edge` ağından (10.231.0.0/24, `TRUSTED_PROXIES`) gelen isteklerde güvenir.
- Traefik, istemcinin gönderdiği `X-Forwarded-*` başlıklarını siler.
- Traefik kapıları `host` kipinde yayımlar. Böylece istek sınırları gerçek istemci IP'sine göre işler.
- İstek sınırları iki katmanlıdır:
  - Traefik: IP başına saniyede 20 istek (anlık 60) ve en çok 40 eşzamanlı istek;
  - api: IP başına, kullanıcı başına yazma ve satın alma doğrulaması sınırları (`API_*` ortam değişkenleri).
- api sınırları her kopyada ayrı tutulur. Toplam sınır, kopya sayısıyla çarpılmış olur.
- Swarm, `security_opt: no-new-privileges` ayarını desteklemez. Bunun yerine api imajında setuid/setgid bitleri silinmiştir; tüm kapsayıcılarda `cap_drop: ALL` vardır.
