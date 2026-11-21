# v56 devir notu (K4)

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
- K3A: aşama 9C (yol işlemleri ve şekil oluşturucu, Bezier tutamakları ve düğüm türü, yolu uzat/ayır/kapat/ters
  çevir, bıçak, konturu yola çevir, değiştirilebilir efektler, zarf). Android + iOS. Sapmalar: `v56-durum.md`
  "Plandan sapmalar (K3A)".
- K4: aşama 10 (tuvale 3B ekleme: kütüphane + GLB, açı seçimi, Açıyı değiştir; tuvalden 3B düzenleyiciye yol, eski 3B
  çizim çubuğu, GLB dışa aktarma kaldırıldı; 3B öneri ücretsiz; 3B Çizimlerim listesi). Android + iOS. Sapmalar:
  `v56-durum.md` "Plandan sapmalar (K4)".
- Plan güncellemeleri: 9A ve 9B K8'e alındı (Ahmet: "9a ve 9b yi en sona al"). Ahmet: "son k7 ye kadar otamatik geç":
  K7 sonuna kadar duraklarda "devam" beklenmez; her durakta commit, zip, durum ve not yine yapılır. K8 onay ister.
- Plan güncellemeleri (önceki): e-posta/şifre girişi tamamen Firebase'de, sunucu değişmez; Xiaomi GetApps iptal.

## Sıradaki adım (K5)
Aşama 11: 3B Çizim temeli (düzlemde çizim, iOS'ta Metal). Sonra K6 (12–13), K7 (14). K8: 9A, 9B, 15.

## Testler / derleme
- K4: V56Model3DTest 3/3, V23Model3DCanvas 1/1, V25CanvasAdd3D 1/1, V40DesignUi 4/4, V44Rules 9/9.
- K3A: V56PathOpsTest 4/4, V56PathOpsUiTest 1/1; V56*, *Codec*, *VectorEdit*, *ShapeEdit* 0 hata.
- K3: V56ColorTest, V56Symmetry*, V56Vector*, V56Export* geçti; V42SaveTest 4/4, V40EditorUiTest 6/6.
- K2 sonunda V56 testlerinin hepsi + V45EditorUiTest + BrushDynamics + V33ToolHelp: 66/66 geçti. Tam takımın v55
  tabanından beri düşen testleri K1 notundaki gibi (V13GalleryShots, V23Model3DCanvas, V25CanvasAdd3D, V34I18nTables,
  V37KidsMascot, V39GatingUi, V40RulerUi, V40Security, V46NewCharacters; ağ isteyenler CanvasFlow, V33Backend,
  V40Android10).
- iOS: Swift testleri yazıldı; ortamda Swift derleyicisi yok. Duraklarda derleme/CI yok (Ahmet'in kararı).

## PC teslimi
- K4'te PC bağlı değildi. `v56/K4/DrawGuidance-v56-K4-degisen.zip` K3A'dan bu yana değişen dosyaları içerir
  (eskileri `_eski_v55\v56-K4-oncesi\` altına alın; silinenler zip içindeki SILINEN.txt'de).
  `DrawGuidance-v56-K4-toplam.zip` v55 tabanından bu yana birikimli (önceki zip'ler uygulanmadıysa bunu kullanın).
- Önceki yedekler: `_eski_v55\v56-K1-oncesi\`, `_eski_v55\v56-K2-oncesi\`, K3 ve K3A zip'leri `v56/K3/`, `v56/K3A/`.

## Açık konular
- AdMob onayı ve app-ads.txt kontrolü (Ahmet).
- `v56-durum.md` "Açık konular" bölümü.

## Yeni oturum
```
git clone https://github.com/ahmetkar/DrawGuidance && cd DrawGuidance && git checkout claude/project-thread-ypg1wj
```
İlk mesaj: "v56 planına K5'ten devam et. Devir notu ve plan: v56-devir-notu.md, v56-plan-tek-surum.md."
