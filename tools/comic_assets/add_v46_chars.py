#!/usr/bin/env python3
"""v46 sahibin verdiği karakter sayfalarını (karakterler.zip: free/ ve premium/ klasörleri) mevcut kataloğa ekler.

Her sayfa aynı düzende: üst satırda 5 (ya da 6) ifade büstü, alt satırda üç tam boy figür (soldan: ayakta poz,
aksiyon, günlük/oturan). Zemin gerçek saydamlık (RGBA), düz beyaz ya da sahte damalı olabilir; RGB sayfalarda zemin
segment.py ile ayrılır. Parçalar satır/sütun düzenine göre bölünür (efektler ve sahne eşyaları yakın figüre katılır),
fix_chars.fix_image ile halesi temizlenir, build_assets ile aynı ölçülerde WebP ve küçük resim yazılır.

Kullanım (projenin kökünden):
  python3 -I tools/comic_assets/add_v46_chars.py --src <karakterler klasörü> [--review <klasör>]

Çıktı: android/app/src/main/assets/comic/chars/ (char_<kimlik>_<çeşit>.webp ve thumbs/), catalog.json'a karakterler
(varsa aynı kimlikli eski kayıt değiştirilir) ve backend taban paket listesine (base-comic) karakter öğeleri.
"""
from __future__ import annotations

import argparse
import json
import os
import sys

import numpy as np
import cv2
from PIL import Image
from scipy import ndimage as ndi

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import segment as sg  # noqa: E402
import fix_chars  # noqa: E402
import manifest as mf  # noqa: E402
from build_assets import save_webp, trim, CHAR_MAX, CHAR_Q, THUMB, THUMB_Q, review_sheet  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "android", "app", "src", "main", "assets")
BASE_PACKS = os.path.join(ROOT, "backend", "src", "main", "resources", "base-packages", "manifest.json")

# (kimlik, klasör/dosya, büst sayısı, temalar). Adlar manifest.LABELS_SRC'de (7 dil). Stil: manga (anime).
SHEETS = [
    # --- Premium ---
    ("elf_buyucu_erkek", "premium/2163cc17-0f4c-4349-9730-87825ddbe2f9.png", 5, ["fantastik"]),
    ("elf_buyucu_kadin", "premium/25985258-0759-4f00-a58c-c0d5add9f6f5.png", 5, ["fantastik"]),
    ("suikastci_kadin", "premium/55c3d06e-bba9-4633-991b-905bd9cf8bf0.png", 5, ["fantastik"]),
    ("kutsal_sovalye_kadin", "premium/608421fc-1ff1-4ab4-ac98-58d59a3b9996.png", 5, ["fantastik"]),
    ("orman_okcusu_erkek", "premium/703eeac9-9b1d-40c7-b8a4-f496eb011c54.png", 5, ["fantastik"]),
    ("suikastci_erkek", "premium/7dfcc408-5c08-480d-bdf8-3bc7efb7fcc3.png", 5, ["fantastik"]),
    ("olu_cagiran_erkek", "premium/7e932b33-ab7c-4bb4-a79f-77a46e4f15ac.png", 5, ["fantastik"]),
    ("kutsal_sovalye_erkek", "premium/9f81c4c5-11eb-44c2-ba4d-1f97b2384ff3.png", 5, ["fantastik"]),
    ("orman_okcusu_kadin", "premium/b24a7049-cd92-4106-8273-2b49d5232b31.png", 5, ["fantastik"]),
    ("olu_cagiran_kadin", "premium/c054267d-437d-4448-8288-7ad143dd2c8b.png", 5, ["fantastik"]),
    # --- Free ---
    ("simyaci_kadin", "free/00501d07-736e-4f6e-b9c5-9d009a3edb09.png", 5, ["fantastik", "steampunk"]),
    ("orman_ozani", "free/875b9ab4-7544-4cdb-99d3-dfe3b5a5ad2e.png", 6, ["fantastik"]),
    ("simyaci_erkek", "free/cf554535-f95e-47e5-a636-a239ea15783f.png", 5, ["fantastik", "steampunk"]),
]
PREMIUM = [c for c, f, _, _ in SHEETS if f.startswith("premium/")]
BOTTOM = ["poz", "aksiyon", "gunluk"]


