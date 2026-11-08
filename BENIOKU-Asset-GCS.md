# Asset dışa aktarma + Google Cloud Storage eşitleme

İki dosya: `Asset-GCS-Esitle.ps1` (asıl iş) ve `Asset-GCS-Esitle.bat` (çift tıkla/komut satırı sarmalayıcı).

## Ne yapar
1. **Dışa aktarma:** Şu anki Android sürümünün bütün pencere resimlerini sunucunun taban-paket yapısına göre bir klasöre (`tools\base_packages`) çıkarır — `V43BasePackExport` testi, `-PbasePackOut`.
2. **Eşitleme (Mongo'suz):** Sunucu CLI'si (`taban-paket --sadece-yukle`) ile klasörü `gs://dgpackets/base/...` kovasına yükler. **Artımlıdır:** her dosyanın MD5'i kovadakiyle kıyaslanır; yalnız **değişen/yeni** dosyalar gönderilir. `bundled-manifest.json` üretilip sunucu kaynaklarına kopyalanır. **Mongo'ya YAZMAZ** — paketleri sunucu açılışta `bundled-manifest.json`'dan kendisi kaydeder (`BasePackagesStartup`). Böylece Mongo'yu dışarı açman gerekmez.

> Neden düz `gsutil rsync` değil: dosya diskte `comic\...`, kovada `base\base-comic\comic\...` (paket kimliği eklenir). Doğru eşlemeyi yalnız sunucunun `BaseSeeder`'ı yapar.

## Kullanım
```bat
rem 1) Önce önizleme (hiçbir şey yüklenmez; paket/öğe sayılarını gösterir)
Asset-GCS-Esitle.bat

rem 2) Gerçek yükleme
Asset-GCS-Esitle.bat -Upload
```

## Doldurman gerekenler (ps1 başındaki AYARLAR)
- `GCS_SERVICE_ACCOUNT_JSON` → `yayin-anahtari\gcs-service-account.json` (gizli anahtar).
- `GCS_BUCKET` → `dgpackets` (hazır).
- **Mongo gerekmez.** (Script yalnız GCS'ye yükler; kayıt sunucu açılışında olur.)

## Sonra (önemli — kayıt burada tamamlanır)
`-Upload` sonrası `backend\src\main\resources\base-packages\manifest.json` güncellenir. Bunu **commit edip sunucuyu yeniden dağıt**: sunucu açılışta `BasePackagesStartup` ile `bundled-manifest.json`'u okuyup paketleri Mongo'ya (sunucunun kendi ağından) kaydeder; uygulama dosyaları imzalı V4 adreslerle indirir. Yani dosyalar yüklemeyle hemen kovada olur; paket kayıtları ise **sunucu yeniden dağıtılınca** aktifleşir.

> Mongo'yu dışarı açmak istemiyorsan yapman gereken bu kadar. İstersen alternatif: dışa aktarılan klasörü sunucuya kopyalayıp orada `taban-paket --yukle` (Mongo'lu, anında kayıt) çalıştırmak — ama gerek yok.

## Not
- İlk kez çalıştırıyorsan kova/hizmet hesabı kurulumu için `v49-backend-gcs-rehberi.md` → B bölümüne bak.
- Script `android` ve `backend` klasörlerinin proje kökünde olduğunu varsayar; `_scripts` içinden iki üst klasörü kök sayar.
