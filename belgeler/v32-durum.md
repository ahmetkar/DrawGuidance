# DrawGuidance v32 — durum

_2026-10-12 · Room v11 (şema değişmedi) · minSdk 21 – targetSdk 36 · Backend v30'daki gibi_

## İstek

Çizim, not alma ve çizgi roman modlarına bir eğitim modu eklendi:

- Moda ilk kez giren ya da isteyen kullanıcıya özellikler adım adım gösterilir; yapay zekâ özellikle anlatılır.
- Her adımda tanıtılan aracın hemen altında bir kutu açılır ve aracı anlatır.
- Ardından çizim alanında o araçla profesyonel bir çizim adım adım yapılır.
- Kullanıcı hangi araç grubunu seçerse onun eğitimi başlar.
- Her adımda "Eğitimi iptal et" ve "Sonraki adım", son adımda "Eğitimi bitir" düğmeleri vardır.
- Tasarım nöromorfik.

## Nasıl çalışır

**Ne zaman açılır**

- Her tuval türüne (çizim, not alma, çizgi roman) ilk girişte araç grubu seçme penceresi açılır.
- Pencerede her grubun adı, kısa açıklaması, adım sayısı, varsa "Yapay zekâ" rozeti ve tamamlandıysa "Tamamlandı" işareti görünür.
- "Şimdi değil" ile kapanır; aynı türde bir daha kendiliğinden açılmaz.
- İstenince yeniden açılır: düzenleyicinin ⋮ menüsü → **Eğitim modu**.
- Ayarlar → Çizim ekranı → **Eğitimleri yeniden öner** ile ilk girişteki öneri yeniden gösterilir.

**Adım kutusu**

- Kutu, tanıtılan aracın hemen altında açılır. Yer yoksa aracın üstünde açılır; çizim alanını anlatan adımlarda alanın altında durur.
- Kutudan araca nöromorfik bir ok uzanır. Araç, nabız gibi atan bir vurgu halkasıyla işaretlenir.
- Kutuda şunlar var:
  - adım sayacı (ör. "3 / 7") ve grup adı,
  - başlık ve açıklama; yapay zekâ adımlarında simge,
  - ilerleme noktaları,
  - "Bu adımı yeniden göster" düğmesi,
  - **Eğitimi iptal et** ve **Sonraki adım** (son adımda **Eğitimi bitir**).
- Tanıtılan araç yana kayan bir satırdaysa kendiliğinden görünür alana getirilir.
- Kâğıt her adımda kutunun kapatmadığı bölgeye yumuşakça sığdırılır.

**Çizim**

- Çizgiler parmakla çiziliyormuş gibi belirir: ekranda gezinen bir "parmak" vardır ve basınç çizginin uçlarını inceltir.
- Şekiller sürüklenerek büyür; düğmelere ve tuvale dokunuş gösterilir.
- Çizim bitmeden "Sonraki adım"a basılırsa adımın çizimi hemen tamamlanır, ardından sonraki adıma geçilir.

**Yapay zekâ adımları**

- Gerçek öneriler kullanılır: şekil düzeltme, şablon tanıma, tümünü düzelt, el yazısını yazıya çevirme.
- Öneri balonu vurgulanır ve kutuda ipucu çıkar. Kullanıcı balona kendisi dokunabilir; dokunmazsa kısa süre sonra parmak dokunur.
- Bu adımlarda öneriler, tuvalin yapay zekâ ayarından ve öneri ayarlarından bağımsız çalışır.
- Öneri gelmezse aynı sonuç doğrudan çizilir.
- Eğitimdeki kabul ve retler kullanıcının modunu eğitmez: sayaçlar ve sinir ağı değişmez.

**Kayıt dışı çalışma ("kum havuzu")**

- Eğitim boş bir sayfada gösterilir.
- Hiçbir şey kaydedilmez ve kullanıcının geri alma geçmişine girmez. Kâğıdın gradyanı da kaydedilmez.
- İptal edilince, bitirilince, geri tuşuna basılınca ya da ekrandan çıkılınca tuval, seçim ve geri alma geçmişi eğitimden önceki haline döner.
- Açık bir yazı taslağı eğitimden önce belgeye yazılır.
- Eğitim sürerken ekrana dokunmalar engellenir. Yalnız vurgulanan öneri balonuna dokunulabilir.

## Gruplar ve çizimler

