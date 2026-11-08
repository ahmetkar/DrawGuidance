# DrawGuidance v26: tuval gibi modlar, kapatılabilir paneller, parça grupları, yalnız çizimleri silen silgi

_2026-10-05 · Room v10 (şema değişmedi) · 3B modeller GLB · minSdk 21 – targetSdk 36_

## İstekler ve yapılanlar

1. **Mod paneli mod değişince kapanır; panelde ✕.**
   - Sahnenin altında açılan her panelin sağ üstünde kapatma düğmesi (✕) var.
   - Mod değişince o moda ait panel kapanır (Fırça, Ekle, İşlemler, Malzeme, Özellikler).
   - Parçalar ve Ayarlar her modda açık kalabilir.
   - Fırçadan Silgiye geçince fırça paneli de kapanır.
2. **Silgi çalışıyor; yalnız çizimleri siler** (isteğiniz: "Yalnız çizimleri silsin").
   - **3B çizgi:** silginin değdiği kısım silinir, çizgi parçalara ayrılır.
   - **Tüp, Şerit, açık Duvar:** silginin değdiği kısım silinir. Kalan her kısım ayrı parça olarak yeniden kurulur; şekil ikiye bölünebilir. Sürerken hemen görünür.
   - **Torna:** dönen halkası silgiye değen bant çıkar (vazo bantlara ayrılır).
   - **Şişir ve kapalı Duvar (prizma):** silgi izi boydan boya oyulur. Biçimin kalanı aynen kalır; ortada delik ya da ikiye ayrılma olabilir. Parmak kalkınca arka planda yapılır ("Siliniyor…").
   - Küp, küre gibi cisimlere ve başka araçla düzenlenmiş çizimlere dokunmaz.
   - Silgi boyu İnce / Orta / Kalın. Sahnede silginin izi ve ucu görünür.
   - Bir silme hareketi tek geri alma adımıdır.
3. **Temizleme.**
   - **Son çizimi sil:** her basışta en son çizileni siler (3B çizgi ya da fırça cismi). Silgiyle bölünmüş parçaları birlikte gider.
   - **Tümünü temizle:** onay sorar, bütün çizgileri ve fırça cisimlerini siler; cisimler kalır.
   - İkisi de geri alınabilir.
4. **Geri al / ileri al.** Üst çubuktan alındı, tuvaldeki gibi her modun araç satırının en soluna kondu (her zaman görünür). Çizimleri, silmeyi ve temizlemeyi de geri alır.
5. **Tuvaldeki gibi gruplama: 5 mod.** Alttaki mod çubuğu: **Seç · Düzenle · Çiz · Boya · Ekle** (seçili modun adı yazar). Üstündeki araç satırında o modun alt araçları ve ilgili düğmeleri durur:

   | Mod | Alt araçlar ve düğmeler |
   |---|---|
   | Seç | Taşı, Çevir, Ölçekle · Eksen · Grupla, Grubu çöz · Çoğalt, Zemine indir · İşlemler (birleştir/çıkar/kesişim/parçala/delik/ayır) · Malzeme · Özellikler · Sil |
   | Düzenle | Nokta, Kenar, Yüz · seçili aracın işlemleri (Birleştir/Kes/Sil · Eğ/Ger/İt/Yuvarlat · Tek yüz/Yüzey, Yükselt, İçe al, Delik aç, Ayrı parça…) |
   | Çiz | Fırça, Silgi · Tür (fırça paneli) · Düzlem · Son çizimi sil · Tümünü temizle |
   | Boya | Yüz, Tüm parça · renk · Malzeme |
   | Ekle | Tümü (ekleme paneli), Küp, Küre, Silindir, Koni, Piramit, Simit |

   Üst çubukta: **Parçalar**, **Ayarlar**, **Kaydet**, ⋮ (yeniden adlandır, farklı kaydet, sığdır, resim/GLB dışa aktar, paylaş).
