# DrawGuidance v35 — durum

_2026-10-19 · Room v13 (değişmedi) · minSdk 21 – targetSdk 36 · sunucu 35.0.0, kapı **4747**_

## İstekler ve yapılanlar

1. **Basit modlarda aynı anda tek araç seçili.**
   - Bir araca dokununca ötekinin seçimi kalkar ve açık penceresi (arka plan, 3B şekil, elemanlar) kapanır.
   - Seçili aracın simgesinin yanında adı yazar; ötekilerde yalnız simge görünür.
   - Seçili araç, en son dokunulan araçtır (hâlâ açıksa). Penceresi kapanınca çizim modunun aracı (fırça, silgi, seç, boya…) seçili görünür.
2. **Basit not almada bütün formüller ve arka plan.**
   - Formül penceresi gelişmiş not almadakiyle aynı: bütün formüller, kategorileriyle (önceki "10 temel formül" kısıtı kalktı).
   - **Arka plan** düğmesi basit notta formül ve diyagramla birlikte şeklin hemen ardında; kaydırmadan görünür.
     - Basit resim ve basit notta adı "Arka plan"; çocuk modunda "Arka plan boyama".
     - Gelişmiş notlardaki pencerenin aynısı açılır: renk, bütün kağıt desenleri ve resimler, sayfa başına arka plan, "Tüm sayfalara uygula".
3. **Google ile girişte "Giriş iptal edildi" sorunu.**
   - Sorun: hesap seçildikten sonra pencere kapanıyor ve uygulama "iptal" alıyordu.
     - Uygulamanın Firebase kaydı doğru (SHA-1 `0e16de72…a017` ve web istemcisi `google-services.json` içinde).
     - Credential Manager'ın alttan açılan hesap penceresi bazı cihazlarda Google tarafındaki yeniden doğrulama ya da yapılandırma hatalarını "iptal" olarak bildiriyor.
   - **Yeni akış:**
     - Düğmeyle başlatılan girişte Google'ın bu iş için önerdiği "Google ile oturum aç" penceresi kullanılıyor (`GetSignInWithGoogleOption`).
     - Bu pencere iptal ya da hata dönerse aynı anda **eski Google Sign-In** (play-services-auth) hesap seçicisi açılır. Bu oturumda sonraki girişler de doğrudan onu kullanır.
     - Hesap silmede yeniden doğrulama: önce sessiz giriş, sonra hesap seçici.
   - **Hata ayrıntısı:**
     - Giriş yine olmazsa iletinin altında Google'ın kodu yazar: 10 = SHA-1/OAuth yapılandırması, 7 = ağ, 12500 = giriş başarısız.
     - Kod Ayarlar → Günlükler'de "Account" etiketiyle de görünür.
     - Yalnız gerçek iptal (12501) "Giriş iptal edildi" der.
4. **Sunucu kapısı 3000 → 4747.**
   - Kapı değişen yerler:
     - Spring Boot (`application.yml`, `PORT` ile değiştirilebilir) ve `Dockerfile` (EXPOSE, sağlık denetimi).
     - `docker-compose.yml` ve `docker-compose.sharded.yml` (`API_PORT`), `.env.example`.
     - Windows betikleri (`scripts\common.ps1` → `Telefonda-Ac.bat`, `Sunucuyu-Baslat.bat`).
     - Uygulama: varsayılan adres ve emülatör `http://10.0.2.2:4747/`, IP yazılınca eklenen kapı, "Ağda ara".
     - Ayarlar'daki örnek adres (7 dilde).
   - Telefonda daha önce kaydedilmiş `…:3000` adresi açılışta bir kez `…:4747`'ye taşınır. Kullanıcı sonradan bilerek 3000 yazarsa korunur.
   - Sunucu sürümü 35.0.0.
   - Bilgisayardaki `backend\docker\.env` dosyası (varsa) elle düzenlenmeli: `API_PORT=4747`. Bu dosya parolalar içerdiği için aktarılmıyor; betikler `.env.example`'dan kopyalıyorsa yeni değer gelir.

## Doğrulama

- **Yeni testler:**
  - `V33SimpleModeTest.onlyOneSimpleToolSelected`: tek seçili araç, adı görünür, öteki pencere kapanır.
  - `simpleNotesHasDiagramAndFormula`: bütün kategoriler; "Türev tanımı" bulunur.
  - `V35ServerPortTest`: 4747, IP'den adres, 3000'den bir kez taşıma.
  - `V35GoogleSignInErrorTest`: Google kodlarının anlamı.
- **Son doğrulama:**
  - Uygulama testleri: bütün V30–V35 sınıfları, ayarlar ve tuval akışı (gerçek Spring Boot + MongoDB 4747'de) ve sunucu adresi testleri. 79 testin 77'si ilk koşuda geçti.
  - Düşen 2 test (Resmi düzenle satırında Efektler) için kalabalık satır kuralı v34'teki haline (7+ düğme → 34 dp) döndürüldü; yeniden koşuldu ve geçti.
  - Sunucu: 29 test, hepsi geçti (sürüm 35.0.0).
  - APK: ortak anahtarla imzalandı (SHA-1 `0e16de72…a017`).
- **Sınır:** gerçek Google girişi bu ortamda denenemiyor (Google hesabı ve cihaz gerekir). Telefonda denendikten sonra sorun sürerse ekrandaki kodu bildirin.

## Dosyalar

- `data/account/Auth.kt` (yedek giriş akışı, `legacyError`), `ui/screens/account/AccountViewModel.kt` ve `AccountUi.kt` (hata ayrıntısı).
- `gradle/libs.versions.toml`, `app/build.gradle.kts` (play-services-auth 21.1.1; `API_BASE_URL` 4747).
- `ui/screens/canvasedit/CanvasEditScreen.kt` (basit menü sırası ve tek seçim), `components/EditorToolbar.kt` (`exclusiveSimpleTools`).
- `ui/drawing/SimpleCatalog.kt` (basit notta formül süzgeci yok).
- `data/settings/ServerConfig.kt` (kapı ve taşıma), `data/remote/LanScanner.kt`, `MainActivity.kt`.
- `res/values*/strings*.xml` (örnek adres, basit not açıklaması), `resources/i18n/*.tsv` (Google hata ayrıntıları).
- **Sunucu:** `application.yml`, `Dockerfile`, `docker/docker-compose*.yml`, `docker/.env.example`, `build.gradle.kts`, `DrawGuidanceProperties.java`, `HealthApiTest.java`, `README.md`.
- **Betikler:** `windows/scripts/common.ps1`.
- **Testler:** `V33SimpleModeTest.kt`, `V35Test.kt`, `SettingsFlowTest.kt`, `ServerConfigNormalizeTest.kt`, `CanvasFlowTest.kt`, `V33SyncTest.kt`.
