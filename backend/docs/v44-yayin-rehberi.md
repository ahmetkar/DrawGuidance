# v44 yayın rehberi: sunucu ve Google Play

Bu rehber v44 sunucusunu üretime (Docker Swarm, `https://drawapi.ahmetkar.com`) kurmayı ya da güncellemeyi, ardından
uygulamayı Google Play'e yüklemeyi adım adım anlatır.

Ayrıntılar başka belgelerdedir:

- Swarm kurulumunun tamamı (güvenlik duvarı, küme, sırlar, yedek): `docker/swarm/README.md`.
- İçerik dağıtımı: `docs/v43-dagitim.md`.
- Play Console: `belgeler/play-yayin-rehberi.md`.

## v44'te sunucuda değişenler

| Konu | Değişiklik |
|---|---|
| Sürüm | `44.0.0` (`/api/health`). |
| 3B dışa aktarma | Yeni Premium hakkı `model3d.export` (GLB dışa aktarma ve paylaşma). Planlarda 21 hak var. |
| İstisna hesabı | `BILLING_EXEMPT_EMAILS` (öntanımlı `ahmetkar8040@gmail.com`). Bu hesap Premium'un bütün haklarını alır; plan ve abonelik kaydı değişmez. Yalnız Firebase jetonundaki **doğrulanmış** e-postaya bakılır. Geçicidir: kaldırmak için değeri boş verin (`BILLING_EXEMPT_EMAILS: ""`). |
| Yığın (`stack.yml`) | v43 dağıtımının zorunlu ayarları eklendi: `GCS_BUCKET=dgpackets`, `GCS_SERVICE_ACCOUNT_JSON` (sır `gcs_service_account`) ve `DEVICE_DATA_KEY_FILE` (sır `device_data_key`). Bunlar olmadan `prod` profili açılmaz. |
| Yerel sunucu | Uygulama artık yalnız `https://drawapi.ahmetkar.com/` adresine bağlanır. Sunucu seçimi, yerel ağ taraması ve düz HTTP kaldırıldı. Bilgisayardaki `yerel-sunucu` paketi geliştirme için kaldı; Play sürümü onu kullanmaz. |

## 1. Bir kezlik: yeni sırlar

v43'ten önce kurulmuş bir kümede bu iki sır yoktur. Yönetici düğümde bir kez oluşturun:

```bash
# Cloud Storage hizmet hesabı (dgpackets kovasında Storage Object Admin)
docker secret create gcs_service_account <YOL>/gcs-service-account.json

# Cihaz bilgisi şifreleme anahtarı: bir kez üretin, güvenli yerde saklayın (değişirse kayıtlı bilgiler okunamaz)
openssl rand -base64 32 | tr -d '\n' | docker secret create device_data_key -

docker secret ls   # gcs_service_account ve device_data_key görünmeli
```

Öteki sırlar (`mongo_*`, `play_service_account`, `slack_webhook`, `ip_hash_salt`) önceki kurulumdan kalır.

## 2. İmajı derleyip göndermek

`backend/` klasöründe:

```bash
./gradlew test                      # 159 test (Testcontainers için Docker gerekir)
export REGISTRY=ghcr.io/ahmetkar API_TAG=44
echo "$GHCR_PAT" | docker login ghcr.io -u ahmetkar --password-stdin
docker build -t "$REGISTRY/drawguidance-server:$API_TAG" .
docker push "$REGISTRY/drawguidance-server:$API_TAG"
```

Etiket sabittir (`44`). `latest` kullanılmaz; geri alma etiketle yapılır.

## 3. Dağıtım

`backend/docker/swarm/` klasöründe:

```bash
export REGISTRY=ghcr.io/ahmetkar API_TAG=44 ACME_EMAIL=ahmetkar.1258@gmail.com
docker stack deploy -c stack.yml --with-registry-auth --detach=false drawguidance
docker service ps drawguidance_api
```

Kopyalar birer birer, önce yeni kopya başlayarak güncellenir. Sağlık denetimi geçmezse Swarm eski sürüme kendisi
döner.

## 4. Denetim

```bash
curl -s https://drawapi.ahmetkar.com/api/health                         # "version":"44.0.0"
curl -sI https://drawapi.ahmetkar.com/api/v1/catalog/manifest            # 200 ve Strict-Transport-Security
curl -sI http://drawapi.ahmetkar.com/api/v1/catalog/manifest             # 301 → https
curl -s -o /dev/null -w '%{http_code}\n' https://drawapi.ahmetkar.com/actuator/health   # 404 (dışarıya kapalı)
docker service logs --since 10m drawguidance_api | grep -iE "error|exception" | head
```

