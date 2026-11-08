# DrawGuidance v10 — Seçim, kırpma, boya kutusu, yazı kenarlığı, kılavuz/perspektif, şekil ve diyagram düzenleme, kaydetme ayarları

_2026-09-21 · Android 122/122 test (57 sınıf) + assembleDebug · DB değişmedi (Room v7) · Kayıt biçimi ContentCodec v3_

## Yapılanlar

- **0 Seçim ortak:** Elemanlar panelinde seçilen eleman çizim alanında da seçili görünür (kesik kutu + ad), kırpma/genişletme dışında her modda.
- **0.1 Taşı/Döndür/Ölçekle:** seçim yokken bir elemanın üstüne dokunulunca ya da üstünden sürüklenince o eleman (grubuyla) seçilir; sürüklemede hemen taşınır.
- **0.2 Kırpma:** eleman seçiliyse kırpma o elemana uygulanır (başlık "Kırp: Seçili eleman"); dışarıda kalan kısmı gizlenir, silinmez (`Stroke.clip`, taşıma/döndürme/ölçeklemeyle birlikte döner). Seçim yoksa eskisi gibi tüm tuval.
- **0.3 Kalem/silgi seçilince** tüm seçimler kalkar.
- **0.4 Boya kutusu** üst araç satırında (silginin yanında). Arka planı olan (şekil/diyagram) eleman seçiliyse onun dolgusunu, değilse tuvalin arka planını boyar. Pencerede yalnızca "Renk seç" ve renk seçici; seçilen renk anında uygulanır, tek adımda geri alınır.
- **1 Yazı:** şekillerdeki gibi kutu/kenar eklenmez. **Dolgu** = harflerin iç rengi, **kenarlık** = harflerin çevresindeki çizginin rengi ve kalınlığı (Elemanlar → Yazı biçimi). Eski kutulu yazılar açılınca kenarlığa çevrilir.
- **2 Kılavuz ve perspektif ayrı:** Ekle → Kılavuz çizgileri / Perspektif çizgileri. Pencerede yalnızca tür; genel ayarlar (aralık/ufuk, görünürlük, Kaldır, Bitti) çizim alanının altındaki çubukta. Perspektifte kaçış noktaları tuvalde sürüklenir. İkisi birlikte kullanılabilir.
- **3 Şekil düzenleme:** eski araçlar kaldırıldı. **Nokta** (kenara dokun: nokta ekle, noktayı sürükle: taşı, "Noktaları çoğalt": tüm kenarlar ikiye bölünür), **Parçala** (dokunulan yerden kopar; şekil tek eleman kalır, kopukluk küçük bir boşluk ve kırmızı uçlarla şeklin üstünde görünür), **Bağla** (iki noktaya sırayla dokun ya da birinden diğerine sürükle: doğrudan bağlanır; iki kopuk uç birleşir/kapanır).
- **4 Diyagram düzenleme** pencerede değil çizim alanında: adıma/oka dokununca yanında küçük panel (yazı, adım ekle, ok ekle → hedef adıma dokun, kutu biçimi, sil). Alt çubukta "Adım ekle" ve "Bitti".
- **5 Kaydetme penceresi:** başlıktaki kaydet düğmesi ve tuval listesinde seçili tuvalin kaydet düğmesi önce ayarları sorar: dosya adı, PNG/JPEG (+JPEG kalitesi), çözünürlük 0,5×/1×/2×/4× (sonuç piksel boyutu gösterilir, en uzun kenar ≤ 4096), çok sayfalı tuvalde bu sayfa/tüm sayfalar (her sayfa ayrı resim `_s1…`), kılavuz çizgileri dahil, kaydedileceği yer.
- **6 Ayarlar → Resim kaydetme:** kaydedilecek dizin (Galeri albümü `Pictures/<albüm>` ya da Android klasör seçiciyle seçilen klasör, kalıcı izinle), varsayılan çözünürlük ve biçim.

## Dosyalar

45 dosya (38 değişen, 7 yeni). Yeni: `data/settings/ExportSettingsStore.kt`, `ui/components/ImageSaveOptionsDialog.kt`, `ui/screens/canvasedit/components/ColorPickSheet.kt`, testler `V10EditorTest.kt`, `V10ExportTest.kt`, `V10SaveSettingsTest.kt`, `V10ShotsTest.kt`. `app/build.gradle.kts`: test bağımlılığı espresso-core 3.5.0.

## Teknik notlar

- Kaydetme penceresi alttan açılan sayfa (`ModalBottomSheet`): AlertDialog içindeki metin kutusu Compose'da ölçüm döngüsüne giriyor (v8'deki yazı sayfasıyla aynı sorun).
- İnce bir çizgide eleman kırpması çökmesin diye kırpma çerçevesinin en küçük boyutu elemanın boyutuyla sınırlandı (testle bulunan hata).
- Diyagram adım paneli alttaki mod çubuğunun altında kalmaz (çubuğun yüksekliği izlenir, sığmazsa adımın üstünde açılır).
- Yazı kenarlığının ilk rengi #1E88E5 (beyaz kağıtta görünür).

## Durum

- Dosyalar bulut çalışma alanında **stage**'de (`outputs/v10src/`), kullanıcı söyleyince `C:\Users\ahmet\Documents\DrawGuidance` altına yazılacak. Yalnızca APK verildi (`DrawGuidance-v10-debug.apk`).
