# DrawGuidance v22: uzak çizimlere tahmin, döndürülebilir 3B sahne, çoklu seçimde düzeltme, son fırçanın hatırlanması

_2026-09-29 · Room v9 (değişmedi) · Kayıt biçimi ContentCodec v7: 3B sahnede yeri olan eleman içeren tuvallerde; diğerleri v4–v6_

## İstekler ve yapılanlar

1. **Uzak çizimlere de tahmin (sabit %50 benzerlik eşiği).**
   - **Yeni ölçek:** Benzerlik ölçeği yeniden ayarlandı: `benzerlik = exp(−uzaklık / 0,9)` (v21'de 0,45'ti). Öneri eşiği sabit %50.
     - Doğru eşleşmelerin benzerliği çoğunlukla %75–95 arasında.
     - Hedefe uzak ama aynı nesneye işaret eden çizimler %50–75 arasında kalıyor ve artık öneriliyor.
   - **Genel şablonlar:** Kaş ve gülümseme gibi her yaya benzeyebilen tek yaylara uzaklık cezası (+0,4) ekleniyor. Böylece her kavis için öneri çıkmıyor.
   - **Tek çizgi:** Tek çizgi artık 2–3 parçalı şablonlarla da karşılaştırılıyor. Örneğin yalnız dış hattı çizilmiş elma "Elma" olarak tahmin ediliyor; eksik parçalar benzerliği düşürüyor ama %50'yi geçen öneriliyor.
     - Basit şekil de (daire, üçgen…) tanındıysa tek çizgiden gelen tahmin yalnız %86 ve üstünde öne geçiyor.
   - **Kart:** Öneri kartında her şablonun yanında benzerlik yüzdesi var (ör. "Masa %89").
   - **Ölçüm** (her şablondan 2 uzak çizim: ±11° döndürme, ±%26 esneme, 2 kat dalgalanma, yerinden kaymış parçalar, çok parçalı şablonların yarısında eksik parça):
     - Uzak çizimlerin %81'inde doğru şablon ilk 3 öneride ve %50'nin üstünde.
     - Normal el çizimlerinde ilk öneri %98,9 doğru.
     - Rastgele karalamaların %90'ı %31 (tek çizgi) / %37 (grup) benzerliğin altında kalıyor, yani öneri almıyor.
2. **Döndürülebilen 3B sahne.**
   - **3B çubuğu:**
     - Çizim düzlemi: Serbest, Ön yüz, Yan yüz, Üst.
     - **Döndürme alanı:** Tek parmakla sürüklenen daire. Yatay hareket yatay dönüş, dikey hareket yukarıdan ya da aşağıdan bakış. Dairenin içinde X (kırmızı), Y (yeşil) ve Z (mavi) eksenleri o anki açıyla görünür.
     - **Hazır açılar:** İzometrik, Önden, Yandan, Üstten.
     - **Perspektif:** %0 paralel izdüşüm, %100 güçlü perspektif.
     - **Derinlik.**
     - Çubuk küçültülebilir; küçültünce tuvalin altı görünür, 3B mod açık kalır. İki parmakla kaydırma ve yakınlaştırma aynen çalışıyor.
   - **Sahnede neler döner:** 3B modda çizilen izler ve 3B modda eklenen gölgeli cisimler gerçek 3B nesnelerdir ve sahneyle birlikte döner. Düz çizimler kağıtta sabit kalır.
   - **Izgara:** 3B ızgara kameraya göre çiziliyor: üç düzlemin çizgileri ve eksenler. Etkin düzlem belirgin görünüyor.
   - **Çizim:** İz parmağın altında, seçilen yüzün üzerine çizilir. Teknik olarak: parmaktan geçen görüş ışınının düzlemle kesiştiği noktalar.
     - Çizerken iz ekranda tam çizdiğin gibi görünür. Sahne döndürülünce yüzdeki gerçek yeri görünür.
     - Aynı yüze çizilen tüm izler aynı düzlemdedir ve birlikte döner.
     - Yüzü düz görerek çizmek için önce Önden, Yandan ya da Üstten açısını seçip çizebilir, sonra döndürebilirsin.
     - Kenardan görünen bir yüze çizilirse iz ekran düzlemine çizilir.
   - **Kabarma:** Derinlik kadar kabarma, yüzün normali yönündedir. Döndükçe kısalır ve yön değiştirir.
   - **Cisimler:** Gölgeli cisimler 3B modda gerçek ağlardır: küp, kutu, silindir, koni, küre, kubbe, piramit, üçgen ve altıgen prizma.
     - Her açıdan kameraya bakan yüzler, ışığa göre (sol üst önden) tonlanarak çizilir. Dış hat tam kalınlıkta, yüz kenarları ince.
     - Eğri yüzeyler kenarsız, dikişsiz parçalardan oluşur.
     - Perspektif de uygulanır.
   - **2B düzenleme:** 3B elemanlar Taşı, Ölçekle ya da silgiyle 2B'de değiştirilirse sahnedeki yerleri de güncellenir. İz aynı düzlemde yeni yerine geçer; cisim taşınır ve büyür.
   - **Geri alma:** Döndürme görünüm ayarıdır, geri alma geçmişine girmez. Geri alınan bir adımın 3B elemanları o anki açıdan çizilir.
   - **Şekil veritabanı önerileri** 3B sahnede ekranda görüldüğü gibi aranır. Yeni çizgiler çizimin düzlemine konur; 3B cisim çizimi gerçek 3B cisimle değiştirilir.