Günlükte şunlar görünmeli:

- `BasePackagesStartup` taban paketlerini (`base-comic`, `base-notes`, `base-art`) kaydetti ya da zaten kayıtlıydı.
- Dağıtım işçisi çalışıyor.

Bir hata görürseniz:

- `device-key` hatası: `device_data_key` sırrı eksik ya da 32 bayt değil.
- `GCS` hatası: `gcs_service_account` sırrı eksik ya da kovaya yetkisi yok.

## 5. Uygulama sürümünü kaydetmek

v44, Play'de yayımlandıktan sonra kaydedilir. Böylece eski sürümdeki cihazlara güncelleme haberi gider. Bir api
kopyasında komut satırını çalıştırın:

```bash
C=$(docker ps -q -f name=drawguidance_api | head -1)
docker exec -it "$C" sh -c 'exec java $JAVA_OPTS -jar /app/app.jar cli surum ekle --kod 44 --ad 1.2.0 --ileti "Çocuk kilidi, yeni seçim araçları ve daha fazlası"'
docker exec -it "$C" sh -c 'exec java $JAVA_OPTS -jar /app/app.jar cli surum listele'
```

Haber yalnız istemiyorsanız `--gonderme` ekleyin. Eski sürümleri zorunlu güncellemeye almak için `--en-eski <kod>`
kullanılır.

## 6. Google Play'e yükleme

1. **Paketi yükleyin.** Play Console → **DrawGuidance** → **Test ve yayın → Üretim** (ya da önce **Kapalı test**) →
   **Yeni sürüm oluştur** → `DrawGuidance-v44.aab`.
   - Paket yükleme anahtarıyla imzalıdır; Play App Signing açıktır.
   - `versionCode` 44, `versionName` 1.2.0.
2. **Sürüm notu.** Örnek: "Çocuk kilidi (ebeveyn şifresi), yeni seçim araçları, kalemle çizilen kapalı şekillerin
   içini boyama, daha fazla ücretsiz çizgi roman içeriği."
3. **Ekran görüntüleri.** **Büyüme → Mağaza varlığı → Ana mağaza girişi**. `play-ekran-goruntuleri/` klasöründen
   yükleyin:

   | Bölüm | Dosyalar | Boyut |
   |---|---|---|
   | Telefon | `telefon-01…06` | 1233×2193 |
   | 7 inç tablet | `tablet7-01…06` | 1200×1920 |
   | 10 inç tablet | `tablet10-01…06` | 1600×2560 |

   Hepsi Play sınırları içindedir: kenarlar 320–3840 piksel, uzun kenar kısa kenarın en çok 2 katı.
4. **Uygulama içeriği.** Bu sürümle değişen beyanlar:
   - **Hedef kitle ve içerik.** Uygulamada çocuk modu ve 12 yaş altına otomatik çocuk kilidi var. Hedef kitle
     beyanında 13 yaş altı seçiliyse Aile politikası geçerlidir:
     - Reklamlar yalnız Aileler sertifikalı SDK ile ve çocuklara uygun ayarla gösterilmelidir.
     - Kişiselleştirilmiş reklam olmamalıdır.
     - Hedef kitle 13 yaş üstüyse çocuk modu "ebeveyn gözetiminde" diye açıklanır.
   - **Veri güvenliği.** Değişiklik yok. Bağlantılar yalnız HTTPS; cihaz bilgileri sunucuda şifreli tutulur.
   - **Hesap silme.** Uygulama içinden ve web formundan silme önceki sürümdeki gibidir.
5. **İnceleme.** **Sürümü incele → Üretime yayınla**. Kademeli yayın önerilir: %20 → %50 → %100.
6. **Yayından sonra.** 5. adımdaki `surum ekle` komutunu çalıştırın.

## 7. Geri alma

```bash
docker service update --with-registry-auth --image ghcr.io/ahmetkar/drawguidance-server:43 drawguidance_api
# ya da: docker service rollback drawguidance_api
```

v44 sunucusu v43 uygulamasıyla uyumludur; `model3d.export` hakkını eski uygulama yok sayar. Veritabanında göç yoktur.

## 8. İstisna hesabını kaldırmak (sonraki sürüm)

Sunucuda:

1. `stack.yml`'de `BILLING_EXEMPT_EMAILS: ""` yapın.
2. Yeniden dağıtın.

Uygulamada `ExemptAccounts.EMAILS` boş bırakılır ve yeni sürüm yayımlanır.
