# DrawGuidance v11 — Dolgu rengi, düzenlemede pasif kalem/silgi, diyagram okları, hata düzeltmeleri

_2026-09-21 · Android 128/128 test (62 sınıf) + assembleDebug · DB değişmedi (Room v7), kayıt biçimi ContentCodec v3_

## İstekler

- **Renk Ver = dolgu rengi:** Elemanlar → Renk Ver ile verilen renk elemanın dolgusu olur: şekilde iç dolgu (dolgu alamayan açık şekilde, ör. doğru, çizgi rengi), diyagramda kutuların dolgusu, yazıda harflerin iç rengi, serbest çizimde mürekkep rengi. Pencere seçili elemanın dolgusuyla açılır.
- **Boya kutusu = aynı dolgu:** üst menüdeki boya kutusu seçili elemanın dolgusunu boyar (Elemanlar panelindeki "Dolgu rengi" aynı değeri gösterir). Artık dolgusu olmayan şekil, yazı ve çizim seçiliyken de elemanı boyar (önce yalnızca kapalı şekil/diyagram boyanıyor, diğerlerinde tuvalin arka planı boyanıyordu). Seçim yoksa tuvalin arka planı.
- **Kalem ve silgi pasif:** yalnızca Çiz ve Seç modlarında etkin; şekil/diyagram/yazı/kılavuz ekleme ve düzenleme, taşı/döndür/ölçekle, kırp/genişlet modlarında soluk ve basılmaz. "Bitti" ile etkinleşir. Fırça göstergesi yalnızca Çiz modunda.
- **Diyagrama ok ekleme:** diyagram düzenlemede alt çubukta "Adım ekle · Ok ekle · ✓". Ok ekle: önce okun başlayacağı, sonra gideceği adıma dokunulur (odakta adım varsa ondan başlar); yeni ok odaklanır, yazısı panelden verilir; aynı ok iki kez eklenmez. Oka odaklıyken "Adım ekle" yeni adımı okun arasına koyar; ortadaki adım silinince zincir kopmaz.

## Hata düzeltmeleri ve kullanım iyileştirmeleri

- Tuvale hemen geri dönülünce (kayıt ~0,4 sn gecikmeli) eski içerik yükleniyor, sonraki çizim bekleyen kaydın üstüne yazıp çizimleri kaybettirebiliyordu → açılışta bekleyen kayıt önce yazılır.
- Yazarken tuvalde başka yere dokununca ya da mod/sayfa değişince yazılan kayboluyordu → yazı kaydedilir. Eklenen yazı seçili gelir.
- Geri alma sayfayı da geri getirir (değişiklik başka sayfadaysa o sayfa açılır); geri almadan sonra diyagram odağı, ok ekleme, şekil bağlama noktası sıfırlanır, kırpma çerçevesi yeniden hesaplanır.
- Diyagramda tüm düzenlemeler tek geri alma adımında birleşiyordu → her ekleme/silme ayrı adım; yazı düzenlemesi ve renk/kaydırıcı sürüklemesi yalnızca 2,5 sn içinde ve aynı elemanda birleşir (farklı elemanlara verilen biçimler ayrı adım).
- Sayfa değişirken kırpma/düzenleme modlarından çıkılır, açık yazı kaydedilir; sayfa numarası kayıt kuyruğuyla yazılır; kayıtlı sayfa numarası aralık dışıysa düzeltilir.
- Küçük tuvalde (64 px altı) kırpma çerçevesi sürüklenince çökme; kırpılmış elemanın eleman dizininde çökme riski; silgilerin kırpılması → düzeltildi.
- Şekil ve diyagram çizimi arka planda (önbellek, dışa aktarma) ve ekranda aynı anda yapılınca paylaşılan boya nesnesi yüzünden yanlış renk/kalınlık çıkabiliyordu → iş parçacığı başına boya.
- Parçalanan şeklin dolgusu kaybolmuyor (kopukluk küçükse dolgu sürer, içine dokunmak seçer).
- 2 sn basılı tutma yalnızca çizim, seçim ve dönüşüm modlarında (şekil/diyagram düzenlemede, yazıda, perspektifte istemeden Seç'e geçmiyor).
- Parçala aracında parmak kayarsa da başlanan yerden parçalanır; resim verisi yazılamazsa uygulama çökmez; büyük seçimlerde seçim kutuları hızlı.

## Dosyalar

Bilgisayardaki v9'a göre birikimli 48 dosya stage'de (`outputs/v11src/`, v10 + v11). v11'de ayrıca değişen: `ShapeRenderer.kt`, `InlineTextEditor.kt`, yeni test `V11EditorTest.kt` (+ v10 dosyalarının güncel halleri).
