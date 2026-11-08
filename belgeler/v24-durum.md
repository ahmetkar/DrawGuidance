# DrawGuidance v24: sayfa sayfa arka plan, ekran kartıyla 3B düzenleyici, glTF/GLB, hazır 3B şekiller

_2026-10-05 · Room v10 (şema değişmedi) · 3B modeller GLB (glTF 2.0) · minSdk 21 – targetSdk 36_

## İstekler ve yapılanlar

1. **Yazı kutusunun arka planı düzenleme alanında.** Not tuvalinde yazı kutusuna verilen arka plan (renk, desen, hazır resim, dokuz parçalı resimler dahil) yazı eklerken ya da düzenlerken açılan yazma alanında da çizilir. Kutunun eni verilmişse alan o ende, verilmemişse yazıya göre genişler; yazı kutunun iç boşluğundan başlar. Düzenlenen yazının kendi kutu eni/boyu korunur.
2. **Arka plan menüsü.**
   - Ekle menüsünden kaldırıldı; **Düzenle** modunun üst menüsüne ve **Çiz** modunun araç satırına taşındı (duvar kâğıdı simgesi, "Arka plan").
   - **Not tuvalinde sayfa başına arka plan:** değişiklik (kağıt rengi, desen, resim) yalnız açık sayfaya uygulanır; pencerede "Değişiklikler yalnız bu sayfaya uygulanır (sayfa x / y)" yazar. **Tüm sayfalara uygula** düğmesi açık sayfanın arka planını hepsine verir. Yeni (ve çoğaltılan) sayfa önceki sayfanın arka planını alır. Tek sayfalı ve resim tuvallerinde eskisi gibi tek arka plan.
   - Kayıt: `CanvasEntity.background` JSON'una `pages` dizisi eklendi (veritabanı şeması değişmedi; eski tuvaller aynen açılır). PNG/PDF dışa aktarma ve tuval küçük resimleri sayfanın kendi arka planıyla çizilir.
3. **3B düzenleyici: hız, az ayrıntı, kaliteli görünüm, sade düzenleme.**
   - **Ekran kartıyla çizim (OpenGL ES 2.0):** her parçanın ağı bir kez ekran kartına yüklenir (malzemeye göre gruplanmış üçgenler, yumuşak normaller, doku koordinatları hazır). Taşıma, çevirme ve ölçeklemede yalnız parçanın matrisi değişir: karede ağ yeniden hesaplanmaz, birkaç çizim çağrısı yapılır, kare yalnız sahne değişince çizilir. Kenar ve dış hat çizgileri de bir kez yüklenir, dış hat sınaması gölgelendiricide yapılır. Zemin gölgesi 256 px'lik dokuda bulanıklaştırılır. Görünüm yazılım çiziciyle aynı ışık modeli (Lambert + Blinn-Phong, metal yansıması, cam saydamlığı, ışıklı malzeme, doku, seçili yüz vurgusu) ve 4× kenar yumuşatma.
   - **Her cihaz:** ekran kartı ilk açılışta görünmez bir yüzeyde sınanır (uygulamanın her sürümünde bir kez). Sınama ya da çizim başarısız olursa düzenleyici kendiliğinden **yazılım çiziciye** geçer (uygulama kapanmaz); yazılım çizici de hızlandırıldı: sürüklerken gölge çizilmez, düşük çözünürlük ve tek örnek, durunca tam kalite.
   - **Az ayrıntı:** cisimlerin varsayılan dilimleri düşürüldü (küre 20×10, silindir/koni 24, simit 24×10…), dilim sınırı 48, halka 24. Eğri yüzeyler yumuşak gölgelemeyle pürüzsüz görünür.
   - **Sürükleme geçmişi:** taşıma/çevirme/ölçekleme/nokta taşıma sürerken ara adımlar geçmişe yazılmaz; parmak kalkınca tek "geri al" adımı.
   - **Nokta/Kenar aracı ("Kenar"):** kenara dokun → seç (birden çok kenar seçilebilir); **kenarı basılı tut → yeni nokta**; noktayı ya da kenarı **sürükle → taşı** (eksen kilidiyle). Alttaki düğmeler: **Eğ** (kenar yay olur), **Ger** (uzat/kısalt), **İt** (yüzeyden dışarı/içeri), **Yuvarlat** (keskin kenara kavis), **Nokta ekle** (kenar ortası). Her işlem kaydırıcıyla önizlenir; **Tamam** tek adımda uygular, **Vazgeç** geri alır. Az noktalı parçada bakana dönük tüm noktalar, çok noktalıda yalnız seçili kenarların uçları gösterilir (nokta düzenlemesi sade kalır).
   - **Yüz aracı ayrı sekmede kaldı:** Yükselt (± çekme/itme), İçe al, Büyüt/Küçült kaydırıcıyla; Boya ve Sil; seçili yüzü sürükleyerek taşıma.
   - **Dokunuşla seçim** artık son çizilen kareden değil, görünümden ışın atılarak yapılır (`Pick3`): ekran kartı ve yazılım çizicide aynı sonuç.
   - **Düzenli ayarlar:** "Sahne" paneli **Ayarlar** oldu: Işık, Görünüm (gölge, kenar çizgileri, ızgara, arka plan), Kamera (perspektif, hazır açılar), **Başarım** (Ekran kartıyla çiz anahtarı ve kartın adı, Görüntü kalitesi: Yüksek 4× / Dengeli 2× / Hızlı ¾ çözünürlük). Fırça paneli yalnız Çiz aracında görünür.