3. **Çoklu seçimde düzeltme önerisi.**
   - **Düğme:** Seç modunda birden çok çizim seçilince alt çubukta ve bağlam menüsünde "Çoklu düzeltme öner" çıkar. Karışık seçimde de (çizim + şekil/yazı) çıkar.
   - **Seçenekler:** Seçilen çizimler birlikte bir şablona (grup), yazıya ve "Tümünü düzelt (n)" seçeneğine bakılır.
   - **"Tümünü düzelt":** Her çizimi kendi düzgün şekline (doğru, daire, dikdörtgen…) aynı fırçayla çevirir, tek geri alma adımıyla. Önizlemesinde tüm çizimler kesik çizgiyle görünür.
4. **Son seçilen fırça hatırlanıyor.**
   - Silgideyken üst menüdeki Fırça düğmesine dokununca son kullanılan fırçaya dönülür. Fırça zaten seçiliyken dokununca fırça seçici açılır.
   - Seçici açılınca son fırça seçili görünür ve onun bölümüne kaydırılır.

## Mimari

- **core/model**
  - `Stroke.space: Space3D?`: 3B sahnedeki yer.
    - İzde: düzlemin çerçevesi (başlangıç ve eksenler), kabarma yönü ve düzlemdeki noktalar.
    - Cisimde: merkez ve yarı boyutlu eksenler.
    - Ayrıca özet (noktaların 2B'de değişip değişmediği) ve kamera anahtarı.
  - `Depth3D.ex/ey`: kabarmanın ekrandaki vektörü.
  - `GuideSpec.yaw3d/pitch3d/persp3d`: kamera.
  - `ContentCodec` v7: `EXT_SPACE` bloğu.
    - Çerçeve 12 float olarak saklanır.
    - Düzlem noktaları 1/16 birimde nicemlenir, farkları zigzag varint ile yazılır; sıkıştırmayla nokta başına ~2–3 bayt.
- **`ui/drawing/Scene3D.kt`** (yeni)
  - `SceneCamera`: yaw/pitch/perspektif, izdüşüm, görüş ışını.
  - Düzlem çerçeveleri ve kenar üstü düzlem kontrolü.
  - Çizim eşlemesi.
  - Eşitleme (`sync` / `syncPages`):
    - yeni izin yeri oluşturulur;
    - 2B'de düzenlenen izin yeri yeniden hesaplanır;
    - kamera değişince izdüşüm yenilenir.
  - Cisim ağları (arka yüz eleme, Lambert tonlama, derinliğe göre sıralama, dışbükey zarf dış hat).
- **Eşitleme noktaları**
  - `EditorStore.apply` her değişiklikte ve geri alma/yinelemede eşitler.
  - `applyView` kamera değişince çalışır. Döndürme sürerken kaydetmez; son adımda kaydeder.
  - Tuval yüklenirken de eşitlenir.
- **Diğer değişiklikler**
  - `CanvasTools.DrawTool`: kameraya göre düzlem ve kabarma yönü.
  - `ShapeController`: 3B modda cisimler sahne nesnesi.
  - `GuideRenderer.drawDraw3d`: kameraya göre ızgara.
  - `StrokeStream` / `StrokeRenderer`: kabarma vektörü.
  - `Draw3dBar`: döndürme alanı, hazır açılar, perspektif, küçültme.
- **Öneri**
  - `ShapeIndex`: yeni ölçek, genel şablon cezası, `TemplateSuggest.MIN_SIMILARITY = 0.5`.
  - `TemplateSuggester`: tek çizgide az parçalı şablon araması; 3B sahnede ekran görünümüyle arama ve düzleme yerleştirme.
  - `HandwritingController`: `multiShapes` ve `acceptAllShapes`, 3B cisim değiştirme.
  - Öneri kartında yüzde ve "Tümünü düzelt".
- **Fırça:** `BrushController.brushButton` ve `pickerSelection`; `BrushPickerModal` seçili fırçanın bölümüne kayar.
- Metinler `strings_v22.xml` dosyasında.

## Doğrulama

- **core/content `ShapeTemplatesTest`:** Yeni `farDrawingsStillGetPredictions` testi (%81). Diğer testler yeni ölçekle geçti.
- **`V22SceneTest`** (8 test):
  - İzometrik ve hazır açıların eksenleri, perspektif.
  - Her düzlemde iz döndürülüp geri döndürülünce aynı yerde kalıyor (< 0,6 birim).
  - Kenar üstü yüz ekran düzlemine geçiyor.
  - Ön yüze izometrik görünümden yatay çizilen çizgi önden bakınca −26,6° eğimde görünüyor.
  - 2B'de taşınan iz yeni yerinde kalıyor.
  - Cisimler: izometrikte 3 yüz, önden 1 yüz; 9 cisim dönüp geri dönüyor; taşınan küp.
  - v7 kaydı gidiş-dönüş çalışıyor.
  - Düz çizimler eşitlemede değişmiyor.
- **`V22SolidMeshShotTest`:** Cisimler 4 açıdan, perspektifle görsel olarak kontrol edildi.
- **Arayüz testleri:**
  - `V22SceneRotateTest`: 3B izin döndürme alanıyla dönmesi; düz çizginin yerinde kalması; Önden açısı; perspektif; 3B modda eklenen küpün sahne nesnesi olması; Üstten açısı; geri almanın kamerayı bozmaması.
  - `V22MultiFixTest`: iki çizim seçilince "Çoklu düzeltme öner", "Tümünü düzelt (2)", geri al.
  - `V22BrushMemoryTest`: silgiden Marker'a dönüş; seçicide Marker seçili.
- **Güncellenen testler:**
  - `V21Draw3dModeTest`: iz parmağın altında çiziliyor ve sahnede yeri var.
  - `V21TemplateShotsTest`: 3B ön yüzde masa (%89) ve gölgeli küp (%94); çubuk küçültülüyor.
  - `EditorTestBase.pickBrush`: silgiden sonra ikinci dokunuş.
- **Tam koşu:** 364 testin tamamı geçti (uygulama 145 sınıf / 246 test; core 118 test).
  - Lint'te hata yok. `assembleDebug` başarılı (minSdk 21, targetSdk 36).

## Yorumlarım ve bilinmesi gerekenler

- **3B'de çizim davranışı değişti.** v21'de çizgi yüze "yatırılıyordu": yatay çizgi 30° eğik görünüyordu. Döndürülebilen sahnede bu yöntem aynı yüze çizilen izleri birbirinden kaydırıyordu; çok parçalı çizimler dönünce dağılıyordu. Bu yüzden artık iz parmağın altında yüzün üzerine çiziliyor. Yüzü düz görerek çizmek için Önden, Yandan ya da Üstten açısı var.
- **Silgi sahneyle dönmez.** Silgi izleri kağıttadır. 3B bir izi sildikten sonra sahne döndürülürse silinen yer kayabilir. 3B izleri seçip silmek ya da silgiyi aynı açıda kullanmak daha doğru olur.
- **2B döndürme cisimde uygulanmaz.** Cisimlere 2B'de uygulanan döndürme sahnede karşılık bulmaz; cisim taşınır ve ölçeklenir ama döndürülmez. Cismi döndürmek için sahneyi döndürmek gerekir.
- **v7 kayıt biçimi.** 3B sahnede yeri olan eleman içeren tuvaller v7 biçimiyle kaydedilir; eski uygulama sürümleri bu tuvalleri açamaz.
- **Performans ölçülmedi.** Çok sayıda 3B iz içeren sahnede döndürme telefonda ölçülmedi. Döndürme saniyede en çok ~30 güncellemeyle sınırlı.

## PC'ye yazarken

- Stage: `outputs/v22src/android/`. v21'e göre 28 dosya (4 yeni, 24 değişen). Silinecek dosya yok.
- Bilgisayardaki proje v18'de. PC'ye yazılırken sırasıyla `v19src`, `v20src`, `v21src`, `v22src` kopyalanmalı.
