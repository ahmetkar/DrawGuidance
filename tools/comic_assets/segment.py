"""v40 karakter sayfalarından arka planı ayırma ve figürleri bölme (klasik yöntemler: taşma doldurma, doku, morfoloji).

Sayfa türleri:
  checker : sahte saydamlık damalı zemini (iki açık gri kare; gerçek alfa yok)
  white   : düz beyaz zemin
  flat    : düz (ya da iki tonlu) açık gri zemin (Flux çizgi roman sayfaları)
  dark    : koyu/renkli gradyan zemin (yumuşak geçişli; komşu farkıyla taşma)
  black   : siyah zemin
"""
from __future__ import annotations

import numpy as np
import cv2
from scipy import ndimage as ndi

WORK_MAX = 2048  # çalışma çözünürlüğü (uzun kenar)


def load(path: str) -> np.ndarray:
    im = cv2.imread(path, cv2.IMREAD_COLOR)
    if im is None:
        raise IOError(path)
    im = im[:, :, ::-1].copy()  # RGB
    h, w = im.shape[:2]
    s = WORK_MAX / max(h, w)
    if s < 1:
        im = cv2.resize(im, (round(w * s), round(h * s)), interpolation=cv2.INTER_AREA)
    return im


def border_pixels(im: np.ndarray, b: int = 6) -> np.ndarray:
    return np.concatenate([im[:b].reshape(-1, 3), im[-b:].reshape(-1, 3), im[:, :b].reshape(-1, 3), im[:, -b:].reshape(-1, 3)]).astype(np.float32)


def kmeans(px: np.ndarray, k: int):
    crit = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 30, 0.5)
    _, lab, cen = cv2.kmeans(px.astype(np.float32), k, None, crit, 4, cv2.KMEANS_PP_CENTERS)
    share = np.bincount(lab.ravel(), minlength=k) / len(lab)
    order = np.argsort(-share)
    return cen[order], share[order]


def detect_kind(im: np.ndarray) -> str:
    bp = border_pixels(im)
    L = bp.mean(1)
    chroma = bp.max(1) - bp.min(1)
    light = (chroma < 14) & (L > 195)
    if light.mean() > 0.6:
        # damalı zeminde kenar şeridinin yerel dokusu yüksek (kareler), düz zeminde ~0
        g = im.astype(np.float32).mean(2)
        ls = local_std(g, 3)
        b = 6
        strip = np.concatenate([ls[:b].ravel(), ls[-b:].ravel(), ls[:, :b].ravel(), ls[:, -b:].ravel()])
        if np.median(strip) > 5:
            return "checker"
        if (L[light] > 248).mean() > 0.95:
            return "white"
        return "flat"
    if (L < 25).mean() > 0.6:
        return "black"
    return "dark"


def local_std(gray: np.ndarray, r: int) -> np.ndarray:
    g = gray.astype(np.float32)
    k = (2 * r + 1, 2 * r + 1)
    m = cv2.blur(g, k)
    m2 = cv2.blur(g * g, k)
    return np.sqrt(np.maximum(m2 - m * m, 0))


def checker_period(gray: np.ndarray) -> int:
    """Damalı karenin kenarı (piksel): üst ve sol kenar şeritlerinin öz-ilintisinden."""
    best = []
    for strip in (gray[2:6, :600].mean(0), gray[:600, 2:6].mean(1)):
        s = strip - strip.mean()
        if np.allclose(s, 0):
            continue
        ac = np.correlate(s, s, "full")[len(s) - 1:]
        lag = int(np.argmax(ac[6:120]) + 6)  # tam dönem (iki kare)
        best.append(lag)
    return max(4, int(round(np.median(best) / 2))) if best else 12


def components_touching_border(mask: np.ndarray):
    n, lab = cv2.connectedComponents(mask.astype(np.uint8), connectivity=4)
    edge = np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))
    edge = edge[edge != 0]
    return n, lab, edge