4. **glTF / GLB.**
   - Tüm 3B modeller **GLB** olarak saklanır (3B Şekillerim ve tuvaldeki 3B şekillerin verisi, MIME `model/gltf-binary`). Uygulamaya özgü düzenleme bilgileri (çokgen yüzler, cisim ayarları, yüz boyaları, sahne, kamera) glTF'in `extras` alanında: başka programlar dosyayı normal GLB olarak açar, uygulama kayıpsız geri okur.
   - Eski (v23 "D3") kayıtlar ilk açılışta bir kez GLB'ye çevrilir (zaman ve küçük resim korunur); tuvallerdeki eski 3B şekiller de okunur, düzenlenince GLB yazılır.
   - **Dışa aktar (GLB)** ve **Paylaş (GLB)**: düzenleyicinin ⋮ menüsünde; dokular PNG olarak gömülür.
   - **İçe aktar:** 3B Şekiller ekranındaki yükleme düğmesi (glTF/GLB). Ayrıntı seçilir: **Az (2 000) / Orta (5 000) / Çok (12 000 üçgen)**. Ayrıntı düşürme: köşe kaynaştırma + kenar daraltma (QEM, sınır koruma, yüz ters dönme sınaması; çok büyük modellerde önce ızgara kümeleme). Model 1,6 birim boyuna getirilip zemine oturtulur, dokular 256 px'e küçültülüp "İçe aktarılan" dokular olarak saklanır. Draco/meshopt sıkıştırmalı dosyalar desteklenmez (anlaşılır hata). Kütüphane: saf Kotlin (dış bağımlılık yok, yalnız kotlinx-serialization-json).
