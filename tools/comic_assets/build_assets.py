#!/usr/bin/env python3
"""v40 çizgi roman modunun hazır karakter ve arka plan varlıklarını üretir.

Girdi: sahibin verdiği karakter sayfaları (PNG/JPEG) ve arka plan resimleri (repoya konmaz).
Çıktı: android/app/src/main/assets/comic/ altında WebP resimler, küçük resimler ve catalog.json; isteğe bağlı olarak
karakter başına önce/sonra inceleme sayfaları.

Kullanım (projenin kökünden):
  python3 -I tools/comic_assets/build_assets.py --chars <karakter klasörü> [--chars <ikinci klasör>] \
      --backgrounds <arka plan klasörü> [--review <inceleme klasörü>] [--only P00,J01]

Gerekenler: numpy, opencv-python-headless, scipy, scikit-image, Pillow (WebP destekli).
Aşamalar: (1) her sayfa ayrılır (segment.py) ve parçalar önbelleğe yazılır (--cache), (2) parçalar karakterlere
toplanır, gruplanır, küçültülüp WebP yazılır, (3) catalog.json ve inceleme sayfaları.
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import sys
from multiprocessing import Pool

import numpy as np
import cv2
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import segment as sg  # noqa: E402
import manifest as mf  # noqa: E402
import fix_chars  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "android", "app", "src", "main", "assets", "comic")

CHAR_MAX = 448        # karakter resminin uzun kenarı
CHAR_Q = 74
THUMB = 160           # seçici küçük resmi
THUMB_Q = 70
# v40: 6 = en küçük dosya (yayın); --fast ile 2 (hızlı ön inceleme)
WEBP_METHOD = 6
BG_MAX = 1440         # arka planın uzun kenarı
BG_Q = 74
BG_THUMB = (256, 144)
GROUP_ORDER = ["ifade", "poz", "aksiyon", "gunluk"]


def find_source(key: str, dirs) -> str:
    name = mf.P[key]
    for d in dirs:
        p = os.path.join(d, name)
        if os.path.exists(p):
            return p
    raise FileNotFoundError(name)


def classify(parts, shape, default: str):
    """v40: Parçanın grubu. Tek türlü sayfada sayfanın türü; karışık sayfada geometri: uzun ve dar tam boy -> poz,
    en uzun parçanın yarısından kısa ve altı düz kesik -> ifade (büst), öteki -> aksiyon. Kendi arka planı olan
    dikdörtgen sahne kareleri atılır. Günlük (oturan, okuyan…) ve hatalar manifest'teki cls ile düzeltilir.
    parts: [(maske, (x0, y0, x1, y1))]"""
    H, W = shape
    info = []
    for m, (x0, y0, x1, y1) in parts:
        h, w = y1 - y0, x1 - x0
        sub = m[y0:y1, x0:x1]
        band = sub[-max(3, h // 40):]
        edges = [sub[:3].any(0).mean(), sub[-3:].any(0).mean(), sub[:, :3].any(1).mean(), sub[:, -3:].any(1).mean()]
        scene = sum(e > 0.8 for e in edges) >= 3 and sub.mean() > 0.75
        info.append((h, w, band.any(0).mean(), scene))
    if default != "mixed":
        return ["drop" if sc else default for (_, _, _, sc) in info]
    hmax = max([h for (h, _, _, sc) in info if not sc] or [H])
    out = []
    for h, w, bf, sc in info:
        if sc:
            out.append("drop")
        elif h / max(w, 1) >= 1.8 and h >= 0.6 * hmax:
            out.append("poz")
        elif h < 0.55 * hmax and bf > 0.3:
            out.append("ifade")
        else:
            out.append("aksiyon")
    return out


def regroup(key, meta, default):
    """v40: Önbellekteki parçaları (alfa) yeniden sınıflandırır, sonra manifest'in cls/drop düzeltmelerini uygular;
    böylece sınıflandırma ayarı yeniden ayırma gerektirmez."""
    o = mf.SHEET_OPTS.get(key, {})
    W, H = meta["size"]
    parts = []
    for p in meta["parts"]:
        a = np.array(Image.open(p["file"]).getchannel("A")) > 8
        x0, y0, x1, y1 = p["box"]
        m = np.zeros((H, W), bool)
        m[y0:y0 + a.shape[0], x0:x0 + a.shape[1]] = a[:H - y0, :W - x0]
        ys, xs = np.nonzero(m)
        box = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1) if len(xs) else (x0, y0, x1, y1)
        parts.append((m, box))
    groups = classify(parts, (H, W), default)
    for p, g in zip(meta["parts"], groups):
        i = p["i"]
        g = o.get("cls", {}).get(i, g)
        if i in o.get("drop", []) or o.get("skip"):
            g = "drop"
        p["group"] = g


def split_merged(img: Image.Image, typical: float, force: int = 0):
    """v40: Yan yana (ya da üst üste) yapışık kalmış büstleri ayırır. Parça, sayfadaki tipik büst en/boy oranının
    ~1,6 katından genişse n = yuvarla(oran) parçaya bölünür; kesim yeri beklenen noktanın ±%12'sinde alfa toplamının
    en az olduğu sütun/satırdır."""
    w, h = img.size
    horiz = w >= h
    ratio = (w / h) / typical if horiz else (h / w) * typical
    n = force or int(round(ratio))
    if (not force and ratio < 1.6) or n < 2:
        return [img]
    a = np.array(img.getchannel("A")).astype(np.float32)
    prof = a.sum(0 if horiz else 1)
    L = w if horiz else h
    cuts = [0]
    for k in range(1, n):
        c = k * L / n
        lo, hi = int(c - 0.12 * L / n), int(c + 0.12 * L / n)
        cuts.append(lo + int(np.argmin(prof[lo:hi])))
    cuts.append(L)
    out = []
    for a0, a1 in zip(cuts, cuts[1:]):
        piece = img.crop((a0, 0, a1, h) if horiz else (0, a0, w, a1))
        out.extend(split_merged(trim(piece), typical))  # hâlâ geniş kalan parça yeniden bölünür
    return out


def process_sheet(args):
    key, dirs, cache = args
    o = dict(mf.SHEET_OPTS.get(key, {}))
    path = find_source(key, dirs)
    im = sg.load(path)
    kind = o.get("kind") or sg.detect_kind(im)
    bg, dist, bgcol = sg.background_mask(im, kind, o)
    fg = sg.remove_lines(~bg) if o.get("lines") else ~bg
    fgc = sg.apply_cuts(fg, o.get("cuts", []), o.get("erase", []))
    parts = sg.split(fgc, o)
    rgb, alpha = sg.refine_alpha(im, fg, dist, bgcol)
    H, W = fg.shape
    # çok küçük parçalar (kopuk süs, gölge) atılır
    parts = [p for p in parts if (p[1][3] - p[1][1]) >= o.get("min_h", 0.12) * H]
    groups = classify(parts, (H, W), o.get("default", "mixed"))
    os.makedirs(os.path.join(cache, key), exist_ok=True)
    meta = []
    k = np.ones((3, 3), np.uint8)
    for i, ((m, (x0, y0, x1, y1)), g) in enumerate(zip(parts, groups)):
        g = o.get("cls", {}).get(i, g)
        if i in o.get("drop", []):
            g = "drop"
        pad = int(0.01 * max(H, W))
        X0, Y0, X1, Y1 = max(0, x0 - pad), max(0, y0 - pad), min(W, x1 + pad), min(H, y1 + pad)
        pm = cv2.dilate(m.astype(np.uint8), k, iterations=1)[Y0:Y1, X0:X1]
        a = (alpha[Y0:Y1, X0:X1].astype(np.float32) * pm).astype(np.uint8)
        rgba = np.dstack([rgb[Y0:Y1, X0:X1], a])
        fn = os.path.join(cache, key, f"{i:02d}.png")
        Image.fromarray(rgba, "RGBA").save(fn, compress_level=1)
        meta.append({"i": i, "group": g, "box": [int(X0), int(Y0), int(X1), int(Y1)], "file": fn})
    # önce/sonra için küçük kaynak
    s = 900 / max(H, W)
    Image.fromarray(cv2.resize(im, (int(W * s), int(H * s)), interpolation=cv2.INTER_AREA)).save(os.path.join(cache, key, "src.jpg"), quality=80)
    json.dump({"key": key, "kind": kind, "size": [W, H], "parts": meta}, open(os.path.join(cache, key, "meta.json"), "w"))
    return key, kind, len(meta)


def manga_tone(img: Image.Image) -> Image.Image:
    """v40: Renkli arka planı manga görünümüne çevirir: siyah-beyaz, dört ton (siyah, koyu tram, açık tram, beyaz),
    45° nokta tram ve mürekkep kenar çizgileri. Çıktı boyutunda çalışır (tram ölçeği sabit kalsın diye)."""
    a = np.asarray(img.convert("RGB")).astype(np.float32) / 255
    g8 = ((0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]) * 255).astype(np.uint8)
    g8 = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(g8)
    smooth = cv2.medianBlur(cv2.bilateralFilter(g8, 9, 50, 9), 5)
    g = smooth.astype(np.float32) / 255
    H, W = g.shape
    lv = np.digitize(g, [0.18, 0.42, 0.70])
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    p = 5.0
    dots = (np.cos((xx + yy) / p * np.pi) * np.cos((xx - yy) / p * np.pi) + 1) / 2
    out = np.full(g.shape, 0.99, np.float32)
    out = np.where(lv == 0, 0.06, out)
    out = np.where(lv == 1, np.where(dots > 0.35, 0.18, 0.62), out)
    out = np.where(lv == 2, np.where(dots > 0.78, 0.35, 0.93), out)
    e = cv2.Canny(cv2.GaussianBlur(g8, (3, 3), 0), 90, 200)
    out = np.where(e > 0, np.minimum(out, 0.08), out)
    out = cv2.GaussianBlur(out, (0, 0), 0.6)
    return Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8), "L").convert("RGB")


def save_webp(img: Image.Image, path: str, max_side: int, q: int, alpha: bool):
    w, h = img.size
    s = min(1.0, max_side / max(w, h))
    if s < 1:
        img = img.resize((max(1, round(w * s)), max(1, round(h * s))), Image.LANCZOS)
    if not alpha:
        img = img.convert("RGB")
    img.save(path, "WEBP", quality=q, method=WEBP_METHOD)
    return img.size


def trim(img: Image.Image) -> Image.Image:
    bbox = img.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox()
    if not bbox:
        return img
    x0, y0, x1, y1 = bbox
    p = 6
    return img.crop((max(0, x0 - p), max(0, y0 - p), min(img.width, x1 + p), min(img.height, y1 + p)))


def checker(size, cell=12):
    w, h = size
    yy, xx = np.mgrid[0:h, 0:w]
    c = np.where(((yy // cell + xx // cell) % 2)[..., None] == 0, 236, 205).astype(np.uint8)
    return Image.fromarray(np.repeat(c, 3, axis=2), "RGB")


def review_sheet(cid, title, sources, cuts, path):
    """Önce (kaynak sayfalar) ve sonra (kesilen parçalar, grupları ve sıraları) tek resimde."""
    from PIL import ImageDraw
    tile = 200
    cols = 8
    rows = (len(cuts) + cols - 1) // cols
    src_imgs = [Image.open(s) for s in sources]
    src_h = 300
    src_w = sum(int(i.width * src_h / i.height) for i in src_imgs) + 10 * len(src_imgs)
    W = max(cols * tile, src_w) + 20
    Hh = 40 + src_h + 30 + rows * (tile + 24) + 10
    sheet = Image.new("RGB", (W, Hh), "white")
    d = ImageDraw.Draw(sheet)
    d.text((10, 10), f"{title} ({cid}) - once: kaynak sayfalar / sonra: {len(cuts)} kesit", fill="black")
    x = 10
    for im in src_imgs:
        t = im.resize((int(im.width * src_h / im.height), src_h))
        sheet.paste(t, (x, 36))
        x += t.width + 10
    y0 = 40 + src_h + 20
    for n, (label, img) in enumerate(cuts):
        cx = 10 + (n % cols) * tile
        cy = y0 + (n // cols) * (tile + 24)
        t = img.copy()
        t.thumbnail((tile - 8, tile - 8))
        bg = checker((tile - 8, tile - 8))
        bg.paste(t, ((tile - 8 - t.width) // 2, (tile - 8 - t.height) // 2), t)
        sheet.paste(bg, (cx, cy))
        d.text((cx, cy + tile - 6), label, fill="black")
    sheet.save(path, quality=82)


def build(args):
    cache = args.cache
    os.makedirs(cache, exist_ok=True)
    only = set(args.only.split(",")) if args.only else None
    keys = [s for c in mf.CHARACTERS for s, _ in c[3]]
    todo = [k for k in keys if (only is None or k in only)]
    for k in todo:
        mf.SHEET_OPTS.setdefault(k, {})
    for c in mf.CHARACTERS:
        for s, d in c[3]:
            mf.SHEET_OPTS.setdefault(s, {})["default"] = d
    if not args.skip_segment and todo:
        with Pool(args.jobs) as pool:
            for r in pool.imap_unordered(process_sheet, [(k, args.chars, cache) for k in todo]):
                print("ayrıldı", *r, flush=True)
    if args.segment_only:
        return

    out = args.out
    if os.path.isdir(out):
        shutil.rmtree(out)
    cdir = os.path.join(out, "chars"); tdir = os.path.join(cdir, "thumbs")
    bdir = os.path.join(out, "backgrounds"); btdir = os.path.join(bdir, "thumbs")
    for d in (cdir, tdir, bdir, btdir):
        os.makedirs(d, exist_ok=True)
    catalog = {
        "version": 2,
        "themes": [{"id": t, "names": dict(zip(mf.LANGS, n))} for t, n in mf.THEMES],
        "characters": [],
        "bgCategories": [{"id": t, "names": dict(zip(mf.LANGS, n))} for t, n in mf.BG_CATEGORIES],
        "backgrounds": [],
    }
    stats = {"scenes_dropped": 0, "groups": {g: 0 for g in GROUP_ORDER}}
    for cid, style, theme, sheets in mf.CHARACTERS:
        variants = []
        cuts = []
        counters = {g: 0 for g in GROUP_ORDER}
        collected = {g: [] for g in GROUP_ORDER}
        sources = []
        for key, _ in sheets:
            meta = json.load(open(os.path.join(cache, key, "meta.json")))
            regroup(key, meta, mf.SHEET_OPTS.get(key, {}).get("default", "mixed"))
            if mf.SHEET_OPTS.get(key, {}).get("skip"):
                continue
            sources.append(os.path.join(cache, key, "src.jpg"))
            for p in meta["parts"]:
                if p["group"] == "drop":
                    stats["scenes_dropped"] += 1
                    continue
                collected[p["group"]].append((key, p))
        # v40: tipik büst oranı (yapışık büstleri bölmek için)
        asp = [b[2] / b[3] for b in ((0, 0, *Image.open(p["file"]).size) for _, p in collected["ifade"])]
        normal = [x for x in asp if 0.6 <= x <= 1.5]
        typical = float(np.median(normal)) if normal else 1.0
        for g in GROUP_ORDER:
            items = []
            for key, p in collected[g]:
                img = trim(Image.open(p["file"]))
                force = mf.SHEET_OPTS.get(key, {}).get("nsplit", {}).get(p["i"], 0)
                pieces = split_merged(img, typical, force) if g == "ifade" else [img]
                for j, piece in enumerate(pieces):
                    items.append((key, p, piece, "" if len(pieces) == 1 else f".{j}"))
            for key, p, img, sub in items:
                counters[g] += 1
                vid = f"{g}_{counters[g]:02d}"
                # v42 bozuk çeşitler atılır, ötekilerin halesi/kırıntıları giderilir (fix_chars.py; kimlikler korunur)
                if f"{cid}/{vid}" in fix_chars.DROP:
                    continue
                img = Image.fromarray(fix_chars.fix_image(np.asarray(img.convert("RGBA")), fix_chars.ERASE.get(f"{cid}/{vid}", ())), "RGBA")
                name = f"char_{cid}_{vid}.webp"
                w, h = save_webp(img, os.path.join(cdir, name), CHAR_MAX, CHAR_Q, True)
                save_webp(img, os.path.join(tdir, name), THUMB, THUMB_Q, True)
                variants.append({"id": vid, "group": g, "file": f"comic/chars/{name}", "thumb": f"comic/chars/thumbs/{name}", "w": w, "h": h})
                cuts.append((f"{key}#{p['i']}{sub} {vid}", img))
                stats["groups"][g] += 1
        nm = mf.LABELS[cid]
        # v42 tema paketleri: ana tema ve ek temalar (uygulama veritabanında tema-varlık bağlantı tablosuna yazılır)
        catalog["characters"].append({"id": cid, "style": style, "theme": theme, "themes": mf.char_themes(cid, theme), "names": nm, "variants": variants})
        if args.review:
            os.makedirs(args.review, exist_ok=True)
            review_sheet(cid, nm["tr"], sources, cuts, os.path.join(args.review, f"{cid}.jpg"))
        print("karakter", cid, {g: counters[g] for g in GROUP_ORDER}, flush=True)

    for src, bid, style, cat, *extra in mf.BACKGROUNDS:
        tone = "tone" in extra
        path = None
        for d in args.backgrounds:
            if os.path.exists(os.path.join(d, src)):
                path = os.path.join(d, src)
        if path is None:
            raise FileNotFoundError(src)
        img = Image.open(path).convert("RGB")
        if tone:  # v40 çiftin manga kopyası: önce çıktı boyutuna, sonra tram
            s0 = min(1.0, BG_MAX / max(img.size))
            img = manga_tone(img.resize((round(img.width * s0), round(img.height * s0)), Image.LANCZOS))
        name = f"bg_{bid}.webp"
        w, h = save_webp(img, os.path.join(bdir, name), BG_MAX, BG_Q, False)
        t = img.copy()
        # küçük resim: 16:9 orta kırpma
        tw, th = BG_THUMB
        s = max(tw / t.width, th / t.height)
        t = t.resize((round(t.width * s), round(t.height * s)), Image.LANCZOS)
        l, u = (t.width - tw) // 2, (t.height - th) // 2
        t.crop((l, u, l + tw, u + th)).save(os.path.join(btdir, name), "WEBP", quality=THUMB_Q, method=WEBP_METHOD)
        catalog["backgrounds"].append({
            "id": bid, "style": style, "category": cat, "themes": mf.bg_themes(bid), "names": dict(zip(mf.LANGS, mf.BGN[bid])),
            "file": f"comic/backgrounds/{name}", "thumb": f"comic/backgrounds/thumbs/{name}", "w": w, "h": h,
        })
    with open(os.path.join(out, "catalog.json"), "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=1)
    total = sum(os.path.getsize(os.path.join(dp, fn)) for dp, _, fns in os.walk(out) for fn in fns)
    print("özet", json.dumps({"characters": len(catalog["characters"]), "variants": stats["groups"],
                              "backgrounds": len(catalog["backgrounds"]), "dropped": stats["scenes_dropped"],
                              "bytes": total}, ensure_ascii=False))


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--chars", action="append", required=True)
    ap.add_argument("--backgrounds", action="append", required=True)
    ap.add_argument("--out", default=OUT)
    ap.add_argument("--cache", default=os.path.join(os.environ.get("TMPDIR", "/tmp"), "comic_assets_cache"))
    ap.add_argument("--review")
    ap.add_argument("--only")
    ap.add_argument("--jobs", type=int, default=4)
    ap.add_argument("--skip-segment", action="store_true")
    ap.add_argument("--segment-only", action="store_true")
    ap.add_argument("--fast", action="store_true")
    a = ap.parse_args()
    if a.fast:
        WEBP_METHOD = 2
    build(a)