def background_mask(im: np.ndarray, kind: str, opts: dict):
    """Arka plan maskesi (True: zemin) ve kenar yumuşatma için zemine uzaklık haritası."""
    h, w = im.shape[:2]
    f = im.astype(np.float32)
    gray = f.mean(2)
    chroma = f.max(2) - f.min(2)
    area = h * w
    hole_min = opts.get("hole_min", 0.00035) * area
    tol = opts.get("tol")

    if kind == "checker":
        per = opts.get("period") or checker_period(gray)
        bp = border_pixels(im)
        bl = bp.mean(1)
        sel = (bp.max(1) - bp.min(1) < 14) & (bl > 195)
        cen, share = kmeans(bp[sel], 2)
        lo, hi = sorted(cen.tolist(), key=lambda c: sum(c))
        lo = np.array(lo, np.float32); hi = np.array(hi, np.float32)
        t = tol or 16
        d_lo = np.abs(f - lo).max(2); d_hi = np.abs(f - hi).max(2)
        dist = np.minimum(d_lo, d_hi)
        cand = (dist < t) & (chroma < 16)
        win = (2 * per + 1, 2 * per + 1)
        fr_lo = cv2.blur((d_lo < 9).astype(np.float32), win)
        fr_hi = cv2.blur((d_hi < 9).astype(np.float32), win)
        tex = (fr_lo > 0.12) & (fr_hi > 0.12)
        flood = cand & tex
        n, lab, edge = components_touching_border(flood)
        bg = np.isin(lab, edge)
        # içteki boşluklar (kol ile gövde arası): damalı doku taşıyan yeterince büyük bileşenler
        sizes = np.bincount(lab.ravel(), minlength=n)
        holes = [i for i in range(1, n) if i not in set(edge.tolist()) and sizes[i] >= max(hole_min, (2 * per) ** 2)]
        if holes:
            bg |= np.isin(lab, holes)
        # doku penceresinin dışında kalan, zemine bitişik zemin renkli şerit (çizgiye kadar)
        grow = cand & ~bg
        for _ in range(per):
            nb = cv2.dilate(bg.astype(np.uint8), np.ones((3, 3), np.uint8)).astype(bool)
            add = nb & grow
            if not add.any():
                break
            bg |= add; grow &= ~add
        bgcol = np.where((d_lo < d_hi)[..., None], lo, hi)
        return bg, dist, bgcol

    if kind == "white":
        t = tol or 10
        dist = np.abs(f - 255).max(2)
        cand = dist < t
        n, lab, edge = components_touching_border(cand)
        bg = np.isin(lab, edge)
        strict = (dist < 3) & (local_std(gray, 2) < 1.0)
        n2, lab2 = cv2.connectedComponents(strict.astype(np.uint8), connectivity=4)
        sizes = np.bincount(lab2.ravel(), minlength=n2)
        big = [i for i in range(1, n2) if sizes[i] >= hole_min * opts.get("white_hole_mult", 4)]
        if big and opts.get("white_holes", True):
            bg |= np.isin(lab2, big)
        bgcol = np.full_like(f, 255)
        return bg, dist, bgcol

    if kind in ("flat", "dark", "black"):
        bp = border_pixels(im)
        if kind == "black":
            cen = np.array([[0, 0, 0]], np.float32)
        else:
            k = 3 if kind == "flat" else 4
            cen, share = kmeans(bp, k)
            # zemin renkleri: kenarda yeterli payı olan ve düşük doygunluklu (flat) merkezler
            keep = share > 0.04
            if kind == "flat":
                keep &= (cen.max(1) - cen.min(1) < 20) & (cen.mean(1) > 150)
            cen = cen[keep] if keep.any() else cen[:1]
        t = tol or {"flat": 14, "dark": 34, "black": 26}[kind]
        d = np.min(np.stack([np.abs(f - c).max(2) for c in cen]), axis=0)
        cand = d < t
        lstd = local_std(gray, 2)
        smooth = lstd < {"flat": 3.0, "dark": 7.0, "black": 6.0}[kind] * opts.get("std_mult", 1.0)
        # taşma: kenardan, komşu farkı küçük (yumuşak geçiş) ve zemin rengine yakın pikseller
        barrier = (~(cand & smooth)).astype(np.uint8)
        ffmask = np.zeros((h + 2, w + 2), np.uint8)
        ffmask[1:-1, 1:-1] = barrier
        work = cv2.GaussianBlur(im, (3, 3), 0) if kind != "flat" else im.copy()
        ld = opts.get("lodiff", {"flat": 3, "dark": 5, "black": 5}[kind])
        flags = 4 | cv2.FLOODFILL_MASK_ONLY | (255 << 8)
        step = 7
        seeds = [(x, 0) for x in range(0, w, step)] + [(x, h - 1) for x in range(0, w, step)] + \
                [(0, y) for y in range(0, h, step)] + [(w - 1, y) for y in range(0, h, step)]
        for (x, y) in seeds:
            if ffmask[y + 1, x + 1] == 0:
                cv2.floodFill(work, ffmask, (x, y), 0, (ld, ld, ld), (ld, ld, ld), flags)
        bg = ffmask[1:-1, 1:-1] == 255
        # içteki boşluklar: zemin rengine çok yakın ve pürüzsüz büyük bileşenler
        if opts.get("holes", True):
            strict = (d < t * 0.6) & (lstd < (1.6 if kind == "flat" else 4.0)) & ~bg
            n2, lab2 = cv2.connectedComponents(strict.astype(np.uint8), connectivity=4)
            sizes = np.bincount(lab2.ravel(), minlength=n2)
            big = [i for i in range(1, n2) if sizes[i] >= hole_min]
            if big:
                bg |= np.isin(lab2, big)
        # zemin rengindeki bitişik şerit (çizgiye kadar birkaç piksel)
        grow = cand & ~bg
        for _ in range(3):
            nb = cv2.dilate(bg.astype(np.uint8), np.ones((3, 3), np.uint8)).astype(bool)
            add = nb & grow
            bg |= add; grow &= ~add
        # yerel zemin rengi (yumuşatma ve renk arındırma için): zemindeki piksellerin bulanık ortalaması
        bgf = bg.astype(np.float32)
        num = cv2.blur(f * bgf[..., None], (61, 61)); den = cv2.blur(bgf, (61, 61))[..., None]
        bgcol = np.where(den > 1e-3, num / np.maximum(den, 1e-3), cen[0])
        dist = np.abs(f - bgcol).max(2)
        return bg, dist, bgcol

    raise ValueError(kind)


