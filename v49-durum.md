# DrawGuidance — v49 durum belgesi

_2026-11-02. Bir önceki sürüm v48 (versionCode 48, 1.6.0). Bu belge v49'da yapılan değişiklikleri, doğrulamayı ve dosyaları özetler. Yeni sohbet için genel devir belgesi `YENI-SOHBET-DEVIR.md`'dir; önce onu okuyun._

**v49 özeti:** versionCode **49** (versionName **1.7.0**), Room **v23** (değişmedi), sunucu değişmedi (44.0.0). Üç kullanıcı isteği + güvenlik:

1. **Basit modlarda dikey ekrandaki üç menü tek satır, sığmazsa yatay kaydırma** (alt satıra sarmaz).
2. **Çizgi romanda eklenen mekân arka planı, bırakıldığı panele (ya da tuvale) sığar.**
3. **Eğitimler** bu değişikliklere göre güncellendi (yeni menü/özellik adımlarını tanıtan v48 yenilik grupları zaten vardı; v49'da menü düzeni değiştiği için korunur ve doğrulanır).
4. **Güvenlik:** çalışma zamanı koruması **freeRASP (Talsec Community 19.3.1)** eklendi; **R8 korundu** (dProtect eklenmedi); **dpt-shell** DEX/APK koruması ayrı, etiketli bir korumalı APK olarak üretilir (freeRASP'ı bozarsa eklenmez — Ahmet'in talimatı).

---

## 1. İstekler ve yapılanlar

### 1. Basit modlarda üç menü tek satır + yatay kaydırma

- **Sorun:** v48'de basit resim/basit not dikey ekranında üç menü (Ekle / Çiz / Seç ve düzenle) vardı; sığmayan düğmeler **alt satıra sarıyordu**. İstek: her menü **tek satır**, ekrana sığmazsa **yatay kaydırılsın**.
- **Yapılan:** `components/V48SimpleMenus.kt` içindeki `SimpleMenuColumn`, `FlowRow` (sarma) yerine her menü için **tek satır** çizer. Satır genişliği aşarsa iç `Row` `horizontalScroll` ile **yatay kayar**; alt satıra sarmaz, gruplama yok.
- **Çiz menüsünde geri al / yinele** satırın başında **sabit** kalır (kaydırılan bölümün dışında, hep görünür); kalan araçlar kaydırılan bölümde.
- Her düğmede simge ve adı yan yana (gelişmiş modların dikey ekranındaki gibi), v48'deki gibi korunur.

### 1.1 Çizgi roman mekân arka planı panele/tuvale sığar

- **Sorun:** Çizgi romanda "Mekân arka planı" bir panele bırakıldığında arka plan tüm tuvale yayılıyordu; panele sığmıyordu.
- **Yapılan:** `CanvasEditViewModel.insertLocationBackground` artık bırakılan **panelin dörtgenini** hesaplar: panelin sınır dikdörtgenini hedef olarak, panelin köşe noktalarını **kırpma (clip)** olarak `ImageController.insertBackdrop`'a geçirir.
- `ImageController.insertBackdrop(path, target, clip, onDone)` yeni `clip` parametresi alır; kırpma yalnız tam **4 köşeli dörtgen** panelde uygulanır.
- `Backdrop.panelClip(stroke)`: panelin köşe noktalarını ayıklar; yakın/eş noktaları birleştirir, kapanış noktasını atar; tam dörtgen değilse (pahlı/yuvarlatılmış panel) `null` döner → o zaman arka plan panelin **sınır dikdörtgenine** sığdırılır.
- Panel yoksa arka plan tüm tuvale sığar (eski davranış).

### 2. Eğitimler güncel

- v48 yenilik eğitimleri (mod menüsü, Özellikler, Taşı/Genişlet) `TutorialV48.kt` ve `TutorialScripts.VERSION = 48` ile zaten tanımlıydı; v49 menü düzeni değiştiği için bu adımlar **korunur ve testle doğrulanır**.
- Çizgi roman yenilik eğitiminde **Özellikler (BRUSH_SETTINGS)** adımı; çizgi romanın tüm grupları arasında **Mekân arka planı** (`add("locations")`) eğitimi korunur (1.1 ile uyumlu).
- Eğitim metinleri 7 dilde (`strings_v48_tutorial.xml`: tr/en/ar/ja/ko/ru/zh).

### 3. Güvenlik — freeRASP (çalışma zamanı) + dpt-shell (DEX/APK)

**freeRASP (Talsec Community 19.3.1) — çalışma zamanı koruması**

- Depolar: `settings.gradle.kts`'ye Talsec Maven depoları eklendi (`common`, `freerasp`).
- Bağımlılık: `app/build.gradle.kts` → `com.aheaditec.talsec.security:TalsecSecurity-Community:19.3.1`.
- `security/TalsecGuard.kt` (yeni): açılışta `DrawGuidanceApp.onCreate`'te başlatılır.
  - Algılar: root, hata ayıklama, emülatör, **hook (Frida/Xposed)**, **kurcalama/yeniden paketleme (imza uyuşmazlığı)**, resmi olmayan mağaza, otomasyon, kötü amaçlı uygulamalar, vb.
  - **Politika (Ahmet'in seçimi):** **hook** ve **kurcalama/geçersiz imza** algılanınca uygulama **kapatılır** (yalnız yayın derlemesinde; hata ayıklamada yalnız günlüğe yazılır). Öteki tehditler (root, emülatör, resmi olmayan mağaza…) yalnız günlüğe yazılır — hatalı pozitiflerde kullanıcı mağdur olmaz (Ahmet yan yükleme yapıyor).
  - **İmza hash'leri (Base64 SHA-256):** release/yükleme anahtarı + 3 Play App Signing anahtarı. Hepsi geçerli olduğundan Play'den inen ve yan yüklenen yasal kopyalarda bütünlük tehdidi tetiklenmez. (Play, yüklenen AAB'yi kendi anahtarıyla **yeniden imzalar**; Play anahtarları eklenmeseydi her Play kurulumu "kurcalanmış" sayılıp — kapatma politikası yüzünden — kendini kapatırdı.)
  - **R8 ile uyum:** Talsec AAR kendi tüketici ProGuard kurallarını (`proguard.txt`) içerir; R8 bunları otomatik uygular (native metotlar, ASN.1 ve `androidx.security.*` korunur). Ek keep kuralı gerekmez.
  - **Testlerde:** `android.os.Build.FINGERPRINT == "robolectric"` iken `start()` erken döner (yerel `androidx.security.BNatives` JVM'de yüklenemez; freeRASP yalnız gerçek cihazda anlamlı).

**R8 korundu, dProtect eklenmedi**

- İlk planda Kotlin/Java gizleme için dProtect düşünülmüştü; Ahmet "R8 kalsın" dediği için **dProtect eklenmedi** (dProtect R8'in yerine geçer). R8 küçültme + kaynak küçültme + kod iyileştirme açık, **tam kip kapalı** (`android.enableR8.fullMode=false`, kaldırmayın).

**dpt-shell — DEX/APK koruması (ayrı, koşullu artefakt)**

- dpt-shell bir Gradle eklentisi değil; imzalı APK **üzerine** çalışan bir araçtır (DEX'i boşaltıp çalışma zamanında geri yükler).
- Üretilen ana imzalı APK/AAB (R8 + freeRASP) **değiştirilmez**. dpt-shell ayrı, etiketli bir korumalı APK olarak üretilir (`DrawGuidance-v49-dptshell.apk`).
- **Çakışma riski:** dpt-shell'in DEX oynaması freeRASP'ın bütünlük/hook denetimini tetikleyebilir. Ahmet'in talimatı: **dpt-shell freeRASP'ı bozarsa/sorun çıkarırsa eklenmez.**
- dpt-shell öncelikle **yan yükleme (APK)** içindir. Play'e **AAB** yüklenir; AAB'yi Play yeniden işlediğinden dpt-shell Play dağıtımına uygulanmaz.

**dpt-shell sonucu (bu ortamda yapılan):**

- Araç: dpt-shell **v2.21.1** (önceden derlenmiş). İmzalı release APK'sı (`DrawGuidance-v49.apk`) paketlendi; **kendi release/yükleme anahtarınızla** imzalandı (imza sertifikası release ile aynı → freeRASP imzayı tanır). Çıktı: `DrawGuidance-v49-dptshell.apk` (v1+v2+v3 imza doğrulandı, SHA-256 release ile aynı).
- dpt'nin kendi RASP'ı (anti-debug / Frida / CRC) `--disable-*` bayraklarıyla kapatılmaya çalışıldı; ancak APK'ya gömülen `risk_check_flags` yine `7` yazıldı — yani dpt'nin iç kontrolleri **hâlâ etkin olabilir**, bu da freeRASP ile çakışabilir.
- **Cihaz doğrulaması yapılamadı** (bu bulut ortamında gerçek cihaz yok). Bu yüzden `DrawGuidance-v49-dptshell.apk` **deneysel** teslim edilir: gerçek cihazda açılıp freeRASP'ın kapatmadığı (logcat'te `Talsec` "appIntegrity"/"hooks" yok) doğrulanmadan **dağıtılmamalı**. Doğrulama `v49-dptshell-guvenlik-rehberi.md` → "5. Gerçek cihazda launch testi"nde. Test başarısızsa yalnız ana imzalı APK/AAB kullanılır (talimatınız gereği).
- **Ana `DrawGuidance-v49.apk` / `.aab` değiştirilmedi** — dpt-shell yalnız ayrı, isteğe bağlı bir artefakt.

### Sürüm

- `app/build.gradle.kts`: versionCode **48 → 49**, versionName **1.6.0 → 1.7.0**.

---

## 2. Doğrulama

- **Etkilenen birim testleri (hepsi geçer):**
  - `V48Test`: basit modda üç menünün **tek satır** olduğu ve ekle menüsünün **yatay kaydığı** (bazı düğmeler ekran dışında); `panelClip` tam dörtgende 4 nokta, pahlı panelde `null`.
  - `V37SimpleModesTest`: `tappedButtonShowsItsNameForAWhile` — sabit geri al düğmesinde `performScrollTo` kaldırıldı (artık kaydırma atası yok, hep görünür).
  - `V46ToolRowSplitTest`: `whatsNewIsV48Everywhere` (VERSION 48, v48 grup kimlikleri).
  - `V41TutorialsTest`: `everyKindHasTutorials` — çizgi roman yenileğinde BRUSH_SETTINGS adımı, gruplarda `add("locations")` korunur.
  - `V33SimpleModeTest`, `V45PortraitLabelsTest`: değişiklikten etkilenmez, geçer.
- freeRASP ile **çevrimiçi derleme + derleme** BAŞARILI (AAR API'si `javap` ile doğrulanmıştı: paket `app.talsec.rasp.security.api`, `TalsecConfig.Builder`, soyut `ThreatListener.ThreatDetected`).
- Tam birim test takımı arka planda çalıştırıldı; yerel sunucu (127.0.0.1:4747) ya da ağ gerektiren testlerdeki hatalar **ortamsal**dır (bu bulut ortamında yerel MongoDB+sunucu yok), v49 değişikliklerinden değil.

---

## 3. Dosyalar

**Yeni:**

- `app/src/main/java/com/karapp/drawguidance/security/TalsecGuard.kt`
- `app/src/main/java/com/karapp/drawguidance/ui/screens/canvasedit/tutorial/TutorialV48.kt`
- `app/src/main/res/values/strings_v48_tutorial.xml` (+ `values-en|ar|ja|ko|ru|zh`)

**Değişen:**

- `app/build.gradle.kts` (versionCode 49, versionName 1.7.0, Talsec bağımlılığı)
- `settings.gradle.kts` (Talsec Maven depoları)
- `app/src/main/java/com/karapp/drawguidance/DrawGuidanceApp.kt` (`TalsecGuard.start`)
- `app/src/main/java/com/karapp/drawguidance/ui/screens/canvasedit/components/V48SimpleMenus.kt` (tek satır + yatay kaydırma)
- `app/src/main/java/com/karapp/drawguidance/ui/screens/canvasedit/CanvasEditViewModel.kt` (`insertLocationBackground` panel kırpma)
- `app/src/main/java/com/karapp/drawguidance/ui/screens/canvasedit/state/ImageController.kt` (`insertBackdrop` clip; `Backdrop.panelClip`)
- `app/src/main/java/com/karapp/drawguidance/ui/screens/canvasedit/tutorial/TutorialScripts.kt` (VERSION 48, v48 grupları)
- Testler: `V48Test.kt`, `V37SimpleModesTest.kt`, `V46ToolRowSplitTest.kt`, `V41TutorialsTest.kt`

**Teslim:**

- İmzalı `DrawGuidance-v49.apk` ve `DrawGuidance-v49.aab` (R8 + freeRASP).
- Koşullu: `DrawGuidance-v49-dptshell.apk` (dpt-shell korumalı; freeRASP'ı bozmazsa).
- Rehberler: `v49-backend-gcs-rehberi.md`, `v49-dptshell-guvenlik-rehberi.md`.

---

## 4. Açık konular / dikkat

- **freeRASP gerçek cihazda denenmeli:** Play iç test kanalından inen kopya (Play'in yeniden imzaladığı) açılmalı ve kapanmamalı — imza hash'lerinin doğru olduğunu kanıtlar. Yan yüklenen imzalı APK de açılmalı. Bir Frida/Xposed denemesi uygulamayı kapatmalı.
- **Talsec portalı (watcherMail):** `TalsecGuard.WATCHER_MAIL` tehdit raporlarının gideceği adrestir; yalnız Talsec portalına kaydolunursa çalışır. İstenirse değiştirilebilir/boş bırakılabilir.
- **dpt-shell:** sonucu "dpt-shell sonucu" notuna göre; freeRASP ile çakışırsa yalnız ana imzalı APK/AAB teslim edilir.
- Önceki sürümlerden kalan backend/GCS işleri `v49-backend-gcs-rehberi.md`'de toplandı (v43/v44 dağıtımı hâlâ uzak sunucuya kurulmayı bekliyorsa).
