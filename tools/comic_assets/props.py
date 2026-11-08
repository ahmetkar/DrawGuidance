"""v42 çizgi roman efekt ve nesne sayfalarını tek tek resimlere böler.

Sayfa türleri:
  alpha : gerçek saydamlığı olan PNG (yumuşak parıltılı efektler); düşük alfalı pus [lo] altı atılır
  black : siyah zeminli JPEG/PNG (ateş, patlama, namlu alevi); "renkten alfa" yöntemiyle saydamlaştırılır
  light : açık (beyaz/gri ya da sahte damalı) zeminli nesne sayfaları; segment.py'nin zemin maskesiyle

Her parça: kutusu, yumuşak alfası ve sayfa içindeki sırası. Komşu parçaların parıltısı, her pikselin en yakın
parçaya atanmasıyla (Voronoi) ayrılır.
"""
from __future__ import annotations

import numpy as np
import cv2
from PIL import Image
from scipy import ndimage as ndi

import segment as sg


def load_rgba(path: str) -> np.ndarray:
    im = Image.open(path)
    if im.mode != "RGBA":
        im = im.convert("RGBA")
    return np.asarray(im).astype(np.float32) / 255.0


def color_to_alpha_black(rgb: np.ndarray, floor: float = 0.05, gamma: float = 0.85):
    """Siyah zemin: alfa en parlak kanaldan; renk ön çarpımdan arındırılır."""
    m = rgb.max(2)
    a = np.clip((m - floor) / (1 - floor), 0, 1) ** gamma
    col = np.clip(rgb / np.maximum(a[..., None], 1e-3), 0, 1)
    return col, a


def sheet_alpha(path: str, mode: str, opts: dict):
    """(renk [0..1], alfa [0..1]) döner."""
    rgba = load_rgba(path)
    rgb, a0 = rgba[..., :3], rgba[..., 3]
    if mode == "alpha":
        lo, hi = opts.get("lo", 0.06), opts.get("hi", 0.85)
        a = np.clip((a0 - lo) / (hi - lo), 0, 1)
        return rgb, a
    if mode == "black":
        col, a = color_to_alpha_black(rgb, opts.get("floor", 0.05), opts.get("gamma", 0.85))
        if opts.get("solid"):
            # koyu duman ve kayalar saydamlaşmasın: parlak bölgelerin içi (delikleri doldurulmuş) opak
            lum = rgb.max(2)
            fg = lum > opts.get("solid_t", 0.07)
            fg = cv2.morphologyEx(fg.astype(np.uint8), cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9))).astype(bool)
            # yalnız küçük delikler doldurulur (halkanın ya da kubbenin büyük karanlık içi saydam kalır)
            filled = ndi.binary_fill_holes(fg)
            holes, nh = ndi.label(filled & ~fg)
            if nh:
                sizes = ndi.sum(np.ones_like(lum), holes, range(1, nh + 1))
                small = np.zeros(nh + 1, bool)
                small[1:] = sizes < opts.get("hole_max", 2500)
                fg = fg | small[holes]
            inner = cv2.erode(fg.astype(np.uint8), cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (13, 13)))
            inner = cv2.GaussianBlur(inner.astype(np.float32), (0, 0), 3)
            # yalnız yeterince büyük iç bölgeler (tek kıvılcımlar renkten alfayla kalır)
            a2 = np.maximum(a, inner)
            w = (inner > 0.01)[..., None]
            col = np.where(w, np.clip(rgb / np.maximum(a2[..., None], 1e-3), 0, 1) * (1 - inner[..., None]) + rgb * inner[..., None], col)
            a = a2
        return col, a
    if mode == "light":
        im8 = (rgb * 255).astype(np.uint8)
        kind = opts.get("kind") or sg.detect_kind(im8)
        bg, dist, bgcol = sg.background_mask(im8, kind, opts)
        fg = ~bg
        # küçük kırıntılar
        n, lab, st, _ = cv2.connectedComponentsWithStats(fg.astype(np.uint8), connectivity=8)
        small = np.zeros(n, bool)
        small[1:] = st[1:, cv2.CC_STAT_AREA] < opts.get("speck", 40)
        fg &= ~small[lab]
        col, alpha = sg.refine_alpha(im8, fg, dist, bgcol, soft=opts.get("soft", (5.0, 34.0)))
        return col.astype(np.float32) / 255.0, alpha.astype(np.float32) / 255.0
    raise ValueError(mode)