def remove_lines(fg: np.ndarray) -> np.ndarray:
    """Panel bölme çizgileri gibi uzun, ince, düz çizgileri ön plandan çıkarır."""
    h, w = fg.shape
    u = fg.astype(np.uint8)
    rect = lambda a, b: cv2.getStructuringElement(cv2.MORPH_RECT, (a, b))
    lh = cv2.morphologyEx(u, cv2.MORPH_OPEN, rect(int(w * 0.35), 1))
    lv = cv2.morphologyEx(u, cv2.MORPH_OPEN, rect(1, int(h * 0.35)))
    # yalnız enine ince olanlar (kalınlık < 10 px): yatay çizgide dikey, dikey çizgide yatay koşu kısa
    lh = lh & ~cv2.morphologyEx(lh, cv2.MORPH_OPEN, rect(1, 10))
    lv = lv & ~cv2.morphologyEx(lv, cv2.MORPH_OPEN, rect(10, 1))
    lines = (lh | lv).astype(bool)
    if not lines.any():
        return fg
    lines = cv2.dilate(lines.astype(np.uint8), np.ones((5, 5), np.uint8)).astype(bool)
    return fg & ~lines


def scene_rects(fg: np.ndarray, opts: dict):
    """Kendi arka planı olan dikdörtgen sahne küçük resimleri: kalın, kutusunu dolduran bölgeler."""
    h, w = fg.shape
    k = max(15, int(min(h, w) * 0.03))
    op = cv2.morphologyEx(fg.astype(np.uint8), cv2.MORPH_OPEN, np.ones((k, k), np.uint8))
    n, lab, st, _ = cv2.connectedComponentsWithStats(op, connectivity=4)
    rects = []
    for i in range(1, n):
        x, y, bw, bh, a = st[i]
        if a < opts.get("scene_min", 0.006) * h * w:
            continue
        if a / float(bw * bh) >= opts.get("scene_fill", 0.9):
            rects.append((x, y, x + bw, y + bh))
    return rects


def refine_alpha(im: np.ndarray, fg: np.ndarray, dist: np.ndarray, bgcol: np.ndarray, soft=(5.0, 34.0)):
    """Yumuşak kenar: sınır şeridinde zemine uzaklıktan alfa; yarı saydam piksellerin rengi zeminden arındırılır."""
    f = im.astype(np.float32)
    k = np.ones((3, 3), np.uint8)
    u = fg.astype(np.uint8)
    outer = cv2.dilate(u, k, iterations=1).astype(bool)
    inner = cv2.erode(u, k, iterations=2).astype(bool)
    band = outer & ~inner
    a = fg.astype(np.float32)
    t0, t1 = soft
    sa = np.clip((dist - t0) / (t1 - t0), 0, 1)
    a[band] = np.where(fg[band], np.maximum(sa[band], 0.0), sa[band] * 0.6)
    a = cv2.GaussianBlur(a, (3, 3), 0.6)
    a[inner] = 1.0
    a[~outer] = 0.0
    # renk arındırma: C = a*F + (1-a)*B  →  F = (C - (1-a)B)/a ; düşük alfada en yakın tam opak pikselin rengi
    core = a >= 0.98
    if core.any():
        _, (iy, ix) = ndi.distance_transform_edt(~core, return_indices=True)
        nearest = f[iy, ix]
    else:
        nearest = f
    aa = a[..., None]
    dec = np.clip((f - (1 - aa) * bgcol) / np.maximum(aa, 1e-3), 0, 255)
    mix = np.clip((aa - 0.35) / 0.5, 0, 1)
    out = np.where(core[..., None], f, mix * dec + (1 - mix) * nearest)
    return out.astype(np.uint8), (a * 255).astype(np.uint8)


