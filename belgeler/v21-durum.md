# DrawGuidance v21: vektör düzenle düğmesi, görünür akışkanlık ve yumuşatma, kalem tanıma, yeni iz/desen/doku fırçaları, 3B cisimler ve 3B çizim modu, SQLite şekil veritabanı

_2026-09-29 · Room v9 (yeni `shape_templates` tablosu) · Kayıt biçimi ContentCodec v6 (yeni fırçalar 38–49 tür kodlarıyla; 3B izlerde `params.depth3d`)_

## İstekler ve yapılanlar

1. **Vektör düzenleme için ayrı düğme.**
   - Düzenle modunun üst menüsünün ilk düğmesi artık "Vektör düzenle". Seçili şekil, diyagram, resim, yazı ya da formül varsa etkin olur; dokununca vektör düzenleme açılır.
   - Seçim yoksa düğme görünür ama pasiftir.
   - Elemana dokunarak açma da çalışmaya devam ediyor. Bunu kaldırmadım, düğme ek bir yol.
2. **Fırçanın düzenleme menüsünde "Düzeltme öner" yalnız öneriler açıkken görünür.**
   - Bu kural hem Seç modunun alt çubuğunda hem iki parmakla açılan bağlam menüsünde geçerli.
   - Fırçanın görünümünü bozacak bir öneri çıkacaksa düğme gösterilmez. Doku, desen ve yumuşak fırçalarda bu yüzden hiç çıkmaz (bkz. 7).
3. **Akışkanlık ve yumuşatma artık gözle görülür etki yapıyor.**
   - **Akışkanlık:** Değer düştükçe boya hem açılıyor hem de kuru fırça gibi taneli bir doku kazanıyor (`BrushTextures.flowGrain`). Bu, çizgiyle çizen 13 fırça motorunda ve doku/desen fırçalarında geçerli.
     - %100'de iz dolu görünür.
     - %60'ta hafif taneli olur.
     - %30'da belirgin biçimde kuru ve açık görünür.
   - **Yumuşat:** Yeniden yazıldı. Çizgi önce eşit aralıklarla yeniden örnekleniyor, sonra Gauss ortalaması alınıyor. Ortalamanın yarıçapı çizgi boyunun %4'ü (4–40 birim).
     - Açık çizgide uçlar yerinde kalır. Kapalı çizgide ortalama çevre boyunca dolanır.
     - Her basış çizgiyi belirgin biçimde düzleştirir.
4. **Kalem tanıma.**
   - Ayarlar'daki kalem kartında "Kalem tanındı / Kalem tanınmadı" durumu yazıyor. Yanında üç seçenek var:
     - **Otomatik:** Kalem destekli ekran ya da Bluetooth kalem bulunursa, ya da kalemle ilk dokunuşta tanınır.
     - **Her zaman açık:** Kalem algılanmasa da kalem ayarları görünür ve kullanılır.
     - **Kapalı:** Kalem parmak gibi çizer; basınç, eğim ve yan tuş kullanılmaz.
   - Kalem tanınmıyorsa Ayarlar'daki kalem anahtarları ve fırça panelindeki eğim bölümü gizlenir.
5. **12 yeni fırça**, fırça seçicide 4 kategoride:
   - **İzler:** Mürekkep izi (sıçrayan damlalar), Döküm izi (aşağı akan damlalar), Boya izi (kıl şeritli kalın boya).
   - **Desenler:** Halı deseni, Duvar kağıdı, Ahşap boyası.
   - **Canlı dokuları:** İnsan (ten), Hayvan (kürk), Bitki.
   - **Malzeme dokuları:** Ahşap, Demir (pas), Metal (fırçalanmış yüzey ve çizikler).
   - Dokular fırçanın rengine göre üretilir; `SurfaceTextures` 128 px'lik döşemeler çıkarır ve önbellekte tutar.
6. **3B cisimler ve 3B çizim modu.**
   - **3B cisimler:** Temel şekillere 9 gölgeli cisim eklendi: Küp, Kutu, Silindir, Koni, Küre, Kubbe, Piramit, Üçgen prizma, Altıgen prizma. Tonlar dolgu renginden, dolgu yoksa kenar renginden türetilir.
   - **3B çizim modu (6.1):**
     - Fırça göstergesinin altındaki "3B kapalı" çipine dokununca açılır. Altta bir çubuk çıkar:
       - **Düzlem:** Serbest, Ön yüz, Yan yüz ya da Üst (zemin).
       - **Derinlik:** %0–100.
     - Tüm fırçalar bu moda uyar:
       - Çizgi, seçilen yüze izometrik olarak (30°) yatırılır.
       - İz, derinlik kadar kalınlaşır ve koyu yan duvarlarıyla katı bir cisim gibi görünür.
     - Kağıtta izometrik ızgara çıkar; seçili düzlem vurgulanır.
     - Mod ayarları tuvalde saklanır. Düzlem ve derinlik her ize de kaydedilir.
7. **İz, desen ve doku fırçalarında öneriler fırçaya göre.** Fırçalar üç gruba ayrıldı:
   - **Yazı kalemleri:** Yazıya çevirme, şekil düzeltmesi ve şekil veritabanı önerileri.
   - **Sanat, iz ve damga fırçaları:** Yalnız geometri düzeltilir (şekil ya da şablon). Fırça, renk ve doku aynı kalır; yazıya çevrilmez.
   - **Doku, desen ve yumuşak fırçalar:** Hiç öneri verilmez. Bu fırçaların görünümü bozulmasın diye.
8. **SQLite şekil veritabanı** ve bu veritabanından öneriler. Ayrıntılar aşağıda.
   - 253 kayıt, 18 kategori.
   - Çizim bir kayda benzeyince "Çiziminiz şöyle düzeltilebilir" kartında şablonun adı çıkar (ör. "Ev (beşik çatılı)"). Şablonun çizgileri tuvalde kesik çizgiyle önizlenir.
   - Kabul edilince çizim şablonla değiştirilir:
     - Aynı fırça, renk, kalınlık ve 3B ayarı kullanılır.
     - Çok parçalı şablonlar gruplanır.
     - Tek geri alma adımıyla eski hâline döner.
   - 3B cisim çizimlerinde (küp, silindir…) bir seçenek daha var: "(gölgeli)". Bu seçenek çizimi gerçek gölgeli cisimle değiştirir; 3B çizim modunda önce gösterilir.

## Şekil veritabanı: tablo

`shape_templates` (Room v8 → v9 geçişi). Tablo ilk açılışta arka planda doldurulur.

| Sütun | Tür | Anlamı |
|---|---|---|
| `id` | INTEGER PK | Otomatik kimlik |
| `key` | TEXT, benzersiz dizin | Kayıt anahtarı. Katalogda şablon anahtarı (`yuz.goz.badem`), kullanıcı örneğinde `<şablon>#u<zaman>` |
| `templateKey` | TEXT, dizinli | Kaydın ait olduğu şablon |
| `name`, `category`, `groupName`, `description` | TEXT | Ad (ör. "Göz (badem)"), kategori ("Yüz"), grup ("Göz") ve açıklama |
| `solid` | TEXT? | 3B cisim karşılığı (`SOLID_CUBE`…) |
| `strokeCount`, `aspectBucket` | INTEGER, **bileşik dizin** | Çizgi sayısı ve 8 kovalı en-boy sınıfı. Ön eleme bunlarla yapılır |
| `closedMask`, `cornerCount` | INTEGER | Kapalı çizgiler (bit maskesi) ve keskin köşe sayısı |
| `geometry` | BLOB | Birim kutudaki çizgiler, 16 bit tam sayı (şablon başına ortalama ~0,4 KB) |
| `descriptor` | BLOB | Öznitelikler: 7 kopya × 320 boyut, karekök sıkıştırmalı 8 bit (2240 bayt) |
| `lshKey` | INTEGER, dizinli | 16 bitlik SimHash kovası |
| `usage`, `rejects` | INTEGER | Kabul ve ret sayaçları (öğrenilen tercih) |
| `source` | TEXT | `catalog` ya da `user` (kullanıcıdan öğrenilen örnek) |
| `catalogVersion` | INTEGER | Katalog sürümü |

