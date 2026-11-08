# DrawGuidance v28 — durum

_2026-10-11 · Room v10 (şema değişmedi) · minSdk 21 – targetSdk 36_

## İstekler ve yapılanlar

1. **Menü ve düğmelerde siyah arka plan, yavaşlama.**
   - Siyahlığın nedeni: içe gömük (basılı) düğmelerin gölge maskesi kutunun dışını temizlemiyordu.
   - Gölgeler artık yalnız gölge içeren şablonlardan çiziliyor (dokuz parçalı görüntü). Siyah zemin kalmadı.
   - Şablonlar boyut ve renge göre önbellekte. Her karede yeniden bulanıklaştırma yapılmıyor, değişmeyen düğme yeniden çizilmiyor: menüler daha akıcı.
2. **Yazı kutusu düzenleme.**
   - Düzenlerken yazı tuvaldeki haliyle canlı görünür: yazı tipi, kalın/italik/altı çizili, boyut, renk, harf aralığı, büyük harf, gölge, kontur, gradyan, kutu arka planı, açı ve ölçek.
   - Yazı orijinal yerinde kalır. Yazı uzadıkça kutu sağa ve aşağı büyür, sol üst köşe kaymaz.
3. **Diyagramlar (diagram-design tarzı).**
   - **Araştırma notu:** GitHub'daki ekran görüntüsü klasörü ve görseller bu ortamdan açılamadı (site erişimi kısıtlı). Deponun metin belgelerindeki tür listesi ve stil rehberi esas alındı. Görseller kopyalanmadı, tarz yeniden çizildi.
   - **Editoryal tasarım dili bütün diyagramlarda:**
     - kağıt zemin, ince mürekkep çizgiler, gölge yok;
     - tek vurgu rengi (1–2 öğe) ve bağlantı mavisi;
     - kutular rolüne göre ayrışır: odak, depo, dış (kesik kenar), güvenlik, göbek (koyu), bitti, engelli, bekleyen, eklendi, kaldırıldı (üstü çizili), değişti;
     - yuvarlatılmış dirsekli oklar ve dolu ok uçları;
     - ok yazıları eşaralıklı, büyük harf ve kağıtla maskeli;
     - adım yazısı: başlık, istenirse "[ÜST ETİKET]" ilk satırı ve eşaralıklı alt satırlar.
   - **Temalar:** Editoryal (yeni diyagramların varsayılanı) ve Editoryal koyu. Eski temalar (Pastel, Canlı…) aynı çizgi diliyle kendi renklerinde.
   - **34 yeni tür, toplam 54.** Seçici dört grupta:
     - Yapı ve akış: kulvar, sıra (mesajlar), göbekli döngü, iç içe, katman yığını, dörtlü konum, Wardley haritası, kullanıcı yolculuğu, kanban, hikâye haritası.
     - Yazılım ve veri: varlık–ilişki, UML sınıf, veritabanı şeması, dağıtım, bağımlılık, BT mevcut durum, üst düzey yığın, madalyon katmanları, veri akışı, platform entegrasyonu, erişim matrisi, mimari farkı, patlatılmış görünüm, aksonometrik plan.
     - Nicel (grafik): sütun, şelale, çizgi, serpilme, radar, kutupsal, ağaç haritası, ısı haritası, Sankey, Gantt.
     - Ders ve sunum: eski türler.
   - **Nicel türlerde değerler adımın yazısında:** "Ocak 120", "Kadro +64", "Son =", "Tasarım 2-5", "A 12 30", "Hız 9 6". Yazı değişince grafik yeniden çizilir.
   - **Düzenleme:**
     - adımda çok satırlı yazı;
     - "Vurgula (odak)" yıldızı;
     - yeni adım yapıyı korur (yolculukta aşama, ısı haritasında hücre; kanbanda sütunun içine kart);
     - grafiklerde yeni adım değeriyle gelir ve oka bağlanmaz.
   - Diyagram türünün en-boy oranıyla yerleşir. Geniş diyagramda yazı ve noktalar bozulmaz.
   - **Eski türler de yenilendi:**
     - organizasyon, ağaç, kavram haritası, neden-sonuç: dirsekli oklar;
     - döngü: yay oklar;
     - zaman çizelgesi: eksenli;
     - durum diyagramı: geri ok kutuların altından dolaşır.
4. **Öneri kutusu sürüklenebilir.**
   - Tuvalde (şekil, parça, el yazısı önerileri) ve 3B düzenleyicide.
   - Soldaki tutamaktan sürüklenir, yeri hatırlanır. Çift dokunuş kutuyu otomatik yerine döndürür.

## Doğrulama

- **Tam koşu: 480 test, hepsi geçti.** Uygulama 174 sınıf / 300 test, core/content 149 (1'i atlandı: ağ eğitimi yalnız istenince çalışır), core/model 31.
- Lint: 0 hata. `assembleDebug` başarılı (minSdk 21, targetSdk 36).
- 54 türün editoryal açık, editoryal koyu ve pastel çizimleri tek tek görüntü olarak incelendi; dar kalan ok yazıları, kulvarların okları örtmesi ve geri ok çakışması düzeltildi.
- **Yeni testler:**
  - `V28DiagramTest` (9 test): 54 türün hazır örneği geçerli, değerler yazıdan okunuyor, her tür her temada çiziliyor, editoryal kağıt ve vurgu rengi doğru, geniş diyagramda oran korunuyor.
  - `V28DiagramUiTest`: gruplu seçici, editoryal varsayılan, yazı değişince çubuk grafik yeniden yerleşiyor, vurgu yıldızı, değerli yeni adım.
  - `V28TextEditTest`: düzenlerken canlı önizleme; kalın ve yazı tipi korunuyor; yazı uzayınca sol üst köşe yerinde.
  - `V28SuggestDragTest`: öneri kutusu sürükleniyor ve yeri kaydediliyor.
  - `V28NeuShotTest`: açık/koyu temada menüler; siyah köşe yok.
- Diyagram düzenleme testleri (v9–v13) değişmeden geçti.

## Sahnede tutulanlar (bilgisayara yazılmadı)

- `v28src/` altında v27'ye göre 22 dosya (7 yeni, 15 değişen; silinen yok) ve `v28-kaynak.tar.gz`.
- APK: `DrawGuidance-v28-debug.apk` (sohbette).

## Bilinmesi gerekenler

- Kayıtlı eski diyagramlar kendi temalarıyla açılır. Editoryal görünüm için: diyagramı seç → Tema → Editoryal.
- Diyagram verisine eklenen alanlar (rol, ok uç yazıları) isteğe bağlı. Eski sürüm bunları yok sayar, veri bozulmaz.
