# DrawGuidance v46 — durum

_2026-11-01 · versionCode **46** (versionName 1.4.0) · Room **v23** (değişmedi) · minSdk 23 – targetSdk 36 · sunucu kodu değişmedi (44.0.0; yalnız taban paket listesinden Lunara çıktı) · 7 dil_

Ahmet'in v46 isteğinin altı maddesi yapıldı. Değişen dosyalar `git reset --soft e7ce7d1` ile hazırlık alanında (staged);
commit yok. 2026-11-02'de bilgisayara yazıldı (653 dosya; eski sürümleri `_eski_v45\`'te).

Teslim: imzalı `DrawGuidance-v46.apk` ve `DrawGuidance-v46.aab` (R8 küçültme + kod iyileştirme açık, **R8 tam kipi kapalı**).

Seçimler (Ahmet'in cevapları): karakterlerde şimdilik yalnız Lunara kaldırıldı (yeni üç elf büyücünün kodu ve Premium
kuralı hazır, resimleri sonra eklenecek); üst menü "dikeyde alt alta, mod menüsü altında; yatayda yan yana"; şablonlar
her tür için 5.

---

## 1. Hazır şablonlar (madde 1)

**Ne yapıldı**

- Yeni tuval formunda (ana sayfa ve Tuvallerim) **Çizgi roman** ve **Not alma** seçilince "Şablon" bölümü çıkar: ilk kart
  **Boş**, ardından 5 şablon; her kartta **kapak resmi ve adı** (7 dilde), altında seçilen şablonun kısa açıklaması.
- Şablon seçilince boyut, kâğıt rengi, arka plan ve sayfa sayısı bölümleri gizlenir (şablonunki kullanılır); "Boş"a
  dönünce geri gelir. Oluştur'a basınca şablon yeni tuvale kopyalanır ve düzenleyici açılır; her şey düzenlenebilir.
- Kurulumda resimler yeni kimliklerle tuvale kaydedilir (iki tuval aynı resim kaydını paylaşmaz) ve çizgi roman
  kapağının başlığı **tuvalin adı** olur (ortalı).
- Resim, basit modlar ve çocuk modunda şablon yok.

**Şablonlar** (yalnız uygulamada olan elemanlarla; Premium karakter, mekân ya da çizgi türü kullanılmadı)

| Kimlik | Ad | İçerik |
|---|---|---|
| `comic_action` | Aksiyon sayfası | Kapak (mekân + kahraman + başlık), "Üç panel (büyük üstte)" sahnesi: üç mekân, üç karakter, anlatı kutusu, konuşma ve bağırma balonları, yatay hız çizgileri, WHOOSH!; aynı düzende boş devam sayfası. 1200×1800, kapaktan sonraki sayfada açılır. |
| `comic_manga` | Manga sahnesi | Kapak + "Dört panel (geniş alt)"; siyah-beyaz manga mekânları ve karakterleri, **sağdan sola** okuma sırasına göre anlatı → bağırma → düşünce → aksiyon (ŞAK!, hız ve odak çizgileri). |
| `comic_webtoon` | Dikey webtoon | 800×1280 telefon sayfaları, 3 sayfa: açılış sahnesi + anlatı, iki panelde karşılıklı konuşma, kapanış koşu sahnesi (VUUUŞ). |
| `comic_yonkoma` | 4 kare mizah | Dört eşit şerit (Üç panel + Panel ekle), giriş–gelişme–ters köşe–espri (ki-shō-ten-ketsu), HAHAHA, patlama çizgileri. |
| `comic_dialogue` | Diyalog sahnesi | Kapak + dört eş panel: anlatı, fısıltı, düşünce ve bağırma balonları, GÜM!, odak çizgileri (dedektif sahnesi). |
| `notes_cornell` | Cornell ders notu | Cornell paneli (anahtar sorular / not alanı / özet), Newton yasaları: başlıklar, F = m·a formülü, değerli serbest cisim diyagramı, el yazısı sorular, özet. Dar çizgili kâğıt. |
| `notes_math` | Matematik çalışma kâğıdı | Başlık + 2 sütun: formüller (ax²+bx+c, Δ, kök formülü), numaralı çözümlü örnek, sonuç; y = x² − 4x + 3 grafiği, açıklama, kutucuklu alıştırmalar. Kareli kâğıt. |
| `notes_lab` | Deney raporu | Amaç ve hipotez, Ad/Sınıf/Tarih; malzemeler (sarkaç şekli + madde işaretli liste), numaralı yöntem; veri grafiği (L–T), T = 2π√(L/g), sonuç, hata kaynakları. |
| `notes_meeting` | Toplantı notu | Tarih/saat/katılımcılar, amaç; numaralı gündem, notlar, kararlar, sürüm takvimi (zaman çizelgesi diyagramı); sorumlu ve tarihli yapılacaklar (kutucuklu), açık sorular, sonraki toplantı. |
| `notes_mindmap` | Zihin haritası | Fotosentez: zihin haritası diyagramı (merkez + 6 dal), tepkime denklemi, bitki hücresi şekli, anahtar kavramlar. |

