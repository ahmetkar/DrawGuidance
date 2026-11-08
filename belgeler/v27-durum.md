# DrawGuidance v27 — durum

_2026-10-11 · Room v10 (şema değişmedi) · şekil veritabanı katalog sürümü 3 · minSdk 21 – targetSdk 36_

## İstekler ve yapılanlar

1. **Taşı modunda yazı kutusu yalnız taşınır.** Kenar/köşe tutamakları yalnız Seç ve Ölçekle'de kutuyu boyutlandırır.
2. **Not tuvalinde perspektif yok, yalnız kılavuz.** Ekle menüsünde ve "buraya ekle"de perspektif çıkmaz; eski kayıttaki perspektif okunurken kaldırılır.
   - **2.1** Kılavuz ve perspektif pencerelerinde **"Mevcut kılavuz çizgilerini temizle"** (her tuval türünde, kılavuz varken).
3. **3B pencere: daha büyük mod menüleri.** Araç alanı iki satır:
   - üstte alt araç seçici (Taşı/Çevir/Ölçekle · Nokta/Kenar/Yüz · Fırça/Silgi · boya kapsamı), 42 dp;
   - altta geri al/ileri al + seçili aracın düğmeleri.
   - Düzenle'de Nokta/Kenar/Yüz seçimi diğer düğmelerden ayrı; seçim durumu ("3 nokta seçili", ✕ ile temizle). Seçim gerektiren düğmeler hep etkin: seçim yokken ne seçileceği söylenir.
