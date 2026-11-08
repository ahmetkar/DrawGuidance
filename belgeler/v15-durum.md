# DrawGuidance v15 — Mod politikası, katmanlar, formül, doku/desen, grafik düzenleme, klasörler, PDF

_2026-09-27 · Room v8 (klasörler) · Kayıt biçimi ContentCodec v4 (katmanlar + formül); eski kayıtlar aynen açılır_

## Sistem olarak kurulanlar (özel durum yerine ortak yapı)

- **Mod oturumu politikası** (`EditMode.kind` → `ModeKind`): tüm geçişler tek tablodan yapılır.
  - **Temel** (Çiz, Seç, İncele) · **Ekleme** (şekil, diyagram, formül, doku/desen): girerken eski seçim kalkar; eklenen eleman seçili olarak **Taşı**'ya geçilir (Taşı'da sil/kopyala ve "Şekli/Diyagramı düzenle" hazır) · **Düzenleme** (şekil, diyagram, resim): düzenlenirken yalnız o eleman seçili, bitince **Seç** · **Araç** (taşı, döndür, ölçekle, kırp, yazı, kılavuz, kalem aracı): bitince eylemden önceki moda dönülür.
  - Dönüş yığını: düzenlemenin içinden açılan araç (resim düzenlerken Kırp / Kalem) bitince o düzenlemeye, düzenlenen eleman yeniden seçili olarak dönülür.
- **Katmanlar** (belge düzeyinde, tüm sayfalarda ortak): her elemanın katmanı var; yeni elemanlar etkin katmana eklenir, yeni katman eski katmanları etkilemez; gizle / kilitle / yeniden adlandır / yukarı-aşağı / seçileni taşı / sil (geri alınabilir). Gizli katman çizilmez ve dışa aktarılmaz; kilitli katmandakiler seçilemez, silinemez; gizli/kilitli katmana çizilmek istenirse uyarı. Başlıkta **Katmanlar** düğmesi, yan panelde **Katmanlar** sekmesi.
- **Silgi yalnızca çizimleri siler**: şekil, diyagram, yazı, formül ve resimler silinmez; silgi yalnız kendi katmanındaki, kendinden önce çizilmiş fırça izlerini siler (çizim önbelleği ve dışa aktarma aynı kuralla).
- **Mod çubuğu genişletme noktaları**: yeni modlar kendi düğme listesini (`ModeAction`) ve ek ayar satırını verir; Ekle (+) menüsü veri listesi (`AddMenuItem`); yan panel sekmeleri, başlıktaki düğmeler ve kısa yazı girişleri (`TextInputSheet`) ortak bileşen.
- **Yeni içerik modülü `:core:content`** (saf Kotlin, testli): vektör çizim dili, şekil kütüphanesi, doku/desen motoru, formül dizgi motoru, grafik motoru, el yazısı tanıma, resim vektörleştirme.

## İstekler ve yapılanlar

