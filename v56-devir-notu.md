# v56 devir notu (K2)

Tarih: 2026-11-21. Dal: `claude/project-thread-ypg1wj` (GitHub ahmetkar/DrawGuidance). Taban: `claude/v55`.

## Biten aşamalar
- K0: 0A ödüllü reklam düzeltmesi, 0 canlı yayın (ara-ver sahipleri, onay önce / şifre sonra, LIVE_INK tür 15).
- K1: aşama 1 (V11 içerik, `V11Flatten`, `V56Flags`), 2 (katman birleştirme motoru), 3 (seçim: sihirli değnek, seçim
  alanı/maske, kement dolgu, kırpma maskesi, grup içi seçim), 3A (sayfalar/tuvaller arası kes/kopyala/yapıştır),
  3B (canlı yayın fark paketi LIVE_DIFF tür 16, LIVE_RESYNC tür 17). Android + iOS. Ayrıntı: `v56-durum.md`.
- K2: aşama 4 (hızlı fırça seçici), 5 (gren, doku, resimden fırça, dokuyla doldur), 6 (Dönüştür paneli, yansıma,
  sıvılaştır), 6A (kaydır/eğ, referans noktası, her birini dönüştür, tekrarla/çoğalt, Nesne menüsü: hizala ve dağıt,
  girdap/büzüştür/şişir). Android + iOS. Sapmalar: `v56-durum.md` "Plandan sapmalar (K2)".
- Plan güncellemeleri: e-posta/şifre girişi tamamen Firebase'de, sunucu değişmez; Xiaomi GetApps iptal.

## Sıradaki adım (K3)
Aşama 7, 8, 8A, 9, 9A, 9B: renk ayarları (HSL, Renk Dengesi, Eğriler), simetri (DocExtras bağlantısı), katman
çoğalt/birleştir, SVG/PSD/DXF dışa aktarma, e-posta/şifre girişi (Firebase), mağaza flavor'ları, sunucu 56.0.0; 2E'nin
K3 maddeleri (Özellikler penceresi, opaklık maskesi, Nesne › Tekrarla).

## Testler / derleme
- K2 sonunda V56 testlerinin hepsi + V45EditorUiTest + BrushDynamics + V33ToolHelp: 66/66 geçti. Tam takımın v55
  tabanından beri düşen testleri K1 notundaki gibi (V13GalleryShots, V23Model3DCanvas, V25CanvasAdd3D, V34I18nTables,
  V37KidsMascot, V39GatingUi, V40RulerUi, V40Security, V46NewCharacters; ağ isteyenler CanvasFlow, V33Backend,
  V40Android10).
- iOS: Swift testleri yazıldı; ortamda Swift derleyicisi yok. Duraklarda derleme/CI yok (Ahmet'in kararı).

## PC teslimi
- K2'de PC bağlı değildi: dosyalar doğrudan yazılmadı. `v56/K2/DrawGuidance-v56-K2-degisen.zip` K1'den bu yana
  değişen dosyaları içerir (PC'ye kopyalanıp eskileri `_eski_v55\v56-K2-oncesi\` altına alınmalı).
  `DrawGuidance-v56-K2-toplam.zip` v55 tabanından bu yana birikimli.
- K1 yedekleri: `_eski_v55\v56-K1-oncesi\`.

## Açık konular
- AdMob onayı ve app-ads.txt kontrolü (Ahmet).
- `v56-durum.md` "Açık konular" bölümü.

## Yeni oturum
```
git clone https://github.com/ahmetkar/DrawGuidance && cd DrawGuidance && git checkout claude/project-thread-ypg1wj
```
İlk mesaj: "v56 planına K3'ten devam et. Devir notu ve plan: v56-devir-notu.md, v56-plan-tek-surum.md."