5. **Hazır 3B şekiller (68):** lise ve üniversite derslerinde ve çizim çalışmasında kullanılmak üzere, kodla üretilir:
   - **Geometri (25):** küp ve köşegenleri, prizmalar, piramit, düzgün çokyüzlüler, kesik koni/piramit, silindir/koni/küre (ölçü çizgileriyle), yarım küre, simit, koordinat sistemi, koni kesitleri, paraboloid, eyer yüzeyi, hiperboloid, Möbius şeridi, helis, simit düğümü, dalga yüzeyi.
   - **Fizik (10):** eğik düzlem, makara, sarkaç, yay-kütle, mercek, ışık prizması, U mıknatıs, atom modeli, vektör toplama, basit devre.
   - **Kimya (10):** H₂O, CO₂, CH₄, NH₃, benzen, etanol, NaCl kristali, beher, erlenmeyer, deney tüpü.
   - **Biyoloji (4):** DNA, hayvan hücresi, virüs, alyuvar. **Astronomi (4):** güneş sistemi, Dünya-Ay, Satürn, Ay'ın evreleri.
   - **Çizim (14):** ışık-gölge çalışması, çizim mankeni, sandalye, kitaplar, vazo, şişe, bardak, elma, armut, merdiven, araba, lamba, ev, ağaç.
   - Görüldüğü yerler: 3B Şekiller ekranında **Hazır şekiller** sekmesi (dokununca kopyası düzenleyicide açılır, kaydedince 3B Şekillerim'e eklenir), düzenleyicinin **Ekle** panelinde (parçaları sahneye eklenir) ve tuvalin 3B şekil seçicisinde (doğrudan tuvale eklenir). En ağır şekil ~6 000 üçgen.

## Mimari

- **core/content/model3d:** `Mesh` (üçgenleme önbelleği, doku koordinatları), `MeshOps` (üçgenleme, kenar bölme, eğ/ger/it/yuvarlat), `Pick3` (ışınla parça/yüz/kenar/nokta seçimi), `UvMapper` (önbellekli), `MeshSimplifier` (QEM), `Gltf3` (GLB yazma/okuma/içe aktarma), `Model3DStore`, `PngWriter`, `Generators3D`, `Catalog3D`; `gl/`: `GlApi` (GLES 2.0 arayüzü), `GlRenderer3`, `GlShaders`.
- **app:** `ui/screens/model3d/gl/` (`AndroidGl`, `GlProbe`, `Model3DGlView`), `Model3DViewport` (ekran kartı ya da yazılım çizici + dokunuş/işaret katmanı), `Model3DEditorViewModel` (canlı sürükleme, kaydırıcı işlemleri, Pick3), `Model3DPanels` (Ekle + hazır şekiller, Ayarlar), `CatalogGrid`, `Model3DListScreen` (sekmeler, içe aktarma), `data/settings/Model3DPrefs`, `Model3DRepository` (GLB, geçiş, içe/dışa aktarma, hazır şekil).
- **Tuval:** `PageBackground` (core/model), `CanvasUi.pageBackgrounds`, `CanvasMappers.encodeBackground/decodePageBackgrounds`, `FrameController.setPageSpec/applyPageToAll`, `PageController` (yeni sayfa önceki arka planı alır), `BackgroundSheet` (sayfa bilgisi, Tüm sayfalara uygula), `InlineTextEditor` (kutu arka planı).
- Metinler `strings_v24.xml`.

## Doğrulama

- **Yeni testler:**
  - core/content: `Model3DV24Test` (7: içbükey yüz üçgenleme, kenar işlemlerinde kapalı ağ, ışınla seçim, GLB kayıpsız gidiş-dönüş, başka programın GLB'sini sadeleştirerek alma, sadeleştiricinin şekli koruması, 68 hazır şeklin üçgen sınırı ve GLB gidiş-dönüşü) ve `GlRendererTest` (5: Mesa'nın ekransız OpenGL ES 2.0'ı ile gerçek çizim; gölgelendiriciler derleniyor, siluet yazılım çiziciyle örtüşüyor (IoU > 0,93; paralel ve perspektif), taşı/döndür/ölçekle'de köşe tamponu yeniden yüklenmiyor, renk farkı küçük, 68 şekil gölge/kenar/doku/saydamlıkla çiziliyor, seçim durumları, bağlam kaybından dönüş).
  - Uygulama: `V24PageBackgroundTest` (Ekle'de arka plan yok, sayfa 2'ye özel desen, Tüm sayfalara uygula, yeni sayfa önceki arka planı alır, veritabanında kalıcı), `V24InlineBoxBackgroundTest` (yazı eklerken düzenleme alanında sarı yapışkan not arka planı), `V24Model3DViewModelTest` (3: kenar seçme/eğme/yuvarlatma/vazgeç/geri al, nokta ekleme ve sürükleyerek taşıma tek geri alma adımı, canlı taşıma ve hazır şekil ekleme, dilim sınırı), `V24GlbDataTest` (GLB kayıt, eski kaydın GLB'ye çevrilmesi, dokuları gömülü dışa aktarma, ayrıntısı düşürülerek içe aktarma, hazır şekil kopyası, tuvaldeki 3B şekil MIME), `V24Model3DEditorUiTest` (Hazır şekiller sekmesi, Kenar aracı, Ayarlar → Başarım, Ekle panelindeki hazır şekiller; ekran görüntüleriyle).
  - Güncellenen v23 testleri: `V23BackgroundTest` (arka plan Düzenle/Çiz menüsünden), `V23Model3DEditorTest` (Yükselt kaydırıcıyla + Tamam, Sahne → Ayarlar), `V23EditorToolsShotsTest` (Köşe → Kenar).
- **Tam koşu:** 413 test (uygulama 158 sınıf / 263 test, core/content 119, core/model 31). Tek hata `V19PictureCanvasTest` idi: Çiz satırına Arka plan düğmesi eklenince 9 düğmeye yayılan aralıkların tam sayı yuvarlaması son düğmeyi sağ kenardan 5 px içeride bırakıyordu; aralık hesabı düzeltildi (artan pikseller aralıklara paylaştırılıyor), en küçük aralık 6 → 4 dp. İlgili testler (V16–V19, V23/V24 3B, kaydetme) yeniden koşturuldu, geçti.
- Ekran kartı testi bu makinede Mesa llvmpipe (OpenGL ES 3.2) ile koştu; telefonda aynı çizici `GLES20` ile çalışır.
- Lint: 0 hata (v24'ün kullanılmayan metinleri silindi). `assembleDebug` başarılı (minSdk 21, targetSdk 36).

## Bilinmesi gerekenler

- Ekran kartı çizicisi telefonda sınanarak açılır; sınama birkaç yüz milisaniye sürer (ilk açılışta, arka planda). Ayarlar → Başarım'da kapatılabilir; kapalıysa ya da kart uygun değilse yazılım çizici kullanılır.
- "Hızlı" kalite ¾ çözünürlükte çizer (eski cihazlar için); "Yüksek" 4× kenar yumuşatma.
- İçe aktarılan modelin dokuları uygulamanın doku tablosunda saklanır; model silinse de doku kalır.
- GLB içe aktarmada düğüm animasyonları, iskelet ve morph hedefleri alınmaz (yalnız durağan ağ ve temel renk dokusu).
- Ekran kartı testi (`GlRendererTest`) Linux'ta Mesa ile çalışır; Windows'ta EGL bulunmadığından kendiliğinden atlanır.

## PC'ye yazarken

- **Bilgisayara henüz yazılmadı** (isteğiniz üzerine). Değişen dosyalar stage'de: `outputs/v24src/android/` (62 dosya: 22 yeni, 40 değişen; silinecek dosya yok).
- Bilgisayardaki proje v23'te: değişecek dosyalardan 39'unun bilgisayardaki kopyası v23 ile birebir aynı çıktı (MD5); son anda değişen `strings_v23.xml` yazma sırasında denetlenecek. "Yaz" dediğinizde üzerine yazılıp MD5 ile doğrulanacak.
- APK: `DrawGuidance-v24-debug.apk` (sohbette).