- Tablonun tamamı yaklaşık 0,7 MB: öznitelikler 567 KB, geometri 104 KB (25 553 nokta).
- Katalog sürümü artarsa katalog kayıtları yeniden yazılır. Kullanım ve ret sayaçları anahtara göre korunur, kullanıcı örnekleri silinmez.

## Kayıtlar

Kayıtların tamamı bu belgenin sonundaki ekte, anahtar ve açıklamalarıyla birlikte. Kısa özet:

| Kategori | Kayıt | İçerik |
|---|---|---|
| Yüz | 39 | Göz ×9 (badem, irisli, bebekli, yuvarlak, çekik, kapalı, kirpikli, yarı kapalı, gözlük), kaş ×4, burun ×6 (düğme, önden, yandan, kemerli, kalkık, geniş), ağız ×8 (gülümseme, üzgün, kahkaha, dişli, dil çıkaran, dudaklar, dalgalı, öpücük), kulak ×2, bıyık, sakal, keçi sakalı, yüz ifadesi ×5, yüz şekli ×2 |
| Saç | 10 | Düz kakül, dalgalı, kıvırcık, topuz, diken diken, at kuyruğu, kısa, uzun, iki yandan toplu, afro |
| Vücut | 5 | El, ayak izi, çöp adam, kol, bacak |
| Çatı | 14 | Beşik, kırma, mansard, tek eğimli, kubbe, külah, pagoda, tonoz, testere dişi, düz (korkuluklu) + 4 ev (beşik, kırma, bacalı, iki katlı) |
| Kapı | 8 | Düz, kemerli, çift kanatlı, panelli, camlı, garaj, ahır, yuvarlak |
| Pencere | 6 | Dört bölmeli, kemerli, yuvarlak, perdeli, panjurlu, çatı (üçgen) |
| Yapı | 11 | Baca, el merdiveni, basamaklar, çit, tuğla duvar, kale kulesi, çadır, köprü, deniz feneri, gökdelen, kubbeli yapı |
| Sandalye | 8 | Yandan, önden, tabure, berjer, sallanan, bar taburesi, ofis koltuğu, bank |
| Mobilya | 9 | Masa, yuvarlak masa, kanepe, yatak, gardırop, komodin, kitaplık, abajur, televizyon |
| Meyve | 16 | Elma, armut, muz, kiraz, üzüm, çilek, portakal, portakal dilimi, limon, karpuz dilimi, ananas, şeftali, kivi, nar, avokado, böğürtlen |
| Sebze | 17 | Havuç, domates, patlıcan, sivri biber, dolmalık biber, soğan, sarımsak, patates, mısır, bezelye, brokoli, mantar, salatalık, lahana, turp, bal kabağı, marul |
| Hayvan uzvu | 24 | Kulak ×4 (kedi, köpek, tavşan, ayı), pati izi, pençe, toynak, kuş ayağı, kanat ×3 (kuş, yarasa, kelebek), kuyruk ×3 (balık, kedi, tilki), boynuz ×3 (koç, geyik, sivri), fil hortumu, gaga ×2, yüzgeç, bıyıklar, antenler, kaplumbağa kabuğu |
| Hayvan | 15 | Balık, uçan kuş, kuş, kedi/köpek/tavşan/ayı yüzü, salyangoz, kelebek, yılan, ördek, kaplumbağa, örümcek, arı, fare |
| Bitki | 13 | Yaprak ×3, papatya, saplı çiçek, lale, gül, ağaç, çam, palmiye, kaktüs, çimen, saksıda bitki |
| Gökyüzü ve doğa | 14 | Güneş, hilal, yıldız ×2, bulut, yağmur bulutu, şimşek, damla, kar tanesi, gökkuşağı, dağ, dalga, kaya, alev |
| Nesne | 26 | Kalp, anahtar, kupa, fincan, şişe, ampul, saat, şemsiye, kalem, kitap ×2, balon, zarf, kilit, çan, hediye, taç, elmas, çapa, konuşma balonu, bayrak, güneş gözlüğü, şapka, tişört, ayakkabı, top |
| Taşıt | 8 | Tekerlek, araba, tekne, uçak, bisiklet, roket, tren, kamyon |
| 3B cisim | 10 | Küp (izometrik ve eğik), dikdörtgen prizma, silindir, koni, küre, piramit, üçgen prizma, altıgen prizma, yarım küre. Her biri gölgeli cisimle eşleşir |

- Şablonlar birim kutuda, elle çizilmiş gibi sade ve düzenlenebilir çizgilerdir. Her çizgi değiştirmede ayrı bir fırça izi olur.
- Tanımları `core/content/.../content/shapes/Templates*.kt` dosyalarında.

## Algoritmalar

**1. İmza (her kayıt için bir kez hesaplanır ve BLOB olarak saklanır).**
- Çizgiler birim kutuya getirilir: uzun kenar 1 olur, oran korunur.
- Küçük parçalar tek noktaya indirgenir. Küçük parça, grubun uzun kenarının %12'sinden küçük olandır: göz, burun deliği, çekirdek. Elle çizimde bunlar genelde nokta ya da minik karalama olur.
- **Yön ızgarası özniteliği** (320 boyut): 8×8 hücre × (4 doğrultu + uç noktası), çift doğrusal dağıtım, hafif bulanıklaştırma, L2 normalizasyon. Bu öznitelik çizgi yönüne ve çizilme sırasına duyarsızdır.
- **Veri çoğaltma:** Katalog şablonu el çizimi bozulmalarıyla 7 kopya olarak saklanır: eğim ±10°, döndürme ±8°, yatay ölçek 0,8 ve 1,25.

**2. Saklama.**
- Öznitelikler 8 bit tutulur, karekök sıkıştırmasıyla: `q = round(√v·255)`. Küçük değerlerde adım daha incedir. Nicemleme hatası %3'ün altında.
- Geometri 16 bit tam sayı olarak tutulur.
- Açılışta tablo bir kez okunur ve bitişik float dizilerine açılır (~2 MB). Bu dizilerde önbellek dostu tarama yapılır.

**3. Arama.**
- **Ön eleme:**
  - Tek çizgi sorgusunda yalnız tek çizgili şablonlar aranır.
  - Grup sorgusunda çizgi sayıları uyumlu olmalı: fark en çok 2 ya da oran en az 1/2.
- **Kaba tarama:**
  - 320 boyut 80 boyuta indirgenir (4×4 havuz).
  - Tüm kopyalarda kosinüs benzerliği ve en-boy farkı hesaplanır; iç çarpım 4 birikimle yapılır.
  - En iyi 14 şablon sonraki aşamaya kalır.
  - Dizin 3000 satırı aşarsa önce yerellik duyarlı özetle aday toplanır: SimHash, 16 rastgele hiper düzlem, Hamming yarıçapı 2 içinde çoklu yoklama (137 kova). Bu testte tam taramayla %99 aynı sonucu verdi.
- **İnce tarama (kNN, k=1):** 320 boyutla şablon başına en yakın kopya ya da kullanıcı örneği bulunur.
- **Yeniden sıralama (en iyi 6 şablon):**
  - Hepsinde nokta bulutu (chamfer) uzaklığı hesaplanır: eksik ya da fazla parçayı cezalandırır.
  - **Bireysel şekil (tek çizgi):** $1 tanıyıcısı benzeri dizi hizalaması.
    - Çizgi 32 noktaya örneklenir.
    - İki yön denenir; kapalı çizgide başlangıç noktası da döndürülerek denenir.
    - Kapalılık ve köşe sayısı farkı eklenir.
  - **Birden çok elemanın oluşturduğu şekil:**
    - Parça eşleme: her parçanın konumu ve boyu, uzunluk ağırlıklı ve iki yönlü karşılaştırılır.
    - Çizgi sayısı farkı eklenir.
    - Öznitelik sıradan bağımsız olduğundan parçaların hangi sırayla çizildiği önemli değildir.
