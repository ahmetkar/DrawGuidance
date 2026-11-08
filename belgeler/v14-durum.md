# DrawGuidance v14 — Parça seçerek Ayır/Parçala, sade tuval ve resim galerisi, günlük denetimi

_2026-09-21 · Room v7 (değişmedi) · Kayıt biçimi değişmedi (ContentCodec v3); eski kayıtlar aynen açılır_

## İstekler ve yapılanlar

1. **Ayır / Parçala parça seçtirir:** Şekil düzenlemede **Ayır** ya da **Parçala**'ya basınca seçme adımı açılır (başlık "Ayır: parçaları seç" / "Parçala: parçaları seç"). Parçalara dokunarak seçilir/bırakılır; seçilenler belirgin vurguyla (geniş hale + koyu çizgi) gösterilir. Onay düğmesi seçilen sayıyı gösterir: **Ayır (N)** seçilenleri birlikte **tek yeni eleman** yapar, **Parçala (N)** seçilen **her parçayı ayrı eleman** yapar; kalan parçalar asıl şekilde kalır (kimliği korunur). Etiketler en yakın seçilen çizgiyle gider. Uygulayınca ayrılan elemanlar seçili olarak Seç moduna geçilir; tek geri al ile eski hâline döner. **İptal** seçme adımını kapatır ve parça seçimini adımdan önceki hâline döndürür. Seçim geçersizse (ör. hiç parça yok ya da Ayır'da hepsi seçili) onay düğmesi pasif kalır.
   - Normal şekil düzenleme alt satırı sadeleşti: Dönüştür · Ayır · Parçala · Sil · Bitti simge + kısa yazı.
   - Başlık satırında yönerge ile sağdaki bilgi (ör. "1 eleman seçili") artık birbirine yapışmıyor; şekil düzenlerken sayı gösterilmiyor (yönerge tam genişlikte).
2. **Tuvallerim = Resimlerim gibi galeri:** her tuval kendi oranında önizleme (basamaklı ızgara), altında ad ve sade bilgi satırı ("1080×1350", not tuvalinde "Not · 1240×1754 · 3 sayfa"); tarihe göre bölümler (Bugün, Dün, Bu hafta, Bu ay, Daha eski); **Sırala** (Son düzenlenen / En yeni / Ada göre); **galeri ↔ liste** görünümü; uzun basınca seçilir (seçili kartta vurgulu çerçeve + işaret). **İki sayfada da kutular sadeleşti:** kart çerçeveleri ve iç kutular kaldırıldı; yalnızca yumuşak gölgeli önizleme + altında yazı. Liste görünümünde satırlar kutusuz (küçük önizleme, ad, bilgi, tarih). Ortak bileşen: `ui/components/Gallery.kt` (GalleryTile, GallerySectionHeader, gallerySection).
3. **Günlüklerdeki hatalar (test ortamında çalıştırılıp tarandı):**
   - Tüm testler günlük açık çalıştırıldı (`-PdgLogs`), hata/uyarı satırları toplandı. Uygulamaya ait tek uyarı: **"OnBackInvokedCallback is not enabled for the application"** (her ekranda, 425 kez). Düzeltme: bildirimde `android:enableOnBackInvokedCallback="true"` — Android 13+ öngörülü geri hareketi açıldı; uygulamadaki tüm geri işlemleri (BackHandler, alt sayfalar, açılır menüler, gezinme) bu yolu destekliyor. Testlerde sistem geri hareketi cihazdaki yoldan (pencerenin OnBackInvokedCallback'i) çalıştırılıyor.
   - Cihazda StrictMode'un yakalayacağı sorunlar önlendi: logcat okuma/temizlemede süreç akışları kapatılıyor ve süreç sonlandırılıyor; ana iş parçacığında dosya/tercih okuma kaldırıldı (ayarlar, renk paleti, dışa aktarma ve geliştirici ayarları ile yazı tipleri açılışta arka planda hazırlanıyor; Günlükler ekranı çökme kaydını arka planda okuyor/siliyor).
   - Derleme uyarıları da tarandı: şekil kataloğunda hiç çalışmayan yinelenmiş bir dal (Eksenler) kaldırıldı (çizim değişmedi); eskimiş ok simgeleri yenilendi (genel oklar sağdan sola dillerde aynalanır, diyagram yön okları Doğu/Batı olarak sabit kalır); kullanılmayan değişken/parametreler temizlendi.
   - Kalan kayıtlar uygulamadan değil: Robolectric test ortamına ait (PackageParser `<queries>`, FileTestStorage, VirtualDeviceManager) ve Android 14'ün kendi geri hareketi kaydı (`WindowOnBackDispatcher: sendCancelIfRunning`, geri çağrısı kaldırılırken sistemin yazdığı bilgi amaçlı uyarı).

## Doğrulama

- Android 144/144 test + `:core:model` 17/17 test + assembleDebug (APK ~22,1 MB; bildirimde `enableOnBackInvokedCallback=true` doğrulandı).
- Yeni testler: `V14CanvasGalleryTest` (galeri, bölümler, bilgi satırı, uzun basınca seçim, liste görünümü, sıralama, açma), `V14LogAuditTest` (tuval aç/çiz/geri/sekmeler: uygulama kaynaklı hata günlüğü yok, geri hareketi uyarısı yok), `V14PredictiveBackTest` (öngörülü geri: menü kapanır, Seç→Çiz, düzenleyici→liste, liste seçimi kalkar), `V14*ShotsTest` (ekran görüntüleri).
- Güncellenen testler: `V13ShapeEditTest` (Ayır/Parçala parça seçerek), `V9ShapeEditTest` (simge düğmeler), geri tuşu kullanan testler `SystemBack.press()`.

## PC'ye yazarken

- Stage: `outputs/v14src/android/` — bilgisayardaki v9'a göre birikimli 136 dosya (v10–v14; v13'e göre 7 yeni dosya). Yeni: `app/src/main/AndroidManifest.xml` (öngörülü geri), `ui/components/Gallery.kt`, `ui/screens/canvaslist/components/CanvasCard.kt`, testler `SystemBack.kt`, `V14ListTest.kt`, `V14ShotsTest.kt`, `CanvasFlowTest.kt`.
- Bilgisayardan silinecek 4 dosya (v12'de core modülüne taşındı): `app/.../data/model/Stroke.kt`, `app/.../data/model/Canvas.kt`, `app/.../data/local/ContentCodec.kt`, `app/src/test/.../ContentCodecTest.kt`.