def split_pieces(a: np.ndarray, opts: dict):
    """Alfadan parçalar: eşik + kapatma ile bileşenler; küçükler en yakın büyüğe katılır ya da atılır.
    Döner: etiket haritası (0 zemin) ve parça kutuları [(etiket, x0, y0, x1, y1)]."""
    h, w = a.shape
    t = opts.get("seg", 0.3)
    if opts.get("grid"):
        # düzenli ızgara: her hücre bir parça (hücredeki içerik)
        cols, rows_n = opts["grid"]
        out = np.zeros((h, w), np.int32)
        boxes = []
        k = 0
        fg = a > t
        for r in range(rows_n):
            for c in range(cols):
                y0, y1 = round(r * h / rows_n), round((r + 1) * h / rows_n)
                x0, x1 = round(c * w / cols), round((c + 1) * w / cols)
                if fg[y0:y1, x0:x1].sum() < opts.get("big_min", 600):
                    continue
                # hücrenin soluk parıltısı da parçaya ait (hücre sınırında kesilir)
                cell = a[y0:y1, x0:x1] > 0.02
                k += 1
                out[y0:y1, x0:x1][cell] = k
                ys, xs = np.nonzero(cell)
                boxes.append((k, x0 + xs.min(), y0 + ys.min(), x0 + xs.max() + 1, y0 + ys.max() + 1))
        return out, boxes
    if opts.get("watershed"):
        # dokunan nesneler: mesafe haritasında su bölümü (segment.split)
        fg = a > t
        parts = sg.split(fg, {"watershed": True, "erode": opts.get("erode", 0.015), "seed_min": opts.get("seed_min", 0.0008),
                              "big_min": opts.get("big_min_frac", 0.0018), "drop_min": 0.00003, "merge": opts.get("merge_frac", 0.006)})
        out = np.zeros((h, w), np.int32)
        boxes = []
        for k, (m, (x0, y0, x1, y1)) in enumerate(parts, start=1):
            out[m & (out == 0)] = k
            boxes.append((k, x0, y0, x1, y1))
        band = opts.get("band", h * 0.12)
        boxes.sort(key=lambda b: (round(((b[2] + b[4]) / 2) / band), b[1]))
        return out, boxes
    m = (a > t).astype(np.uint8)
    k = opts.get("close", 5)
    if k > 1:
        m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (k, k)))
    for x0, y0, x1, y1 in opts.get("cut_rects", ()):  # elle verilen ayırma şeritleri (normalize)
        m[int(y0 * h):int(y1 * h), int(x0 * w):int(x1 * w)] = 0
    n, lab, st, _ = cv2.connectedComponentsWithStats(m, connectivity=8)
    area = st[:, cv2.CC_STAT_AREA]
    big_min = opts.get("big_min", 900)
    drop_min = opts.get("drop_min", 60)
    merge_d = opts.get("merge", 18)
    big = [i for i in range(1, n) if area[i] >= big_min]
    owner = {i: i for i in big}
    if big:
        bigmask = np.isin(lab, big)
        dmap, (iy, ix) = ndi.distance_transform_edt(~bigmask, return_indices=True)
        nearest = lab[iy, ix]
        for i in range(1, n):
            if i in owner or area[i] < drop_min:
                continue
            ys, xs = np.nonzero(lab == i)
            j = int(np.argmin(dmap[ys, xs]))
            if dmap[ys[j], xs[j]] <= merge_d:
                owner[i] = int(nearest[ys[j], xs[j]])
    out = np.zeros((h, w), np.int32)
    for i, o in owner.items():
        out[lab == i] = o
    # sıralama: satır bantları, soldan sağa
    boxes = []
    for o in big:
        ys, xs = np.nonzero(out == o)
        boxes.append((o, xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    band = opts.get("band", h * 0.12)
    boxes.sort(key=lambda b: (round(((b[2] + b[4]) / 2) / band), b[1]))
    return out, boxes


def extract(col: np.ndarray, a: np.ndarray, labels: np.ndarray, boxes, opts: dict):
    """Her parçanın RGBA resmi: alfa, en yakın parçaya ait ve [glow] uzaklık içindeki piksellerle sınırlı."""
    glow = opts.get("glow", 22)
    fgmask = labels > 0
    dmap, (iy, ix) = ndi.distance_transform_edt(~fgmask, return_indices=True)
    nearest = labels[iy, ix]
    h, w = a.shape
    pieces = []
    for idx, (o, x0, y0, x1, y1) in enumerate(boxes):
        X0, Y0 = max(0, x0 - glow), max(0, y0 - glow)
        X1, Y1 = min(w, x1 + glow), min(h, y1 + glow)
        own = (nearest[Y0:Y1, X0:X1] == o) & (dmap[Y0:Y1, X0:X1] <= glow)
        # parıltı kenarda yumuşak biter
        fade = np.clip(1 - dmap[Y0:Y1, X0:X1] / max(glow, 1), 0, 1) ** 0.5
        pa = a[Y0:Y1, X0:X1] * own * np.where(labels[Y0:Y1, X0:X1] == o, 1.0, fade)
        ys, xs = np.nonzero(pa > opts.get("trim", 0.03))
        if len(xs) == 0:
            continue
        cx0, cy0, cx1, cy1 = xs.min(), ys.min(), xs.max() + 1, ys.max() + 1
        rgba = np.dstack([col[Y0:Y1, X0:X1], pa])[cy0:cy1, cx0:cx1]
        img = Image.fromarray((np.clip(rgba, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")
        pieces.append({"i": idx, "box": (X0 + cx0, Y0 + cy0, X0 + cx1, Y0 + cy1), "img": img})
    return pieces


def dhash(img: Image.Image, size: int = 12) -> int:
    """Gri zemine bindirilmiş resmin fark özeti (yinelenenleri bulmak için)."""
    base = Image.new("RGBA", img.size, (128, 128, 128, 255))
    base.alpha_composite(img)
    g = np.asarray(base.convert("L").resize((size + 1, size), Image.LANCZOS)).astype(np.int16)
    bits = (g[:, 1:] > g[:, :-1]).ravel()
    v = 0
    for b in bits:
        v = (v << 1) | int(b)
    return v


def hamming(a: int, b: int) -> int:
    return bin(a ^ b).count("1")


def color_sig(img: Image.Image) -> np.ndarray:
    """Alfa ağırlıklı ortalama renk ve en/boy (özet eşleşince renk ve oran da benzemeli)."""
    x = np.asarray(img).astype(np.float32) / 255
    w = x[..., 3:4]
    s = w.sum()
    mean = (x[..., :3] * w).sum((0, 1)) / max(s, 1e-3)
    return np.concatenate([mean, [img.width / max(img.height, 1)]])
