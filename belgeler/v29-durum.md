# DrawGuidance v29 — durum

_2026-10-11 · Room v10 (şema değişmedi) · minSdk 21 – targetSdk 36_

## İstekler ve yapılanlar

1. **Yazı biçimi tek yerde, tekrar yok.**
   - Elemanlar panelinde yazının bütün ayarları tek "Yazı biçimi" bölümünde, dört sekmede:
     - **Yazı:** yazı tipi, boyut, kalın/eğik/altı çizili, hizalama; not tuvalinde başlık ve liste.
     - **Renk:** harf rengi, harf kenarlığı (renk ve kalınlık).
     - **Efekt:** gölge, gradyan, büyük harf, harf aralığı.
     - **Kutu ve yer:** not tuvalinde yazı kutusu (en, boy, arka plan); seçili yazının sayfadaki yeri.
   - Eski ayrı "Yazı Ayarları" ve "Yazı biçimi" kutuları kaldırıldı. Fırça panelinde yazı ayarları yalnız yeni yazılar için (Yazı modunda, seçim yokken) görünür.
   - Yazıya dokununca açılan düzenleyicide yeni **Yazı biçimi** (palet) düğmesi var. Çubukta yazı tipi, boyut, B/I/U ve hizalama durur. Düğme renk, kenarlık, efekt ve kutu ayarlarını açar; çubuktakiler panelde tekrar edilmez. Değişiklikler yazıda canlı görünür.
2. **Her tuvalin ayarları kendine.** Son renk, fırça, fırça başına ayar ve boyut, yazı biçimi, şekil dolgusu ve Ekle menüsünde son öğe her tuvalde ayrı saklanır. Yeni tuval öntanımlı ayarlarla açılır. Silinen tuvalin ayarları da silinir.
3. **Çizgi romanda özel yazılar son ayar olmaz.**
   - Balon ya da efekt yazısı eklemek veya açmak son ayarı değiştirmez.
   - Son ayar yalnız kullanıcının değiştirdiği alanlarla güncellenir. Örneğin balon seçiliyken "Kalın" denirse yalnız kalınlık geçer; balonun yazı tipi ve ortalaması geçmez.
4. **Editoryal saydam tema** (Tema → "Editoryal saydam"): kağıt kartı ve ok yazısı maskeleri yok, kutular dolgusuz, tonlar saydam. Tuvalin rengi ve deseni diyagramın arkasında görünür. Koyu kutulardaki yazılar yine okunur.
5. **Moda özel yapay zekâ.**
   - **Ayrı şekil kümeleri:**
     - Resim modu genel katalogla çalışır (406 şekil).
     - Not alma modu 170 şekille çalışır. 26'sı yeni: eksenler, sayı doğrusu, dik üçgen, açı, vektör, kesir, Venn, akış şeması kutuları, sütun/çizgi grafiği, parabol, sinüs, direnç, pil, devre, atom, hücre, mercek, erlenmeyer.
     - Çizgi roman modu 406 şekille çalışır. 17'si yeni: konuşma, düşünce ve bağırma balonu, patlama, ter damlası, öfke damarı, şaşkınlık, hız çizgileri, sarmal, Zzz, paneller, kalp gözler, ünlem, soru işareti, manga gözü, baş üstünde yıldızlar.
   - Moda özel şekiller yalnız kendi modunda önerilir. Hem tek çizgi ve çok elemanlı şekil aramasında hem de az elemanlı parça önerisinde geçerlidir.
   - **Her modun kendi sinir ağı:**
     - İlk kullanımda uygulamayla gelen ağdan kurulur; yeni şekiller cihazda öğrenilir (iki mod ~0,7 sn).
     - Öneri **kabul** edilince o mod çizimden öğrenir. Parça önerisinde seçili eksik çizimden öğrenir.
     - Öneri **kapatılınca** o mod, o çizimde o şekli daha az olası bulur.
     - Diğer modlar etkilenmez.
     - Modeller uygulama klasöründe saklanır. Ayarlar → "Moda özel yapay zekâ" bölümünde her modun sayaçları görünür ve sıfırlanabilir.
   - **3B düzenleyici:** önceki modelinin yanına kendi küçük sinir ağı eklendi. Kabul ve retlerle cihazda eğitilir; birkaç geri bildirimden sonra önerileri kişisel tercihe göre sıralar.
6. **Konuşma balonları.**
   - 8 yeni biçim: çift çizgi, buz saçaklı, damlayan, fistolu, flaş, kalp, köşesi kesik, pürüzlü. Toplam 16.
   - Balon penceresinde **boyut** ayarı: genişlik, yükseklik otomatik ya da sabit. Köşe tutamaklarıyla da boyutlandırılır.
7. **Modlar arası aktarma ve resim ekleme.**
   - **Resim tuvali → not alma / çizgi roman:** ⋮ menüsünde "Not / çizgi romana aktar".
     - **Vektör:** elemanlar düzenlenebilir kopya olarak aktarılır.
     - **Resim:** saydam zeminli tek resim olarak aktarılır.
     - Seçili elemanlar ya da tüm sayfa aktarılabilir. Hedef yeni ya da kayıtlı bir tuvaldir.
   - **3B → tuval / çizgi roman:** 3B düzenleyicinin ⋮ menüsünde "Tuvale / çizgi romana aktar".
     - **Vektör:** görünen kenarlar (dış hat ve kıvrımlar; arkada kalanlar çıkarılır) tek grup mürekkep çizgisi olur.
     - **Resim:** saydam zeminli resim olarak aktarılır.
   - **Not alma ve çizgi romanda Resim ekle:** iki seçenek sunar: "Cihazdan seç" ve "Kayıtlı resimlerimden seç". İkincisi Resimlerim'i küçük resimlerle listeler.
