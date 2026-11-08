# DrawGuidance v34 — durum

_2026-10-18 · Room v13 (dil ayarı tablosu) · minSdk 21 – targetSdk 36 · katalog 34000 · 7 dil_

## İstekler ve yapılanlar

1. **Düzenle üst menüsünde şekil araçları doğrudan.**
   - "Vektör düzenle" kalktı. Satırda doğrudan **Nokta düzenle**, **Çizgi düzenle**, **Alan boya** var.
   - Şekil seçiliyse araç hemen açılır. Seçim yoksa düğme seçili kalır; dokunulan şekil o araçla açılır.
   - 10 düğme (Katmanlar dahil) 411 dp telefonda sığar (kalabalık satırda düğmeler 34 dp).
2. **Ana sayfada giriş önerisi.**
   - İnternet var ve giriş yapılmamışsa en üstte estetik bir kart (Google "G" işareti, başlık, kısa metin, düğme). Düğme doğrudan Google hesap seçicisini açar (otomatik seçim kapalı). Sonra doğum tarihi sorulur.
   - Giriş yapılmışsa: adla selamlama ("İyi günler, Ahmet") ve sağ üstte gradyan halkalı profil fotoğrafı. Fotoğrafa dokununca Hesap ayarları açılır.
3. **Hesap ayarları.**
   - Google'dan alınan bilgiler (e-posta + doğrulandı rozeti, fotoğraf, hesap oluşturma tarihi, kısaltılmış kimlikler) yazı alanlarıyla aynı boy ve biçimde salt okunur kutularda.
   - "Hesabı değiştir" seçeneği ve kodu tamamen kaldırıldı.
   - **Hesabı sil:** alttan açılan pencerede "hesabımı silmek istiyorum" yazılmadan düğme etkinleşmez (büyük/küçük harf fark etmez; her dilde kendi cümlesi).
   - Silme sırası: Google ile yeniden doğrulama → bekleyen sunucu işi (USER_UPSERT) silinir → sunucuda `DELETE /api/v1/users/me` → Firebase hesabı silinir → cihazdaki kullanıcı silinir, oturum kapanır.
   - Sunucuya ulaşılamazsa hiçbir şey silinmez ("Sunucuya ulaşılamadı, hesap silinmedi"); eşitlenmemiş değişiklik yeniden kuyruğa girer. Yalnız Firebase silme başarısızsa yine de çıkış yapılır ve cihaz verisi silinir (kısmi ileti).
4. **Basit çizimde daha çok şekil.** Her basit türde 42 şekil, üç kategori:
   - Basit resim: Temel 18, Doğa ve gökyüzü 14, Canlılar ve eşyalar 10.
   - Basit not: Temel 13 + koordinat ekseni, Geometri 17, Cisimler ve ölçme 11.
5. **Ana sayfada açılan liste.** Basit/Gelişmiş çizim döşemelerine dokununca ayrı pencere yerine hemen altında animasyonlu düğme listesi açılır (aynı anda biri açık; tekrar dokununca kapanır).
   - 5.1 **Resmi düzenle** üst menüsünde "Vektörleştir" yerine seçenekleri doğrudan: **Otomatik**, **Kenarları**, **Çizgileri vektörleştir** (satır sığmazsa kayar).
   - 5.2 **Basit modlarda yapay zekâ ve çocuk içeriği.**
     - Çocuk modu kendi modu ve ağıyla çalışır (`ShapeMode.KIDS`, `mode_kids.bin`, 332 sınıf). Basit resimde ayrıntılı/teknik şablonlar elenir.
     - Basit türlerde öneri balonu 8 seçenek gösterir (önce 4). `SimpleSuggest` çizimi türün kendi kataloğuyla da karşılaştırır.
     - Popüler çocuk çizgi filmleri tür bakımından araştırıldı (`docs/v34-cizgi-film-arastirma.md`). **36 özgün** boyama şekli eklendi; tanınmış karakter, ad, logo ya da internetten izlenmiş çizim yok:
       - Çizgi film karakterleri (14),
       - Masal ve macera yerleri (11),
       - Sihirli nesneler (11).
     - Çocuk şekilleri 50 → 86, şablonlar 465 → 551, vektörler 290 → 326, mod ağları 3 → 4. Çocuk ağı 24.489 örnekle eğitildi (`KidsNetTraining`).
     - Sunucu: şablon ve vektörlerde `kinds` alanı; `GET /templates?kind=KIDS` (türe göre ETag); `kinds` dizini; tohum yeniden üretildi (2,6 MB).