| Mod | Grup | Adımlar (tanıtılan araç) | Ortaya çıkan çizim |
|---|---|---|---|
| Çizim | Çizim araçları | Fırça, Fırça ayarları, Boya kutusu, Silgi, Kalem aracı, Geri al/yinele, Katmanlar | Gün batımı manzarası: mürekkep hatlar, katman katman boyanmış dağlar ve tepeler, gradyan gökyüzü, silgiyle açılan dere, kuşlar |
| Çizim | Şekil, yazı ve düzenleme | Ekle modu, Hazır şekiller, Yazı, Seç, Taşı, Düzenle | Vektör manzara: gradyanlı güneş, karlı dağlar, tepeler, bulutlar, çamlar, kuş, başlık; güneş seçilip dağların ardına batırılır ve büyütülür |
| Çizim | Yapay zekâ önerileri | ⋮ yapay zekâ ayarı, Şekil düzeltme (ay), Şablon tanıma (ev), Tümünü düzelt (çamlar), Seçimde öneri | Elle çizilen ay, ev ve ağaçlar öneriyle düzelir |
| Not alma | Yazı ve kalemler | Başlık yazısı, Not kalemleri, Fosforlu kalem, Sayfalar ve PDF | "Newton'un Hareket Yasaları" başlığı, alt başlık, el yazısı notlar, fosforlu vurgu |
| Not alma | Formül, diyagram ve şekil | Ekle menüsü, Formül, Diyagram, Ok | F = m·a ve hız denklemi, editoryal akış diyagramı, formülden diyagrama ok |
| Not alma | Yapay zekâ ile not | El yazısını yazıya çevir, Şekil düzeltme, Tümünü düzelt, ⋮ ayarlar | "kuvvet" el yazısı yazıya döner; kutu ve halkalar düzelir |
| Çizgi roman | Paneller ve çizim | Ekle menüsü, Panel düzeni, Mürekkep kalemi, Hız ve odak çizgileri | Büyük üst panel ve iki küçük panel, pelerinli kahraman, odak çizgileri |
| Çizgi roman | Balon ve efekt yazısı | Konuşma balonu, Efekt yazısı, Anlatı kutusu | "Bu şehir güvende!" balonu, POW!, "Bir gece, şehrin üstünde…" |
| Çizgi roman | Yapay zekâ ile çizgi roman | Balon tanıma, Yüz ifadesi, Tümünü düzelt, ⋮ ayarlar | Elle çizilen konuşma balonu ve gülen yüz şablonla düzelir; baloncuklar düzelir |

## Doğaçlamalar ve düzeltmeler

- Adımı yeniden gösterme düğmesi eklendi.
- Eğitim bitince "Eğitim tamamlandı" bildirimi gösterilir.
- Ayarlara "Eğitimleri yeniden öner" eklendi.
- Tamamlanan gruplar öneri penceresinde işaretlenir.
- Tanıtılan araç kayan satırda ekran dışındaysa görünür alana getirilir.
- Yapay el yazısı üreteci (`InkSynth`) testlerden uygulamaya taşındı. Eğitimde el yazısının yazıya çevrilmesi gerçek tanıyıcıyla gösterilir.
- Çizgi roman denetleyicisinde balon, efekt yazısı ve hız çizgileri elemanları ayrı kurulabiliyor. Ekleme davranışı aynı kaldı.

## Testler (`V32Test.kt`)

- **V32OfferTest:** İlk girişte öneri penceresi ve üç grup açılır. "Şimdi değil" ile kapanır. ⋮ → Eğitim modu ile yeniden açılır.
- **V32ArtBasicsTest:**
  - Yedi adımda doğru araç tanıtılır ve son adımda "Eğitimi bitir" görünür.
  - Bitince bellekteki ve kayıttaki tuval eğitimden önceki halindedir; grup tamamlandı sayılır.
  - Kutu fırça düğmesinin hemen altında durur.
  - Demo çizgileri bellektedir, kayıtta değildir.
- **V32CancelTest:**
  - Kullanıcının çizimi eğitimde görünmez.
  - Çizim bitmeden "Sonraki adım"a basılınca çizim tamamlanır.
  - İptal edilince çizim ve geri alma geçmişi geri gelir.
- **V32ArtAiTest:**
  - Öneri balonu vurgulanır ve ipucu görünür.
  - Elle çizilen çember düzgün çembere döner, ev şablon çizgilerine döner, ağaçlar düzelir.
  - Bitince tuval boş kalır.
- **V32NotesComicTest:**
  - Not alma: el yazısı "kuvvet" yazısına dönüşür; formül, diyagram ve ok eklenir.
  - Çizgi roman: üç panel, kahraman ve odak çizgileri çizilir; iki balon ve POW! eklenir.
- **V32ShotsTest:** Ekran görüntüleri (vektör manzara, not yazma, çizgi roman yapay zekâ).

## Dosyalar

- **Yeni:**
  - `ui/screens/canvasedit/tutorial/Tutorial.kt`: denetleyici, adım ve grup modeli, oynatıcı.
  - `ui/screens/canvasedit/tutorial/TutorialScripts.kt`: içerik.
  - `ui/screens/canvasedit/tutorial/TutorialArt.kt`: çizim geometrisi ve hazır elemanlar.
  - `ui/screens/canvasedit/tutorial/TutorialUi.kt`: kutu, vurgu, parmak, öneri penceresi, araç yerleri.
  - `data/settings/TutorialPrefs.kt`.
  - `res/values/strings_v32.xml`.
- **Değişen:**
  - `EditorStore`: kum havuzu (kayıt dışı eğitim).
  - `DrawingController.commitDemo`.
  - `ComicController`: balon, efekt ve çizgi kurucuları.
  - `CanvasEditViewModel`: eğitim bağlantısı, yapay zekâ kapısı, öğrenmesiz şablon kaynağı.
  - `CanvasEditScreen`: kutu, canlı çizim, görünüm sığdırma, geri tuşu, ⋮ menüsü.
  - `EditorToolbar` ve `EditSheets`: araç yerleri ve Eğitim modu menüsü.
  - `ViewportState`: bölgeye sığdırma.
  - `SettingsScreen` ve `SettingsViewModel`.
  - `AppModule`, `DrawGuidanceApp`.
  - `core/content` `InkSynth`: testten uygulamaya taşındı.
