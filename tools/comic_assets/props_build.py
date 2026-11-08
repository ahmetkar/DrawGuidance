"""v42 efekt ve nesne varlıklarını üretir: sayfaları böler, yinelenenleri atar, küçültüp WebP yazar ve kataloğa ekler.

Kullanım:
  python3 props_build.py --src <sayfaların klasörü> --review <inceleme klasörü>          # yalnız inceleme resimleri
  python3 props_build.py --src <klasör> --out android/app/src/main/assets/comic          # efekt/nesne dosyaları + catalog
Katalog (catalog.json) önce build_assets.py ile üretilir; bu betik "effects", "objects", "effectCategories",
"objectCategories" ve yeni temaları ekler.
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import sys
from multiprocessing import Pool

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import props as pp  # noqa: E402
import props_manifest as pm  # noqa: E402
from props_review import review  # noqa: E402

FX_MAX, OBJ_MAX = 288, 256   # uzun kenar (kaynaktaki boyuttan büyütülmez; v42 APK boyutu için)
THUMB = 128
Q, THUMB_Q = 70, 64
DUP_BITS = 10                # fark özetinde bu kadar ya da daha az bit farkı: yinelenen


def opts_for(kind, mode, extra):
    o = dict(pm.DEFAULTS[mode])
    if kind == "object" and mode == "alpha":
        o.update({"lo": 0.25, "hi": 0.9, "seg": 0.5, "close": 3, "big_min": 600, "merge": 10, "glow": 3, "watershed": True})
    o.update({k: v for k, v in extra.items() if k not in ("rows", "drop", "cat", "keep")})
    return o


def find(src, key):
    m = glob.glob(os.path.join(src, key + ".*"))
    if not m:
        raise FileNotFoundError(key)
    return m[0]


def process(args):
    src, key, kind, mode, extra = args
    path = find(src, key)
    o = opts_for(kind, mode, extra)
    col, a = pp.sheet_alpha(path, mode, o)
    labels, boxes = pp.split_pieces(a, o)
    pieces = pp.extract(col, a, labels, boxes, o)
    # satır bantları (kategori/tema satır başına verilebilir)
    h = a.shape[0]
    centers = sorted(((p["box"][1] + p["box"][3]) / 2, p["i"]) for p in pieces)
    row, last, rows = -1, None, {}
    gap = o.get("row_gap", h * 0.06)
    for cy, i in centers:
        if last is None or cy - last > gap:
            row += 1
        rows[i] = row
        last = cy
    for p in pieces:
        p["row"] = rows[p["i"]]
    return key, path, pieces


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--review")
    ap.add_argument("--out")
    ap.add_argument("--only")
    ap.add_argument("--jobs", type=int, default=2)
    a = ap.parse_args()
    only = set(a.only.split(",")) if a.only else None
    sheets = [s for s in pm.SHEETS if only is None or s[0] in only]
    jobs = [(a.src, s[0], s[1], s[2], s[6]) for s in sheets]
    results = {}
    with Pool(a.jobs) as pool:
        for key, path, pieces in pool.imap_unordered(process, jobs):
            results[key] = (path, pieces)
            print("ayrıldı", key, len(pieces), flush=True)
    if a.review:
        os.makedirs(a.review, exist_ok=True)
        for s in sheets:
            path, pieces = results[s[0]]
            drop = set(s[6].get("drop", []))
            # satır numarasını göstermek için parça adına ekle
            for p in pieces:
                p["label"] = f"{p['i']} r{p['row']}"
            review(path, pieces, os.path.join(a.review, s[0] + ".jpg"), title=f"{s[0]} ({len(pieces)})", dropped=drop)
    if a.out:
        build(a.out, sheets, results)


def save_webp(img: Image.Image, path: str, max_side: int, q: int):
    s = min(1.0, max_side / max(img.size))
    if s < 1:
        img = img.resize((max(1, round(img.width * s)), max(1, round(img.height * s))), Image.LANCZOS)
    img.save(path, "WEBP", quality=q, method=6)
    return img.size


def build(out, sheets, results):
    fdir, odir = os.path.join(out, "effects"), os.path.join(out, "objects")
    for d in (fdir, odir, os.path.join(fdir, "thumbs"), os.path.join(odir, "thumbs")):
        os.makedirs(d, exist_ok=True)
        for f in glob.glob(os.path.join(d, "*.webp")):
            os.remove(f)
    cat_path = os.path.join(out, "catalog.json")
    catalog = json.load(open(cat_path, encoding="utf-8"))
    # yeni temalar (bir kez)
    known = {t["id"] for t in catalog["themes"]}
    for tid, names in pm.NEW_THEMES:
        if tid not in known:
            catalog["themes"].append({"id": tid, "names": dict(zip(pm.LANGS, names))})
    catalog["effectCategories"] = [{"id": c, "names": dict(zip(pm.LANGS, n))} for c, n in pm.EFFECT_CATEGORIES]
    catalog["objectCategories"] = [{"id": c, "names": dict(zip(pm.LANGS, n))} for c, n in pm.OBJECT_CATEGORIES]
    effects, objects = [], []
    seen = {"effect": [], "object": []}
    stats = {"dup": 0, "drop": 0, "small": 0}
    for key, kind, mode, styles, cat, themes, extra in sheets:
        path, pieces = results[key]
        drop = set(extra.get("drop", []))
        rows = extra.get("rows", {})
        cats = extra.get("cat", {})
        n = 0
        for p in pieces:
            img = p["img"]
            keep = extra.get("keep")
            if p["i"] in drop or (keep is not None and p["i"] not in keep):
                stats["drop"] += 1
                continue
            if max(img.size) < 40 or (np.asarray(img)[..., 3] > 30).sum() < 500:
                stats["small"] += 1
                continue
            h = pp.dhash(img)
            sig = pp.color_sig(img)
            dup = False
            for h2, s2 in seen[kind]:
                if pp.hamming(h, h2) <= DUP_BITS and np.abs(sig[:3] - s2[:3]).max() < 0.08 and abs(np.log(sig[3] / s2[3])) < 0.25:
                    dup = True
                    break
            if dup:
                stats["dup"] += 1
                continue
            seen[kind].append((h, sig))
            n += 1
            rcat, rthemes = rows.get(p["row"], (None, None))
            c = cats.get(p["i"]) or rcat or cat
            th = rthemes or themes
            pid = f"{key}_{n:02d}"
            d = fdir if kind == "effect" else odir
            sub = "effects" if kind == "effect" else "objects"
            name = pid + ".webp"
            w, hgt = save_webp(img, os.path.join(d, name), FX_MAX if kind == "effect" else OBJ_MAX, Q)
            save_webp(img, os.path.join(d, "thumbs", name), THUMB, THUMB_Q)
            entry = {"id": pid, "styles": styles, "category": c, "themes": th,
                     "file": f"comic/{sub}/{name}", "thumb": f"comic/{sub}/thumbs/{name}", "w": w, "h": hgt}
            (effects if kind == "effect" else objects).append(entry)
        print("sayfa", key, n, flush=True)
    catalog["effects"] = effects
    catalog["objects"] = objects
    with open(cat_path, "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=1)
    size = sum(os.path.getsize(os.path.join(dp, fn)) for dd in (fdir, odir) for dp, _, fns in os.walk(dd) for fn in fns)
    print("özet", json.dumps({"effects": len(effects), "objects": len(objects), **stats, "bytes": size}))
    # v43 katalog düzeltmeleri (Meslekler teması, tekrar edenler, kategoriler, manga mekânları, adlar)
    import v43_fix
    v43_fix.apply(os.path.dirname(out.rstrip("/")))


if __name__ == "__main__":
    main()