Araştırma: Cornell yöntemi (anahtar sütun, notlar, özet), laboratuvar raporu bölümleri (amaç/hipotez, malzeme, yöntem,
veri, sonuç, hata), toplantı notu (gündem, kararlar, sorumlu + tarihli eylemler), yonkoma yapısı ve manga sağdan sola
okuma, webtoon dikey kaydırma ve geniş aralıklar.

**Nasıl üretildi / saklanıyor**

- `V46TemplateExport` (yalnız `-PtemplateOut=<klasör>` ile çalışır) düzenleyiciyi gerçekten sürer: panel şablonu ekler,
  mekânı panele bırakır, karakter ekleyip boyutlandırır, balon/efekt/çizgi ekler; notlarda Panel ekle düzenleri,
  yazılar (liste işaretleri düzenleyicideki `TextLists` ile), formüller, grafikler, diyagramlar ve bilim şekilleri.
  Sonra kaydedilen belgeyi, kullanılan resimleri ve kapak resmini (420 px WebP) yazar.
- Dosyalar: `app/src/main/assets/templates/catalog.json` (7 dilde ad ve açıklama), her şablonda `meta.json`,
  `content.bin` (ContentCodec), `cover.webp`, `img_N.webp`. Toplam ≈ 5,2 MB.
- Kod: `data/templates/CanvasTemplates.kt` (katalog, `prepare`: kimlik yenileme + kapak başlığı),
  `CanvasRepository.createFromTemplate`, `CanvasAssetStore.saveData` (boyutla), `CanvasListViewModel.createCanvas(templateId)`,
  `canvaslist/components/TemplatePicker.kt`, `CanvasFormModal` (`CanvasFormResult.templateId`),
  `ComicCover.retitle`, `strings_v46.xml` (7 dil).
- Bilinen sınır: şablonların içindeki örnek yazılar Türkçe (ad ve açıklamalar 7 dilde).

**Bulunan ve düzeltilen hata:** Çizgi romanda sayfanın sonuna eklenen panel (Panel ekle → sona) eski son panelin
alanına düştüğü için onun içeriği sayılıyor, küçülüp onun içine taşınıyordu (4 kare şablonu üretilirken görüldü).
`PanelSets.followContent` yeni panelleri içerik saymaz. Test: `V46PanelAppendTest`.

## 2. Karakterler (madde 2 ve sonradan gelen karakterler.zip)

- Elf büyücü **Lunara** kaldırıldı: katalog, 12 resim, sunucunun taban paket listesi, `tools/comic_assets/manifest.py`.
  Eski sunucudan inmiş bir paket getirse de görünmez (`ComicPacks.REMOVED_CHARACTERS`).