8. **Hız ve odak çizgileri.**
   - 4 türden **14 türe** çıktı. Yeni türler: çapraz hız, hareket izi, ürkütücü odak, flaş, neşeli ışınlar, kasvet (dikey), titreme, kızarma, girdap, şok halkaları.
   - Eklerken sıklık ×0,25–×3 arasında ayarlanır.
   - Eklenmiş çizgi grubu seçilince "Çizgi sıklığı" ile sıklık sonradan artırılıp azaltılır. Aynı türle yeniden üretilir; grup taşınmış ya da büyütülmüşse yeni yerinde. Tek geri alma adımıdır.
   - Odak ve ışın çizgileri artık karenin dışına taşmıyor.

## Araştırma kaynakları (çizgi roman)

- Balon türleri: [Blambot – Comic Book Grammar & Tradition](https://blambot.com/articles_grammar.shtml)
- Manga efekt çizgileri: [japanesewithanime – line effects](https://japanesewithanime.com/2020/03/line-effects.html), [shaking lines](https://www.japanesewithanime.com/2020/03/shaking-lines.html), [motion lines](https://www.japanesewithanime.com/2020/03/motion-lines.html)
- Hız/odak çizgileri: [CLIP STUDIO TIPS – Creating Focus Lines](https://tips.clip-studio.com/en-us/articles/4603), [CLIP STUDIO – Speed lines and Focus lines](https://help.clip-studio.com/en-us/manual_en/540_comic/Speed_lines_and_Focus_lines.htm)
- Görseller kopyalanmadı; biçimler ve çizgiler yeniden çizildi.

## Doğrulama

- **Tam koşu: 501 test.** Uygulama 311, core/content 159 (1'i atlandı: ağ eğitimi yalnız istenince çalışır), core/model 31.
  - İki süre testi (V9 yoğun sayfa karesi, iz çıkarma süresi) testler aynı anda koşarken işlemci yükünden eşiği aştı. Tek başına koşunca ikisi de geçti: kare 46,9 ms (sınır 60), iz 48 ms (sınır 150).
  - Diğer testlerin hepsi geçti.
- Lint: 0 hata. `assembleDebug` başarılı (minSdk 21, targetSdk 36).
- **Yeni testler:**
  - `V29TextFormatTest`:
    - Elemanlarda tek yazı biçimi bölümü ve dört sekme.
    - Kenarlık Renk sekmesinden.
    - Düzenleyicide "Yazı biçimi" ile büyük harf.
    - Yalnız değişen alanın uygulanması.
  - `V29TextDefaultsTest`:
    - Balon ve efekt yazısından sonra yeni yazı düz ve sola hizalı; balonda seçilen "Kalın" son ayar oluyor.
    - Bir tuvalde seçilen Marker yeni tuvale geçmiyor.
  - `V29ComicTest`:
    - Sıklık ×2 ile odak çizgisi; seçili grubun sıklığı ×0,5'e iniyor; tek geri alma adımı.
    - 16 balon biçimi çiziliyor.
    - Saydam editoryal temada zemin görünüyor.
  - `V29TransferTest`:
    - Resim tuvalinden yeni not tuvaline vektör, aynı tuvale resim aktarılıyor.
    - 3B çizgileri çizgi romana tek grup olarak gidiyor.
    - Not tuvalinde kayıtlı resimden ekleniyor.
  - `V29ModeAiTest`: çizgi roman kapsamında balon tanınıyor, ders şablonu çıkmıyor; kabul veritabanına ve yalnız çizgi roman ağına gidiyor.
  - `ModeShapesTest`:
    - Mod kapsamları ayrı; moda özel şablonlarda ilk-1 %100 (129 benzetim).
    - Kabul olasılığı artırıyor, ret azaltıyor; kayıt ve sıfırlama çalışıyor; resim modu etkilenmiyor.
  - `V29ContentTest`:
    - 14 çizgi türünün sayısı sıklıkla artıyor ve çizgiler alanın dışına çıkmıyor.
    - Taşınan grubun alanı doğru.
    - Küpün yalnız görünen 9 kenarı çıkıyor.
  - `Sketch3Test`: 3B ağı tercihi öğreniyor ve kayıtta korunuyor.
- Var olan yazı paneli testleri yeni sekmelere göre güncellendi (V10, V23, V24). Şablon sayısı testi yeni katalogla güncellendi (V21).

## Bilgisayara yazılanlar

- `android/` altında v27'ye göre 66 dosya (v28 ve v29 değişiklikleri; 19 yeni, 47 değişen; silinen yok). Değişen dosyaların eski halleri v27 ile birebir aynıydı.
- `v29-kaynak.tar.gz`: bu 66 dosya. `DrawGuidance-debug.apk`: v29 APK (Telefonda-Ac / Emulatorde-Ac bunu kurar).
- `v28-durum.md` ve bu not.

## Bilinmesi gerekenler

- Önceki sürümde kaydedilen son ayarlar (renk, fırça, yazı) genel olduğu için tuvallere aktarılmadı. Var olan tuvaller bir kez öntanımlı ayarlarla açılır; sonra her biri kendi ayarını hatırlar.
- Şekil veritabanı ilk açılışta yeni şablonlarla bir kez yeniden yazılır. Kullanıcı örnekleri ve sayaçlar korunur.
- Bir modun ağı o modda ilk öneride kurulur (arka planda, ~0,5 sn). Sonraki açılışlarda dosyadan okunur.
