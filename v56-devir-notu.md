# v56 devir notu (K3)

Tarih: 2026-11-21. Dal: `claude/project-thread-ypg1wj` (GitHub ahmetkar/DrawGuidance). Taban: `claude/v55`.

## Biten aşamalar
- K0: 0A ödüllü reklam düzeltmesi, 0 canlı yayın (ara-ver sahipleri, onay önce / şifre sonra, LIVE_INK tür 15).
- K1: aşama 1 (V11 içerik, `V11Flatten`, `V56Flags`), 2 (katman birleştirme motoru), 3 (seçim: sihirli değnek, seçim
  alanı/maske, kement dolgu, kırpma maskesi, grup içi seçim), 3A (sayfalar/tuvaller arası kes/kopyala/yapıştır),
  3B (canlı yayın fark paketi LIVE_DIFF tür 16, LIVE_RESYNC tür 17). Android + iOS. Ayrıntı: `v56-durum.md`.
- K2: aşama 4 (hızlı fırça seçici), 5 (gren, doku, resimden fırça, dokuyla doldur), 6 (Dönüştür paneli, yansıma,
  sıvılaştır), 6A (kaydır/eğ, referans noktası, her birini dönüştür, tekrarla/çoğalt, Nesne menüsü: hizala ve dağıt,
  girdap/büzüştür/şişir). Android + iOS. Sapmalar: `v56-durum.md` "Plandan sapmalar (K2)".
- K3: aşama 7 (renk ayarları HSL/Renk Dengesi/Eğriler, katman çoğalt/birleştir, ışık/gölge katmanı, parıltı fırçası),
  8 (simetri kılavuzu ve canlı ayna, Desen yap), 8A (gelişmiş görünüm, bağlı kopya, opaklık maskesi, canlı Tekrarla),
  9 (SVG, katmanlı PSD, DXF). Android + iOS. Sapmalar: `v56-durum.md` "Plandan sapmalar (K3)".
- Plan güncellemeleri: 9A ve 9B K8'e alındı (Ahmet: "9a ve 9b yi en sona al"). Ahmet: "son k7 ye kadar otamatik geç":
  K7 sonuna kadar duraklarda "devam" beklenmez; her durakta commit, zip, durum ve not yine yapılır. K8 onay ister.
- Plan güncellemeleri (önceki): e-posta/şifre girişi tamamen Firebase'de, sunucu değişmez; Xiaomi GetApps iptal.

## Sıradaki adım (K3A)
Aşama 9C (2E vektör): yol işlemleri ve şekil oluşturucu, düğüm türleri ve Bezier tutamakları, yol birleştir/ayır/
kapat/ters çevir/uzat, bıçak, konturu yola çevir, efektler (öteleme, zarf, kıvırma, pürüz, zikzak). Sonra K4 (aşama
10), K5 (11), K6 (12–13), K7 (14). K8: 9A, 9B, 15.

## Testler / derleme
- K3: V56ColorTest, V56Symmetry*, V56Vector*, V56Export* geçti; V42SaveTest 4/4, V40EditorUiTest 6/6.
- K2 sonunda V56 testlerinin hepsi + V45EditorUiTest + BrushDynamics + V33ToolHelp: 66/66 geçti. Tam takımın v55
  tabanından beri düşen testleri K1 notundaki gibi (V13GalleryShots, V23Model3DCanvas, V25CanvasAdd3D, V34I18nTables,
  V37KidsMascot, V39GatingUi, V40RulerUi, V40Security, V46NewCharacters; ağ isteyenler CanvasFlow, V33Backend,
  V40Android10).
- iOS: Swift testleri yazıldı; ortamda Swift derleyicisi yok. Duraklarda derleme/CI yok (Ahmet'in kararı).

## PC teslimi
- K3'te PC bağlı değildi: dosyalar doğrudan yazılmadı. `v56/K3/DrawGuidance-v56-K3-degisen.zip` K2'den bu yana
  değişen dosyaları içerir (PC'ye kopyalanıp eskileri `_eski_v5556-K3-oncesi\` altına alınmalı).
  `DrawGuidance-v56-K3-toplam.zip` v55 tabanından bu yana birikimli (K2 zip'i uygulanmadıysa bunu kullanın).
- Önceki yedekler: `_eski_v5556-K1-oncesi\`, `_eski_v5556-K2-oncesi\`.

## Açık konular
- AdMob onayı ve app-ads.txt kontrolü (Ahmet).
- `v56-durum.md` "Açık konular" bölümü.

## Yeni oturum
```
git clone https://github.com/ahmetkar/DrawGuidance && cd DrawGuidance && git checkout claude/project-thread-ypg1wj
```
İlk mesaj: "v56 planına K3A'dan devam et. Devir notu ve plan: v56-devir-notu.md, v56-plan-tek-surum.md."
