# DrawGuidance v25: 3B nokta/kenar/kesme, yüzey işlemleri, katı cisim işlemleri, 3B fırça, resim dışa aktarma

_2026-10-05 · Room v10 (şema değişmedi) · 3B modeller GLB · minSdk 21 – targetSdk 36_

## İstekler ve yapılanlar

1. **Nokta ekleme ve silme (yeni "Nokta" aracı).** Araç çubuğunda Ölçekle'nin yanında.
   - Kenar seçmeden, istenen **kenara dokunmak** oraya nokta ekler. Yeni nokta seçili olarak gelir.
   - **Noktaya dokunmak** noktayı seçer ya da bırakır. Seçim sırası numaralarla gösterilir ve noktalar kesik çizgiyle bağlanır.
   - Noktayı **sürüklemek** onu taşır; seçim ve sıra korunur. Boşlukta sürüklemek sahneyi döndürür.
   - **Sil** seçili noktaları siler. Kenar üstündeki nokta iz bırakmaz; köşe noktası silinince şekil kesilmiş gibi kapanır ve kapalı kalır.
   - Kenar aracı (eğ, ger, it, yuvarlat) eskisi gibi duruyor.
2. **İki noktayı birleştirip kenar yapma ve halkadan kesme.**
   - **Birleştir** seçili noktaları seçim sırasıyla kenarlarla bağlar: aynı yüzdeki iki nokta o yüzü ikiye böler. Noktalar aynı yüzde değilse hangi çiftin olmadığı söylenir.
   - **Kes** en az üç noktanın oluşturduğu halka boyunca keser:
     - Eksik kenarlar önce kendiliğinden birleştirilir.
     - Halkanın çevirdiği küçük taraf **ayrı parça** olur ve iki tarafın kesik yeri **kapatılır**.
     - Kesilen parça görünsün diye biraz aralanır ve seçili gelir.
     - Kenar birleştirme dahil **tek geri alma adımıdır**.
3. **Parçalama, delik, birleştirme, kesişim, yüzey seçimi.**
   - **Yüz aracı:**
     - **Tek yüz / Yüzey** seçimi. Yüzey kipinde dokunulan yüzün bütün yüzeyi seçilir (silindirin yanı, kürenin tamamı, düz bir taraf).
     - Seçili yüzeyde mevcut işlemler: Yükselt, İçe al, Büyüt/Küçült, Boya, Sil. Yükselt artık bölgeyi birlikte yükseltir; iç kenarlarda duvar oluşmaz.
     - **Delik aç**: seçili yüzeyden boydan boya delik. Genişlik kaydırıcıyla seçilir ve sahnede turuncu çemberle önizlenir.
     - **Ayrı parça**: seçili yüzey yeni parça olur.
   - **Yeni "İşlemler" paneli:**
     - **Birleştir** (kaynaştırır), **Çıkar** (ilk seçilen parçadan diğerlerini çıkarır), **Kesişim** ve **Grupla** (eski "Birleştir"; parçaları kaynaştırmadan tek parça yapar).
     - **Parçala**: X / Y / Z ya da ekrana dik düzlem ve konum kaydırıcısı. Düzlem sahnede görünür; iki taraf kapatılır.
     - **Delik**: genişlik ayarlanır. Yüz seçiliyse o yüzden, değilse parçanın ortasından ekrana dik delinir.
     - **Kopuk parçaları ayır.**
   - Malzemeler ve yüz boyamaları korunur. Delik ve parçalamada parçanın konumu, dönüşü ve ölçeği değişmez.
   - Ağır işlemler arka planda yapılır ("Birleştiriliyor…" göstergesi) ve geçmişe tek adım olarak girer.
3.1. **3B fırça.** Çiz aracının Fırça panelinde **fırça türü** seçilir:
   - **Çizgi**: eskisi gibi 3B çizgi.
   - **Tüp**: iz boyunca yuvarlak tüp.
   - **Şerit**: kalınlıklı yassı bant.
   - **Duvar**: düzleme dik yükselir; kapalı çizilirse içi dolu prizma olur.
   - **Torna**: eksenden başlayıp çizilen yarım profil dikey eksen çevresinde döndürülür (vazo, şişe).
   - **Şişir**: kapalı çizilen şekil yastık gibi şişirilir.

   Tuval fırçası gibi ayarlar:
   - Renk ve kalınlık. Türe göre yükseklik, şişkinlik ve uç incelmesi.
   - Yumuşatma ve **kalem basıncı**: kalemle çizilirse basınç kalınlığı değiştirir.
   - **Malzeme** (plastik, metal, cam…) ve **örtücülük**.
   - Her cisim kapalı bir katıdır, yeni parça olarak eklenir. Diğer araçlarla düzenlenebilir ve katı cisim işlemlerine girebilir.
   - Çizgiden cisim türüne geçerken ince kalınlık 18 px'e çıkarılır.
