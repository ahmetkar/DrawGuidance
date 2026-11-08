# DrawGuidance v12 — Mimari optimizasyonlar, inceleme modu (fare imleci), okları istenen tarafa bağlama

_2026-09-21 · Android 128/128 test (66 sınıf) + `:core:model` 13/13 test + assembleDebug · DB değişmedi (Room v7), kayıt biçimi ContentCodec v3 (eski kayıtlar aynen açılır; okların taraf alanı yoksa "Otomatik")_

## İstekler

- **Üst menüde fare imleci = İnceleme modu (seçimsiz):** dokunmak hiçbir şeyi seçmez, uzun basmak seçmez, tek parmak sürükleme yalnızca kaydırır (çizmez/taşımaz), iki parmak yakınlaştırır, çift dokunma kağıdı ekrana sığdırır. Altta "İnceleme · kaydır, yakınlaştır · çift dokun: sığdır" çipi; çipe, imlece ya da geri tuşuna basınca Çiz'e dönülür. Kalem/silgiye basmak da doğrudan çizime geçirir.
- **Ok, seçilen adımın istenen tarafına:** her ok için çıkış ve giriş tarafı (Otomatik / Üst / Sağ / Alt / Sol). Oklar dik açılı çizilir, kutuların içinden geçmemeye çalışır.
  - Adıma dokun → dört kenarında **+** tutamaklar. Tutamaktan **sürükle**, hedef adımın hangi kenarında bırakırsan oraya bağlanır (adımın ortasında bırakırsan otomatik taraf).
  - Tutamağa **dokun** → ok o taraftan başlar; sonra hedef adımın bağlanacağı kenarına dokun.
  - Alttaki **Ok ekle** → kaynak adımın çıkacağı kenarına, sonra hedef adımın gireceği kenarına dokun. Kaynağa tekrar dokunmak çıkış tarafını değiştirir, boşa dokunmak vazgeçer. Ok eklerken panel gizlenir, kaynak tarafı dolu gösterilir, diğer adımlarda taraf noktaları görünür.
  - Oka dokun → panel: yazı, **Yönü çevir**, sil ve **Çıkış / Giriş** taraf düğmeleri. Aynı ok yeniden çekilirse yalnızca tarafları güncellenir.
- **Bulut ve LLM hariç optimizasyonlar:** aşağıda.

## Mimari ve optimizasyonlar

1. **Modül:** yeni saf Kotlin/JVM modülü `:core:model` — veri modeli, `ContentCodec`, eski JSON uyumu (`LegacyJson`), ok yönlendirme (`DiagramRouting`), geri alma geçmişi (`EditHistory`). Android'siz birkaç saniyede test edilir; Gradle paralel derler.
2. **Geri al / Yinele (Command deseni):** her değişiklik "önce/sonra" belge durumu olarak kaydedilir (sayfalar, boyut, arka plan, açık sayfa); 100 adım; aynı elemandaki yazı/kaydırıcı düzenlemeleri 2,5 sn içinde tek adımda birleşir. Üst menüde **Yinele** düğmesi (yeni değişiklik yapılınca yinelenecekler silinir).
3. **ViewModel bölündü:** `EditorStore` (tek doğruluk kaynağı: tuval, seçim, geçmiş, kayıt) + özellik denetleyicileri (Fırça, Kırpma/Çerçeve, Stil, Sayfa, Kılavuz, Şekil, Diyagram, Yazı, Seçim, Çizim, Resim). `CanvasEditViewModel` yalnızca bunları birleştiren ince cephe; ekran API'si değişmedi.
4. **Dokunma = Strategy:** her mod kendi `CanvasTool` sınıfında (Çiz, Seç, İncele, Taşı/Döndür/Ölçekle, Genişlet, Kırp, Şekil/Diyagram yerleştir ve düzenle, Perspektif). `DrawingCanvas`'taki dev `when` bloğu kalktı; yeni mod eklemek tek sınıf.
5. **Kayıt güvenliği:** bir sayfanın içeriği, boyut, arka plan ve açık sayfa tek Room işleminde (transaction) yazılır. Uygulama arka plana geçince ya da ekran kapanınca bekleyen kayıt beklemeden yazılır (sistem uygulamayı kapatsa da son çizim kaybolmaz).
6. **Gözlemlenebilirlik (yerel):** yakalanmamış hata uygulama klasörüne (`files/diagnostics/last-crash.txt`) yazılır, sonraki açılışta logcat'e aktarılır; debug derlemede StrictMode ana iş parçacığındaki disk/ağ erişimini ve kapatılmamış kaynakları logcat'e raporlar.
7. **Derleme hızı:** `org.gradle.parallel` + `org.gradle.caching`; testler çekirdek sayısının yarısı kadar (en çok 4) paralel JVM'de.
8. **Arayüz:** yakınlık oranı üst menüden tuvalin sağ üst köşesine taşındı (dokununca sığdırır) — Yinele ve imlece yer açıldı. Diyagram odak paneli alttaki mod çubuğunun düğmelerini kapatmaz (altına/üstüne/yanına yerleşir), adımın alt tutamağını örtmez; kırık okta okun yolunun ortasına yerleşir; yatay telefonda ok tarafları tek satır.

## Hata düzeltmeleri

- Uygulama kapanırken Koin hazır değilse `onStop` çökebiliyordu → kayıt kuyruğu `onCreate`'te alınır.
- Küçük adımlarda ortasına dokunmak bile bir kenar tutamağı sayılıp yanlış tarafa bağlanıyordu → adımın içine dokunuş kenara yakınlığa göre (ortası otomatik), tutamak yalnızca adımın hemen dışında.

## PC'ye yazarken

- Stage: `outputs/v12src/android/` — bilgisayardaki v9'a göre **birikimli 82 dosya** (v10 + v11 + v12). Yeni klasör: `android/core/model/` (tamamı yeni), yeni: `diagnostics/Diagnostics.kt`, `canvasedit/state/*` (12 dosya), `components/CanvasTools.kt`.
- Bilgisayardan **silinecek 4 dosya** (core modülüne taşındı; kalırsa "Redeclaration" derleme hatası verir):
  - `android/app/src/main/java/com/ahmet/drawguidance/data/model/Stroke.kt`
  - `android/app/src/main/java/com/ahmet/drawguidance/data/model/Canvas.kt`
  - `android/app/src/main/java/com/ahmet/drawguidance/data/local/ContentCodec.kt`
  - `android/app/src/test/java/com/ahmet/drawguidance/ContentCodecTest.kt`
