# DrawGuidance v31 — durum

_2026-10-12 · Room v11 (şema değişmedi) · minSdk 21 – targetSdk 36 · Backend v30'daki gibi_

## İstekler ve yapılanlar

1. **Açılıştaki logo saydam; nöromorfik ve editoryal temaya uygun; altında yalnız "Draw Guidance" yazılarak görünür.**
   - **Zemin:**
     - Karşılama ekranının mor gradyan zemini kalktı.
     - Ekran uygulamanın nöromorfik zemininde açılır (açık: #E4E9F1, koyu: #272B33).
     - Pencerenin açılış rengi (Android 12+ sistem açılış ekranı dahil) de aynı renk. Açılışta koyu mor ya da beyaz parlama olmaz.
     - Koyu tema için `values-night` rengi eklendi.
   - **Logo** (`AppLogo`, yeni `LogoStyle.INK` düzeni):
     - Zeminsiz (saydam).
     - Kılavuz kesik çizgisi ve kalemin dış hattı mürekkep renginde. Kalemin gövdesi zemin renginde (editoryal çizgi üslubu).
     - Çizilen çizgi marka mor gradyanında.
     - Parıltılar editoryal tek vurgu renginde (#D9572B; koyuda #F0784A).
     - Çizgi ve kalem nöromorfik iki gölgeyle zemine kabartılır: açık sol-üst, koyu sağ-alt.
     - Logo kendini çizer; kalem kılavuzu izler.
   - **Yazı:**
     - Logonun altında yalnız **Draw Guidance**. Slogan kaldırıldı.
     - Editoryal serif yazıyla harf harf yazılır. Son harfin ardında vurgu renginde imleç görünür.
     - Yazılmamış harfler saydam tutulur. Böylece yazı ortada sabit durur, harfler eklendikçe kaymaz. Ekran okuyucu baştan tam adı okur.
     - Toplam süre yaklaşık 2 sn. Dokununca hemen geçilir.
   - **Uyum:** Ana sayfadaki logolar (renkli kart üstünde) eski görünümünde kaldı (`BRAND` / `ON_DARK`).
2. **Ayarlardaki detaylı yapay zekâ açıklamaları ve ayarları kaldırıldı.**
   - "Moda özel yapay zekâ" kartı kaldırıldı. Kartta şunlar vardı:
     - modların açıklaması,
     - mod başına şekil/kabul/ret/eğitim sayıları ve **Sıfırla** düğmeleri,
     - 3B notu,
     - karmaşıklığa göre algoritmalar,
     - veri kümesi metni.
   - Kartın metinleri ve ayar ekranındaki durum/sıfırlama kodu da kaldırıldı.
   - Öneri anahtarları kaldı ve açıklamaları kısaltıldı: Çizim önerileri, Yazı önerisi, Şekil önerisi, Seçimde otomatik öneri.
3. **Bir arada çizilen 3–4 elemanda çok elemanlı öneri kendiliğinden çıkar.**
   - Çiz modunda kalem durunca (0,75 sn) son çizimle bir arada duran çizimler bulunur:
     - aynı katman ve düzlemde olmalı,
     - kutuları birbirine yakın olmalı (iç içe ya da değen),
     - çizimler arasında ara verilmiş olsa da aynı grup sayılır.
   - Grup **3 ya da daha çok** elemanlıysa, seçim yapmaya gerek kalmadan çoklu seçimdeki öneri gösterilir:
     - **Tümünü düzelt (n)**,
     - grup şablonları (ör. Şaşkın yüz),
     - hazır vektör şekiller,
     - son çizimin kendi düzgün şekli.
   - Öneri uygulanınca ya da kapatılınca aynı grup için yeniden çıkmaz. Gruba yeni eleman eklenince yeniden çıkar.
   - İki elemanda ve birbirinden uzak çizimlerde çıkmaz.
   - El yazısı tanındığında (not yazarken) grup önerisi yalnız güçlü bir grup şablonu varsa yazı önerisiyle birlikte çıkar. Yazarken gereksiz balon açılmaz.
   - Hesap arka planda yapılır. Öneriler ve tuvalin yapay zekâ ayarı kapalıyken çalışmaz.
4. **Çoklu seçimde "Tümünü düzelt" bir şey yapmıyordu: test edildi ve düzeltildi.**
   - **Sebep (testlerde ve ekran görüntülerinde görüldü):**
     - Öneri balonu tuvalin üst göstergelerinden (yakınlık **%100** düğmesi, fırça çipi) ve alttaki mod çubuğundan **önce** çiziliyordu.
     - Seçim ekranın üstüne ya da altına yakınken balon bu düğmelerin altında kalıyordu.
     - "Tümünü düzelt" şablonların sonunda, balonun sağ ucunda duruyordu (yakınlık düğmesinin altı).
     - Ona dokununca yakınlık düğmesi ya da mod çubuğu çalışıyordu. Kullanıcıya hiçbir şey olmamış gibi görünüyordu.
   - **Düzeltme:**
     - Balonlar artık bu düğmelerden sonra (üstte) çizilir.
     - Balonun yeri yakınlık göstergesinin altından başlar ve mod çubuğunun üstünde biter.
     - Çok elemanlı öneride **Tümünü düzelt** ilk seçenek. Tuvaldeki kesik çizgili önizleme de onun.
     - Hemen ardından en iyi tahmin (şablon) gelir.
   - **"Tümünü düzelt" artık seçimdeki tüm çizimleri düzeltir:**
     - Düzgün geometrisi tanınan çizim o şekle çevrilir (doğru, kırık çizgi, çember, elips, üçgen, dörtgen, çokgen).
     - Tanınmayan serbest çizim yumuşatılır (önceden yalnız tanınan şekiller değişiyordu).
     - Geri alma tek adımdır.
   - **Çok büyük seçimler:**
     - 24'ten çok elemanda şablon araması yapılmaz; "Tümünü düzelt" yine sunulur (önceden kendiliğinden öneride hiç çıkmıyordu).
     - "Çoklu düzeltme öner" hesabı arka planda yapılır; çok eleman seçiliyken arayüz donmaz.

## Testler

Yeni test sınıfları (`V31Test.kt`):

- **V31FixAllTest**
  - Beş çizim seçilir. Seçim çubuğundaki "Çoklu düzeltme öner" ile açılan balonda **gerçek dokunuşla** "Tümünü düzelt" uygulanır. Aynısı kendiliğinden seçim önerisinden de yapılır.
  - Serbest çizimlerde (4 dalgalı çizgi) "Tümünü düzelt (4)" dördünü de değiştirir. Geri alma tek adımdır.
- **V31BalloonPlacementTest:** Seçim ekranın üstüne ve altına kaydırılınca balon yakınlık göstergesiyle kesişmez, mod çubuğunun üstünde kalır. "Tümünü düzelt" dokunuşla çalışır.
- **V31AutoGroupTest:**
  - Yüz (baş ve ara vererek iki göz) çizilir. İki elemanda öneri yok, üçüncüde "Tümünü düzelt (3)" kendiliğinden çıkar ve uygulanır.
  - Uzak çizimlerde öneri çıkmaz.
  - Kapatılan grup sessiz kalır; gruba çizgi eklenince "Tümünü düzelt (4)" ile yeniden çıkar.
- **V31WelcomeTest:** Nöromorfik zeminde logo ve "Draw Guidance" (harf harf). Slogan yok. Ardından Ana sayfa açılır.
- **V30HomeTest** yeni başlığa göre güncellendi.
- **V21 şablon testleri:** Balondaki seçenekler yatay kaydığından tıklamadan önce görünür alana kaydırılıyor.

**Doğrulama:**

- Tam paket: 533 test (uygulama 331, çekirdek içerik 169, model 33). Uygulamada yalnız eğitim/ekran görüntüsü testleri atlanır.
- İlk tam koşuda iki V21 şablon testi düştü. Sebep, şablon seçeneğinin balonun görünmeyen kısmında kalmasıydı. Düzeltmeden sonra bu testler ve V21–V29 öneri testleri (71) yeniden geçti.
- Lint: 0 hata.

## Dosyalar

- **Karşılama ve logo:**
  - `ui/components/AppBranding.kt`: `LogoStyle`, `WelcomeScreen`.
  - `res/values/strings_v31.xml`, `res/values/colors_v30.xml`, `res/values-night/colors_v31.xml`, `res/values-v31/themes.xml`.
- **Ayarlar:**
  - `ui/screens/settings/SettingsScreen.kt` ve `SettingsViewModel.kt`, `di/AppModule.kt`.
  - `strings_v20/v23/v27/v29/v30.xml`.
- **Öneriler:**
  - `canvasedit/state/HandwritingController.kt`: grup önerisi, arka planda seçim önerisi.
  - `ShapeSuggester.kt`: `fixAll`, `smoothFix`, `multiFirst`.
  - `TemplateSuggester.kt`: `group`.
  - `SelectionSuggester.kt`.
  - `components/EditSheets.kt`: seçenek sırası.
  - `components/EditorToolbar.kt`: `zoom_chip` etiketi.
  - `CanvasEditScreen.kt`: balonların katmanı ve yeri.