3.2. **Resim olarak dışa aktarma.** Düzenleyicinin ⋮ menüsünde **Resim olarak dışa aktar** var.
   - Ayarlar:
     - Boyut (720–3072 px, cihazın sınırına kadar).
     - Oran: kare, 3:4, 4:3, 16:9.
     - Arka plan: saydam ya da renk.
     - Zemin ızgarası, zemine gölge, kenar çizgileri.
     - Açı: modele sığdır ya da şu anki görünüm.
     - Biçim: PNG ya da JPEG (JPEG'de saydamlık beyaz olur).
     - Kenar yumuşatma: az / orta / çok.
   - Ayar değiştikçe önizleme yenilenir.
   - **Resimlerime kaydet** resmi Resimlerim'e (ve ayarlardaki kayıt yerine) yazar. **Paylaş** kaydedip paylaşır.
4. **Cihaz aralığına göre iyileştirmeler (Android 5 – 16).**
   - Cihaz üç sınıfa ayrılır (`DeviceCaps.tier`, `Budget3D`):
     - **Giriş:** az bellekli, Android 7 öncesi, < 3,2 GB RAM ya da ≤ 4 çekirdek.
     - **Orta.**
     - **Üst:** ≥ 5,6 GB, ≥ 8 çekirdek, Android 9+.
   - Sınıfa göre ayarlananlar:

     | | Giriş | Orta | Üst |
     |---|---|---|---|
     | Katı cisim işleminde üçgen sınırı (aşılırsa önce sadeleştirilir) | 4 000 | 12 000 | 30 000 |
     | Fırça cisminin çevre dilimi | 8 | 10 | 14 |
     | Fırça cisminin nokta sayısı | 90 | 160 | 240 |
     | Delik dilimi | 16 | 24 | 32 |
     | Resim dışa aktarmada en uzun kenar | 1 600 | 2 048 | 3 072 |
     | Varsayılan görüntü kalitesi | Hızlı | Dengeli | Yüksek |
     | Ekran kartı gölge dokusu | 128 px | 256 px | 256 px |
     | Yazılım çizici çözünürlüğü | ¾ | tam | tam |

   - Resim dışa aktarmada örnekleme, cihazın piksel bütçesine sığacak şekilde (2,5 / 6 / 12 milyon piksel) seçilir.
   - Hazır şekil küçük resimleri giriş düzeyinde tek tek çizilir.
   - İçe aktarmada önerilen ayrıntı giriş düzeyinde "Az", diğerlerinde "Orta".
   - Uzun işlemler ana iş parçacığını kilitlemez.
   - Yeni kodda Android 5'te olmayan bir API yok (lint NewApi temiz).
5. **Tuvalde "3B şekil ekle".**
   - Ekle menüsünde ayrı seçenek ve kendi simgesi. Perspektif çizgilerinin simgesi ayrıldı.
   - 3B Şekillerim şekil seçicinin içinden çıkarıldı.
   - Açılan pencerede:
     - 3B Şekillerim ve hazır şekiller var. **Dokunmak şekli seçer** ve vurgulanır.
     - Alttaki **"3B şekil ekle"** düğmesi seçili şekli tuvale ekler (seçim yokken pasif; "Seçili: …" yazar).
     - Kalem düzenleyicide açar. **Yeni 3B şekil** yukarıda duruyor.

## Mimari

- **core/content/model3d:**
  - `MeshTopo`: nokta silme, iki noktayı bağlama, halkadan kesme, yüzey bölgesi, bölge yükseltme ve ölçekleme, kopuk parçalar.
  - `Csg`: BSP katı cisim işlemleri (yinelemeli, malzeme etiketli, kaynak + T-kavşağı onarımı).
  - `Solid3`: parça düzeyinde birleştir/çıkar/kesişim, delik, parçalama, ayırma, kesme; bütçe aşılırsa sadeleştirme.
  - `BrushShapes`: tüp, şerit, duvar/prizma, torna, şişir.
- **app:**
  - `Budget3D` ve `DeviceCaps.Tier`.
  - `Model3DEditorViewModel`: Nokta aracı, yüzey kipi, ağır işlemler `runHeavy` ile arka planda, fırça, resim üretimi.
  - `Model3DEditorScreen`: Nokta aracı, Yüz düğmeleri, İşlemler paneli düğmesi, ⋮ menüsü, iş göstergesi.
  - `Model3DPanels`: İşlemler paneli, yeni Fırça paneli.
  - `Model3DViewport`: kalem basıncı, nokta sırası, parçalama düzlemi, delik çemberi.
  - `Model3DImageExport` (yeni pencere).
  - `Model3DPickerSheet`: seç + ekle.
  - `CatalogGrid` ve `Model3DCard`: seçili vurgusu.
  - `ImagesRepository.saveBitmap`, `ImageExporter.exportBitmap`.
  - `GlRenderer3`: gölge dokusu boyu parametre.
- Metinler `strings_v25.xml`. `strings_v23.xml`'de "Birleştir" → "Grupla", "3B şekil" → "3B şekil ekle", fırça ve çiz ipuçları güncellendi.

## Doğrulama

- **Yeni testler:**
  - core/content `Model3DV25Test` (6):
    - Köşe silinince kapalı ağ.
    - İki noktayı bağlayıp halkadan kesme; iki yarı kapalı, hacimler 0,5 + 0,5.
    - Yüzey bölgesi ve bölge yükseltme.
    - Birleştir/çıkar/kesişim hacimleri, delik, küre parçalama, ayırma.
    - Beş fırça türü kapalı ve hacimli.
    - Önizleme resimleri.
  - Uygulama `V25Model3DViewModelTest` (6):
    - Nokta aracı: kenara dokunarak ekleme, dokunarak seçme/bırakma, silme, Birleştir, köşeyi Kes (iki taraf kapalı, hacim korunur, tek geri alma adımı).
    - Yüzey kipi, Ayrı parça, Delik (önizleme çemberi, hacim ~%25 azalır, parça yerinde kalır).
    - Birleştir/Çıkar/Kesişim (kapalı; birleşim ≈ a + b − kesişim), Parçala (iki yarı kapalı, hacimler yarı yarıya), Grupla → Ayır.
    - Çizgi ve beş cisim fırçası.
    - Resim: boyut ve oran, saydam köşe, JPEG beyaz zemin, cihaz sınırı, Resimlerim'e kayıt.
    - Cihaz sınıfı bütçeleri.
  - `V25Model3DEditorUiTest`: Nokta aracı (3 nokta, numaralar, Kes), Yüzey kipi, İşlemler paneli (düzlem ve delik çemberi; Birleştir), Fırça paneli ve tüp çizimi, Resim olarak dışa aktar penceresi ve kaydetme. Ekran görüntüleriyle.
  - `V25CanvasAdd3DTest`: Ekle menüsünde "3B şekil ekle"; seçmeden düğme pasif, dokunmak eklemez, düğme ekler.
  - Güncellenen v23 testleri:
    - `V23Model3DCanvasTest`: seç + "3B şekil ekle" düğmesi.
    - `V23SuggestShotsTest`: 3B pencere artık Ekle menüsünden.
    - `V23Model3DEditorTest` ve `V23EditorToolsShotsTest`: araç çubuğu uzadığı için araca önce kaydırılıyor.
- **Tam koşu:** 427 test. Uygulama 161 sınıf / 271 test, core/content 125, core/model 31.
  - Tek hata `V23Model3DEditorTest` idi: Nokta aracı eklenince "Yüz" aracı dar ekranda araç çubuğunun görünmeyen kısmına kaydı ve test dokunuşu ıskaladı.
  - Testte araca önce kaydırma eklendi; ilgili üç v23 sınıfı yeniden koşturuldu, geçti.
- Lint: 0 hata (kullanılmayan metin silindi). `assembleDebug` başarılı (minSdk 21, targetSdk 36). APK'da yeni metinler doğrulandı.

## Bilinmesi gerekenler

- Katı cisim işlemleri (birleştir, çıkar, kesişim, delik, parçalama) ağı üçgenler. Sonuç parça "düzenlenmiş ağ" olur ve cisim ayarları (dilim sayısı) artık değiştirilemez.
- Çok ayrıntılı parçalar işlemden önce cihaz sınıfının üçgen sınırına sadeleştirilir.
- Kes için noktalar bir halka oluşturmalı ve şekli ikiye ayırmalı. Ardışık iki nokta aynı yüzde değilse araya nokta ekleyip adım adım birleştirmek gerekir (ileti bunu söyler).
- Fırça cisimleri yumuşak gölgeli parça olarak eklenir; rengi fırça rengi, malzemesi fırça panelindeki malzemedir.
- Resim dışa aktarma yazılım çiziciyle yapılır (ekran kartı gerekmez). Büyük boyutta birkaç saniye sürebilir.

## PC'ye yazarken

- **Bilgisayara yazılmadı** (isteğiniz üzerine). Değişen dosyalar stage'de: `outputs/v25src/android/`.
  - 73 dosya, v23'e göre birikimli (v24 dahil): 31 yeni, 42 değişen, silinecek dosya yok.
  - Bilgisayardaki proje hâlâ v23'te. "Yaz" dediğinizde v24 + v25 birlikte yazılacak.
- Değişecek dosyalardan 39'unun bilgisayardaki kopyası v24 sırasında v23 ile MD5'le eşleşmişti. Kalan 3 dosya (`strings_v23.xml`, `ImagesRepository.kt`, `DeviceCaps.kt`) yazmadan önce denetlenecek (bilgisayar şu an bağlı değildi).
- APK: `DrawGuidance-v25-debug.apk` (sohbette).