4. **3B Boya: belirli yüz / hepsi.** Kapsam: **Tek yüz · Yüzey · Parça · Tümü**; dokunmadan önce boyanacak bölge önizlenir.
5. **3B pencerelerde açılış yavaşlığı.** Küçük resimler kademeli (ilk kare hemen, kalanlar arka planda), katalog küçük resimleri diskte önbellekli, neumorfik gölgeler `Modifier.Node` + bitmap önbelleği, doku ve küçük resim çözme ana iş parçacığı dışında.
   - **5.1** **GLB içe aktar** düğmesi: tuvalin 3B şekil ekleme penceresinde (her modda), 3B Şekillerim'de ve 3B düzenleyicinin Ekle panelinde. Dosya seçilince **"3B Şekillerim'e ayrı şekil olarak kaydedilsin mi?"** diye sorulur.
   - **5.1** **Gradyan:** arka planda ve yazı kutusu arka planında; şekil dolgusunda Renk/Gradyan sekmesi (2–3 renk, açı ya da dairesel); 3B malzemede iki renkli gradyan (eksen seçimli, GLB'de saklanır).
6. **Kalem aracı yalnız Çiz menüsünde.** Ekle ve resim düzenleme menülerinden kalktı. Dar telefonda 10 düğme sığsın diye Çiz satırı bir kademe küçüldü.
7. **Çizgi roman modu** (yeni tuval türü; çizim modunun bütün özellikleri + sayfalar):
   - **Konuşma balonları:** Konuşma, Kutu, Düşünce, Bağırma, Fısıltı, Anlatı, Telefon/radyo, Titrek.
   - Kuyruk: kuyruksuz, 1 kişi, 2–4 kişi (birden çok konuşana işaret). Kuyruk uçları Seç modunda sürüklenir. Balon ve kenar rengi.
   - **Panel düzenleri** (tek, iki şerit, iki sütun, üç şerit, dörtlü, altılı…).
   - **Efekt yazıları** (ses efektleri): kalın kontur, gölge, gradyan dolgu, eğim.
   - **Hız / odak / patlama çizgileri.**
   - **Resim filtreleri:** Çizgi roman, Yarım ton (Ben-Day), Manga, Pop.
   - **12 çizgi roman yazı tipi** (Türkçe karakterli, açık lisanslı): Comic Relief (+Kalın), Bangers, Luckiest Guy, Shantell Sans, Boogaloo, Grandstander, Bungee, Bungee Shade, Rubik Bubbles, Margarine, Mansalva.
   - Yazıya **gölge, kontur, gradyan, büyük harf, harf aralığı**.
8. **3B çizim önerisi.** Çizgi ya da fırça izi bitince cihazda tanınır (24 öznitelik + Gauss Naive Bayes, ~0,15 ms). Küre, silindir, simit, kubbe, kapsül, küp, piramit, koni, prizma, yıldız, tüp, şerit, duvar, torna, şişir… önerilir. Kabul/ret cihazda öğrenilir.
9. **Gelişmiş çok elemanlı tanıma** (seçtiğiniz dördü de): $Q nokta bulutu, parça ilişki grafiği (Macar eşleme), görüntü deformasyon modeli (IDM), küçük int8 sinir ağı (406 şekil, ~57 KB).
   - **9.1** **Seçimde otomatik öneri** (Çiz, Seç, Taşı, Döndür, Ölçekle, Ekle, Düzenle): seçim ~0,45 sn aynı kalınca düğmesiz öneri.
     - Çok eleman: şekil önerisi.
     - 1–3 eleman ya da şekil bulunamazsa: **parça önerisi** ("Kulaklı yüz: +5 parça"). Kabul edilince eksik parçalar seçilenin fırçası ve rengiyle, gruplu, tek geri alma adımıyla eklenir.
     - Ayarlar'da, ⋮ menüsünde ve balonda kapatılabilir. Kapatılan seçim için yeniden önerilmez.
   - **9.1** Veritabanı sürüm 3: 10 tam figür (kulaklı yüz, saçlı yüz, gövdeli insan, kapılı-pencereli ev, saksıda çiçek, dağ ve güneş, elma ağacı, tırtıl, pencereli araba, robot kafası).
   - **9.2** **Hazır şekillere yakınsa o şekil:** seçili çizgiler 31 hazır vektör şekle $Q ile benzetilir. Örneğin dört ayrı doğruyla çizilen kare → "Şekil: Kare". Kabul edilince tek vektör şekil olur. Dikdörtgen/elips gibi şekiller en-boy oranından bağımsız karşılaştırılır.
   - **9.3** Hız ve donma:
     - öneriler arka planda ve iptal edilebilir;
     - gelişmiş tanımanın şablon tarafı açılışta arka planda hazırlanır;
     - balon yolları önbellekli.
10. **Cihaz uyumu ve istisnalar.**
    - Resim filtresi ve GLB içe aktarma bellek yetmezliğinde (OutOfMemoryError) kapanmaz, bildirir.
    - Düşük sınıf cihazda filtre boyutu küçük.
    - Seçim önerileri bir elemanın içi düzenlenirken araya girmez.
    - Lint'te API (NewApi) uyarısı yok.

## Ölçümler

| Ölçüm | v26 | v27 |
|---|---|---|
| El çizimi ilk-1 (406 şablon) | %99,0 | %99,4 |
| Uzak/bozuk çizim ilk-1 | %88,2 | %91,1 |
| Karalama benzerliği p90 (eşiğin altında kalmalı) | 0,32 | 0,30 |
| Arama süresi (masaüstü JVM) | ~1,1 ms | ~1,5 ms |

- Kısmi eşleme (parça önerisi): ~1 ms; iki parçası seçilen sık şekillerde doğru şekil ilk 4'te %93.
- Vektör şekil: kare 20/20, yıldız 20/20, 2:1 dikdörtgen 18/20; karalamada yanlış 0/60.
- Sinir ağı doğrulama ilk-1: %99,8 (int8), çıkarım ~0,1–0,2 ms.

## Doğrulama

- **Tam koşu: 465 test, hepsi geçti.** Uygulama 168 sınıf / 285 test, core/content 149, core/model 31.
- **Yeni testler:**
  - `AdvancedMatchTest` (v26'dan geri kalmama, kısmi eşleme, vektör şekil, PartFit, Macar, $Q, ağ).
  - `V27SelectionSuggestTest`: dört doğru → şekil önerisi → vektör kare; tek çember → parça önerisi → gruplu ekleme ve geri al; kapalıyken öneri yok.
  - `V27GuideTest`, `V27ComicTest`, `V27LogicTest`, `V27Model3DSuggestTest`, `Sketch3Test`.
- **Güncellenen testler** (istenen davranış değişiklikleri):
  - not tuvalinde perspektif adımları kaldırıldı;
  - Taşı'da kutu yalnız taşınır, Ölçekle'de boyutlanır;
  - kalem yalnız Çiz'de;
  - 3B alt araç seçicisi;
  - "3 nokta seçili".
- Lint: 0 hata. `assembleDebug` başarılı (minSdk 21, targetSdk 36). APK'da ağırlık dosyası ve yazı tipleri doğrulandı.

## Bilgisayara yazılanlar

- `C:\Users\ahmet\Documents\DrawGuidance\android\` altına 100 dosya (32 yeni, 68 değişen; silinen yok). v26'ya göre; MD5 ile doğrulandı.
- Kök klasöre `v27-durum.md` ve `v27-kaynak.tar.gz` (aynı dosyaların arşivi).
- APK: `DrawGuidance-v27-debug.apk` (sohbette).

## Bilinmesi gerekenler

- İlk açılışta şekil veritabanı sürüm 3'e yenilenir. Öğrettiğiniz örnekler ve kabul/ret sayaçları korunur.
- Parça önerileri şablonlardaki bütün figürlerden gelir. Seçilen tek çember yüz, top, saat, tekerlek gibi birçok şekle uyabildiğinden en olası üç seçenek gösterilir; seçtikçe öğrenir.
- Sinir ağını yeniden eğitmek için (şablon eklenince):
  `SHAPENET_TRAIN=1 ./gradlew :core:content:test --tests '*ShapeNetTrainer*'` (~2 dk).
  Eğitilmemiş yeni şablonlar cezasız kalır; arama yine çalışır.