def load_rgba(path: str):
    """Sayfa RGB ve alfa (0..255). Gerçek saydamlık yoksa zemin segment.py ile ayrılır."""
    src = Image.open(path)
    if src.mode == "RGBA":
        a = np.asarray(src)
        al = a[..., 3]
        if (al < 8).mean() > 0.2:  # gerçek saydam zemin
            return a[..., :3].copy(), al.copy()
    im = np.asarray(src.convert("RGB")).copy()
    kind = sg.detect_kind(im)
    # Bu sayfaların açık zemini hafif desenli (düz değil): renk payı ve pürüzlülük sınırı geniş tutulur
    opts = {"tol": 26, "std_mult": 5, "lodiff": 9} if kind == "flat" else {}
    bg, dist, bgcol = sg.background_mask(im, kind, opts)
    rgb, alpha = sg.refine_alpha(im, ~bg, dist, bgcol)
    return rgb, alpha


def row_split(al: np.ndarray) -> int:
    """Üst (büst) satırı ile alt satırın sınırı: %18–%45 boyunda en boş satır."""
    H = al.shape[0]
    prof = (al > 24).sum(1).astype(np.float32)
    lo, hi = int(0.18 * H), int(0.45 * H)
    sm = np.convolve(prof, np.ones(9) / 9, "same")
    return lo + int(np.argmin(sm[lo:hi]))


def busts(al: np.ndarray, n: int):
    """Büst satırında n parçanın sütun sınırları: eşit aralığın ±%25'inde en boş sütun."""
    W = al.shape[1]
    prof = (al > 24).sum(0).astype(np.float32)
    prof = np.convolve(prof, np.ones(5) / 5, "same")
    xs = np.nonzero(prof > 0)[0]
    x0, x1 = (int(xs.min()), int(xs.max()) + 1) if len(xs) else (0, W)
    cuts = [x0]
    step = (x1 - x0) / n
    for k in range(1, n):
        c = x0 + k * step
        lo, hi = int(c - 0.25 * step), int(c + 0.25 * step)
        cuts.append(lo + int(np.argmin(prof[lo:hi])))
    cuts.append(x1)
    return list(zip(cuts, cuts[1:]))


def figures(al: np.ndarray, k: int = 3):
    """Alt satırda k figürün maskeleri: büyük bileşenler soldan sağa k kümeye, küçük parçalar (efekt, eşya) en yakın
    büyük parçanın kümesine katılır."""
    solid = al > 24
    lab, n = ndi.label(solid, structure=np.ones((3, 3)))
    if n == 0:
        return []
    sizes = ndi.sum(np.ones_like(al, np.float32), lab, range(1, n + 1))
    order = np.argsort(-sizes)
    # en büyük k bileşen tohum; yapışık figürler tek bileşense sütun profiline göre bölünür
    seeds = [i + 1 for i in order[:k] if sizes[i] > 0.02 * sizes.sum()]
    W = al.shape[1]
    if len(seeds) < k:
        prof = solid.sum(0).astype(np.float32)
        prof = np.convolve(prof, np.ones(15) / 15, "same")
        xs = np.nonzero(prof > 0)[0]
        x0, x1 = xs.min(), xs.max()
        bounds = [x0]
        for j in range(1, k):
            c = x0 + j * (x1 - x0) / k
            lo, hi = int(c - 0.18 * (x1 - x0) / k), int(c + 0.18 * (x1 - x0) / k)
            bounds.append(lo + int(np.argmin(prof[lo:hi])))
        bounds.append(x1 + 1)
        out = []
        for a, b in zip(bounds, bounds[1:]):
            m = np.zeros_like(solid)
            m[:, a:b] = solid[:, a:b]
            out.append(m)
        return out
    cx = {s: np.nonzero(lab == s)[1].mean() for s in seeds}
    seeds.sort(key=lambda s: cx[s])
    seedmask = np.isin(lab, seeds)
    _, (iy, ix) = ndi.distance_transform_edt(~seedmask, return_indices=True)
    nearest = lab[iy, ix]
    masks = []
    for s in seeds:
        m = solid & (nearest == s)
        masks.append(m)
    return masks