- **Güven:** `exp(−uzaklık / 0,45)`.
- **Eşikler:**
  - Tek çizgide 0,50, grupta 0,42.
  - Kaş ve gülümseme gibi genel yaylarda 0,80. Her yay bunlara benzeyebildiği için eşik yüksek tutuldu.
  - Basit şekil (daire, üçgen…) de güçlü tanındıysa şablonun önde gösterilmesi için 0,75 gerekir.

**4. Grubun seçilmesi.**
- Son çizilen iz tohumdur. Aynı katman ve aynı 3B düzlemdeki son 40 iz aday olur.
- Kutuları arasındaki boşluk, büyük olanın boyunun %20'sini (en az 12 birim) aşmayan izler gruba katılır. Bu tekrarlanır.
- Böylece iç içe ve birbirine değen parçalar birleşir (yüzün içindeki göz ve ağız gibi). Yan yana duran ayrı çizimler birleşmez.
- Seçimden istenirse ("Düzeltme öner") seçilen izler kullanılır.

**5. 3B çizim modunda.**
- Her iz kendi başlangıç noktası etrafında düzleme yatırıldığından, izometrik matrisin tersiyle düz görünüme kesin olarak geri açılır.
- Arama düz görünümde yapılır. Test edildi: ön, yan ve üst düzlemde çizilen masa düz çizimle aynı güveni (0,90) aldı.
- Şablon düz görünümde çizimin kutusuna oturtulur, aynı düzleme yeniden yatırılır ve çizimin göründüğü yere kaydırılır.
- Yeni izler aynı derinlik bilgisini taşır, yani onlar da katı gibi kabarır.
- 3B cisim şablonlarında gölgeli cisim seçeneği gelir ve 3B modda önce gösterilir.

**6. Öğrenme (makine öğrenmesi).**
- **Örnekle öğrenme (few-shot kNN):** Kabul edilen önerinin, düzleme açılmış çizimi o şablonun yeni bir örneği olarak tabloya yazılır (`source = user`). Şablon başına en çok 4 örnek tutulur; en eskisi silinir. Kullanıcının kendi çizim tarzı sonraki aramalarda doğrudan eşleşir (testte güven > 0,8).
- **Tercih önseli:** Kabul ve ret sayılarından Laplace düzeltmeli log oran hesaplanır: `0,05·ln((kabul+1)/(ret+1))`. Kapatılan öneriler biraz geriye düşer, sık kullanılanlar öne çıkar. Etkisi sınırlıdır ve geometriyi geçemez.
- Sinir ağı ya da harici kütüphane kullanılmadı. Tamamı saf Kotlin, NDK ya da TFLite gerekmez. minSdk 21'deki cihazlarda da aynı şekilde çalışır.