- Ahmet'in `karakterler.zip`'i (13 sayfa: `premium/` 10, `free/` 3) eklendi. Her sayfadaki her ifade (büst) ve alt
  satırdaki üç figür (ayakta poz, aksiyon, günlük/oturan) ayrı çeşit olarak kesildi; zemin ayrıldı (gerçek saydamlık ya
  da hafif desenli açık zemin), hale temizlendi, 448 px WebP + 160 px küçük resim. Toplam 105 çeşit; hepsi anime
  (manga) stilinde, Fantastik temada (simyacılar ayrıca Steampunk).
  - **Premium (10):** Elf büyücü (erkek), Elf büyücü (kadın, kapalı giysili), Kukuletalı suikastçı (kadın/erkek),
    Kutsal şövalye (kadın/erkek), Orman okçusu (erkek/kadın), Ölü çağıran büyücü (erkek/kadın).
  - **Free (3):** Simyacı (kadın), Simyacı (erkek), Orman ozanı (druid; 6 ifade).
  - Adlar 7 dilde (`manifest.LABELS_SRC`). Premium listesi: `PremiumRules.PREMIUM_CHARACTERS` (26).
  - Araç: `tools/comic_assets/add_v46_chars.py --src <karakterler klasörü> [--review <klasör>]` (catalog.json'a ve
    sunucunun `base-packages/manifest.json` taban paketine ekler; aynı kimlikleri değiştirir).
- İstenen **ikinci erkek elf büyücü** zip'te yoktu (suikastçı, şövalye, okçu ve ölü çağıran ayrı karakterler olarak
  eklendi). Kimliği `elf_buyucu_erkek_2` Premium olarak hazır (`UPCOMING_PREMIUM_CHARACTERS`), sayfası gelince eklenir.
- Sunucuya: taban paket dosyaları (`V43BasePackExport` + `taban-paket --yukle`) yeniden yüklenince yeni karakterler
  sunucudan da dağıtılır; uygulama zaten içinde taşır.

## 3. Eğitimler (madde 3)

- Sürüm 46: eski eğitimleri bitirmiş kullanıcıya her türde **yenilikler** grubu bir kez önerilir
  (`TutorialScripts.VERSION = 46`, `whatsNew`). Yeni dosya `tutorial/TutorialV46.kt`, metinler `strings_v46_tutorial.xml` (7 dil).
  - **Çizim (5 adım):** iki parçalı üst menü (gün batımı gradyanı + fırçayla dağ silueti), boş yere dokununca ekleme
    menüsü (gradyanlı güneş ve dağlar), seçim menüsü, Ölçekle ve Kopyala (kuşlar çoğalır), Düzenle.
  - **Not alma (6 adım):** Panel ekle → Cornell, başlık ve el yazısı soru, serbest cisim diyagramı, değer yazınca
    yeniden çizim (F = 40, α = 30°), element şekli (Na → Cl), formül ve özet.
  - **Çizgi roman (6 adım):** gerçek panel şablonu (Üç panel), hazır mekânlar panellerin arkasında, hazır karakterler,
    anlatı/konuşma/bağırma balonları, hız çizgileri karakterin arkasında + WHOOSH!, kapak ve hazır şablonlar.
  - **Basit resim (4):** iki parçalı menü, şekiller, üst menüdeki dikdörtgen seçim, damlalık.
  - **Basit not (3):** Panel ekle (kare + not), kaldıraç şekli, değer yazınca yeniden çizim.
  - **Çocuk tanıtımı:** v45'in **Boya fırçası** adımı eklendi (12 adım).
- Çizimler hand-drawn çöp adam yerine uygulamanın hazır mekân ve karakter resimleri, gradyanlı şekiller ve formülle
  çizilen bilim şekilleriyle yapılır. `TutorialHost.assetImage` (eğitimde hazır resim; kullanılmayan veri bir sonraki
  açılışta temizlenir). Seçim menüsünün düğmeleri eğitimde tanıtılabilir (`TutorialAnchor.selection`).

## 4. Formülle yeniden çizilen not şekilleri (madde 4)

- **Element şekilleri:** periyodik tablo hücresi ve Bohr atom modeli yazılan elemente göre çizilir (sembol "Na",
  Türkçe ad "sodyum", "11" ya da "Z = 11"). 118 element: Türkçe ad, atom kütlesi, grup rengi, katman dağılımı (Aufbau +
  istisnalar). `PeriodicTable.kt`, `ParamElements.kt`.
