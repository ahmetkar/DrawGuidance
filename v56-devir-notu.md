# v56 devir notu (K0)

Tarih: 2026-11-20. Dal: `claude/project-thread-ypg1wj` (GitHub ahmetkar/DrawGuidance). Taban: `claude/v55`.

## Biten aşamalar
- 0A ödüllü reklam düzeltmesi (Android + iOS).
- 0 canlı yayın: ara-ver sahipleri, yayın onayı önce / şifre sonra, LIVE_INK (tür 15), alan tarafta yakınlaştırma ve
  "Yayıncı başka bir ekranda" ipucu (Android + iOS). Ayrıntı: `v56-durum.md`.
- Plan güncellemeleri: e-posta/şifre girişi tamamen Firebase'de, sunucu değişmez; Xiaomi GetApps iptal.

## Sıradaki adım (K1)
Aşama 1: `ContentCodec` V11 (Layer opacity/blend/alphaLock/clipToBelow, Stroke clip, LASSO_FILL, grain, quad/mesh/wave,
symmetry), `V56Flags`, paylaşım sürüm kontrolü ve düzleştirme. Sonra aşama 2 (katman birleştirme motoru), 3 (seçim
maskesi), 3A (sayfalar arası kes/kopyala/yapıştır), 3B (yayın fark paketi).

## Testler / derleme
- Android `V56LiveShareTest` 9/9 geçti, derleme hatasız.
- iOS CI #145 (run 44797508269): imzasız Release derleme ve .ipa **başarılı**; birim/arayüz testleri Ahmet'in
  isteğiyle iptal edildi. Ahmet'in kararı: duraklarda derleme/CI yok, bütün duraklar bitince derleme Ahmet'te.

## Açık konular
- AdMob onayı ve app-ads.txt kontrolü (Ahmet).
- `V56Flags` henüz yok (K1).

## Yeni oturum
```
git clone https://github.com/ahmetkar/DrawGuidance && cd DrawGuidance && git checkout claude/project-thread-ypg1wj
```
İlk mesaj: "v56 planına K1'den devam et. Devir notu ve plan: v56-devir-notu.md, v56-plan-tek-surum.md."
