# DrawGuidance v13 — Genel kullanıcıya göre şekil düzenleme, bölge renkleri, yazı tipleri ve not listeleri, menüler, galeri, günlükler, çizim motoru

_2026-09-21 · Room v7 (değişmedi) · Kayıt biçimi ContentCodec v3 + yeni ek bayrak (yazının paragraf biçimi); eski kayıtlar aynen açılır_

## İstekler ve yapılanlar

1. **Boşluğa bırakılan ok yeni adım açar:** Diyagram düzenlemede bir adımın + tutamağından çekilen ok başka bir adıma bırakılırsa ona bağlanır; boşluğa bırakılırsa tam bırakılan yerde yeni adım açılır ve ok ona bağlanır (tek geri alma adımı). Adım diyagramın dışına bırakılırsa diyagram o tarafa büyür; diğer adımların yeri ve boyutu değişmez. Tutamağa dokunup ok eklerken diyagramın içinde boşluğa dokunmak da orada adım açar. Böyle yerleştirilen diyagramlarda "Adım ekle" artık tüm adımları yeniden dizmez; yeni adım odaktaki adımın yanına konur.
2. **Seçili elemanı silme:** Seç modunda eleman seçiliyken alt çubukta **Sil**, **Kopyala** ve seçimi temizleme düğmeleri var (şekil, diyagram, yazı, resim, çizim).
3. **Fare imleci mod menüsünde, Kırp üst menüde.**
4. **Şekil düzenleme (vektör araçları yerine):**
   - **Parça:** şeklin bir parçasına (kenar, yüz, çizgi, etiket) dokunarak seç; sürükleyerek taşı; üstte çıkan tutamaktan döndür (15°'ye yapışır). Şekil bölünmez, tek eleman kalır.
   - **Çizgi:** şekle düz çizgi ekle. **Eğri:** parmakla çiz, yumuşatılarak eklenir.
   - **Boya:** bölgenin içine dokun → o bölgenin dolgusu; çizgisine dokun → o çizginin rengi.
   - **Dönüştür:** şekli başka bir şekle çevir (yeri, boyutu ve biçimi korunur). **Ayır:** seçili parçaları ayrı bir eleman yap (seçim yoksa her parça ayrı eleman olur). **Sil:** seçili parçaları sil.
5. **Şekil eklenince Seç moduna geçilir:** yeni şekil seçili gelir; hemen düzenlenebilir, taşınabilir, silinebilir.
6. **Bölgelere ayrı renk:** şekilde bölge/parça başına dolgu ve çizgi rengi (Boya aracı); diyagramda adım başına dolgu/kenar/yazı rengi ve ok başına renk (odak panelindeki **Renk** düğmesi, hızlı renkler + renk seçici + "Varsayılan renge dön"). Elemanın tamamına Renk Ver / boya kutusu uygulanırsa parçaların ayrı renkleri o renge döner.
7. **Yazı tipleri:** 37 yazı tipi (11 sistem çeşidi + 26 uygulamayla gelen, Türkçe karakter destekli: Montserrat, Poppins, Nunito, Raleway, Quicksand, Comfortaa, Oswald, Merriweather, Lora, Playfair Display, Roboto Slab, Caveat, Kalam, Patrick Hand, Dancing Script, Pacifico, Great Vibes, Amatic SC, Bebas Neue, Lobster, Abril Fatface, Righteous, Press Start 2P, JetBrains Mono, Source Code Pro, Special Elite). Yazı tipi düğmesi seçili yazı tipini kendi harfleriyle gösterir; dokununca gruplu (Düz, Tırnaklı, El yazısı, Dekoratif, Sabit genişlik) liste açılır, her satır kendi yazı tipiyle ve Türkçe örnekle. Dosyalar Latin/Türkçe alt kümesine indirildi (~1,8 MB); lisanslar `assets/fonts/LICENSES.txt`.
8. **Not tuvalinde liste ve başlık:** Metin / Başlık 1-2-3 (büyük ve kalın) ve madde işaretli / numaralı / yapılacaklar listesi. Enter bir sonraki maddeyi açar, boş maddede Enter listeyi bitirir, numaralar kendiliğinden sıralanır, girinti düğmeleriyle alt seviye (•/–/·, 1./a./i.).
9. **Menüler:** üst menü gruplu: geri al · yinele | fırça · silgi · boya kutusu | ekle | kırp | elemanlar · ayarlar. Mod menüsünde (Çiz, İncele, Seç, Taşı, Döndür, Ölçekle, Genişlet) seçili modun adı simgesinin yanında yazar (yatay ekranda altında). Yatay telefonda alt çubuk tek satıra iner (tuvale yer kalır).
10. **Çizim motoru:** (a) görünüm önbelleği yalnızca ekranda görünen bölgeye değen çizimleri çizer — yakınlaştırılmışken yeniden çizim 2–3 kat hızlı, görüntü piksel piksel aynı; (b) bitmiş fırça izleri bir kez kaydedilip (Android Picture, Skia'nın C++ çizim kaydı) sonraki çizimlerde yalnızca oynatılır — tam sayfa yeniden çizim ~1,6–2 kat hızlı. C/C++ (NDK) eklenmedi: Android'in çizim motoru (Skia) zaten C++; ölçümlerde süre Kotlin hesabında değil rasterleştirmede, bu yüzden NDK ekleyip derlemeyi zorlaştırmak yerine iş azaltıldı.
11. **Geliştirici modu:** Ayarlar → Geliştirici modu açılınca **Günlükleri aç**: uygulamanın kendi logcat kayıtları (hata, uyarı, bilgi, ayrıntı, StrictMode, çökme), önem süzgeci ve sayılar, arama, yenile, kopyala, paylaş, temizle; son çökme kaydı en üstte.
12. **Resimlerim galerisi:** çizim uygulamalarının galerileri gibi basamaklı ızgara (her resim kendi oranında, yuvarlak köşeli kart, biçim rozeti, köşede silme), tarihe göre bölümler (Bugün, Dün, Bu hafta, Bu ay, Daha eski), sıralama (yeni/eski/ad/boyut), galeri ↔ liste görünümü, başlıkta resim sayısı ve toplam boyut.

## Doğrulama

- Android 138/138 test + `:core:model` 17/17 test + assembleDebug (APK ~21,7 MB; yazı tipleri ~1,8 MB).
- Yeni testler: `V13ModelTest` (core: yazı tipi/paragraf kodlama, parça ve diyagram renkleri, çerçeve büyütme, listeler), `V13ShapeEditTest`, `V13DiagramDropTest`, `V13TextListsTest`, `V13MenuTest`, `V13DeveloperLogsTest`, `V13RenderPerformanceTest` (görünmeyen çizimleri atlama ve kayıttan çizim: hız + aynı görüntü), `V13*ShotsTest` (ekran görüntüleri).
- Güncellenen testler: şekil ekleme sonrası Seç modu, yeni şekil araçları, yazı tipi listesi, İncele'nin mod menüsüne taşınması.

## PC'ye yazarken

- Stage: `outputs/v13src/android/` — bilgisayardaki v9'a göre birikimli 129 dosya (v10–v13; v12'ye göre 47 yeni dosya). Yeni: `app/src/main/assets/fonts/` (27 dosya), `diagnostics/AppLog.kt`, `data/settings/DeveloperSettingsStore.kt`, `ui/screens/logs/`, `ui/drawing/FontRegistry.kt`, `core/.../data/text/TextLists.kt`, `core/.../data/diagram/DiagramFrames.kt`.
- Bilgisayardan silinecek 4 dosya (v12'de core modülüne taşındı): `app/.../data/model/Stroke.kt`, `app/.../data/model/Canvas.kt`, `app/.../data/local/ContentCodec.kt`, `app/src/test/.../ContentCodecTest.kt`.
