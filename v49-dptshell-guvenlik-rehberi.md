# v49 — dpt-shell (DEX/APK koruması) güvenlik rehberi

_Ahmet için. dpt-shell (luoyesiqiu/dpt-shell), imzalı bir APK'nın DEX dosyalarını "boşaltıp" (gizleyip) çalışma zamanında geri yükleyen bir kabuk (shell/packer) aracıdır. Tersine mühendisliği zorlaştırır. Bir Gradle eklentisi **değildir**; derlemeden **sonra** imzalı APK üzerinde çalışır. Bu rehber hem kavramı hem de bilgisayarınızda uygulama adımlarını içerir._

---

## 0. Önemli: freeRASP ile birlikte çalışma

v49'da çalışma zamanı koruması **freeRASP** var ve politikanız **kurcalama/geçersiz imza → uygulamayı kapat**. dpt-shell APK'yı değiştirdiğinden iki risk var:

1. **İmza değişir.** dpt-shell paketledikten sonra APK yeniden imzalanmalı. Eğer **dpt'nin kendi hata ayıklama anahtarıyla** imzalanırsa, imza sertifikası `TalsecGuard.SIGNING_HASHES` listesinde olmadığından freeRASP **appIntegrity** tetikler ve uygulama **kendini kapatır**.
   → **Çözüm:** paketlenen APK'yı **kendi release/yükleme anahtarınızla** yeniden imzalayın (aynı sertifika → freeRASP imzayı tanır).
2. **DEX bütünlüğü/hook sezgisi.** dpt-shell DEX'i çalışma zamanında belleğe yükler; bazı RASP motorları bunu "kod enjeksiyonu/hook" sayabilir. freeRASP'ın **onHooks** denetimi bunu yanlış pozitif olarak işaretlerse uygulama kapanır.
   → Bunu **ancak gerçek cihazda** kesin anlarsınız. Kapanıyorsa:

**Karar kuralı (sizin talimatınız):** dpt-shell freeRASP'ı bozuyorsa / sorun çıkarıyorsa **eklemeyin**. O zaman yalnız ana imzalı APK/AAB (R8 + freeRASP) dağıtılır. dpt-shell korumalı APK yalnız launch testini **geçerse** kullanılır.

> Özet: Play'e her zaman **AAB** (R8 + freeRASP, dpt-shell'siz) yüklenir — Play AAB'yi yeniden işlediğinden dpt-shell uygun değildir. dpt-shell yalnız **yan yükleme (doğrudan APK)** senaryosu içindir ve yukarıdaki launch testini geçerse.

---

## 1. Aracı edinme (bilgisayarda)

Önceden derlenmiş sürümü kullanın (kaynaktan derlemek NDK ister):

1. https://github.com/luoyesiqiu/dpt-shell → **Releases** → en son sürümün `executable.zip` dosyasını indirin.
2. Boş bir klasöre açın, ör. `C:\Users\ahmet\Tools\dpt-shell\`. İçinde `dpt.jar` ve yardımcı dosyalar olur.
3. JDK 11+ gerekir (zaten var). Kontrol: `java -version`.

---

## 2. Girdi: imzalı release APK

dpt-shell'e **imzalı** release APK'yı verin (bu sohbette üretilen `DrawGuidance-v49.apk`). Paketleme bu APK'nın imzasını geçersiz kılacağı için 3. adımda **yeniden imzalayacağız**.

---

## 3. Paketleme (imzasız bırak, sonra kendi anahtarınla imzala)

dpt'nin kendi anahtarıyla imzalamasını **engelleyip** paketleyin, sonra release anahtarınızla imzalayın:

```bat
cd C:\Users\ahmet\Tools\dpt-shell
java -jar dpt.jar -f C:\Users\ahmet\Documents\DrawGuidance\DrawGuidance-v49.apk -o out -x
```

- `-f` : girdi APK.
- `-o out` : çıktı klasörü (`out\` altında paketlenmiş APK).
- `-x` / `--no-sign` : dpt kendi anahtarıyla **imzalamasın** (biz imzalayacağız). (Sürümünüzde seçenek adı farklıysa `java -jar dpt.jar -h` ile bakın.)

Çıktı: `out\DrawGuidance-v49-dpt.apk` benzeri, **imzasız** paketlenmiş APK.

---

## 4. Zipalign + release anahtarıyla yeniden imzalama

freeRASP'ın imzayı tanıması için **aynı release/yükleme anahtarı** şart. `keystore.properties`'teki değerleri kullanın.

```bat
set BT=%LOCALAPPDATA%\Android\Sdk\build-tools\35.0.0
"%BT%\zipalign" -p -f 4 out\DrawGuidance-v49-dpt.apk out\DrawGuidance-v49-dptshell-aligned.apk