6. **Parçalar panelinde gruplar.**
   - **Grupla** seçili parçaları adlandırılmış bir grupta toplar (Grup 1, Grup 2…).
   - Sahnede gruptaki bir parçaya dokunmak bütün grubu seçer.
   - Taşı birlikte uygulanır. Çevir ve Ölçekle grubun ortası çevresinde birlikte uygulanır (konumlar da döner/ölçeklenir).
   - Panelde grup başlığı:
     - aç/kapat,
     - görünürlük,
     - yeniden adlandır,
     - grubu çöz,
     - dokununca bütün grubu seç.
   - Üyeler başlığın içinde listelenir. Üye satırına dokunmak yalnız o parçayı seçer (tek parçayı düzenlemek için).
   - Kopyalanan grup yeni grup olur. Tek üyesi kalan grup kendiliğinden çözülür.
   - Eski "Birleştir" (ağları tek parçaya katar) **Tek parça yap** oldu.
   - Gruplar GLB'de saklanır.
7. **3B modelleme uygulaması açısından arayüz.**
   - **Sahne önde:** yönerge yalnız araç değişince 4,5 sn görünür, sonra "?" düğmesiyle açılır. Yönerge dokunuşu tutmaz, altına çizilebilir.
   - **Seçim çipi:** sahnenin altında neyin seçili olduğu yazar ("Küp 1", "Grup 1 · 2 parça", "3 parça seçili"). Dokununca Parçalar açılır.
   - **Hazır açılar düğmesi** (sağ üst): Eğik, Ön, Yan, Üst. Yakınlaştır, uzaklaştır ve sığdır düğmeleri yanında.
   - **Dokunuş düzeni:**

     | Mod | Tek parmak | İki parmak |
     |---|---|---|
     | Seç, Ekle, Düzenle | Parçanın üstünden sürüklemek düzenler, boşluktan sürüklemek sahneyi döndürür | Kaydırır ve yakınlaştırır |
     | Çiz, Boya | Çizer / boyar | Döndürür ve yakınlaştırır |

   - **Araç satırı** tuvaldeki gibi kayar. Kaydırılabilen kenarda solma olur; mod değişince satır baştan görünür.
   - Seçili bütün parçaların kenarı vurgulanır (grup), yalnız asıl parça değil.
8. **Hız.**
   - Silgi çizgi, tüp, şerit, duvar ve tornada izin kaynağından yeniden kurar. Bu cisim işlemi değildir, sürerken akıcıdır.
   - Şişir ve prizmada yalnız silgiye yakın yüzler kesilir ("yerel çıkarma"). Uzaktaki yüzler bölünmez: ağ küçük kalır, işlem kısa sürer (örnek yastıkta ~0,35 sn, arka planda).
   - Önce ekran kutusuyla sınanır; silgiye değmeyen çizimlere hiç bakılmaz.

## Mimari

- **core/content/model3d:**
  - `BrushErase` (yeni): çizgi bölme, iz kaynağından yeniden kurma, torna bantları, alan oyma (ızgara alanı + marching squares + prizma kesici).
  - `MeshSig` ve `BrushSource`: fırça kaynağı parçanın yerel uzayında; ağ başka araçla değişince geçersiz olur.
  - `BrushShapes`:
    - `BrushInput.closed/axis`,
    - `frame`,
    - `isClosed`,
    - `rebuild`,
    - `prismOf`,
    - `toPart(source)`.
  - `Csg.subtractLocal` ve `Csg.stitch`.
  - `Part.group/brush`, `Group3`, `Model3D.groups`, `Stroke3.time`.
  - `Gltf3`: grup, fırça kaynağı ve çizim anı extras'ta.
  - `Renderer`: `selectedParts`.
- **app:**
  - `Model3DEditorViewModel`:
    - `Mode3`, `setMode`, modun son alt aracı ve panel kuralları,
    - gruplar (`selectInScene`, `groupSelected`, `ungroup…`, `normalizeGroups`),
    - ortak merkezli döndürme/ölçekleme,
    - silgi (`beginErase`, `eraseAlong`, `endErase`),
    - `deleteLastDrawing`, `clearDrawings`.
  - `Model3DEditorScreen` yeniden yazıldı:
    - üst çubuk,
    - panel çerçevesi (✕),
    - seçim çipi,
    - yönerge,
    - mod çubuğu,
    - araç satırı.
  - `Model3DPanels`: gruplu Parçalar paneli, sade Fırça paneli.
  - `Model3DViewport`: silgi hareketi ve izi, boşlukta döndürme, iki parmakla kaydırma.
- Metinler `strings_v26.xml`. `strings_v23/v25.xml`'de ipuçları ve adlar güncellendi.

## Doğrulama