def split(fg: np.ndarray, opts: dict):
    """Figürlere böler: aşındırmayla tohumlar, mesafe haritasında su bölümü (dokunan figürler ayrılır); küçük
    parçalar (kopuk silah, alev, saç ucu) komşu ya da yakın büyük parçaya katılır. Döner: [(maske, kutu)]."""
    from skimage.segmentation import watershed
    h, w = fg.shape
    area = h * w
    u = fg.astype(np.uint8)
    dist = cv2.distanceTransform(u, cv2.DIST_L2, 5)
    r = opts.get("erode", 0.03) * min(h, w)
    seeds = (dist > r).astype(np.uint8)
    n, lab, st, _ = cv2.connectedComponentsWithStats(seeds, connectivity=8)
    seed_min = opts.get("seed_min", 0.0025) * area
    markers = np.zeros((h, w), np.int32)
    k = 0
    for i in range(1, n):
        if st[i, cv2.CC_STAT_AREA] >= seed_min:
            k += 1
            markers[lab == i] = k
    if not opts.get("watershed", True):
        # varsayılan: bağlı bileşenler (dokunan figürler elle verilen kesme çizgileriyle ayrılır)
        n2, lab2 = cv2.connectedComponents(u, connectivity=8)
        parts = lab2
    else:
        parts = watershed(-dist, markers, mask=fg, connectivity=2) if k else np.zeros((h, w), np.int32)
    # tohumu olmayan bileşenler (küçük kopuk parçalar) ayrı etiket alır
    rest = fg & (parts == 0)
    n3, lab3 = cv2.connectedComponents(rest.astype(np.uint8), connectivity=8)
    base = parts.max()
    parts = np.where(rest, lab3 + base, parts)
    labels = [l for l in np.unique(parts) if l != 0]
    areas = {l: int((parts == l).sum()) for l in labels}
    big_min = opts.get("big_min", 0.006) * area
    drop_min = opts.get("drop_min", 0.00004) * area
    merge_d = opts.get("merge", 0.02) * min(h, w)
    big = [l for l in labels if areas[l] >= big_min]
    owner = {l: l for l in big}
    if big:
        bigmask = np.isin(parts, big)
        dmap, (iy, ix) = ndi.distance_transform_edt(~bigmask, return_indices=True)
        nearest = parts[iy, ix]
        for l in sorted(labels, key=lambda l: -areas[l]):
            if l in owner or areas[l] < drop_min:
                continue
            m = parts == l
            dd = np.where(m, dmap, np.inf)
            pos = int(np.argmin(dd))
            if dd.ravel()[pos] <= merge_d:
                owner[l] = nearest.ravel()[pos]
    out = []
    for b in big:
        members = [l for l, o in owner.items() if o == b]
        m = np.isin(parts, members)
        ys, xs = np.nonzero(m)
        out.append((m, (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)))
    # okuma sırası: satır satır (üstten alta, soldan sağa)
    out.sort(key=lambda t: (round(((t[1][1] + t[1][3]) / 2) / (h * 0.2)), t[1][0]))
    return out


def is_scene_rect(m: np.ndarray, box) -> bool:
    """Kendi arka planı olan dikdörtgen sahne küçük resmi mi (kutusunun büyük bölümünü dolduruyor)."""
    x0, y0, x1, y1 = box
    sub = m[y0:y1, x0:x1]
    fill = sub.mean()
    if fill < 0.88:
        return False
    # kenarları düz mü: dört kenar şeridinin dolu oranı
    edges = [sub[:3].mean(), sub[-3:].mean(), sub[:, :3].mean(), sub[:, -3:].mean()]
    return sum(e > 0.85 for e in edges) >= 3


def apply_cuts(fg: np.ndarray, cuts, erase=()):
    """Elle verilen kesme çizgileri (normalize [x, y] noktaları) boyunca ön planı ayırır; [erase] dikdörtgenlerini siler."""
    h, w = fg.shape
    u = fg.astype(np.uint8)
    for line in cuts:
        pts = np.array([[x * w, y * h] for x, y in line], np.int32)
        cv2.polylines(u, [pts], False, 0, thickness=max(3, int(min(h, w) * 0.003)))
    for x0, y0, x1, y1 in erase:
        u[int(y0 * h):int(y1 * h), int(x0 * w):int(x1 * w)] = 0
    return u.astype(bool)
