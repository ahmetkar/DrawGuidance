# Moda özel sinir ağlarının eğitimi (v30)

Ağlar uygulamanın içinde gelir: `core/content/src/main/resources/shapenet/mode_{art,notes,comic}.bin`.
Yeniden eğitmek için:

1. **Veri:** `mapping.tsv` içindeki 181 "Quick, Draw!" kategorisinin ilk 1000 çizimi
   `https://storage.googleapis.com/quickdraw_dataset/full/simplified/<kategori>.ndjson` adresinden indirilir
   (veri kümesi: Google Creative Lab, CC BY 4.0).
2. `python3 -I prep.py <ham> <temiz>`: tanınmış çizimler; kategori başına 700 eğitim, 150 test.
3. `python3 -I totxt.py <temiz>`: Kotlin'in okuduğu düz metin biçimi.
4. `MODENET_DATA=<temiz> MODENET_MAP=mapping.tsv MODENET_OUT=<çıktı> ./gradlew :core:content:test --tests '*ModeNetTraining.export'`:
   her mod için 83 boyutlu girdiler (internetteki 2+ çizgili çizimler ve şablon benzetimleri).
5. `python3 -I train_modenet.py <çıktı> <art|notes|comic> 256 40`: numpy ile eğitim (Adam, sınıf dengeli örnekleme).
6. `MODENET_PACK=1 MODENET_OUT=<çıktı> ./gradlew :core:content:test --tests '*ModeNetTraining.pack'`: int8 ağlar
   kaynaklara yazılır; ağ değişince `ShapeNet.MODE_NET_VERSION` artırılır.
7. `MODENET_EVAL=1 ... --tests '*ModeNetTraining.evaluateSearch'`: tam aramanın ağlı/ağsız doğruluğu.

## v37: kütüphane nesneleri, karakterler ve efektlerle yeniden eğitim (ART, COMIC, NOTES)

v30 ağları (`src/test/resources/shapenet-v1/mode_{art,comic}.bin`) başlangıç alınır; v36 kütüphanesinin nesneleri,
çizgi roman karakterleri ve efektleri çizgi haline getirilip (`LibraryLineArt`) yeni sınıf olarak eklenir ve ağlar
yeniden eğitilir. Not alma (NOTES) ağı yalnız şekil düzeltmesi için resim ağının gizli katmanından yeniden kurulur;
eski not alma ağının verisi kullanılmaz (cihazda öğrenilenler de silinir, bkz. `ShapeNet.resetBelow`). Çocuk (KIDS)
ağı değişmez. Tek komut (Quick, Draw! verisi gerekmez, ~10 dk):

    ./gradlew :core:content:test --tests '*ModeNetRetrain*' -PretrainNets=1

Çıktılar: `shapenet/mode_{art,comic,notes}.bin` ve `shapes/library_templates.bin` (kütüphane şablonları). Çizgi roman
karakterleri ya da kütüphane değişince bu komut yeniden çalıştırılır; ağ değişince `ShapeNet.modeNetVersion` artırılır.

## v37: not alma modunun yazı düzeltme ağı (`shapenet/notes_text.bin`)

Şekil ağından ayrı, `HandwritingNet` (616 → 256 → 192 → yazı sistemi başı + Latin/Kiril/Arapça/Japonca başları +
Korece ilk ünsüz/ünlü/son ünsüz başları, int8). Yazı tiplerinin iskeletlerinden yapay el yazısıyla eğitilir:

    ./gradlew :core:content:test --tests '*TextNetTraining.train' -PtrainTextNet=1        # ~13 dk (4 çekirdek), rapor: build/textnet-report.txt
    ./gradlew :core:content:test --tests '*TextNetTraining.decodeReport' -PtrainTextNet=1 # satır okuma raporu
    ./gradlew :core:content:test --tests '*TextNetTraining.detectSweep' -PtrainTextNet=1  # ipucusuz yazı sistemi önselleri

Ağ değişince `HandwritingNet.VERSION` artırılır. Sunucu kataloğunda `NOTES_TEXT` adlı ağ kaydı olarak taşınır
(`CatalogCodec.bundledModeNets`, tohum: `-PcatalogOut=<dosya> --tests '*CatalogTest.exportSeed'`).

## v38: üç ayrı öneri türü — şekil ağları 4+ elemanla, yazı ağına "yazı mı" kapısı

Öneriler üç ayrı türdür (bkz. `SuggestRules`): **çizgi** (2+ çizgi; klasik makine öğrenmesi/geometri, ağ yok),
**yazı** (birbirine yakın 2+ eleman; yazı ağı) ve **şekil** (birbirine yakın 4+ eleman; modun şekil ağı + şablon
veritabanı). Tek çizgiye öneri yok.

Şekil ağları (ART, NOTES, COMIC, KIDS; sürüm 38, gizli katman 256 → 320) v37 ağlarından başlar
(`src/test/resources/shapenet-v37/`) ve yalnız 4+ elemanlı el çizimi benzetimleriyle (eksik çizimler ve parçalara
bölünmüş az çizgili şablonlar dahil; karakterlerde eğitimde görülmeyen poz/ifade birleşimleri sınamada) yeniden
eğitilir (~6 dk, 4 çekirdek; rapor `build/modenet-v38-report.txt`):

    ./gradlew :core:content:test --tests '*ModeNetsV38Training*' -PtrainNets38=1     # MODENET38_MODES=ART,NOTES ile yalnız bazı modlar

Yazı ağının gövdesi değişmez; üstüne iki doğrusal baş eklenir (`WRITING`: karakter kümelerinin ortalaması,
`WRITING_MAX`: en büyüğü; 2 sınıf, int8). Eğitim: yazı tiplerinden yapay satırlar (Latin/Türkçe, Kiril, Arapça,
Japonca, Korece) ve şablonların el çizimi benzetimleriyle çizgi grupları (~1 dk; rapor `build/textgate-v38-report.txt`):

    ./gradlew :core:content:test --tests '*TextGateTraining*' -PtrainNets38=1

`HandwritingNet.VERSION` 2, `CatalogInfo.VERSION` 38000; tohum yeniden üretilir (yukarıdaki `exportSeed`).
Eski uygulamalar yeni başları tanımaz ve yok sayar (biçim aynı: `inknet-int8-v1`).