- **Serbest cisim diyagramı** (m, F, α, μ, g): G, N, Fs, a hesaplanır; statik sürtünme; oklar en büyük kuvvete göre.
- **Sabit makara** (F = P, s = h), **hareketli makara** (F = (P + Pm)/2, s = 2h), **kaldıraç** (F·b = P·a; destek ve
  kuvvet tutamakları). `ParamMechanics.kt`. Kimlikler ve kutular eski çizimlerle aynı (eski kayıtlar açılır).
- Testler: `V46ParamMechanicsTest` (6 test).

## 5. Üst menü iki parça (madde 5)

- Gelişmiş ve basit modlarda moda bağlı üst menü ikiye bölünür (`ToolRowSplit.index`: dengeli, dörtlü grup sınırına
  yakınsa oradan; iki parçada da en az iki düğme).
  - **Dikey ekran:** iki satır alt alta; tek satıra sığıyorsa tek satır. Mod menüsü (Çiz/Ekle/Seç…) altlarında.
  - **Yatay ekran / geniş:** iki parça yan yana, aralarında ayraç (`tool_part_divider`).
  - Mod değişince iki parça da o modun araçlarını gösterir. Çocuk menüsü bölünmez (kendi düzeni).
- Kod: `V45ToolRow.kt` (`ToolRowSplitMode`, `LocalToolRowSplit`, `ToolRowSplit`), `EditorToolbar.ToolRowFrame`,
  `CanvasEditScreen` (moda göre bölme kipi). Test etiketleri: `<satır>_1`, `<satır>_2`.

## 6. Testler ve yayın (madde 6)

- Yeni testler:
  - `V46ToolRowSplitRulesTest`, `V46PortraitSplitTest`, `V46LandscapeSplitTest`
  - `V46TutorialsTest` (6)
  - `V46TemplateFilesTest` (6), `V46TemplateFormTest` (3), `V46PanelAppendTest`, `V46LunaraRemovedTest`, `V46NewCharactersTest` (3)
  - `V46ParamMechanicsTest` (core)
- Bölünmüş menüye göre güncellenen eski testler: `V45EditorUiTest` (parça başına gruplar), `V40SimpleEditorTest`
  (okuma sırası), `V37KidsLogicTest` (12 adım), `V40ComicAssetsTest`, `V44PlayShotsTest`.
- Bölünmüş menüde bulunan hata: dikeyde sığma ölçümü için menünün ölçüm kopyası kuruluyordu; kopyanın düğmeleri
  erişilebilirlik ağacında ikinci kez görünüyordu (aynı etiketli iki düğme, iki kat ayraç). Artık satırın kendisi
  kaydırılabiliyorsa (sığmıyorsa) iki satıra geçer; kopya yok.

## Test ve derleme sonuçları

- **Tam çalıştırma** (core:model + core:content + app, 935 test, 1 sa 32 dk): 11 kırmızı. Yeniden çalıştırınca
  `V9VectorShapesTest`, `V24/V25Model3DViewModelTest` (Robolectric "AppOpsManager" sıfırlama hatası) ve
  `V40KidsChallengeSuggestTest` (yük altında süre) yeşil; menü kopyası hatası (`V45NotesPanelsUiTest`,
  `V45SharedFeaturesTest`) düzeltildi ve menüye dokunan 20 test sınıfı yeniden yeşil.
  Tek kalan kırmızı: `V37KidsMascotTest.suggestionsSpeakInBalloonApplyAndClose` (v44'ten beri bilinen; v45 devir notunda).
- core:content (V46ParamMechanicsTest dahil) ve core:model: hepsi yeşil. Sunucu: 26 test sınıfı yeşil.
- **Yayın:** `DrawGuidance-v46.apk` 72,6 MB, `DrawGuidance-v46.aab` 79,7 MB; versionCode 46 / 1.4.0; yükleme
  anahtarıyla imzalı (apksigner v1+v2 doğrulandı, jarsigner AAB doğrulandı, CN=Ahmet Kar); R8 küçültme + kod
  iyileştirme açık, **tam kip kapalı** (`android.enableR8.fullMode=false`), DEX 18,25 MB (3 dosya). Boyut artışı
  (v45 62,3 MB): şablonlar ≈ 5,2 MB, yeni karakterler ≈ 4,9 MB.