def cut(rgb, al, mask, box):
    x0, y0, x1, y1 = box
    a = np.where(mask[y0:y1, x0:x1], al[y0:y1, x0:x1], 0).astype(np.uint8)
    img = Image.fromarray(np.dstack([rgb[y0:y1, x0:x1], a]), "RGBA")
    img = trim(img)
    return Image.fromarray(fix_chars.fix_image(np.asarray(img), ()), "RGBA")


def process(path: str, nbust: int):
    rgb, al = load_rgba(path)
    H, W = al.shape
    y = row_split(al)
    parts = []
    top = np.zeros((H, W), bool); top[:y] = True
    for x0, x1 in busts(al[:y], nbust):
        m = np.zeros((H, W), bool); m[:y, x0:x1] = True
        parts.append(("ifade", cut(rgb, al, m, (x0, 0, x1, y))))
    low = al.copy(); low[:y] = 0
    for g, m in zip(BOTTOM, figures(low)):
        ys, xs = np.nonzero(m)
        parts.append((g, cut(rgb, al, m, (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1))))
    return parts


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--review")
    args = ap.parse_args()
    cdir = os.path.join(ASSETS, "comic", "chars"); tdir = os.path.join(cdir, "thumbs")
    cat_path = os.path.join(ASSETS, "comic", "catalog.json")
    catalog = json.load(open(cat_path, encoding="utf-8"))
    themes_tr = {t["id"]: t["names"]["tr"] for t in catalog["themes"]}
    new_ids = {c for c, _, _, _ in SHEETS}
    catalog["characters"] = [c for c in catalog["characters"] if c["id"] not in new_ids]
    pack_items = []
    group_tr = {"ifade": "İfade", "poz": "Poz", "aksiyon": "Aksiyon", "gunluk": "Günlük"}
    for cid, rel, nbust, themes in SHEETS:
        parts = process(os.path.join(args.src, rel), nbust)
        counters = {}
        variants, cuts = [], []
        for g, img in parts:
            counters[g] = counters.get(g, 0) + 1
            vid = f"{g}_{counters[g]:02d}"
            name = f"char_{cid}_{vid}.webp"
            w, h = save_webp(img, os.path.join(cdir, name), CHAR_MAX, CHAR_Q, True)
            save_webp(img, os.path.join(tdir, name), THUMB, THUMB_Q, True)
            variants.append({"id": vid, "group": g, "file": f"comic/chars/{name}", "thumb": f"comic/chars/thumbs/{name}", "w": w, "h": h})
            cuts.append((f"{g} {counters[g]}", img))
            pack_items.append({"id": f"{cid}-{vid}", "window": "characters",
                               "title": f"{mf.LABELS_SRC[cid][0]} – {group_tr[g]} {counters[g]}",
                               "category": themes_tr[themes[0]], "file": f"comic/characters/{cid}/{vid}.webp"})
        names = dict(zip(mf.LANGS, mf.LABELS_SRC[cid]))
        catalog["characters"].append({"id": cid, "style": "manga", "theme": themes[0], "themes": themes, "names": names, "variants": variants})
        if args.review:
            os.makedirs(args.review, exist_ok=True)
            review_sheet(cid, names["tr"], [os.path.join(args.src, rel)], cuts, os.path.join(args.review, f"{cid}.jpg"))
        print("karakter", cid, counters, flush=True)
    with open(cat_path, "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=1)
    # Sunucunun taban paket listesi (base-comic): aynı kimlikli eski öğeler değiştirilir
    bp = json.load(open(BASE_PACKS, encoding="utf-8"))
    for p in bp["packages"]:
        if p["id"] == "base-comic":
            p["items"] = [i for i in p["items"] if not (i["window"] == "characters" and i["id"].split("-")[0] in new_ids)] + pack_items
    with open(BASE_PACKS, "w", encoding="utf-8") as f:
        json.dump(bp, f, ensure_ascii=False, separators=(", ", ": "))
    print("premium", PREMIUM)


if __name__ == "__main__":
    main()