"%BT%\apksigner" sign ^
  --ks C:\Users\ahmet\Documents\DrawGuidance\yayin-anahtari\drawguidance-upload.jks ^
  --ks-key-alias <ALIAS> ^
  --out C:\Users\ahmet\Documents\DrawGuidance\DrawGuidance-v49-dptshell.apk ^
  out\DrawGuidance-v49-dptshell-aligned.apk

"%BT%\apksigner" verify --print-certs C:\Users\ahmet\Documents\DrawGuidance\DrawGuidance-v49-dptshell.apk
```

`--print-certs` çıktısındaki **SHA-256**, release anahtarınızınkiyle (yani `TalsecGuard.SIGNING_HASHES[0]`'ın kaynağı) aynı olmalı. Aynıysa freeRASP imzayı tanır.

> Base64 kontrolü (freeRASP listesiyle eşleşme):
> ```bash
> apksigner verify --print-certs app.apk | grep -i "SHA-256"
> # Hex SHA-256'yı Base64'e çevir:
> echo "<HEX>" | tr -d ':' | xxd -r -p | openssl base64
> ```

---

## 5. Gerçek cihazda launch testi (kritik)

```bat
adb install -r C:\Users\ahmet\Documents\DrawGuidance\DrawGuidance-v49-dptshell.apk
adb logcat -c
adb shell am start -n com.karapp.drawguidance/.MainActivity
adb logcat | findstr /C:"Talsec" /C:"DrawGuidance" /C:"AndroidRuntime"
```

Değerlendirme:

- **Açılıyor ve çalışıyor, Talsec "appIntegrity/hooks" kapatması yok** → dpt-shell korumalı APK kullanılabilir. Yan yükleme için bunu dağıtın.
- **Açılır açılmaz kapanıyor** ya da logcat'te `Talsec` "appIntegrity"/"hooks" → dpt-shell freeRASP ile çakışıyor. **Dağıtmayın**; yalnız ana imzalı APK/AAB'yi kullanın (talimatınız gereği).

> İpucu: Çakışmanın imzadan mı yoksa hook sezgisinden mi olduğunu ayırmak için, geçici olarak `TalsecGuard.start`'ı devre dışı bırakan bir hata ayıklama derlemesiyle dpt-shell APK'sını test edebilirsiniz; açılıyorsa sorun freeRASP+dpt etkileşimidir.

---

## 6. Dağıtım matrisi

| Kanal | Artefakt | Koruma |
|---|---|---|
| Google Play | `DrawGuidance-v49.aab` | R8 + freeRASP (Play yeniden imzalar; dpt-shell yok) |
| Yan yükleme (varsayılan) | `DrawGuidance-v49.apk` | R8 + freeRASP |
| Yan yükleme (sertleştirilmiş, **test geçerse**) | `DrawGuidance-v49-dptshell.apk` | R8 + freeRASP + dpt-shell DEX kabuğu |

---

## 7. Sık sorunlar

- **`java -jar dpt.jar` seçenekleri farklı:** sürüme göre bayrak adları değişebilir; `-h`/`--help` ile doğrulayın. İmzalamayı atlatan bayrak yoksa, dpt imzalar; siz APK'yı **zipalign + apksigner** ile release anahtarınızla **yeniden** imzalayın (eski imzanın üstüne yazılır).
- **Paketlenen APK Android 14/15'te açılmıyor:** dpt-shell sürümünü targetSdk 36 ile uyumlu en son sürüme yükseltin.
- **64-bit/çok mimari:** dpt-shell yerel kütüphaneleri enjekte eder; APK'da `arm64-v8a` (ve gerekiyorsa `armeabi-v7a`) olduğundan emin olun.
- **Play'e dpt-shell APK yüklemeyin:** Play yalnız AAB ister ve yeniden işler; dpt-shell kabuğu bozulur.
