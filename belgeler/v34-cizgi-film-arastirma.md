# v34 — Çocuk modu için çizgi film araştırması

_2026-10-18 · "Çocuklar için çizim" kataloğuna eklenen 36 özgün boyama şeklinin dayanağı_

## Yöntem

Popüler çocuk çizgi filmlerine dair izleme ve sıralama haberleri tarandı. Amaç yalnızca **hangi türlerin, karakter tiplerinin, mekânların ve nesnelerin** çocuklarca sevildiğini çıkarmaktı. Hiçbir dizinin karakteri, adı, logosu ya da kendine özgü görünüşü kullanılmadı. Çizimler bu uygulamanın kendi sade boyama üslubuyla sıfırdan tasarlandı. İnternetten resim izlenmedi.

Bakılan kaynaklar (tür bilgisi için):

- Kidscreen: ABD'de okul öncesi yayın akışında en çok izlenen diziler.
- C21 Media: 2025'in ikinci yarısında Netflix çocuk izlenmeleri.
- Parrot Analytics: küresel çocuk içeriği talep sıralamaları.
- TRT Haber ve NTV: Türkiye'de çocuklara yönelik yeni çizgi filmler ve en çok izlenen animasyonlar.
- Trakya Üniversitesi: okul öncesi çocukların en çok izlediği çizgi filmlerin içerik analizi.

## Öne çıkan türler

| Tür | Çocuk dizilerinde sık görülen öğeler | Eklenen özgün çizimler |
|---|---|---|
| Konuşan hayvanlar, aile ve arkadaşlık | Sevimli büyük gözlü hayvanlar, orman evi, çiftlik | Kurbağa prens, Ağaç ev, Ahır |
| Masal ve prensesler | Şato, taç, sihirli değnek, peri, büyücü | Prenses, Peri, Büyücü, Şato, Taç, Sihirli değnek, Büyü kitabı, İksir şişesi |
| Macera ve korsanlar | Korsan gemisi, hazine, harita, pusula | Korsan, Korsan gemisi, Hazine sandığı, Hazine haritası, Pusula, Orman kulübesi |
| Süper kahramanlar ve kurtarma | Pelerin, maske, kahraman çocuk | Süper kahraman çocuk, Kalkan ve kılıç, Şövalye |
| Ejderhalar ve dinozorlar | Yavru ejderha, yumurtadan çıkan dinozor, mağara | Yavru ejderha, Yumurtadan çıkan dinozor, Ejderha mağarası |
| Uzay ve bilim | Uzaylı arkadaş, uzay istasyonu, teleskop | Sevimli uzaylı, Uzay istasyonu, Teleskop |
| Deniz altı | Deniz kızı, deniz feneri | Deniz kızı, Deniz feneri |
| Sevimli canavarlar ve hayaletler | Tek gözlü tüylü canavar, korkutmayan hayalet | Sevimli canavar, Sevimli hayalet |
| Hayal ve sihir | Tek boynuzlu at, uçan halı, sihirli lamba, sirk | Tek boynuzlu at, Uçan halı, Sihirli lamba, Sirk çadırı, İglo, Şeker ev |

## Katalog

Üç yeni kategori eklendi:

- **Çizgi film karakterleri:** 14 çizim.
- **Masal ve macera yerleri:** 11 çizim.
- **Sihirli nesneler:** 11 çizim.

Toplam çocuk şekli 50'den 86'ya çıktı. Her çizimde kalın kenar var ve parçalar kapalı, beyaz bölgelerden oluşuyor. Her bölge "Şekil boyama" ile tek dokunuşta boyanabiliyor; bu, `KidsShapesTest` testinde her bölge için ayrı ayrı denetleniyor. Ayrıntılar `core/content/.../library/kids/KidsStories.kt` dosyasında.
