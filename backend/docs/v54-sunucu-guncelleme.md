# v54 sunucu güncelleme rehberi

Üretim sunucusunu (Docker Swarm) **v50.0.0**'dan **v54.0.0**'a günceller. Uygulama tarafı (Android/iOS 1.12.0 / 54)
eski sunucuyla da çalışır; v54 sunucu değişiklikleri yönetim komut satırı ve dağıtılan resimlerin güvenliğiyle ilgilidir.

Sıra önemli: **önce sır (1), sonra imaj (2), sonra dağıtım (3)**. 1. adım atlanırsa `stack deploy` başarısız olur
(`stack.yml` artık `cli_password_hash` sırrını bekler).

## v54'te sunucuda değişenler

| Konu | Değişiklik |
|---|---|
| Sürüm | `54.0.0` (`/api/health`). |
| Yönetim şifresi | `cli` komutlarının hepsi şifre doğrulanınca çalışır. Şifrenin PBKDF2-SHA256 özeti (`pbkdf2-sha256:<tur>:<tuz>:<özet>`) Docker sırrı `cli_password_hash` → `CLI_PASSWORD_HASH_FILE`. Şifre terminalde gizli sorulur ya da standart girdiden okunur; komut satırı argümanında kabul edilmez. 3 yanlış denemede çıkar. Yeni komut: `cli sifre-ozeti`. |
| Taban paket resimleri | `taban-paket --yukle` her dosyayı yüklemeden önce denetler: yalnız PNG/JPEG/WEBP (imzadan; uzantı uyuşmalı), en çok 20 MB ve 8192 px, parça/segment yapısı tutarlı, üst veride ya da dosya sonunda betik/HTML/PHP, çalıştırılabilir, arşiv imzası yok. Cloud Storage'a içerik türü dosyanın imzasından yazılır. |
| Veritabanı | Göç yok. |

## 1. Bir kezlik: yönetim şifresi sırrı

```bash
# Yeni imajla özet üret (şifre iki kez gizli sorulur, en az 10 karakter)
docker run --rm -it --entrypoint java ghcr.io/ahmetkar/drawguidance-server:54.0.0 -jar /app/app.jar cli sifre-ozeti
# Çıkan "pbkdf2-sha256:..." satırını sır yap
printf '%s' 'pbkdf2-sha256:...' | docker secret create cli_password_hash -
docker secret ls   # cli_password_hash görünmeli
```

Şifreyi parola yöneticisinde saklayın; sunucuda yalnız özeti vardır.

## 2. İmaj

v50 rehberindeki gibi (`docker build` / GitHub Actions) etiket `54.0.0`.

## 3. Dağıtım

```bash
API_TAG=54.0.0 docker stack deploy -c stack.yml drawguidance
curl -fsS https://drawapi.ahmetkar.com/api/health   # "version":"54.0.0"
./dg-cli.sh paket listele                           # şifre sorulmalı
```
