# DrawGuidance v16 — İş bitince İncele, vektör noktaları, formül üstünde düzenleme, alçak yazı çubuğu

_2026-09-27 · Veritabanı değişmedi (Room v8) · Kayıt biçimi aynı (ContentCodec v4); şekil parçalarına isteğe bağlı `nodes` alanı eklendi, eski kayıtlar aynen açılır_

## İstekler ve yapılanlar

1. **İş bitince İncele modu**: hangi iş olursa olsun (şekil/diyagram/formül ekleme → Taşı → Bitti, şekil/diyagram/resim düzenleme, taşı/döndür/ölçekle/kırp, yazı, kılavuz, kalem, Seç'te Bitti) bitince **İncele** moduna geçilir (yalnızca kaydır/yakınlaştır). Düzenlemenin içinden açılan araç (resim düzenlerken Kırp/Kalem) bitince o düzenlemeye dönülür; düzenleme bitince İncele. Geri tuşu: Seç → İncele → Çiz → liste. Vektörleştirilen resim yeni eleman gibi seçili olarak Taşı'ya geçer.
2. **Formül formülün üstünde düzenlenir** (pencere kalktı): eklenen formül Taşı'dayken ya da Seç'te seçiliyken katsayı/bilinmeyen kutucuğuna dokununca küçük yazı alanı o kutucuğun tam üstünde açılır; yazdıkça formül güncellenir. Klavyede "ileri" / → düğmesi sonraki kutucuğa geçer; Σ düğmesi formülün tamamını formülün altında açılan alanda yazmaya geçirir; ✓ ile biter. "Formülü düzenle" düğmesi ilk kutucuğu açar.
3. **Yazı çubuğu** alçak ve geniş: iki satır (yazı tipi + boyut kaydırıcısı · kalın/eğik/altı çizili/hizalama/paragraf + ✕ ✓), çizim alanının en altında, ekran genişliğinde. Yazı yazılırken mod çubuğu gizlenir. Yazı alanı çubuğun altında kalacaksa görünüm kendiliğinden yukarı kayar: yazılan yer hep görünür.
4. **Şekil düzenleme — vektör araçları** (grafik aracı ve Özellik dışındaki eski araçlar kaldırıldı: Parça, Çizgi, Eğri, Boya, Nokta, Dönüştür, Ayır, Parçala):
   - **Noktalar**: şeklin vektör noktaları görünür; dokunarak seç (çoklu), sürükleyerek taşı — bağlı çizgiler birlikte gelir, kavisli çizgiler kırılmadan esner; seçili noktalar birlikte taşınır.
   - **Nokta ekle**: çizgiye dokun → oraya nokta; boş yere dokun → serbest nokta (çizilmez, çizgi bağlanmayı bekler; kesik çerçeveli görünür).
   - **Çizgi çiz**: bir noktaya sonra diğerine dokun → arasına düz çizgi; zincir gibi devam eder, başlangıca yeniden dokununca biter; noktadan sürükleyerek de çizilir. Açık yolun ucundan çizilince yol uzar, iki açık yol birleşir, aynı yolun iki ucu bağlanınca yol kapanır (içi boyanabilir).
   - **Çizgiler**: çizgiye dokunarak seç; ortadaki tutamakla **kavis**, ucundaki tutamakla **eğim** (15°'ye yapışır); alt satırda "Eğim N°", −15°/+15°, Yatay, Dikey, Düzleştir. Seçili çizgi sürüklenince taşınır.
   - **Kes**: seçili noktalar ve onlara bağlı çizgiler şekilden çıkar, **ayrı eleman** olur; seçili olarak Taşı'ya geçilir (istenirse oradan silinir). **Sil**: seçili noktayı (çizgileriyle) ya da seçili çizgiyi siler; hiçbir şey kalmazsa şekil silinir.
   - **Özellik** (parça özellikleri) seçime dahil parçalara, seçim yoksa tüm şekle uygulanır; Çizgi çiz ile eklenen çizgiler son verilen biçimi alır. Grafiklerde **Grafik** aracı aynen durur.
   - Kalem aracıyla çizilen yolların vektör noktaları kalemin noktalarıdır.
5. **Doku/desen ekleme tamamen kaldırıldı**: Ekle menüsü öğesi, Doku/Desen modu, seçici pencere, boy/sıklık ayarı, desen motoru (`core/content/.../pattern`) ve veri modelindeki `PatternSpec`. Eski kayıtlardaki desenler sıradan şekil olarak açılır.

## Mimari

- `ui/drawing/VectorEdit.kt` (yeni, saf mantık): şeklin vektör görünümü (noktalar, çizgiler; aynı yerdeki noktalar tek nokta), taşıma, nokta ekleme, serbest nokta, bağlama (uzat/birleştir/kapat), kavis, düzleştir, döndür/eğim, kes, sil.
- `SubPath.nodes` / `EditablePath.nodes` / `ShapePath.nodes`: parçanın vektör noktaları (yoksa belirgin noktalar bulunur, ilk düzenlemede yazılır). Nokta sayısını değiştiren eski yardımcılar `nodes`'u sıfırlar.
- `ShapeController`: `VectorSelection` (seçili noktalar/çizgi konumlarıyla, işlemlerden sonra da geçerli), dokunuş/sürükleme (`ShapeDrag`: NODES, CONNECT, EDGE, BEND, TURN), kes/sil/eğim.
- Mod politikası: `finishAction` → İncele; `EditorHost.endAction()` (düzenlenen eleman kalmadı → İncele).
- `FormulaController`: `editSource`, `nextSlot`, `onModeChange`, `onSelectionChanged`; `FormulaRenderer.slotQuad`; `FormulaInlineEditor` (EditSheets.kt).
- `InlineTextEditor`: iki satırlı çubuk + `onAvoid` (görünümü kaydırır); `ParagraphInlineControls` (TextTools.kt).
- `ModeAction`: `danger`, `description`; `EdgeControls`.

## Doğrulama

- Yeni testler: `V16VectorEditTest` (11 saf mantık testi), `V16EditorTest` (İncele politikası + desen yok, vektör araçları arayüzden, formül üstünde düzenleme, yazı çubuğu), `V16ShotsTest` (ekran görüntüleri).
- Güncellenen testler: iş bitince İncele (Bitti sonrası Çiz/Seç seçimi), formül penceresi yerine formül üstü alan, yeni şekil düzenleme araçları; eski parça/ayır/parçala testleri kaldırıldı; desen testleri kaldırıldı.

## PC'ye yazarken

- Stage: `outputs/v16src/android/` — v15'e göre değişen/eklenen dosyalar.
- Bilgisayardan silinecek (v16'da kaldırıldı): `app/.../state/PatternController.kt`, `core/content/src/main/kotlin/.../content/pattern/` (Pattern.kt, PatternFill.kt, PatternLibrary.kt), `core/content/src/test/kotlin/.../content/PatternTest.kt`.