1. **Şekil düzenleme**: Çizgi ve Eğri eklemenin yanına **Nokta** aracı (noktayı sürükle, çizgiye dokunup nokta ekle, çift dokunup sil) ve **Özellik** penceresi: seçili parçalara (seçim yoksa tüm şekle) çizgi rengi, dolgu (var/yok + renk), çizgi biçimi (düz, kesik, noktalı, çizgisiz), kalınlık, opaklık. Verilen biçim sonraki çizgi/eğri eklemelerinde de kullanılır.
2–4. Mod politikası (yukarıda).
5. **Grafikler ve grafik eğrileri** şekil penceresinde ayrı **Grafik** bölümünde iki ayrı kategori: Grafikler (eksen, koordinat düzlemi, sayı doğrusu, birim çember, 3B eksen, sütun, çizgi, pasta, serpilme, histogram) ve Grafik eğrileri (formülü yazılabilen fonksiyon grafiği + 20 hazır eğri).
6. **Grafik aracı** (grafik düzenlenince açılır): **Desen −/+** (eğrinin periyodu/aralığı; sütun grafiğinde öğe sayısı), **Nokta −/+** (koordinat noktaları), Sayılar / Izgara / Altını boya / Önemli noktalar. Grafiğin üstündeki **sayılara, değerlere, eksen adlarına ve formüle dokunarak** yeni değer girilir. Yeniden kurulan grafik kaymaz, büyümez.
7. **Diyagramlar**: 12 yeni tür (Döngü, Zihin haritası, Piramit, Venn, Dört bölmeli tablo, Huni, Balık kılçığı, Kavram haritası, Sınıflandırma ağacı, Bil-İste-Öğren, Basamaklar, Neden-sonuç), 8 yeni kutu biçimi, **renk temaları** (Klasik, Pastel, Canlı, Okyanus, Orman, Gün batımı, Tebeşir, Sade; seçicide ve düzenlerken "Tema"). Seçicide gerçek çizimli önizleme; yazılar kutu biçimine sığar.
8. **El yazısı önerisi**: kalemle rakam/harf/işaret yazıp kısa bir süre durulunca üstünde öneri balonu; dokununca aynı yerde aynı renkte yazıya dönüşür (tek geri alma). Ayarlar'dan ve ⋮ menüsünden kapatılabilir.
9. **Ekle (+) menüsü**: son seçilen öğe etkin (vurgulu + işaretli) görünür; hatırlanır.
10. Silgi (yukarıda).
11. **Resim düzenleme** (resim seçiliyken "Resmi düzenle"): **Kırp**, **Vektörleştir** (renk sayısı, ayrıntı, yumuşatma, resmi koru; sonuç düzenlenebilir vektör şekil) ve **Kalem** (dokunarak köşe, sürükleyerek eğri noktası; ilk noktaya dokununca kapanır). Kalem aracı Ekle menüsünde de var.
12. **Tuval listesi**: bilgi satırında piksel boyutu + kayıt boyutu (çizim + resimler); **klasörler** (yeni klasör, aç, klasöre taşı, yeniden adlandır). Not tuvalinin tüm sayfaları resim olarak kaydedilince tuvalin adını taşıyan tek klasöre gider (Resimlerim'de klasör, dosyada alt klasör).
13. **Otomatik kayıt** seçilebilir (⋮ menüsü ve Ayarlar); kapalıyken başlıkta **Kaydet** (kaydedilmemiş değişiklik varsa vurgulu); çıkarken sorulur.
14. **Toplu silme**: Resimlerim'de resimler ve klasörler, Tuvallerim'de tuvaller ve klasörler uzun basınca seçilir, üst menüdeki çöp kutusuyla (içindekilerle) silinir; taşıma da üst menüden.
15. Katmanlar (yukarıda).
16. **İçi dolu şekil**: şekil penceresinde "İçi dolu ekle" + dolgu rengi (hatırlanır).
17. **Nokta izi** ve **3B fırça** (resim ve not tuvalinde).
18. **Hazır çizimler**: 54 ayrıntılı çizim (ev ve şehir, doğa, hayvanlar, taşıtlar, eşyalar, yiyecek).
19. **Doku ve desen** ayrı menü ve pencere: 35 motif (çimen, çalı, bulut, yaprak, çiçek, lale, çam, taş, dalga, yağmur, kar, tuğla, petek…); sürüklenen alan doldurulur, boy ve sıklık sonradan değişir.
20. **PDF**: not tuvalinde ⋮ → "PDF olarak kaydet" (tüm sayfalar, görünür katmanlar).
21. **Fen şekilleri**: fizik 26, kimya 18, biyoloji/tıp 18, uzay 16, geometri 18 (mercek, prizma, devre sembolleri, hücre, kalp, DNA, deney düzenekleri, güneş sistemi, açınımlar…).
22. **Formül** (not tuvalinde Ekle → Formül ekle): 148 formül (cebir, fonksiyonlar, trigonometri, matris ve vektör, türev ve integral, diferansiyel denklemler, olasılık, fizik, geometri). Formül **yazı olarak** dizilir (resim değil); bilinmeyen ve katsayılar vurgulu, dokununca düzenlenir; formülün tamamı da yazılabilir.
23. **Son renk ve yazı ayarları** tuvaller arasında hatırlanır (son fırça da tuval türüne göre); başlığın alt satırında boyut · renk · yazı tipi ve boyutu (· etkin katman) görünür, dokununca ayar paneli açılır.

## Doğrulama

- Android 157/157 test + `:core:model` 22/22 + `:core:content` 67/67 test + assembleDebug (APK ~22,9 MB).
- Yeni testler: `V15ModelTest` (katman/formül/desen/grafik kodlaması, eski kayıt, katman yardımcıları, silinebilirlik), `V15EditorTest` (mod politikası, silgi, katmanlar, formül, desen, otomatik kayıt, grafik aracı), `V15ListTest` (klasörler, toplu silme), `V15ShotsTest` (ekran görüntüleri).
- Güncellenen testler: eklemeden sonra Taşı, yazı bitince önceki mod, silginin nesneleri silmemesi, grafiklerin yeni bölümü, liste bilgi satırı, veritabanı v8.

## PC'ye yazarken

- Stage: `outputs/v15src/android/` — bilgisayardaki v9'a göre birikimli 237 dosya (v14'e göre 101 yeni dosya; yeni `core/content` modülü dahil, `settings.gradle.kts` ve `app/build.gradle.kts` güncel).
- Bilgisayardan silinecek 4 dosya (v12'de core modülüne taşındı): `app/.../data/model/Stroke.kt`, `app/.../data/model/Canvas.kt`, `app/.../data/local/ContentCodec.kt`, `app/src/test/.../ContentCodecTest.kt`.
