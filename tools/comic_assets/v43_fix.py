#!/usr/bin/env python3
"""v43 çizgi roman kataloğu düzeltmeleri (catalog.json ve resimler; tekrar çalıştırılabilir):

- Mekân, nesne ve efekt pencerelerinde "Meslekler" teması kaldırılır (karakterlerde kalır); o temadaki resimlerin
  zaten başka temaları vardır.
- Tekrar eden / bozuk resimler atılır (aynı topuz, aynı meşale, aynı tozluk; mavi çizgi kırıntılı duman; birebir aynı
  mekân).
- Yanlış kategorideki nesne/efektler taşınır (çit ve kalkanlar → sancak/kalkan/engel; kılıç kesikleri → atış ve
  kesik; kaya-kristal patlamaları → patlama); kategori adları içeriğe göre düzeltilir.
- "Manga" sekmesindeki renkli mekânlar: renkli karşılığı olanlar manga için gri tonlamaya çevrilir, olmayanlar çizgi
  roman sekmesine taşınır. Eksik/yanlış mekân adları düzeltilir.

props_build.py sonunda kendiliğinden çalışır; elle: python3 v43_fix.py <android/app/src/main/assets>
"""
import json
import os
import sys

from PIL import Image, ImageEnhance, ImageOps

LANGS = ["tr", "en", "ar", "ja", "ko", "ru", "zh"]

DROP = {
    "objects": {"it_armory_03", "it_armory_37", "it_light_24"},
    "effects": {"fx_c1_41"},
    "backgrounds": {"yikik_merdiven_m"},
}
RECAT = {
    "objects": {**{i: "sancak_engel" for i in ["it_medieval_16", "it_medieval_17", "it_armory_21", "it_armory_22", "it_armory_23", "it_armory_24"]},
                "it_treasure_18": "silah_zirh"},
    "effects": {**{f"fx_c5_{n}": "silah" for n in range(30, 38)},
                **{f"fx_m6_{n:02d}": "silah" for n in [7, 8, 9, 17, 18, 19, 23]},
                **{f"fx_c5_{n}": "patlama" for n in [25, 26, 27]}},
}
CATEGORY_NAMES = {
    ("objectCategories", "isik"): ["Işık ve ateş", "Lights & fire", "أضواء ونار", "照明・火", "조명·불", "Свет и огонь", "灯光与火"],
    ("effectCategories", "silah"): ["Atış ve kesik", "Gunfire & slashes", "إطلاق نار وضربات", "射撃・斬撃", "사격·베기", "Выстрелы и удары", "射击与斩击"],
}
# Renkli karşılığı olan "manga" mekânları gri tonlamaya; olmayanlar çizgi roman sekmesine
BG_TO_GRAY = ["orman_harabe_m", "orman_ussu_m", "ucurum_kale_m", "kutup_us_m", "tropik_sahil_m", "col_karakol_m"]
BG_TO_COMIC = ["vadi_karakol_m", "deniz_feneri_m", "kiyi_siginak_m"]
BG_NAMES = {
    "carsi": ["Çarşı sokağı", "Shopping street", "شارع التسوق", "商店街", "상점가 거리", "Торговая улица", "商业街"],
    "kopru_alti": ["Yağmurlu köprü altı", "Under the bridge in the rain", "تحت الجسر تحت المطر", "雨の橋の下", "비 오는 다리 아래", "Под мостом в дождь", "雨中桥下"],
    "kiyi_siginak_ton": ["Kıyı sığınağı", "Coastal bunker", "ملجأ ساحلي", "海岸のトーチカ", "해안 벙커", "Береговой бункер", "海岸掩体"],
    "deniz_feneri_m": ["Deniz feneri ve iskele", "Lighthouse and pier", "منارة ورصيف", "灯台と桟橋", "등대와 부두", "Маяк и пирс", "灯塔与码头"],
}


def gray(path):
    im = Image.open(path)
    if im.mode == "RGBA":
        rgb, a = im.convert("RGB"), im.getchannel("A")
    else:
        rgb, a = im.convert("RGB"), None
    g = ImageOps.autocontrast(ImageOps.grayscale(rgb), cutoff=1)
    g = ImageEnhance.Contrast(g).enhance(1.15).convert("RGB")
    if a is not None:
        g.putalpha(a)
    g.save(path, "WEBP", quality=82, method=6)


def apply(assets):
    cat_path = os.path.join(assets, "comic", "catalog.json")
    c = json.load(open(cat_path, encoding="utf-8"))
    changed = {"drop": 0, "recat": 0, "themes": 0, "gray": 0, "style": 0, "names": 0}
    for kind, ids in DROP.items():
        keep = []
        for x in c[kind]:
            if x["id"] in ids:
                for f in (x["file"], x["thumb"]):
                    p = os.path.join(assets, f)
                    if os.path.exists(p):
                        os.remove(p)
                changed["drop"] += 1
            else:
                keep.append(x)
        c[kind] = keep
    for kind, m in RECAT.items():
        for x in c[kind]:
            if x["id"] in m and x["category"] != m[x["id"]]:
                x["category"] = m[x["id"]]
                changed["recat"] += 1
    for kind in ("backgrounds", "effects", "objects"):
        for x in c[kind]:
            th = x.get("themes", [])
            if "meslek" in th:
                x["themes"] = [t for t in th if t != "meslek"] or ["gunluk"]
                changed["themes"] += 1
    for (key, cid), names in CATEGORY_NAMES.items():
        for cat in c[key]:
            if cat["id"] == cid and cat["names"].get("tr") != names[0]:
                cat["names"] = dict(zip(LANGS, names))
                changed["names"] += 1
    for b in c["backgrounds"]:
        if b["id"] in BG_TO_GRAY:
            # Yalnız henüz renkliyse (tekrar çalıştırmada yeniden işlenmez)
            im = Image.open(os.path.join(assets, b["thumb"])).convert("RGB").resize((32, 18))
            px = [im.getpixel((x, y)) for y in range(im.height) for x in range(im.width)]
            if sum(max(p) - min(p) for p in px) / len(px) > 8:
                gray(os.path.join(assets, b["file"]))
                gray(os.path.join(assets, b["thumb"]))
                changed["gray"] += 1
        if b["id"] in BG_TO_COMIC and b["style"] != "cizgi_roman":
            b["style"] = "cizgi_roman"
            changed["style"] += 1
        if b["id"] in BG_NAMES and b["names"].get("tr") != BG_NAMES[b["id"]][0]:
            b["names"] = dict(zip(LANGS, BG_NAMES[b["id"]]))
            changed["names"] += 1
    # Denetim: taşınan kimlikler gerçekten var
    all_ids = {x["id"] for k in ("objects", "effects") for x in c[k]}
    missing = [i for m in RECAT.values() for i in m if i not in all_ids]
    if missing:
        raise SystemExit(f"Kimlik bulunamadı: {missing}")
    # v43 katalog sürümü (tema paketi tohumu ve önbellekler yenilensin)
    c["version"] = max(int(c.get("version", 1)), 3)
    with open(cat_path, "w", encoding="utf-8") as f:
        json.dump(c, f, ensure_ascii=False, indent=1)
    print("v43_fix", json.dumps(changed))
    return changed


if __name__ == "__main__":
    apply(sys.argv[1] if len(sys.argv) > 1 else "android/app/src/main/assets")