- **Yeni testler:**
  - core/content `Model3DV26Test` (7):
    - Tüp ortadan silinince iki kapalı parça; ilk parça kimliğini korur, kaynak yeni ağla geçerli; tümü silinince boş.
    - Taşınmış ve ölçeklenmiş çizim göründüğü yerden silinir. Başka araçla düzenlenmiş çizime silgi dokunmaz.
    - Seyrek noktalı 3B çizgi bölünür, çizim anı korunur.
    - Şişir ortadan oyulur: tek parça, hacim az azalır, kalan biçim aynı. Dikey şeritle iki parça; prizma da oyulur.
    - Torna bantlara ayrılır.
    - GLB'de gruplar, fırça kaynağı (imza geçerli) ve çizim anı saklanır; okunan çizim yine silinebilir.
    - Önizleme resmi.
  - Uygulama `V26Model3DViewModelTest` (4):
    - **Modlar:** panel kuralları, son alt aracın hatırlanması, başka moddaki araç seçilince mod değişimi.
    - **Gruplar:** dokununca grup seçilir; birlikte taşıma; ortak merkez çevresinde 90° döndürme (merkez sabit, aralık (x, z) → (z, −x)); ölçekte aralık iki kat; kopya yeni grup; grubu çöz; tek üyeli grup kendiliğinden çözülür.
    - **Silgi:** tüp ve çizgi bölünür, küpe dokunulmaz, tek geri alma adımı; şişir parmak kalkınca ikiye oyulur.
    - **Temizleme:** Son çizimi sil (önce tüp, sonra çizgi), Tümünü temizle (cisim kalır), geri al.
  - `V26Model3DEditorUiTest` (ekran görüntüleriyle):
    - mod çubuğu,
    - Ekle modu ve panelin ✕ ile kapanması,
    - Çiz → Seç'te fırça panelinin kapanması,
    - Grupla, seçim çipi "Grup 1 · 2 parça", Parçalar'da grup satırı,
    - sahnede küpü sürükleyince grubun birlikte taşınması,
    - tüp çizip silgiyle ikiye bölme,
    - Son çizimi sil,
    - Tümünü temizle (onay) ve araç satırındaki Geri al,
    - Düzenle modu.
  - Güncellenen testler: v23, v24 ve v25 düzenleyici testleri yeni düzene uyarlandı (mod çubuğu, araç satırı, üst çubuktaki Parçalar/Ayarlar, panel ✕).
- **Tam koşu:** 439 test, hepsi geçti. Uygulama 163 sınıf / 276 test, core/content 132, core/model 31.
- Lint: 0 hata (yeni düzenle kullanılmaz kalan 6 metin silindi). `assembleDebug` başarılı (minSdk 21, targetSdk 36). APK'da yeni metinler doğrulandı.

## Bilinmesi gerekenler

- Silgi yalnız fırça ve çizgi çizimlerine etki eder. Fırça cismi Nokta/Kenar/Yüz araçlarıyla ya da katı cisim işlemiyle değiştirilirse artık çizim sayılmaz ve silgi ona dokunmaz. Son çizimi sil / Tümünü temizle onu yine çizim olarak siler.
- Şişir/prizma oyması katı cisim kesmesidir (arka planda). Oyulan yüzeyin kenarında çok ince kıl payı dikişler kalabilir; görüntüye yansımaz.
- Silgi halkası bir adayı çevirirse (iz kapalı bir çember çizerse) içindeki ada da silinir.
- Eski kayıtlardaki fırça cisimlerinin kaynağı yok (v25'te saklanmıyordu): silgi onlara dokunmaz, temizleme düğmeleri de onları çizim saymaz.

## PC'ye yazarken

- **Bilgisayara yazılmadı** (isteğiniz üzerine). Değişen dosyalar stage'de: `outputs/v26src/android/`.
  - 77 dosya, v23'e göre birikimli (v24 + v25 + v26): 35 yeni, 42 değişen, silinecek dosya yok.
  - Bu turda değişen: 4 yeni, 15 değişen dosya.
- Bilgisayardaki proje v23'te. "Yaz" dediğinizde hepsi birlikte yazılıp MD5 ile doğrulanacak.
- Değişecek dosyalardan 39'unun bilgisayardaki kopyası v23 ile eşleşmişti. Kalan 3 dosya (`strings_v23.xml`, `ImagesRepository.kt`, `DeviceCaps.kt`) yazmadan önce denetlenecek (bilgisayar şu an bağlı değildi).
- APK: `DrawGuidance-v26-debug.apk` (sohbette).