**7. Hız ve cihaz desteği.**
- Arama ortalama 0,7 ms. Tek çizgi ve grup araması birlikte, şablonun oturtulmasıyla toplam öneri ortalama 2,7 ms (JVM ölçümü; telefonda birkaç kat yavaş olabilir, yine de 16 ms'lik karenin altında).
- Arama arka planda yapılır.
- İlk açılışta tablo arka planda doldurulur (JVM'de ~2 sn). Tablo hazır olana kadar yalnız yazı ve basit şekil önerileri çıkar. Sonraki açılışlarda yalnız okuma yapılır.
- Bellek kullanımı ~3 MB.

**8. Doğruluk** (her şablondan 3 elle çizim benzetimi, toplam 759 çizim).
- Benzetim: dalgalanma, titreme, ±5° döndürme, ±%12 esneme, kapalı çizgide başlangıç kayması ve boşluk, karışık yön ve sıra, küçük parçaların yarısı nokta.
- **İlk öneri doğru: %98,9. İlk 3 içinde: %99,6.**
- Rastgele karalamalarda en iyi güvenin %90'ı 0,17'nin altında. Eşik 0,42–0,50 olduğundan karalamalara öneri çıkmıyor.
- Karışan kayıtlar birbirine çok benzeyen çiftler: kutu ile eğik küp, gülen yüz ile göz kırpan yüz, mansard ile kırma çatı. İkisi de listede çıkıyor.

## Mimari

- **core/content** (`content/shapes`, yeni):
  - `ShapeTemplates` ve `Templates*.kt`: katalog, 253 kayıt, `VERSION = 1`.
  - `ShapeSignature` ve `ShapeSignatures`: imza, sıkıştırılmış saklama, SimHash.
  - `ShapeIndex`: arama. Ayrıca `TemplateFit` (oturtma ve sıklaştırma), `TemplateGrouping` (grup seçimi) ve `TemplateSuggest` (eşikler).
- **Veri katmanı:**
  - `data/local/ShapeTemplateEntity.kt`: tablo ve DAO.
  - `AppDatabase` v9 ve `MIGRATION_8_9`.
  - `data/repository/ShapeTemplateRepository.kt`: doldurma, yükleme, öğrenme. `TemplateSource` arayüzünü uygular.
  - Koin'de tanımlı. `CanvasEditViewModel` açılırken ısınma başlar.
- **Öneri:**
  - `state/TemplateSuggester.kt` (yeni): tek çizgi ve grup araması, 3B açma ve yatırma, `TemplateFix`.
  - `HandwritingController`:
    - `templates` önerileri ve `templateFirst` sıralaması.
    - `acceptTemplate`: gruplu değiştirme ya da gölgeli cisim, ardından öğrenme.
    - `dismiss`: gösterilen şablonların ret sayacını artırır.
    - Tek noktalık izler (göz noktası) de artık öneriye katılıyor.
- **Arayüz:**
  - Öneri kartında şablon seçenekleri (`template_option_i`). 3B cisimde "(gölgeli)".
  - Önizleme çok parçalı (`CanvasOverlay.correction`).
- **Önceki maddeler:**
  - Fırça: `BrushStyle` (yeni motorlar SURFACE/TRAIL, `SuggestPolicy`), `SurfaceTextures`, `BrushTextures.flowGrain`, `StrokeStream` (akışkanlık dokusu, yüzey/iz motorları, boya izi bantları, 3B kabarma).
  - 3B: `Iso3D`, `Draw3dBar`, `GuideRenderer.drawDraw3d`, `ShapeSolids`.
  - Kalem: `Stylus` (tanıma), `PenInput`, `SettingsScreen`, `BrushTuningControls`.
  - Düzenleme: `InkSmoothing`, `CanvasEditScreen` (Vektör düzenle, Düzeltme öner, 3B çip ve çubuk).
  - Model: `core/model` `Stroke.kt` (12 yeni `BrushType`, `Depth3D`, `Plane3D`, `GuideSpec.draw3d`) ve `ContentCodec` (38–49 tür kodları).

## Doğrulama

- **core/content `ShapeTemplatesTest`** (9 test):
  - katalog;
  - saklama biçimi (gidiş-dönüş);
  - elle çizim doğruluğu (%98,9 / %99,6);
  - karalama eşikleri;
  - LSH ile tam taramanın uyumu;
  - kullanıcı örneğiyle öğrenme;
  - oturtma;
  - gruplama (yan yana ev ve yüz ayrı kalıyor);
  - katalog önizleme sayfaları.
- **`V21ShapeDbTest`:**
  - SQLite doldurma: 253 kayıt, kategori sayıları, bileşik dizinle sorgu;
  - veritabanından yüklenen dizinde arama;
  - kabul edilince örnek ve sayaç; ret sayacı;
  - katalog sürümü değişince sayaçların ve kullanıcı örneğinin korunması;
  - ev (grup) ve çekik göz (tek çizgi) önerisi; uzaktaki çizimin gruba katılmaması;
  - masa ön, yan ve üst düzlemde: değiştirme aynı yerde ve düzleme yatırılmış;
  - küp çizimi 3B modda: önce gölgeli küp; silindir;
  - hız.
- **Arayüz testleri:**
  - `V21VectorEditButtonTest`
  - `V21InkCorrectionButtonTest` (öneriler kapalıyken alt çubukta yok, açıkken var ve çalışıyor)
  - `V21StylusRecognitionTest` (Ayarlar'da tanıma durumu ve seçenekleri; fırça panelinde eğim bölümü yalnız tanınınca)
  - `V21Draw3dModeTest` (çip, çubuk, ön yüz; iz 30° eğik ve derinlik bilgili; kapatınca düz)
  - `V21TemplateSuggestionTest` (parmakla çizilen ev → "Ev (beşik çatılı)", gruplu 4 iz, kullanım sayacı, geri al)
- **Ekran görüntüleri:**
  - `V21TemplateShotsTest`: ev, yüz, çekik göz, 3B ön yüzde masa, gölgeli küp.
  - `V21BrushShotTest`, `V21SolidShotTest`, `V21Draw3dShotTest`.
- **Güncellenen testler:**
  - `DatabaseAndExportTest`: v1'den v9'a geçiş ve yeni tablo.
  - `V20ShotsTest`: eğim bölümü için kalem tanıma "Her zaman açık".
  - `BrushDynamicsTest`: sanat tuvalinde 12 fırça kategorisi.
  - `StrokeStreamTest`: değişiklik yok, düzeltme kodda yapıldı. Az akışkanlıkta canlı çizim kayıtlı çizimden farklı görünüyordu. Neden: akışkanlık dokusu ölçeklenirken süzülüyordu ve üst üste binen parçalarda koyulaşıyordu. Doku artık süzülmeden (en yakın piksel) örnekleniyor; canlı ve kayıtlı çizim aynı.
- **Tam koşu:** 351 testin tamamı geçti (uygulama 140 sınıf / 234 test; core 117 test).
  - Tam koşuda yalnız süre ölçen `V13RenderPerformanceTest` bir kez eşiğin az altında kaldı: 2× hız bekleniyordu, makine yükü altında 1,9× çıktı. Tek başına tekrar koşunca 4,2× ile geçti.
  - Lint'te hata yok. `assembleDebug` başarılı (minSdk 21, targetSdk 36).

## Yorumlarım ve bilinmesi gerekenler

- **Madde 1:** Elemana dokunarak vektör düzenlemeyi açma yolunu kaldırmadım; düğme ek bir yol oldu. Kaldırılmasını istersen tek satırlık bir değişiklik.
- **Madde 3:** Akışkanlığı "boya miktarı" olarak yorumladım: az boyada iz açılır ve kuru fırça gibi taneli olur. Yumuşatmayı da "Yumuşat" komutu olarak yorumladım.
- **Madde 6.1:** "3B çizim"i izometrik düzlem ve katı gibi kabarma olarak yaptım. Gerçek bir 3B sahne (döndürülebilen kamera) değil. Tüm fırçalar ve düzeltme önerileri bu modla uyumlu.
- **Madde 8:** Kayıt sayısı sabit tutuldu (253). Katalog koddan geliyor; kullanıcı arayüzden kayıt eklemiyor, yalnız kabul ettiği çizimler öğrenilen örnek olarak ekleniyor. Önerideki şablonlar gerçekçi resim değil, çizgi şablonları. Kabul edilince çizimin kendi fırçasıyla yeniden çiziliyor.
- **Kalem:** Kalem tanıma ve eğim ayarları gerçek bir tablet kalemiyle denenmedi; testler taklit edilmiş olaylarla yapıldı.
- **Veritabanı:** Room v9: eski sürüme geri dönülürse veritabanı açılamaz. İlk açılışta tablo birkaç saniye içinde arka planda doluyor; bu sürede şablon önerisi çıkmaz.

## PC'ye yazarken

- Stage: `outputs/v21src/android/`. v20'ye göre 53 dosya (21 yeni, 32 değişen). Silinecek dosya yok.
- Bilgisayardaki proje v18'de. PC'ye yazılırken sırasıyla `v19src`, `v20src`, `v21src` kopyalanmalı.

## Ek: şekil veritabanının tüm kayıtları

#### Yüz (39 kayıt)

| Anahtar | Ad | Grup | Çizgi | Açıklama |
|---|---|---|---|---|
| `yuz.goz.badem` | Göz (badem) | Göz | 1 | Uçları sivri, badem biçimli göz çerçevesi |
| `yuz.goz.badem_irisli` | Göz (irisli) | Göz | 2 | Badem göz ve ortasında iris |
| `yuz.goz.bebekli` | Göz (iris ve bebek) | Göz | 3 | Badem göz, iris ve göz bebeği |
| `yuz.goz.yuvarlak` | Göz (yuvarlak) | Göz | 2 | Yuvarlak göz ve alt kısmında iris |
| `yuz.goz.cekik` | Göz (çekik) | Göz | 1 | Dış köşesi yukarı kalkık göz |
| `yuz.goz.kapali` | Göz (kapalı) | Göz | 4 | Kapalı göz: aşağı kıvrılan kapak ve kirpikler |
| `yuz.goz.kirpikli` | Göz (kirpikli) | Göz | 7 | Badem göz, iris ve üst kapakta kirpikler |
| `yuz.goz.yari_kapali` | Göz (yarı kapalı) | Göz | 3 | Ağır kapaklı göz: düz üst kapak, alt kapak ve yarım iris |
| `yuz.goz.gozluk` | Gözlük | Göz | 3 | İki yuvarlak cam ve köprü |
| `yuz.kas.kavisli` | Kaş (kavisli) | Kaş | 1 | Ortası yukarı kavisli kaş |
| `yuz.kas.duz` | Kaş (düz) | Kaş | 1 | Neredeyse düz, hafif eğik kaş |
| `yuz.kas.acili` | Kaş (açılı) | Kaş | 1 | Tepesi köşeli kaş |
| `yuz.kas.kalin` | Kaş (kalın) | Kaş | 1 | Kalın, dolgun kaş çerçevesi |
| `yuz.burun.dugme` | Burun (düğme) | Burun | 3 | Küçük yuvarlak burun ucu ve iki burun deliği |
| `yuz.burun.duz` | Burun (düz, önden) | Burun | 2 | Burun sırtı çizgisi ve alt kıvrım |
| `yuz.burun.yandan` | Burun (yandan) | Burun | 1 | Profilden burun: sırt, uç ve kanat |
| `yuz.burun.kemerli` | Burun (kemerli) | Burun | 1 | Sırtı çıkıntılı, ucu aşağı dönük profil |
| `yuz.burun.kalkik` | Burun (kalkık) | Burun | 1 | Ucu yukarı kalkık profil |
| `yuz.burun.genis` | Burun (geniş) | Burun | 3 | Geniş burun ucu ve yanlarda kanatlar |
| `yuz.agiz.gulumseme` | Ağız (gülümseme) | Ağız | 1 | Yukarı kıvrılan gülümseme |
| `yuz.agiz.uzgun` | Ağız (üzgün) | Ağız | 1 | Aşağı kıvrılan ağız |
| `yuz.agiz.kahkaha` | Ağız (kahkaha) | Ağız | 1 | Açık gülen ağız (D biçimi) |
| `yuz.agiz.disli` | Ağız (dişli gülüş) | Ağız | 2 | Gülen ağız ve üst dişler çizgisi |
| `yuz.agiz.dilli` | Ağız (dil çıkaran) | Ağız | 2 | Açık ağız ve sarkan dil |
| `yuz.agiz.dudak` | Dudaklar | Ağız | 2 | Yay biçimli üst dudak, dolgun alt dudak ve orta çizgi |
| `yuz.agiz.dalgali` | Ağız (şaşkın, dalgalı) | Ağız | 1 | Titrek, dalgalı ağız çizgisi |
| `yuz.agiz.opucuk` | Ağız (öpücük) | Ağız | 2 | Büzülmüş dudaklar (iki küçük yay) |
| `yuz.kulak.onden` | Kulak | Kulak | 2 | Kulak kepçesi ve iç kıvrım |
| `yuz.kulak.basit` | Kulak (C) | Kulak | 1 | C biçimli basit kulak |
| `yuz.biyik` | Bıyık | Bıyık/Sakal | 1 | Uçları kıvrık, iki yana açılan bıyık |
| `yuz.sakal` | Sakal | Bıyık/Sakal | 1 | Çeneyi saran U biçimli sakal |
| `yuz.keci_sakal` | Keçi sakalı | Bıyık/Sakal | 1 | Çenede sivri sakal |
| `yuz.ifade.gulen` | Gülen yüz | Yüz ifadesi | 4 | Yuvarlak yüz, iki göz ve gülümseme |
| `yuz.ifade.uzgun` | Üzgün yüz | Yüz ifadesi | 4 | Yuvarlak yüz, iki göz ve aşağı kıvrık ağız |
| `yuz.ifade.saskin` | Şaşkın yüz | Yüz ifadesi | 4 | Yuvarlak yüz, iri gözler ve O biçimli ağız |
| `yuz.ifade.goz_kirpan` | Göz kırpan yüz | Yüz ifadesi | 4 | Bir göz açık, bir göz kırpık, gülümseme |
| `yuz.ifade.notr` | Nötr yüz | Yüz ifadesi | 4 | Yuvarlak yüz, iki göz ve düz ağız |
| `yuz.oval_yuz` | Yüz (oval, çene) | Yüz şekli | 1 | Oval yüz hattı ve sivri çene |
| `yuz.kare_yuz` | Yüz (köşeli çene) | Yüz şekli | 1 | Geniş, köşeli çeneli yüz hattı |

#### Saç (10 kayıt)

| Anahtar | Ad | Grup | Çizgi | Açıklama |
|---|---|---|---|---|
| `sac.duz_kakul` | Saç (düz kakül) | Saç | 6 | Başı saran saç çizgisi ve alna düşen düz kakül |
| `sac.dalgali` | Saç (dalgalı) | Saç | 1 | Omuza dökülen dalgalı saç |
| `sac.kivircik` | Saç (kıvırcık) | Saç | 1 | Başın üstünde tomurcuk tomurcuk kıvırcık saç |
| `sac.topuz` | Saç (topuz) | Saç | 2 | Başın üstünde yuvarlak topuz |
| `sac.kirpi` | Saç (diken diken) | Saç | 1 | Yukarı doğru sivri, diken diken saç |
| `sac.atkuyrugu` | Saç (at kuyruğu) | Saç | 2 | Başın arkasından sarkan at kuyruğu |
| `sac.kisa` | Saç (kısa) | Saç | 1 | Başı kapak gibi saran kısa saç |
| `sac.uzun` | Saç (uzun) | Saç | 1 | Yüzün iki yanından aşağı inen uzun saç |
| `sac.iki_yandan` | Saç (iki yandan toplu) | Saç | 3 | Baş hattı ve iki yanda toplu saç |
| `sac.afro` | Saç (afro) | Saç | 2 | Başı saran iri, yuvarlak kabarık saç ve yüz |

#### Vücut (5 kayıt)

| Anahtar | Ad | Grup | Çizgi | Açıklama |
|---|---|---|---|---|
| `vucut.el` | El | El/Ayak | 1 | Avuç ve beş parmaklı el |
| `vucut.ayak_izi` | Ayak izi | El/Ayak | 6 | Taban ve beş parmak izi |
| `vucut.cop_adam` | Çöp adam | Figür | 4 | Yuvarlak baş, gövde, kollar ve bacaklar |
| `vucut.kol` | Kol (bükük) | Figür | 2 | Dirsekten bükülmüş kol, iki çizgi |
| `vucut.bacak` | Bacak | Figür | 2 | Diz ve ayakla bacak |

#### Çatı (14 kayıt)

| Anahtar | Ad | Grup | Çizgi | Açıklama |
|---|---|---|---|---|
| `yapi.cati.besik` | Beşik çatı | Çatı | 1 | Üçgen alınlıklı iki yöne eğimli çatı |
| `yapi.cati.kirma` | Kırma çatı | Çatı | 1 | Dört yöne eğimli, mahyası kısa yamuk çatı |
| `yapi.cati.mansard` | Mansard çatı | Çatı | 1 | Alt eğimi dik, üst eğimi yatık kırık çatı |
| `yapi.cati.tek_egim` | Tek eğimli çatı | Çatı | 1 | Bir yana eğimli, sundurma tipi çatı |
| `yapi.cati.kubbe` | Kubbe | Çatı | 1 | Yarım daire kubbe ve tabanı |
| `yapi.cati.kulah` | Külah çatı | Çatı | 1 | Kuleye ait sivri, yüksek konik çatı |
| `yapi.cati.pagoda` | Pagoda çatısı | Çatı | 1 | Saçak uçları yukarı kıvrık uzak doğu çatısı |
| `yapi.cati.tonoz` | Tonoz çatı | Çatı | 1 | Yarım silindir (beşik tonoz) kesiti, alçak kemer |
| `yapi.cati.testere` | Testere dişi çatı | Çatı | 1 | Fabrika çatısı gibi art arda dişler |
| `yapi.cati.duz` | Düz çatı (korkuluklu) | Çatı | 2 | Düz dam ve üstünde korkuluk çizgisi |
| `yapi.ev.besik` | Ev (beşik çatılı) | Ev | 4 | Kare gövde, üçgen çatı, kapı ve pencere |
| `yapi.ev.kirma` | Ev (kırma çatılı) | Ev | 5 | Kare gövde, yamuk çatı, kapı ve iki pencere |
| `yapi.ev.bacali` | Ev (bacalı) | Ev | 4 | Beşik çatılı ev ve çatıda baca |
| `yapi.ev.iki_katli` | Ev (iki katlı) | Ev | 6 | Yüksek gövde, üçgen çatı, iki kat pencere ve kapı |

#### Kapı (8 kayıt)

| Anahtar | Ad | Grup | Çizgi | Açıklama |
|---|---|---|---|---|
| `yapi.kapi.duz` | Kapı (düz) | Kapı | 2 | Dikdörtgen kapı ve kapı kolu |
| `yapi.kapi.kemerli` | Kapı (kemerli) | Kapı | 2 | Üstü yarım daire kemerli kapı ve kolu |
| `yapi.kapi.cift` | Kapı (çift kanatlı) | Kapı | 4 | İki kanatlı kapı, orta çizgi ve iki kol |
| `yapi.kapi.panelli` | Kapı (panelli) | Kapı | 4 | İki gömme panelli ahşap kapı ve kolu |
| `yapi.kapi.camli` | Kapı (camlı) | Kapı | 4 | Üst yarısı camlı kapı, cam bölmeleri ve kol |
| `yapi.kapi.garaj` | Garaj kapısı | Kapı | 4 | Geniş, yatay şeritli kepenk kapı |
| `yapi.kapi.ahir` | Ahır kapısı | Kapı | 3 | Çapraz (X) destekli ahır kapısı |
| `yapi.kapi.yuvarlak` | Kapı (yuvarlak) | Kapı | 2 | Yuvarlak (masal evi) kapı ve ortada kol |

#### Pencere (6 kayıt)

| Anahtar | Ad | Grup | Çizgi | Açıklama |
|---|---|---|---|---|
| `yapi.pencere.dort` | Pencere (dört bölmeli) | Pencere | 3 | Kare pencere ve artı biçimli kayıt |
| `yapi.pencere.kemerli` | Pencere (kemerli) | Pencere | 2 | Üstü kemerli pencere ve orta kayıt |
| `yapi.pencere.lomboz` | Pencere (yuvarlak) | Pencere | 2 | Gemi lombozu: iç içe iki daire |
| `yapi.pencere.perdeli` | Pencere (perdeli) | Pencere | 4 | Pencere ve iki yana toplanmış perdeler |
| `yapi.pencere.panjurlu` | Pencere (panjurlu) | Pencere | 4 | Ortada pencere, iki yanda panjur kanatları |
| `yapi.pencere.ucgen` | Çatı penceresi (üçgen) | Pencere | 2 | Çatı arasındaki üçgen pencere |

#### Yapı (11 kayıt)

| Anahtar | Ad | Grup | Çizgi | Açıklama |
|---|---|---|---|---|
| `yapi.baca` | Baca | Yapı elemanı | 2 | Dikdörtgen baca ve üstünden çıkan duman |
| `yapi.merdiven_el` | El merdiveni | Yapı elemanı | 6 | İki dikme ve basamaklar |
| `yapi.basamak` | Basamaklar | Yapı elemanı | 1 | Yandan görünen merdiven basamakları |
| `yapi.cit` | Çit | Yapı elemanı | 6 | Sivri uçlu tahtalar ve iki yatay kuşak |
| `yapi.tugla` | Tuğla duvar | Yapı elemanı | 16 | Şaşırtmalı sıralanmış tuğla örgüsü |
| `yapi.kale_kulesi` | Kale kulesi | Yapı | 2 | Mazgallı (dişli) üst kısımlı kule ve kapısı |
| `yapi.cadir` | Çadır | Yapı | 3 | Üçgen çadır ve açık giriş |
| `yapi.kopru` | Köprü | Yapı | 4 | Kemerli taş köprü, yol ve kemer |
| `yapi.deniz_feneri` | Deniz feneri | Yapı | 4 | Yukarı daralan kule, ışık odası ve kubbe |
| `yapi.gokdelen` | Gökdelen | Yapı | 7 | Yüksek dikdörtgen bina ve pencere sıraları |
| `yapi.cami_kubbe` | Kubbeli yapı | Yapı | 3 | Kare gövde üzerinde kubbe ve yanda ince kule |

#### Sandalye (8 kayıt)

| Anahtar | Ad | Grup | Çizgi | Açıklama |
|---|---|---|---|---|
| `mobilya.sandalye.yandan` | Sandalye (yandan) | Sandalye | 2 | Yandan sandalye: arkalık, oturak ve iki bacak |
| `mobilya.sandalye.onden` | Sandalye (önden) | Sandalye | 4 | Önden sandalye: arkalık çerçevesi, oturak ve bacaklar |
| `mobilya.sandalye.tabure` | Tabure | Sandalye | 4 | Yuvarlak oturak ve üç bacak |
| `mobilya.sandalye.berjer` | Berjer koltuk | Sandalye | 6 | Yuvarlak arkalıklı, kollu, kısa ayaklı koltuk |
| `mobilya.sandalye.sallanan` | Sallanan sandalye | Sandalye | 4 | Yandan sandalye ve altında kavisli kızaklar |
| `mobilya.sandalye.bar` | Bar taburesi | Sandalye | 4 | Yüksek oturak, uzun bacaklar ve ayak desteği |
| `mobilya.sandalye.ofis` | Ofis koltuğu | Sandalye | 6 | Arkalık, oturak, orta direk ve tekerlekli ayak |
| `mobilya.sandalye.bank` | Bank | Sandalye | 6 | Uzun oturak, arkalık tahtası ve ayaklar |

#### Mobilya (9 kayıt)

| Anahtar | Ad | Grup | Çizgi | Açıklama |
|---|---|---|---|---|
| `mobilya.masa` | Masa | Masa | 3 | Tabla ve dört (önden iki) bacaklı masa |
| `mobilya.masa_yuvarlak` | Yuvarlak masa | Masa | 3 | Elips tabla, orta ayak ve taban |
| `mobilya.kanepe` | Kanepe | Oturma | 7 | Arkalık, iki kol, oturak minderleri ve ayaklar |
| `mobilya.yatak` | Yatak | Yatak | 4 | Başlık, şilte, yastık ve ayak ucu |
| `mobilya.dolap` | Gardırop | Dolap | 6 | Uzun dolap, iki kapak ve kulplar |
| `mobilya.komodin` | Çekmeceli komodin | Dolap | 6 | Üç çekmeceli kutu ve kulplar |
| `mobilya.kitaplik` | Kitaplık | Dolap | 8 | Rafları ve kitapları olan dolap |
| `mobilya.abajur` | Abajur | Aydınlatma | 3 | Yamuk başlık, gövde ve taban |
| `mobilya.televizyon` | Televizyon | Elektronik | 3 | Ekran çerçevesi ve ayak |

#### Meyve (16 kayıt)

| Anahtar | Ad | Grup | Çizgi | Açıklama |
|---|---|---|---|---|
| `meyve.elma` | Elma | Meyve | 3 | Üstü çukur, yuvarlak elma, sap ve yaprak |
| `meyve.armut` | Armut | Meyve | 2 | Üstü dar, altı geniş armut ve sapı |
| `meyve.muz` | Muz | Meyve | 2 | Hilal biçimli muz ve sap ucu |
| `meyve.kiraz` | Kiraz | Meyve | 4 | İki yuvarlak kiraz ve birleşen sapları |
| `meyve.uzum` | Üzüm salkımı | Meyve | 11 | Aşağı daralan üzüm taneleri ve sap |
| `meyve.cilek` | Çilek | Meyve | 7 | Kalp biçimli çilek, çekirdekler ve yaprak tacı |
| `meyve.portakal` | Portakal | Meyve | 2 | Yuvarlak portakal ve tepede küçük yaprak |
| `meyve.portakal_dilim` | Portakal dilimi | Meyve | 10 | Kesilmiş portakal: kabuk, iç halka ve dilim çizgileri |
| `meyve.limon` | Limon | Meyve | 1 | İki ucu sivri oval limon |
| `meyve.karpuz` | Karpuz dilimi | Meyve | 5 | Yarım daire karpuz dilimi, kabuk çizgisi ve çekirdekler |
| `meyve.ananas` | Ananas | Meyve | 6 | Oval gövde, çapraz desen ve sivri yapraklar |
| `meyve.seftali` | Şeftali | Meyve | 3 | Ortasında oluk bulunan yuvarlak şeftali ve yaprak |
| `meyve.kivi` | Kivi dilimi | Meyve | 12 | Kivi kesiti: kabuk, orta göbek ve çekirdek halkası |
| `meyve.nar` | Nar | Meyve | 2 | Yuvarlak nar ve tepesinde taç |
| `meyve.avokado` | Avokado (kesik) | Meyve | 2 | Armut biçimli yarım avokado ve yuvarlak çekirdek |
| `meyve.bogurtlen` | Ahududu / böğürtlen | Meyve | 6 | Küçük tanelerden oluşan böğürtlen |

#### Sebze (17 kayıt)

| Anahtar | Ad | Grup | Çizgi | Açıklama |
|---|---|---|---|---|
| `sebze.havuc` | Havuç | Sebze | 6 | Uzun sivri havuç, halka çizgileri ve yaprakları |
| `sebze.domates` | Domates | Sebze | 2 | Basık yuvarlak domates ve yıldız biçimli sapı |
| `sebze.patlican` | Patlıcan | Sebze | 3 | Kavisli uzun patlıcan ve şapkası |
| `sebze.sivri_biber` | Sivri biber | Sebze | 2 | İnce uzun, ucu kıvrık biber ve sapı |
| `sebze.dolmalik` | Dolmalık biber | Sebze | 2 | Dört loblu kısa biber ve sapı |
| `sebze.sogan` | Soğan | Sebze | 5 | Damla biçimli soğan, katman çizgileri ve kökler |
| `sebze.sarimsak` | Sarımsak | Sebze | 3 | Dişli sarımsak başı ve sivri ucu |
| `sebze.patates` | Patates | Sebze | 4 | Yamru yumru oval patates ve gözleri |
| `sebze.misir` | Mısır koçanı | Sebze | 7 | Taneli koçan ve iki yanında yapraklar |
| `sebze.bezelye` | Bezelye kabuğu | Sebze | 4 | Açık kabuk ve içinde sıralı taneler |
| `sebze.brokoli` | Brokoli | Sebze | 2 | Bulut biçimli çiçek başı ve kalın sap |
| `sebze.mantar` | Mantar | Sebze | 2 | Yarım kubbe şapka ve kalın sap |
| `sebze.salatalik` | Salatalık | Sebze | 4 | Uzun oval salatalık ve kabuk noktaları |
| `sebze.lahana` | Lahana | Sebze | 4 | Yuvarlak baş ve iç içe yaprak çizgileri |
| `sebze.turp` | Turp | Sebze | 4 | Yuvarlak kök, ince kuyruk ve yapraklar |
| `sebze.balkabagi` | Bal kabağı | Sebze | 3 | Dilimli basık kabak ve sapı |
| `sebze.marul` | Marul | Sebze | 2 | Dalgalı kenarlı yaprak demeti |

#### Hayvan uzvu (24 kayıt)

| Anahtar | Ad | Grup | Çizgi | Açıklama |
|---|---|---|---|---|
| `uzuv.kulak.kedi` | Kedi kulağı | Kulak | 2 | Sivri üçgen kulak ve iç çizgisi |
| `uzuv.kulak.kopek` | Köpek kulağı (sarkık) | Kulak | 1 | Aşağı sarkan uzun, yuvarlak uçlu kulak |
| `uzuv.kulak.tavsan` | Tavşan kulakları | Kulak | 2 | İki uzun oval kulak |
| `uzuv.kulak.ayi` | Ayı kulağı | Kulak | 2 | Yarım daire kulak ve iç yayı |
| `uzuv.pati_izi` | Pati izi | Ayak | 5 | Büyük taban yastığı ve dört parmak yastığı |
| `uzuv.pence` | Pençe | Ayak | 3 | Kıvrık, sivri üç tırnak |
| `uzuv.toynak` | Toynak | Ayak | 4 | Bacak ucu ve ortası yarık toynak |
| `uzuv.kus_ayagi` | Kuş ayağı | Ayak | 5 | İnce bacak ve üç öne bir arkaya parmak |
| `uzuv.kanat.kus` | Kuş kanadı | Kanat | 1 | Tüy uçlu, açık kuş kanadı |
| `uzuv.kanat.yarasa` | Yarasa kanadı | Kanat | 1 | Kemikli, alt kenarı fistolu yarasa kanadı |
| `uzuv.kanat.kelebek` | Kelebek kanadı | Kanat | 2 | Üstte büyük, altta küçük kelebek kanadı |
| `uzuv.kuyruk.balik` | Balık kuyruğu | Kuyruk | 1 | Çatal (V) biçimli balık kuyruğu |
| `uzuv.kuyruk.kedi` | Kedi kuyruğu | Kuyruk | 1 | İnce, ucu kıvrık uzun kuyruk |
| `uzuv.kuyruk.tilki` | Tilki kuyruğu | Kuyruk | 2 | Gür, uç kısmı ayrılmış kabarık kuyruk |
| `uzuv.boynuz.koc` | Koç boynuzu | Boynuz | 1 | Sarmal kıvrılan koç boynuzu |
| `uzuv.boynuz.geyik` | Geyik boynuzu | Boynuz | 4 | Dallanan geyik boynuzu |
| `uzuv.boynuz.sivri` | Sivri boynuzlar | Boynuz | 2 | İki yana kavisli, sivri boynuzlar |
| `uzuv.hortum` | Fil hortumu | Baş | 2 | Aşağı sarkıp ucu kıvrılan hortum |
| `uzuv.gaga.kus` | Kuş gagası | Baş | 2 | Sivri, iki parçalı gaga |
| `uzuv.gaga.ordek` | Ördek gagası | Baş | 2 | Yassı, yuvarlak uçlu ördek gagası |
| `uzuv.yuzgec` | Yüzgeç | Kanat | 3 | Çizgili, yelpaze biçimli yüzgeç |
| `uzuv.biyik` | Hayvan bıyıkları | Baş | 6 | Burnun iki yanında üçer bıyık |
| `uzuv.anten` | Böcek antenleri | Baş | 4 | Uçları topuzlu iki anten |
| `uzuv.kabuk` | Kaplumbağa kabuğu | Gövde | 6 | Kubbe kabuk ve altıgen desen |

#### Hayvan (15 kayıt)

| Anahtar | Ad | Grup | Çizgi | Açıklama |
|---|---|---|---|---|
| `hayvan.balik` | Balık | Deniz | 3 | Oval gövde, üçgen kuyruk ve göz |
| `hayvan.kus_ucan` | Uçan kuş | Kuş | 1 | Uzaktan uçan kuş (iki kavis) |
| `hayvan.kus` | Kuş | Kuş | 7 | Yuvarlak gövde, baş, gaga, kanat ve bacaklar |
| `hayvan.kedi_yuz` | Kedi yüzü | Evcil | 8 | Yuvarlak baş, sivri kulaklar, gözler, burun ve bıyıklar |
| `hayvan.kopek_yuz` | Köpek yüzü | Evcil | 7 | Oval baş, sarkık kulaklar, gözler ve burun |
| `hayvan.tavsan_yuz` | Tavşan yüzü | Evcil | 6 | Yuvarlak baş, uzun kulaklar, gözler ve burun |
| `hayvan.ayi_yuz` | Ayı yüzü | Vahşi | 7 | Yuvarlak baş, yuvarlak kulaklar, burun ağzı |
| `hayvan.salyangoz` | Salyangoz | Böcek | 4 | Sarmal kabuk, uzun gövde ve antenler |
| `hayvan.kelebek` | Kelebek | Böcek | 7 | İnce gövde, dört kanat ve antenler |
| `hayvan.yilan` | Yılan | Sürüngen | 3 | Kıvrımlı uzun gövde ve baş |
| `hayvan.ordek` | Ördek | Kuş | 4 | Kayık biçimli gövde, baş, gaga ve kanat |
| `hayvan.kaplumbaga` | Kaplumbağa | Sürüngen | 5 | Kubbe kabuk, baş, ayaklar ve kuyruk |
| `hayvan.orumcek` | Örümcek | Böcek | 10 | Yuvarlak gövde ve iki yanda dörder kırık bacak |
| `hayvan.ari` | Arı | Böcek | 6 | Çizgili oval gövde, kanatlar ve iğne |
| `hayvan.fare` | Fare | Evcil | 3 | Damla biçimli gövde, yuvarlak kulak, burun ve kuyruk |

#### Bitki (13 kayıt)

| Anahtar | Ad | Grup | Çizgi | Açıklama |
|---|---|---|---|---|
| `bitki.yaprak` | Yaprak | Yaprak | 2 | Sivri uçlu yaprak ve orta damar |
| `bitki.yaprak_damarli` | Yaprak (damarlı) | Yaprak | 6 | Yaprak, orta damar ve yan damarlar |
| `bitki.akcaagac` | Akçaağaç yaprağı | Yaprak | 2 | Beş loblu sivri akçaağaç yaprağı ve sapı |
| `bitki.papatya` | Papatya | Çiçek | 9 | Yuvarlak göbek ve çevresinde yuvarlak taç yaprakları |
| `bitki.cicek_basit` | Çiçek (saplı) | Çiçek | 8 | Beş yapraklı çiçek, sap ve yaprak |
| `bitki.lale` | Lale | Çiçek | 4 | Üç dişli kupa çiçek ve sap |
| `bitki.gul` | Gül | Çiçek | 3 | Sarmal taç ve sap |
| `bitki.agac` | Ağaç | Ağaç | 3 | Bulut biçimli yuvarlak taç ve gövde |
| `bitki.cam` | Çam ağacı | Ağaç | 2 | Üst üste üç katlı üçgen taç ve gövde |
| `bitki.palmiye` | Palmiye | Ağaç | 5 | Kavisli ince gövde ve sarkan yapraklar |
| `bitki.kaktus` | Kaktüs | Bitki | 5 | Uzun gövde ve iki yana yukarı kalkan kollar |
| `bitki.cimen` | Çimen | Bitki | 1 | Yan yana sivri ot yaprakları |
| `bitki.saksi` | Saksıda bitki | Bitki | 5 | Yamuk saksı ve üç yapraklı bitki |

#### Gökyüzü ve doğa (14 kayıt)

| Anahtar | Ad | Grup | Çizgi | Açıklama |
|---|---|---|---|---|
| `doga.gunes` | Güneş | Gökyüzü | 9 | Daire ve çevresinde ışınlar |
| `doga.hilal` | Hilal (ay) | Gökyüzü | 1 | İnce hilal biçimli ay |
| `doga.yildiz` | Yıldız | Gökyüzü | 1 | Beş köşeli yıldız |
| `doga.yildiz_cizgi` | Yıldız (tek çizgi) | Gökyüzü | 1 | Kesişen çizgilerle tek hamlede çizilen beş köşeli yıldız |
| `doga.bulut` | Bulut | Gökyüzü | 1 | Düz tabanlı, kabarık bulut |
| `doga.yagmur` | Yağmur bulutu | Gökyüzü | 4 | Bulut ve altında yağmur çizgileri |
| `doga.simsek` | Şimşek | Gökyüzü | 1 | Zikzak yıldırım |
| `doga.damla` | Damla | Gökyüzü | 1 | Ucu sivri su damlası |
| `doga.kar_tanesi` | Kar tanesi | Gökyüzü | 9 | Altı kollu, uçları çatallı kar tanesi |
| `doga.gokkusagi` | Gökkuşağı | Gökyüzü | 3 | İç içe üç yarım yay |
| `doga.dag` | Dağlar | Doğa | 2 | Karlı zirveli iki dağ |
| `doga.dalga` | Dalga | Doğa | 2 | Tepesi kıvrılan deniz dalgası |
| `doga.tas` | Taş / kaya | Doğa | 1 | Köşeli yuvarlak kaya |
| `doga.ates` | Ateş / alev | Doğa | 1 | Yukarı dalgalanan alev dilleri |

#### Nesne (26 kayıt)

| Anahtar | Ad | Grup | Çizgi | Açıklama |
|---|---|---|---|---|
| `nesne.kalp` | Kalp | Sembol | 1 | İki yuvarlak loblu, alt ucu sivri kalp |
| `nesne.anahtar` | Anahtar | Ev eşyası | 4 | Yuvarlak baş, uzun gövde ve dişler |
| `nesne.kupa` | Kupa (bardak) | Mutfak | 2 | Silindir bardak ve yan kulp |
| `nesne.fincan` | Fincan ve tabak | Mutfak | 4 | Kase biçimli fincan, kulp ve tabak |
| `nesne.sise` | Şişe | Mutfak | 2 | Dar boyunlu şişe ve kapak |
| `nesne.ampul` | Ampul | Ev eşyası | 4 | Armut biçimli cam, duy ve vida çizgileri |
| `nesne.saat` | Saat | Ev eşyası | 7 | Yuvarlak kadran, akrep ve yelkovan |
| `nesne.semsiye` | Şemsiye | Giyim/Aksesuar | 2 | Dilimli kubbe, sap ve kıvrık uç |
| `nesne.kalem` | Kalem | Kırtasiye | 3 | Altıgen gövde, açılmış uç ve silgi |
| `nesne.kitap` | Kitap (açık) | Kırtasiye | 2 | Ortadan açılmış iki sayfa ve cilt |
| `nesne.kitap_kapali` | Kitap (kapalı) | Kırtasiye | 3 | Dikdörtgen kapak, sırt ve sayfa kenarı |
| `nesne.balon` | Balon | Oyuncak/Parti | 3 | Oval balon, düğüm ve ip |
| `nesne.zarf` | Zarf | Kırtasiye | 2 | Dikdörtgen zarf ve V biçimli kapak |
| `nesne.kilit` | Asma kilit | Ev eşyası | 4 | Kilit gövdesi, U biçimli halka ve anahtar deliği |
| `nesne.zil` | Çan / zil | Ev eşyası | 2 | Çan gövdesi, geniş ağız ve tokmak |
| `nesne.hediye` | Hediye kutusu | Oyuncak/Parti | 5 | Kutu, kurdele ve fiyonk |
| `nesne.tac` | Taç | Giyim/Aksesuar | 1 | Beş sivri uçlu taç |
| `nesne.elmas` | Elmas | Sembol | 4 | Kesilmiş taş: üst yüzey ve sivri alt |
| `nesne.capa` | Çapa | Sembol | 4 | Halka, gövde, çapraz kol ve kıvrık uçlar |
| `nesne.konusma` | Konuşma balonu | Sembol | 1 | Oval balon ve aşağıdaki sivri kuyruk |
| `nesne.bayrak` | Bayrak | Sembol | 2 | Direk ve dalgalanan bayrak |
| `nesne.gozluk_gunes` | Güneş gözlüğü | Giyim/Aksesuar | 3 | İki yamuk cam ve köprü |
| `nesne.sapka` | Şapka | Giyim/Aksesuar | 2 | Yuvarlak tepe ve geniş kenar |
| `nesne.tisort` | Tişört | Giyim/Aksesuar | 2 | Kısa kollu tişört ve yaka |
| `nesne.ayakkabi` | Ayakkabı | Giyim/Aksesuar | 2 | Yandan spor ayakkabı ve taban |
| `nesne.top` | Top | Oyuncak/Parti | 3 | Daire ve kavisli dikiş çizgileri |

#### Taşıt (8 kayıt)

| Anahtar | Ad | Grup | Çizgi | Açıklama |
|---|---|---|---|---|
| `tasit.tekerlek` | Tekerlek | Taşıt parçası | 3 | Lastik, jant ve göbek |
| `tasit.araba` | Araba | Kara | 5 | Gövde, kabin, pencere ve iki tekerlek |
| `tasit.tekne` | Tekne | Deniz | 3 | Yamuk gövde, direk ve üçgen yelken |
| `tasit.ucak` | Uçak | Hava | 4 | Uzun gövde, kanatlar ve kuyruk |
| `tasit.bisiklet` | Bisiklet | Kara | 7 | İki tekerlek, üçgen kadro, sele ve gidon |
| `tasit.roket` | Roket | Hava | 5 | Sivri burunlu gövde, pencere, kanatçıklar ve alev |
| `tasit.tren` | Tren | Kara | 6 | Lokomotif, baca, kabin ve tekerlekler |
| `tasit.kamyon` | Kamyon | Kara | 5 | Kasa, kabin ve üç tekerlek |

#### 3B cisim (10 kayıt)

| Anahtar | Ad | Grup | Çizgi | Açıklama |
|---|---|---|---|---|
| `cisim.kup` | Küp (çizim) | Cisim | 3 | Ön yüz karesi, üst ve yan yüz: altıgen dış hat ve iç Y → 3B: SOLID_CUBE |
| `cisim.kup_onden` | Küp (eğik izdüşüm) | Cisim | 3 | Önde kare, arkada kaydırılmış kare ve birleştiren kenarlar → 3B: SOLID_CUBE |
| `cisim.kutu` | Dikdörtgen prizma (çizim) | Cisim | 3 | Uzun kutu: ön dikdörtgen, üst ve yan yüz → 3B: SOLID_BOX |
| `cisim.silindir` | Silindir (çizim) | Cisim | 4 | Üstte elips, iki dikey kenar ve altta yay → 3B: SOLID_CYLINDER |
| `cisim.koni` | Koni (çizim) | Cisim | 2 | Tepe noktasından inen iki kenar ve taban elipsi → 3B: SOLID_CONE |
| `cisim.kure` | Küre (çizim) | Cisim | 2 | Daire ve ön yarısı görünen ekvator elipsi → 3B: SOLID_SPHERE |
| `cisim.piramit` | Piramit (çizim) | Cisim | 2 | Kare tabanlı piramit: tepe, iki görünen yüz ve taban → 3B: SOLID_PYRAMID |
| `cisim.ucgen_prizma` | Üçgen prizma (çizim) | Cisim | 2 | Önde üçgen, arkaya uzanan kenarlar → 3B: SOLID_PRISM |
| `cisim.altigen_prizma` | Altıgen prizma (çizim) | Cisim | 4 | Üstte basık altıgen, dikey kenarlar ve alt hat → 3B: SOLID_HEX_PRISM |
| `cisim.kubbe` | Yarım küre (çizim) | Cisim | 2 | Yarım daire kubbe ve taban elipsi → 3B: SOLID_DOME |
