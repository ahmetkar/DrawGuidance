# Çeviri görevi (DrawGuidance Android uygulaması)

DrawGuidance: Türkçe bir mobil çizim uygulaması (tuvaller, fırçalar, vektör şekiller, diyagramlar, formüller, 3B şekiller,
çizgi roman, not alma, çocuklar için boyama, yapay zekâ çizim önerileri, Google ile giriş, sunucu eşitleme).

## Girdi
`chunks/*.tsv` (10 dosya: res1-4 = Android arayüz metinleri, lit1-6 = koddaki içerik/arayüz metinleri).
Her satır: `kimlik<TAB>bağlam<TAB>Türkçe metin`.
- res dosyalarında bağlam: kaynak XML adı; kimlik (ör. `help_tool_region_fill`, `account_sign_in`) metnin nerede kullanıldığını anlatır.
- lit dosyalarında bağlam: Kotlin dosyası adı. Örn. `Templates*.kt` = hazır şekil adları ve kısa çizim tarifleri,
  `FormulaCatalog.kt` = matematik/fizik formül adları, `Catalog3D.kt`/`Samples.kt` = 3B model adları ve açıklamaları,
  `DiagramCatalog.kt` = örnek diyagram düğüm etiketleri (teknik), `Space/Physics/Biology/Chemistry/GeometryPlus.kt` =
  bilim çizimlerindeki etiketler, `KidsShapes.kt` = çocuk boyama şekilleri, `PaperCatalog.kt` = kağıt/desen adları,
  `Stroke.kt` = yazı tipi adları, `*ViewModel.kt`/`*Controller.kt`/`*Screen.kt` = arayüz iletileri.

## Çıktı
`out/<dil>/<aynı dosya adı>.tsv` — her satır: `kimlik<TAB>çeviri`. Girdideki her kimlik tam bir kez, başka sütun yok,
başlık satırı yok. Sıra girdiyle aynı olsun.

## Kurallar
1. Yer tutucuları aynen koru: `%1$s`, `%2$d`, `%d`, `%s`, `{0}` … (sayısı ve biçimi aynı kalmalı; sıraları dilin
   cümle yapısına göre yer değiştirebilir).
2. `\n` (ters bölü + n) satır sonu işaretidir; aynen yaz (gerçek satır sonu koyma). Çeviride SEKME karakteri olmasın.
3. Arayüz düğmeleri kısa kalsın (Türkçedekine yakın uzunluk). Metin dili doğal, uygulama arayüzüne uygun olsun.
4. Özel adlar, markalar ve teknik simgeler değişmez: Google, Firebase, Android, PDF, PNG, JPEG, SVG, GLB, glTF, API,
   JSON, ZIP, LaTeX, Roboto/Montserrat/Poppins gibi yazı tipi adları, "Draw Guidance"/"DrawGuidance", sürüm numaraları,
   birimler, matematik/kimya gösterimleri (ör. `y = tan(x)`, `Na⁺`, `H₂O`), emoji ve `·`, `→`, `…` gibi işaretler.
   Yazı tipi adlarında yalnız Türkçe kelime varsa çevir (ör. "Roboto İnce" → "Roboto Thin", "El Yazısı" → "Handwriting").
5. Diyagram örneklerindeki teknik etiketlerde (ör. `[YÖNETİLEN]\nRDS birincil\npostgres 15.4`) Türkçe kelimeleri çevir,
   ürün/teknoloji adlarını, kodları ve sayıları koru.
6. Terimleri dosyalar boyunca tutarlı kullan. Yardımcı sözlük (TR → EN): Tuval → Canvas, Tuvallerim → My canvases,
   Not alma → Note-taking, Çizgi roman → Comic, Katman → Layer, Fırça → Brush, Silgi → Eraser, Şekil → Shape,
   Seç → Select, Ekle → Add, Düzenle → Edit, Taşı → Move, Genişlet → Expand, İncele → Inspect, Öneri → Suggestion,
   Yapay zekâ → AI, 3B → 3D, Kılavuz → Guide, Diyagram → Diagram, Formül → Formula, Eşitleme → Sync, Sunucu → Server,
   Hesap → Account, Giriş yap → Sign in, Çıkış yap → Sign out, Sayfa → Page, Arka plan → Background, Dolgu → Fill,
   Alan boya → Fill area, Nokta → Point/Node, Çizgi → Line, Kırp → Crop, Döndür → Rotate, Ölçekle → Scale,
   Geri al → Undo, Yinele → Redo, Basit çizim → Simple drawing, Gelişmiş çizim → Advanced drawing.
7. Türkçe büyük/küçük harf düzenini hedef dilin doğal düzeniyle eşle (İngilizcede düğmelerde cümle düzeni: "Add shape").

## Yöntem
- Dosyaları Read ile oku, çeviriyi Write aracıyla `out/<dil>/` altına yaz. Bir dosya tek Write için büyükse parçalara
  ayırıp (`out/<dil>/parts/…`) sonra Bash `cat` ile birleştirebilirsin; son dosyalar `out/<dil>/res1.tsv` … `lit6.tsv`.
- Çeviriyi kendin yap (internet ya da makine çeviri aracı kullanma; betikle otomatik çeviri üretme).
- Bitince `python3 validate.py <dil>` çalıştır; "0 sorun" olana kadar düzelt.
- Son iletinde yalnız kısa özet ver (satır sayısı, doğrulama sonucu); çevirileri iletiye yapıştırma.
