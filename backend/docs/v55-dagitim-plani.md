# v55 backend dağıtım planı (sunucu 54.0.0)

Android 1.12.1 (55) için **sunucuda yeni kod yok**. Sunucu sürümü **54.0.0** olarak kalır. Bu plan üretimi
**v50.0.0'dan 54.0.0'a** güvenle taşır ve web sitesinin v54 ayarlarını da kapsar. Komutların ayrıntısı
`v54-sunucu-guncelleme.md` dosyasında.

Sıra önemli:

1. Önce sır.
2. Sonra imaj.
3. Sonra dağıtım.
4. Sonra doğrulama.
5. En son web.

1. adım atlanırsa `stack deploy` başarısız olur, çünkü `stack.yml` artık `cli_password_hash` sırrını bekler.

## 0. Ön kontrol (5 dk)

- Şu anki sürüm: `curl -fsS https://drawapi.ahmetkar.com/api/health` (beklenen `50.0.0`).
- Geri dönüş etiketi: `docker service inspect drawguidance_api --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}'`.
  Çıktıyı not edin; geri alma bu etiketle yapılır.
- MongoDB yedeği (göç yok, yine de):
  ```bash
  docker exec $(docker ps -qf name=drawguidance_mongo) mongodump --archive=/tmp/v54-oncesi.gz --gzip
  ```
- `docker secret ls` ile var olan sırları görün: `mongo_*`, `ip_hash_salt`, `gcs_service_account`, `device_data_key`
  ve ötekiler. **Bunlara dokunmayın.**

## 1. Yönetim şifresi sırrı (bir kez)

```bash
docker run --rm -it --entrypoint java ghcr.io/ahmetkar/drawguidance-server:54.0.0 -jar /app/app.jar cli sifre-ozeti
printf '%s' 'pbkdf2-sha256:...' | docker secret create cli_password_hash -
```

- `cli sifre-ozeti` şifreyi iki kez gizli sorar ve bir özet satırı yazar. Şifre en az 10 karakter olmalı.
- İkinci komutta tırnak içine, birinci komutun yazdığı özet satırını koyun. Şifrenin kendisini değil, özeti yazın.
- Şifreyi parola yöneticisinde saklayın. Şifre ve özet hiçbir sohbete ya da dosyaya yazılmasın.

> İmaj henüz itilmediyse önce 2. adımı yapın, sonra bu adımı yapın, sonra 3. adıma geçin.

## 2. İmaj

`backend/` klasöründe:

```bash
./gradlew test                       # Testcontainers için Docker açık olmalı
export REGISTRY=ghcr.io/ahmetkar API_TAG=54.0.0
echo "$GHCR_PAT" | docker login ghcr.io -u ahmetkar --password-stdin
docker build -t "$REGISTRY/drawguidance-server:$API_TAG" .
docker push "$REGISTRY/drawguidance-server:$API_TAG"
```

## 3. Dağıtım

`backend/docker/swarm/` klasöründe. Yönetici düğümdeki `stack.yml` güncel olmalı; v54 sürümünde `cli_password_hash`
sırrı var.

```bash
export REGISTRY=ghcr.io/ahmetkar API_TAG=54.0.0 ACME_EMAIL=ahmetkar.1258@gmail.com
docker stack deploy -c stack.yml --with-registry-auth --detach=false drawguidance
docker service ps drawguidance_api
```

Kopyalar birer birer güncellenir. Sağlık denetimi geçmezse Swarm otomatik olarak eski sürüme döner.

## 4. Doğrulama (10 dk)

| Kontrol | Beklenen |
|---|---|
| `curl -fsS https://drawapi.ahmetkar.com/api/health` | `"version":"54.0.0"` |
| `./dg-cli.sh paket listele` | Önce şifre sorulur, sonra liste gelir. Yanlış şifre 3 denemede çıkar. |
| Android 1.12.1 (55): giriş, eşitleme, paylaşım gelen kutusu | Normal çalışır. Uygulama `deviceSecret` gönderir. |
| Eski uygulama (1.12.0 ve öncesi) | Normal çalışır. Gizli anahtarı olmayan eski cihaz kayıtları ilk kayıtta anahtarı alır. |
| Aynı cihaz kimliği başka hesapla (anahtarsız) | `409 device_owned` |
| `POST /api/v1/reports` jetonsuz | `401` (hata raporu yalnız oturum açıkken gider) |
| `docker service logs drawguidance_api --since 10m \| grep -i error` | Yeni hata yok |

## 5. Web sitesi (Vercel)

Vercel'de **Settings → Environment Variables → Production** bölümünü açın:

- **`IP_HASH_SALT`:** en az 32 karakter rastgele bir değer olmalı.
  - v54'ten beri üretimde zorunlu; yoksa ya da 16 karakterden kısaysa form uçları hata verir.
  - `ADMIN_SETUP_TOKEN` yedek olarak kullanılır, ama ayrı tuz önerilir.
- **`TRUSTED_PROXY_HOPS`:** yalnız Vercel'in önünde ayrıca bir ters vekil (ör. Cloudflare proxy) varsa onların
  sayısını girin. Yoksa boş bırakın (0).

Ardından **Redeploy** edin. Kontrol için ana sayfayı açın, iletişim formunu bir kez gönderin ve yönetim girişini deneyin.

## Geri alma

```bash
export API_TAG=50.0.0   # 0. adımda not edilen etiket
docker stack deploy -c stack.yml --with-registry-auth --detach=false drawguidance
```

- `cli_password_hash` sırrı kalabilir; eski sürüm onu okumaz.
- Veritabanında göç yok. Yeni `devices.secretHash` alanını eski sürüm görmezden gelir.
- Web için Vercel → **Deployments** → önceki dağıtım → **Promote to Production**.

## Dikkat

- Hız sınırı sayaçları kopya başına bellekte tutulur. API kopya sayısını 1'in üstüne çıkarırsanız merkezi bir sayaç
  (Redis) gerekir.
- Android 1.12.1, 54.0.0 sunucusunu gerektirmez; eski 50.0.0 sunucusuyla da çalışır. Yalnız cihaz devralma koruması
  sunucu güncellenince etkin olur.
- App içi satın alma için kullanılan `ASC_*` / Play değişkenlerine dokunulmaz.
