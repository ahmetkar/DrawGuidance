# Çeviri araçları (v34)

Uygulama 7 dilde: tr (kaynak), en, zh, ja, ru, ko, ar.

- **Arayüz metinleri:** `app/src/main/res/values/*.xml` (Türkçe kaynak) → `values-<dil>/strings.xml` (bu araçlarla üretilir,
  elle düzenlemeyin). Lint `MissingTranslation` hatası verir: her yeni Türkçe metnin 6 dilde karşılığı olmalı.
- **Koddaki Türkçe metinler** (hazır şekil/formül/diyagram/3B adları, iletiler): `app/src/main/resources/i18n/<dil>.tsv`
  (`Türkçe<TAB>çeviri`). Çalışma anında `i18n/I18n.kt` → `I18n.tr()` / `I18n.trf("{0} sayfa", n)`; arayüzde
  `ui/i18n/LocalizedText.kt` içindeki `Text` her metni kendiliğinden çevirir (kullanıcı metinlerinde `translate = false`).

## Yeni metin eklenince

Çalışma klasörü olarak bu klasörde `work/` açın, betikleri oradan çalıştırın (`DG_ANDROID` verilmezse depo kökü kullanılır):

1. `python3 ../extract.py lits.json` — koddaki Türkçe metinler (`static`) ve `$` içeren kalıplar (`dyn`; arayüzde
   görünenleri `I18n.trf("… {0} …", x)` yapın).
2. `python3 ../build_src.py` → `src.json` (kaynak XML metinleri + kod metinleri).
3. Önceki `src.json` ile karşılaştırıp yalnız yeni/değişen satırları `chunks/*.tsv` içine yazın
   (`kimlik<TAB>bağlam<TAB>Türkçe`; `chunk.py` hepsini parçalara böler).
4. Çeviri: `BRIEF.md` kurallarıyla `out/<dil>/<parça>.tsv` (`kimlik<TAB>çeviri`); `python3 ../validate.py <dil>` 0 sorun.
5. `generate.py` çıktıları birleştirip `values-<dil>/strings.xml` ve `resources/i18n/<dil>.tsv` yazar (v34'teki çalışma
   klasörü düzenine göre yazıldı: `out/` = 1. dalga, `wave2/` = 2. dalga; yeni dalga için okuma listelerini güncelleyin).
   Not: kaynakta biçim belirteci olmayan, `%` içeren çeviriler `formatted="false"` alır.
6. `./gradlew :app:lintDebug` (MissingTranslation, StringFormat*) ve `V34I18nTablesTest`.

Daha basit yol: birkaç yeni metin için `values-<dil>/strings.xml` ve `i18n/<dil>.tsv` dosyalarına elle satır ekleyin
(XML'de `'` → `\'`, `"` → `\"`, `&` → `&amp;`).
