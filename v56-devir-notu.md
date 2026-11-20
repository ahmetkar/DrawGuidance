# v56 devir notu (K1)

Tarih: 2026-11-20. Dal: `claude/project-thread-ypg1wj` (GitHub ahmetkar/DrawGuidance). Taban: `claude/v55`.

## Biten aşamalar
- K0: 0A ödüllü reklam düzeltmesi, 0 canlı yayın (ara-ver sahipleri, onay önce / şifre sonra, LIVE_INK tür 15).
- K1: aşama 1 (V11 içerik, `V11Flatten`, `V56Flags`), 2 (katman birleştirme motoru), 3 (seçim: sihirli değnek, seçim
  alanı/maske, kement dolgu, kırpma maskesi, grup içi seçim), 3A (sayfalar/tuvaller arası kes/kopyala/yapıştır),
  3B (canlı yayın fark paketi LIVE_DIFF tür 16, LIVE_RESYNC tür 17). Android + iOS. Ayrıntı: `v56-durum.md`.
- Plan güncellemeleri: e-posta/şifre girişi tamamen Firebase'de, sunucu değişmez; Xiaomi GetApps iptal.

## Sıradaki adım (K2)
Plan 2A ve 2E'deki K2 maddeleri: hızlı fırça seçici, renk butonu, damlalık + büyüteç, Dönüştür alt paneli (yansıt,
çevir, kaydır/eğ, serbest/perspektif/bozma, 9 noktalı referans, X/Y/G/Y/açı, her birini dönüştür), hizala ve dağıt
(üst/dikey orta/alt; kâğıda/seçime/ana nesneye göre). Yapıştırınca Dönüştür panelinin açılması da K2'de.

## Testler / derleme
- Android tam birim test takımı: çalışıyor (sonuç K1 raporunda). Derleme hatasız.
- iOS: Swift testleri yazıldı; ortamda Swift derleyicisi yok. Ahmet'in kararı: duraklarda derleme/CI yok, bütün
  duraklar bitince derleme Ahmet'te.

## PC teslimi
- Doğrudan yazılan dosyaların eskileri: `_eski_v55\v56-K1-oncesi\`.
- 7 klasörden derin Android dosyaları ve `.github\` için: `DrawGuidance-v56-K1-pc-elle-kopyala.zip` (v55 tabanından
  bu yana birikimli; K0'ın elle kopyalanacak zip'i de bunun içinde).

## Açık konular
- AdMob onayı ve app-ads.txt kontrolü (Ahmet).
- `v56-durum.md` "Açık konular" bölümü.

## Yeni oturum
```
git clone https://github.com/ahmetkar/DrawGuidance && cd DrawGuidance && git checkout claude/project-thread-ypg1wj
```
İlk mesaj: "v56 planına K2'den devam et. Devir notu ve plan: v56-devir-notu.md, v56-plan-tek-surum.md."