6. **Çok dilli uygulama: Türkçe, İngilizce, Çince (basitleştirilmiş), Japonca, Rusça, Korece, Arapça.**
   - Bütün arayüz metinleri (1949 metin) 6 dile çevrildi: `res/values-en|zh|ja|ru|ko|ar/strings.xml`.
   - Koddaki Türkçe metinler (hazır şekil, formül, diyagram, 3B model, bilim çizimi, çocuk şekli adları ve iletiler; ~2.700) `resources/i18n/<dil>.tsv` tablolarıyla çalışma anında çevrilir. Arayüzdeki her `Text` bu tabloya bakar (`ui/i18n/LocalizedText.kt`); kullanıcının yazdığı adlar (tuval, klasör, resim) çevrilmez.
   - **Logo ve ad dile göre:** karşılama yazısı ve uygulama adı (ör. お絵かきガイド, 绘画向导, دليل الرسم). Arapçada logo aynalanır (kalem sağdan sola çizer). Arayüz sağdan sola dizilir; çizim ve 3B düzenleyiciler koordinatlar bozulmasın diye soldan sağa kalır. Adres, e-posta ve sayı alanları her dilde soldan sağa.
   - **İlk açılışta otomatik:** sistemin dili bu dillerden biriyse o; değilse sistemin (ya da SIM/şebeke) ülkesinin dili (ör. Almanca + Türkiye → Türkçe); ikisi de eşleşmezse İngilizce.
   - **Veritabanında:** `language_settings` tablosu (dil, otomatik mi, sistem dili/ülkesi). Açılışta okunabilsin diye ayar dosyasında aynası tutulur.
   - **Ayarlar → Dil:** yedi dil kendi adlarıyla. Seçince onay sorulur, uygulama yeni dille yeniden başlar (karşılama ekranı atlanır, kayıt kuyruğu önce boşaltılır).

## Doğrulama

- Yeni testler: `V34EditRowTest`, `V34HomeAccountTest`, `V34AccountDeleteTest`, `V34AiTest`, `V34LanguageDetectTest`, `V34I18nTablesTest`, `V34LanguageUiTest` (Ayarlar'dan İngilizce ve Arapça seçilir; veritabanı, metinler, sağdan sola denetlenir).
- Veritabanı göç testi v13'e güncellendi.
- Ekran görüntüleri: giriş kartı, selamlama, açılan liste, hesap ayarları, silme penceresi, Düzenle satırı, dil kartı (Türkçe, İngilizce, Arapça).

### Son doğrulama

- **Tam test takımı:** 590 test (uygulama 380, 4'ü atlanan; core:content 177; core:model 33).
  - İlk tam koşuda 13 uygulama testi düştü; hepsi düzeltildi ve yeniden koşuldu:
    - Boya kutusu ve Arka plan artık yalnız Çiz satırında: test yardımcıları Çiz'e geçiyor.
    - Veritabanı sürümü 13.
    - Kırpınca görünüm yeni kağıda sığdırıldığı için genişletme "Sağdan genişlet" düğmesiyle yapılıyor.
    - Ayarlar'da dil kartı sunucu kartının üstünde: düğmeler kaydırılarak tıklanıyor.
    - Diyagram bırakma toleransı 5 px.
    - Sunucu gerektiren 3 test (gerçek Spring Boot + MongoDB) geçti.
- **Sunucu testleri:** 29 test, hepsi geçti (Testcontainers).
- **Lint:** 0 hata. Her metnin 6 dilde karşılığı var; `%` içeren çeviriler `formatted="false"`.
- **APK:** ortak anahtarla imzalandı (SHA-1 `0e16de72…a017`). targetSdk 36. Çeviri tabloları ve `mode_kids.bin` APK'da.

## Dosyalar

- **Bilgisayara yazılanlar** (`C:\Users\ahmet\Documents\DrawGuidance`):
  - Projenin tamamı güncellendi: `android\`, `backend\` (Spring Boot), `scripts\`, `.bat` dosyaları, v30–v34 belgeleri, `YENI-SOHBET-DEVIR.md`, `DrawGuidance-v34-debug.apk` (ve `DrawGuidance-debug.apk`).
  - Eski Node.js sunucusunun 22 dosyası silinmedi, `_eski_v34\` klasörüne taşındı.
  - `CanvasDto.kt` olduğu gibi bırakıldı.
- **Dil:**
  - `i18n/I18n.kt`, `i18n/AppLanguage.kt` (algılama, `LanguageManager`).
  - `data/local/LanguageEntities.kt`, `data/settings/LanguageRepository.kt`.
  - `ui/i18n/LocalizedText.kt`, `ui/screens/settings/LanguageSettings.kt` (kart ve yeniden başlatma).
  - `res/values-*/strings.xml`, `resources/i18n/*.tsv`.
  - Araçlar: `android/tools/i18n/` (README).
- **Hesap:** `Auth.kt`, `AccountRepository.kt`, `AccountUi.kt`, `AccountViewModel.kt`, `NeuComponents.kt` (salt okunur alan).
- **Ana sayfa:** `HomeScreen.kt`, `CanvasKindUi.kt`.
- **Düzenleyici:** `CanvasEditScreen.kt`, `CanvasEditViewModel.kt`, `EditorToolbar.kt`, `EditSheets.kt`, `ToolHelp.kt`.
- **Şekiller ve öneriler:**
  - `ShapeCatalog.kt`, `ShapeRegistry.kt`, `SimpleCatalog.kt`, `SimpleSuggest.kt`.
  - `TemplateSuggester.kt`, `ShapeSuggester.kt`, `SelectionSuggester.kt`, `HandwritingController.kt`.
- **İçerik:** `KidsStories.kt`, `TemplatesKids.kt`, `ModeShapes.kt`, `Catalog.kt`, `mode_kids.bin`.
- **Sunucu:**
  - `CatalogController.java`, `CatalogService.java`, `CatalogSeeder.java`, `CatalogMeta.java`.
  - `catalog.json.gz`, `CatalogApiTest.java`.
