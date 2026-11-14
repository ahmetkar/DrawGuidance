# DrawGuidance v50 devir notu (Android + iOS birleşik)

_2026-11-14. Hafızası olmayan yeni bir Claude sohbeti için. Önce bunu, sonra `backend/docs/v50-sunucu-guncelleme.md`,
`backend/docs/v50-app-store-abonelik.md`, sonra `YENI-SOHBET-DEVIR.md` (v49 ve öncesi) ve `v49-durum.md`'yi okuyun._

## Özet

- **Sürüm:** Android versionCode **50** / versionName **1.8.0**; iOS **1.8.0 (50)**; sunucu **50.0.0**. Room **v23** (değişmedi).
- **Kaynak:** GitHub `github.com/ahmetkar/DrawGuidance` (**public**), dal **`ios/v49-port`**: Android, iOS, sunucu ve
  araçlar tek dalda. `android-v49` dalı v49'un sır içermeyen kopyasıdır (taban). `main` yalnız README.
- **PC:** `C:\Users\ahmet\Documents\DrawGuidance` asıl kopya. v50 değişen dosyaları zip ile açıldı (eskiler
  `_eski_v49\v50-oncesi\`), ardından ek düzeltmeler zip'i (`DrawGuidance-v50-ek-duzeltmeler.zip`), ardından iOS düzeltme zip'i (`DrawGuidance-v50-ios-duzeltme.zip`). PC'de
  yeni `ios\` klasörü var.
- **Teslimler** (`/mnt/project-files/drawguidance/v50/` ve sohbette): `DrawGuidance-v50.apk` ve `.aab` (yükleme
  anahtarıyla imzalı, R8 + freeRASP), `DrawGuidance-v50-mapping.txt.gz`, Device Farm için imzasız
  `DrawGuidance-v50-ios-unsigned.ipa`.
- **Sunucu henüz güncellenmedi.** Ahmet her şey bitince `backend/docs/v50-sunucu-guncelleme.md` ile güncelleyecek.

## v50'de neler var

1. **iOS sürümü (yeni):** `ios/` altında baştan Swift/SwiftUI (Kotlin yok). XcodeGen (`ios/project.yml`), bundle id
   `com.karapp.drawguidance`, iOS 17, Swift 5 dil kipi. Android'deki bütün modlar, araçlar, eğitimler, çocuk modu,
   çizgi roman, 3B, bilim şekilleri, ana ekran sekmeleri, tuval formu (şablon/boyut/arka plan), Ayarlar. AdMob test
   kimlikleri. Android'in `strings.xml`'leri `ios/tools/strings-export/convert.py` ile
   `Resources/<dil>.lproj/Android.strings`'e çevrilir (`L("anahtar")`); koddaki Türkçe metinler `I18n.tr` + `Resources/i18n/*.tsv`.
2. **Resimden çizim eğitimi (vektörleştirme + kalıpla çizme)**: Android `TutorialV50.kt`, iOS `Tutorial/TutorialV50.swift`;
   `TutorialScripts.VERSION = 50`. Mağaza görüntü araçları `android/tools/store-shots`.
3. **Yetişkinler için alıştırmalar:** 23 yeni kütüphane şekli (`lib.practice.*`), 36 seviye (üzerinden çiz / kaybolan
   kılavuz / simetri). Basit resimde temel olanlar, gelişmiş resimde hepsi. Şekil önerisi şablonları `alistirma.*`,
   `ShapeTemplates.VERSION = 8`. Android `challenge/Practice*.kt`, iOS `Features/CanvasEdit/Challenge/Practice*.swift`.
4. **Google ve Apple ile giriş:** iki platformda Firebase Auth. Android'de Apple, `OAuthProvider("apple.com")` ile.
   iOS'ta Firebase 11.15 + GoogleSignIn 8 (SPM). `GoogleService-Info.plist` depoda yok: CI, GitHub sırrı
   `GOOGLE_SERVICE_INFO_PLIST_B64`'ten yazar. Sunucu `users.signInProvider` alanını tutar. Apple Team ID `G3LKL7QH6D`.
5. **Düzenleyici, çeviri ve çizgi roman:**
   - Dikey ekranda alt pencere küçük; bağlam menüsüyle çakışınca son kullanılan üstte (Android `NeuSheet`,
     iOS `DockedSheets.swift`).
   - Şekil ve şablon yazıları ile örnek metinler çevrildi; çizgi roman şablonlarında örnek kapak var.
   - Karakter, efekt ve nesne kategorileri bağlama göre yeniden adlandırıldı.
   - Odak çizgileri çoğaldı ve "Çizgi roman" / "Manga" diye ayrıldı; her biri ayrı düzenlenir (`ComicLinesKind`'a
     sona eklenen 9 tür).
6. **Abonelik güvenliği:** v44 istisna hesabı kaldırıldı (Android `ExemptAccounts`, sunucu `BILLING_EXEMPT_EMAILS`).
   Android'de hak yalnız sunucunun en çok 7 gün önce doğruladığı kayıtla açılır; 12 saatte bir yeniden doğrulanır.
   Sunucu 12 saatten eski Play kaydını Play'den yeniden okur.
7. **iOS StoreKit 2 aboneliği:** ürün `com.karapp.drawguidance.premium.monthly`. Sunucuda
   `/subscriptions/verify-apple` ve `/billing/apple-notifications` uçları var; iki platformda aynı Premium geçerli.
   Sunucunun Apple ayarları `stack.yml`'de, `.p8` anahtarı Docker sırrı `apple_iap_key`.

## Derleme

- **Android (bulut ya da PC):** `android/` → `gradle :app:assembleRelease :app:bundleRelease`.
  - Gereken dosyalar (git'te yok): `app/keystore/release.properties` + `drawguidance-upload.jks` (PC'de `yayin-anahtari\`),
    `app/keystore/debug.keystore` (proje arşivinde) ve `app/google-services.json` (PC'de `android\app\`).
  - Maven Central 429 verirse `DG_MAVEN_MIRROR=https://maven-central.storage-download.googleapis.com/maven2/` ortam
    değişkeniyle derleyin (`settings.gradle.kts` destekler).
  - Talsec deposu artık bulutta erişilebilir.
- **iOS:** yalnız macOS. GitHub Actions `.github/workflows/ios.yml` her push'ta çalışır (ios/** ve iş akışı dosyası
  değişince). Çalıştırıcı `macos-15-intel`; arm64 kuyruğu çok bekletiyor. Adımlar:
  1. Firebase plist (sırdan)
  2. XcodeGen
  3. Release derleme ve imzasız `.ipa`
  4. Açılış tanısı (ekran görüntüsü ve `sample`)
  5. Birim ve arayüz testleri (`continue-on-error`)

  Çıktılar `ios-build` adlı yapıtta. Tam koşu yaklaşık 1,5 saat sürer.
- **Linux'ta Swift denetimi:** SwiftUI ve CoreGraphics derlenmez; yalnız saf Swift çekirdeği denetlenebilir. Gerçek
  derleme için CI kullanılır.

## Son düzeltmeler (birleştirmeden sonra)

- Temel alıştırma şekilleri (`practice.lines/radial/ellipses/spiral/waves/value_scale/sphere`) şekil önerisinden
  çıkarıldı (`TemplatesPractice.kt` `SUGGEST_EXCLUDED`): sıradan çizgi ve daire düzeltmelerini ele geçiriyorlardı.
- Dikey ekran yerleşik pencereleri (`DockedSheets.kt`): seçim menüsünün `zIndex`'i yalnız pencere açıkken uygulanır
  (önceden menü öneri balonlarının üstüne çıkıyordu). Kaydırılmayan 6 pencere (renk, fırça boyutu, kılavuz, sahne)
  `verticalScroll` aldı, çünkü yerleşik pencere çizim alanının en çok %46'sı.
- Testler: küçük pencerede ekran dışında kalan düğmeler `clickInPopup()` (anlam eylemi) ile tıklanır.
- **iOS düzenleyici donması (kritik):** `ViewportState`'in `@Published` alanları `EditorCanvas.updateUIView` →
  `layoutViewport()` içinde her düzende aynı değerle yeniden yayınlanıyordu; SwiftUI sonsuz güncelleme döngüsüne
  giriyor, tuval hiç açılmıyordu. Artık alanlar yalnız değer değişince `objectWillChange` gönderir. Kural: SwiftUI
  `updateUIView`/düzen içinde yayınlanan değer yalnız değişince yayınlanmalı.
- iOS tanı: DEBUG derlemede `-dgOpenNew <TÜR>` açılış argümanı doğrudan yeni tuval açar (`HomeView.swift`); CI bununla
  düzenleyici ekran görüntüleri alır (`diag/editor-*.png`). `ShapeTests` şablon sayıları 766/5362.
- iOS testleri (ce3db5a): birim 180/180. Arayüz testleri 3/8; kalan 5'i (çocuk görevi düğmesi ekran dışında, maskot,
  3B görünüm, eğitim grubu, basit not menüsü) test betiğinin güncel arayüzü bulamamasından. 3B düzenleyicide küp
  simülatörde çizilmiyor; gerçek cihazda bakılmalı.
- Android birim testleri: v49'da geçen her sınıf v50'de de geçiyor. Kalan başarısızlıklar ortamdan kaynaklanıyor
  (Robolectric SDK 21/24/29 jar'ları, yerel sunucu, temel paketler) ve v49'da da başarısız. Bulutta çalıştırmak için
  init betiği `robolectric.dependency.repo.url` ve `maxParallelForks=1` ayarlar.

## Bilinen durumlar ve sonraki işler

- **iOS freeRASP:** `TalsecRuntime`'ın ilk kanca taraması ana iş parçacığını dakikalarca bekletiyordu (simülatörde).
  v50'de arka planda başlıyor; simülatörde ve UI testlerinde kapalı. Gerçek cihazda imzalı derlemeyle
  (Team ID `DGRaspEnforce`) denenmeli.
- **Device Farm:** imzasız `.ipa` Device Farm'ın yeniden imzalamasına bırakılır. Apple ile giriş ve StoreKit orada
  çalışmaz (entitlement düşer). Kabul edilmezse Apple Developer hesabıyla ad hoc imza gerekir.
- **Sunucu güncellemesi bekliyor:** önce `apple_iap_key` sırrı, sonra imaj `50`, sonra `stack deploy`. App Store
  Connect'te bildirim adresi (Version 2).
- **App Store'a çıkış için:** imzalı derleme (Distribution sertifikası ve profil), Ücretli Uygulamalar sözleşmesi,
  abonelik ürünü, gizlilik etiketleri, ekran görüntüleri.
- **Tanıtım görüntüleri:** "Vektörleştirme eğitimi ve mağaza görselleri" iş parçacığı Play ve App Store boyutlarında
  üretiyor (PC `tanitim\`).
- **Web sitesi:** v50/v51 site değişiklikleri PC'de ayrı (`_eski_v50`, `_eski_v51`). Bu birleştirme `web/`'e dokunmadı.

## Sırlar

Asla yazdırılmaz ve depoya konmaz. Hepsi PC'de `yayin-anahtari\`:

- yükleme anahtarı ve `keystore.properties`
- `sunucu.env`
- Play ve GCS hizmet hesapları
- `SubscriptionKey_MTT897CZ3J.p8`
- `AuthKey_M9KHGT2ANU.p8`
- `GoogleService-Info.plist`

GitHub sırrı: `GOOGLE_SERVICE_INFO_PLIST_B64`.
