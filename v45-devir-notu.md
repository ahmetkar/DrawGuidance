# DrawGuidance — v45 devir notu (sonraki Claude için)

_2026-10-31. Hafızası olmayan yeni bir Claude oturumu projeyi buradan sürdürebilsin diye yazıldı. Önce bunu, sonra
`v45-durum.md` (kullanıcıya dönük özellik listesi), sonra `YENI-SOHBET-DEVIR.md` (mimari, paketler, yayın; v44'e kadar
ayrıntılı) okuyun._

## 0. Kısaca

- Uygulama: Android çizim uygulaması `com.karapp.drawguidance` ("Çizim Rehberi" / "DrawGuidance"), Kotlin + Compose.
  Sunucu Spring Boot + MongoDB (`backend/`, prod `https://drawapi.ahmetkar.com`), site Next.js (`web/`).
- Son sürüm **v45**: versionCode 45, versionName **1.3.0**, Room **v23** (v44'ten beri değişmedi), sunucu 44.0.0
  (değişmedi). Teslim: imzalı `DrawGuidance-v45.apk` + `DrawGuidance-v45.aab`.
- Kullanıcı **Ahmet**, Türkçe yazar; **Türkçe yanıt verin**. Kısa, sonuca giden yanıtları sever; ilerleme görmediğinde
  rahatsız olur (uzun işlerde durum satırını sık güncelleyin).

## 1. Dosyalar nerede (doğruluk kaynağı)

| Yer | Ne |
|---|---|
| Bilgisayar `C:\Users\ahmet\Documents\DrawGuidance\` | **Doğruluk kaynağı.** `.git` yok. `android\`, `backend\`, `web\`, `scripts\`, `tools\`, `yerel-sunucu\`, `belgeler\`, kökte `vNN-durum.md`, `YENI-SOHBET-DEVIR.md`, bu not. `app\` altında APK/AAB'ler. `yayin-anahtari\` sırlar (DOKUNMAYIN, yazdırmayın). Eski/yerine konan dosyalar `_eski_vNN\` altında (asla silinmez). |
| Bulut `/home/claude/DrawGuidance-proje` (yalnız o oturumun kapsayıcısında) | git deposu. v44 kaynağı `ee42f19`'da PC'den kopyalandı. Taban `e7ce7d1`; teslimde `git reset --soft e7ce7d1` ile bütün değişiklikler **staged** tutulur (Ahmet'in isteği). Kapsayıcı geçicidir, yeni oturumda yoktur. |
| Proje klasörü `/mnt/project-files/drawguidance/` | Kalıcı ortak klasör: APK/AAB'ler, `vNN-durum.md`, `YENI-SOHBET-DEVIR.md`, bu not, çevrimdışı Google kütüphaneleri `gmaven-v39/` ve `gmaven-v45/`, `sdk/`. |

**Yeni bulut oturumunda derlemek için:** PC klasörünü cihaz klasör araçlarıyla bağlayın (`connect_device`), kaynağı
buluta kopyalayın (FUSE yavaş, `device_bash` 45 sn sınırı: tar ile parça parça), `git init` + taban commit. Android
SDK `/root/android-sdk` (yoksa `/mnt/project-files/drawguidance/sdk/`'dan), Gradle `/opt/gradle-8.14.3`, JDK 21.
`dl.google.com` engelli; `settings.gradle.kts` `gmaven-v39` ve `gmaven-v45` klasörlerini yerel Maven deposu olarak
ekler. Derleme: `cd android && /opt/gradle-8.14.3/bin/gradle --offline --max-workers=2 …`.

## 2. Kurallar (Ahmet'in kalıcı istekleri)

- Türkçe yanıt. Bilgisayara **yalnız Ahmet açıkça söyleyince** yazın; yazarken eskiyi `_eski_vNN\`'e taşıyın, silmeyin.
- Değişen dosyalar staged kalsın (commit'siz teslim). Zip yalnız istenirse.
- Sürüm belgeleri PC kökünde (`vNN-durum.md`).
- Fazladan `CanvasDto.kt`'ye dokunmayın. Windows ayarlarını değiştirmeyin. Yalnız isteneni silin.
- İnternetten resim ya da telifli karakter izlemeyin/kopyalamayın.
- Gizli anahtarları (keystore parolaları, hizmet hesabı JSON'ları, Slack webhook, `sunucu.env`) asla yazdırmayın,
  commit etmeyin.
- **Her özellik için test yazın.** Testleri geçiştirmeyin; ayrıntılı rapor isteniyor.
- Push, PR ya da prod değişikliği sormadan yapılmaz. Android 10'da test şart değil (cihaz yok).

## 3. v45'te ne yapıldı, kod nerede

Özellik listesi `v45-durum.md`'de. Geliştiriciye dönük harita:

| Madde | Ana dosyalar (`android/app/src/main/java/com/karapp/drawguidance/ui/screens/canvasedit/` altında, aksi yazmıyorsa) |
|---|---|
| 1 Çocuk modu | `components/KidsToolbar.kt` (menü, sabit geri/ileri al, gruplar), `editor/KidsBrush.kt`, `state/KidsBrushController.kt`, `components/KidsBrushTool.kt`, `core/content/.../fill/RegionClip.kt`, `res/values*/strings_v45_kids.xml` |
| 2 Kapak | `core/model` `PageBackground.cover` (`Stroke.kt`), `Canvas.kt`, `PageSettings.kt`; `PageStrip.kt`, `PageScrubber.kt`, `V40EditorExtras.kt`, `PageDecorRenderer.kt`, `ImageExporter.kt`, `CanvasRepository.kt`, `CanvasListViewModel.kt` |
| 3 Düğme adları | `V45InlineLabels.kt`, `ToolHelp.kt` |
| 4 Basit not | `SimpleCatalog.kt`, `ShapeRegistry.kt` |
| 5–9 Bilim | `core/content/.../math/MathExpr.kt`, `formula/FormulaEval.kt`, `math/Exact.kt`, grafik dosyaları (`Graphs`, `GraphFormula`, `GraphModel`…), bilim kategorileri (Physics, Chemistry, GeometryPlus, Biology), `library/Library.kt` (`LibShape.param`); uygulamada `ParamShapeController.kt`, `components/ParamControls.kt`, `ShapeController.kt` (PARAMS aracı, HANDLE sürükleme), `FormulaController.kt`, `strings_v45_science.xml` |
| 10 Panel ekle | `V45Icons.NotesPanels`, not paneli yerleşimleri (çizgi roman panel motoru), `notes.panel` şekli |
| 11–12 Menü sırası/gruplar | `V45ToolRow.kt`, `EditorToolbar.kt`, `NeuComponents.kt` |
| 13 Tuval menüleri | `V45CanvasMenus.kt`, `CanvasMenus.kt`, `CanvasEditScreen.kt`, `BackgroundTransformOverlay.kt`, `strings_v45_editorui.xml` |
| 13.1 Tablet pencereleri | `V45TabletWindows.kt`, `NeuSheet.kt`, `EditorSidePanel.kt` |
| 14 R8 | `android/app/build.gradle.kts` (`proguard-android-optimize.txt`), `android/app/proguard-rules.pro` (`-dontoptimize` yok), `android/gradle.properties` (`android.enableR8.fullMode=false` KALMALI) |

Model değişiklikleri hep geriye uyumlu: `ShapeSpec.params`, `GraphSpec.angleDeg`, `PageBackground.cover` hepsi
`@EncodeDefault(NEVER)` ya da false iken yazılmaz. **Room göçü yok (v23).**

### R8 hakkında bilinmesi gereken (önemli)

- v40'ta Android 10'da Ayarlar çöküyordu; v42'de `android.enableR8.fullMode=false` ile düzeldi. Ahmet
  "optimizasyonu kaldırınca ayarlar sorunsuz çalışıyordu" dedi. İnceleme: o "optimizasyon" aslında **R8 tam kipi**ydi;
  `-dontoptimize` hep vardı (v40–v44'te DEX iyileştirmesi hiç açık değildi).
- v45: iyileştirme açık, tam kip **kapalı**. Tam kipi açmayın; açmak gerekirse Ayarlar ekranını gerçek Android 10
  cihazda deneyin. Yansımayla kullanılan sınıflar için `proguard-rules.pro`'daki keep kuralları `V45R8ConfigTest`
  ile korunuyor.
- Karşılaştırma aracı (yalnız o kapsayıcıda): `/home/claude/r8cmp/scan.py` (dexdump ile iki APK'nın sınıf adı
  dizgilerini karşılaştırır).

## 4. Test durumu (v45 son ana dal)

Son ana dalda (bütün v45 dalları birleşik, `8f9e22c` içeriği) tam takım, 2026-11-01:

| Modül | Test | Hata | Atlanan |
|---|---|---|---|
| `:core:model:test` | 34 | 0 | 0 |
| `:core:content:test` | 313 | 0 | 20 (v45 öncesinden atlananlar) |
| `:app:testDebugUnitTest` (368 sınıf) | 898 | **3** | 31 (kendini atlayan ekran görüntüsü testleri) |

**Yeni v45 testleri: 40 sınıfta 177 test, hepsi geçti.** Çocuk (V45Kids*: fırça, menü mantığı, 6 ekran düzeni,
karanlık tema karşıtlığı, metinler, görev fırçası; V45RegionClipTest), kapak/not/panel (V45Cover*, V45ComicCoverUiTest,
V45NotesPanels*, V45SimpleNotes*, V45NotesComicStringsTest), bilim (V45MathExpr 11, V45FormulaEval 15, V45GraphFormula 13,
V45ParamShapes 14, V45Chemistry 10, V45ScienceLogic/Ui 8), menüler (V45EditorRules 7, V45PortraitLabels 5,
V45ToolGroups 3, V45CanvasMenus 5, V45RtlMenu, tablet pencereleri, V45SharedFeatures 3), R8 (V45R8ConfigTest 4).

**Kalan 3 kırmızı, hiçbiri v45'ten değil:**
- `CanvasFlowTest.createDrawUpdateDeleteLocallyWithoutCloud` ve `V33BackendTest.healthAndCatalogFromRealServer`:
  127.0.0.1:4747'de gerçek sunucu ister (ConnectException). Arayüz adımlarının hepsi geçiyor.
- `V37KidsMascotTest.suggestionsSpeakInBalloonApplyAndClose`: v44 tabanında da aynı yerde düşüyor (bkz. 5.2).

Yayın derlemesi: `:app:assembleRelease :app:bundleRelease` başarılı (12 dk 31 sn, R8 uyarısı yok). `apksigner`
v1+v2 doğruladı, AAB `jarsigner` "jar verified"; sertifika CN=Ahmet Kar, O=DrawGuidance (SHA-256 `4296621d…6a022a`).
APK `aapt2`: versionCode 45, versionName 1.3.0. APK 62,76 MB (v44 64,16), AAB 69,78 MB, DEX 19,05 MB / 27151 sınıf
(v44: 24,4 MB / 33618). Yansıma dizgisi taraması: v44'te bulunup v45'te kaybolan sınıf adı 0.
SHA-256: APK `d70ef65c0f6a637c…77ccba`, AAB `f855935a202654b1…49a81`.
R8 eşleme dosyası (çökme raporlarını çözmek ve Play Console'a yüklemek için): `DrawGuidance-v45-mapping.txt.gz`.

### Test yazarken bilinen tuzaklar

- Robolectric `forkEvery=1`; tam takım bu kapsayıcıda 1–2 saat sürer. Uzun Gradle komutlarını arka planda,
  uzun zaman aşımıyla çalıştırın.
- Üst menü satırları artık kayıyor: düğmeye tıklamadan önce `EditorTestBase.clickTool()` ya da `performScrollTo()`.
- Ekran dışındaki düğümün kırpılmış `boundsInRoot`'u sıfırdır; sıra karşılaştırmasında `positionInRoot` kullanın.
- Kayan satırın içinde seçili öğeyi `scale()` ile büyütmek `performScrollTo`'yu sonsuz döngüye sokup OOM yaptı;
  yapmayın.
- Seçim önerisi (gruplama önerisi) arayüzü Robolectric'te Compose'un durulmasını engelleyebilir; programla çok eleman
  ekleyen testlerde `prefs().setSelectionSuggest(false)`.
- Kayıt bekleyen testlerde `waitFor { …single{} }` yerine `singleOrNull`.
- `CanvasFlowTest`, `V33BackendTest`: 127.0.0.1:4747'de gerçek sunucu ister.
- Performans testleri (`HandwritingTest` performans, `V9PerformanceTest`) yük altında düşer; tek başına koşun.

## 5. Bilinen sorunlar ve öneriler (Ahmet'e de bildirildi)

1. **Gelişmiş modlarda dikeyde geri al kayan satırda ekran dışına gidebilir** (madde 3 ve 12'nin sonucu). Öneri:
   çocuk menüsündeki gibi geri al / ileri al'ı sabitlemek.
2. **`V37KidsMascotTest.suggestionsSpeakInBalloonApplyAndClose`** v44 tabanında da düşüyor (v45'te `V37KidsTest.kt:350`).
   Maskot katmanı 3. ve 4. dairenin başını örtüyor olabilir; incelenmeli.
3. **"dg-warmup" başlangıç iş parçacığı** (`DrawGuidanceApp.kt:173`) Robolectric sıfırlamasıyla yarışıp testleri
   ara sıra kırıyor. Öneri: `TestDrawGuidanceApp`'te atlamak ya da testte beklemek.
4. **PDF sayfa aralığı penceresi kapağı sayıyor** ("1–1" kapak demek). Öneri: kapağa duyarlı etiketler.
5. **Seç kipinde dokunma seçime ekliyor** (çoklu seçim). Kullanıcı yerine geçmesini bekleyebilir.
6. **Parametreli şekilde vektör düzenlemesi**, sonradan değer değişince kaybolur (şekil yeniden kurulur).
7. **Bilim şekillerinin içindeki etiketler yalnız Türkçe** (eski bilim şekilleri gibi); arayüz metinleri 7 dilde.
8. Düzenle menüsünde "Nokta düzenle" hâlâ doğrudan NODES açıyor (bilinçli bırakıldı).
9. Sunucu katalogundaki bilim şekli kopyaları parametreli şekillerin yerine geçmez (geçerse düzenleyici kapanırdı).
10. Android 10 Ayarlar çökmesinin nedeni (R8 tam kipi) çıkarımdır, cihazda doğrulanmadı.

## 6. Sonraki adımlar (önerilen)

- AAB'yi Play Console'a yüklemek (Ahmet yapar; `v44-yayin-rehberi.md` / `play-yayin-rehberi.md`).
- Yukarıdaki 1, 3 ve 4 numaralı öneriler küçük ve güvenli işler.
- Yeni sürüm: versionCode 46'dan devam; yeni `vNN-durum.md` PC köküne.

## 7. Teslim edilen dosyalar

- `DrawGuidance-v45.apk`, `DrawGuidance-v45.aab` (yükleme anahtarıyla imzalı) ve `DrawGuidance-v45-mapping.txt.gz`: proje klasöründe ve PC'de `app\`. v44 APK/AAB `_eski_v44\app\`'te.
- Kaynak: PC klasörü v45 hâline güncellendi (165 dosya yazıldı, içerikleri md5 ile doğrulandı); değişen ya da kaldırılan 106 dosyanın v44 hâli `_eski_v44\` altında aynı göreli yollarla. Aktarım arşivi ve dosya listeleri `_eski_v44\_aktarim\`. PC'deki dosyalar yazmadan önce v44 kopyasıyla (`ee42f19`) birebir aynıydı.
