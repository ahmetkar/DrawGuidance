"""v42 karakter resimlerinin bozukluk giderme adımı (build_assets.py'den sonra çalıştırılır).

Kullanım:
  python3 fix_chars.py --out android/app/src/main/assets/comic

Yapılanlar (inceleme: tüm çeşitlerin yeşil zemin üzerindeki kontak sayfaları):
  1. DROP: düzeltilemeyen çeşitler katalogdan ve diskten silinir: iki figür tek resimde, başı silinmiş gövde,
     sahte saydamlık (dama) deseni parıltının içine işlemiş büyü/hologram resimleri, gölge lekesi kalan portreler.
  2. ERASE: küçük dama kalıntıları belirtilen bölgede silinir ("checker": yalnız renksiz yüksek frekanslı desen,
     "all": bölgedeki her şey).
  3. Bütün karakterlerde: kenardaki açık renkli hale (beyaz saçak) en yakın opak pikselin rengiyle değiştirilir;
     ana gövdeden kopuk küçük kırıntılar silinir.
"""
from __future__ import annotations

import argparse
import json
import os

import numpy as np
from PIL import Image
from scipy import ndimage as ndi

DROP = {
    # iki figür tek resimde
    "ates_yumruk/aksiyon_01", "uzayli_buyucu/aksiyon_04", "boksor_emre/poz_02", "golge_ninja/poz_02", "ogrenci_ece/gunluk_04",
    # baş silinmiş (açık ten zeminle birlikte gitmiş)
    "doktor_ayse/gunluk_01", "doktor_ayse/gunluk_02", "doktor_ayse/gunluk_03",
    # parıltı/hologram içinde dama deseni
    "buz_buyucu_ayaz/aksiyon_01", "buz_buyucu_ayaz/aksiyon_02", "hacker_ozan/aksiyon_01", "elf_lunara/aksiyon_01",
    "ogrenci_ece/ifade_01", "ogrenci_ece/ifade_09", "buyucu_efe/aksiyon_01", "buyucu_efe/aksiyon_02", "buyucu_efe/aksiyon_03", "nisanci_tolga/ifade_02",
    # omuz arkasında kahverengi gölge lekesi
    "robotcu_mert/ifade_01", "robotcu_mert/ifade_02", "robotcu_mert/ifade_04", "robotcu_mert/aksiyon_01",
}

# çeşit → [(mod, x0, y0, x1, y1)] (normalize)
ERASE = {
    "buyucu_efe/aksiyon_04": [("checker", 0.74, 0.12, 1.0, 0.48)],
    "profesor_nuri/aksiyon_03": [("checker", 0.52, 0.12, 0.9, 0.48)],
}

HALO_GAP = 0.14       # yarı saydam kenar pikseli en yakın opak pikselden bu kadar açıksa hale sayılır
CRUMB_FRAC = 0.012    # ana gövdeden kopuk ve toplamın bu oranından küçük parçalar silinir


def checker_mask(C: np.ndarray, al: np.ndarray) -> np.ndarray:
    L = C.mean(2)
    hp = C - np.stack([ndi.gaussian_filter(C[..., k], 2.0) for k in range(3)], 2)
    hpL = hp.mean(2)
    chroma = np.abs(hp - hpL[..., None]).max(2)
    energy = ndi.uniform_filter(np.abs(hpL), 7)
    chrE = ndi.uniform_filter(chroma, 7)
    sat = C.max(2) - C.min(2)
    m = (energy > 0.03) & (chrE < energy * 0.7) & (ndi.uniform_filter(L, 5) > 0.55) & (al > 0.02)
    # açık ve az doygun pikseller (dama kareleri; parıltının renkli çekirdeği kalır)
    m |= (L > 0.72) & (sat < 0.22) & (al > 0.02)
    return ndi.binary_dilation(m, iterations=1)


def fix_image(a8: np.ndarray, erase=()) -> np.ndarray:
    a = a8.astype(np.float32) / 255
    C, al = a[..., :3].copy(), a[..., 3].copy()
    h, w = al.shape
    for mode, x0, y0, x1, y1 in erase:
        box = np.zeros_like(al, bool)
        box[int(y0 * h):int(np.ceil(y1 * h)), int(x0 * w):int(np.ceil(x1 * w))] = True
        if mode == "all":
            al[box] = 0
        else:
            al[box & checker_mask(C, al)] = 0
    # kopuk kırıntılar
    solid = al > 0.2
    lab, n = ndi.label(solid, structure=np.ones((3, 3)))
    if n > 1:
        sizes = ndi.sum(np.ones_like(al), lab, range(1, n + 1))
        tot = sizes.sum()
        small = np.zeros(n + 1, bool)
        small[1:] = sizes < CRUMB_FRAC * tot
        small[1 + int(np.argmax(sizes))] = False
        kill = ndi.binary_dilation(small[lab], iterations=2) & ~ndi.binary_dilation(~small[lab] & solid, iterations=1)
        al[kill | small[lab]] = 0
    # beyaz saçak: saydam kenara 3 piksel yakın, beyazımsı ve çevresindeki opak pikseller koyu olan noktalar
    # (beyaz zeminden kalan kırıntılar; beyaz saç/yaka gibi açık bölgelerin komşuları da açık olduğundan korunur)
    clear = al < 0.1
    if clear.any():
        dist = ndi.distance_transform_edt(~clear)
        L = C.mean(2)
        sat = C.max(2) - C.min(2)
        whiteish = (L > 0.7) & (sat < 0.25) & (dist <= 4) & (al > 0)
        if whiteish.any():
            solid_dark = ((al > 0.5) & ~whiteish).astype(np.float32)
            dark_sum = ndi.uniform_filter(solid_dark * (1 - L), 9)
            cnt = ndi.uniform_filter(solid_dark, 9)
            neigh_dark = np.where(cnt > 0.05, dark_sum / np.maximum(cnt, 1e-3), 0)
            speck = whiteish & (neigh_dark > 0.42)
            al[speck] = 0
    # hale: yarı saydam kenarın rengi en yakın opak pikselden çok açıksa onun rengi alınır
    opaque = al >= 0.85
    if opaque.any():
        _, (iy, ix) = ndi.distance_transform_edt(~opaque, return_indices=True)
        near = C[iy, ix]
        edge = (al > 0) & (al < 0.85)
        lighter = C.mean(2) - near.mean(2) > HALO_GAP
        sel = edge & lighter
        C[sel] = near[sel]
    al[al < 0.02] = 0
    return (np.clip(np.dstack([C, al]), 0, 1) * 255 + 0.5).astype(np.uint8)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    root = os.path.dirname(os.path.abspath(args.out.rstrip("/")))
    cat_path = os.path.join(args.out, "catalog.json")
    catalog = json.load(open(cat_path, encoding="utf-8"))
    dropped, fixed = 0, 0
    for c in catalog["characters"]:
        keep = []
        for v in c["variants"]:
            key = f"{c['id']}/{v['id']}"
            if key in DROP:
                for f in (v["file"], v["thumb"]):
                    p = os.path.join(root, f)
                    if os.path.exists(p):
                        os.remove(p)
                dropped += 1
                continue
            keep.append(v)
            for f, q in ((v["file"], 74), (v["thumb"], 70)):
                p = os.path.join(root, f)
                im = np.asarray(Image.open(p).convert("RGBA"))
                out = fix_image(im, ERASE.get(key, ()))
                # yalnız belirgin değişiklikte yeniden kodlanır (kayıplı WebP tekrar tekrar bozulmasın)
                diff = np.abs(out.astype(np.int16) - im.astype(np.int16)).max(2) > 12
                if diff.mean() > 0.002:
                    Image.fromarray(out, "RGBA").save(p, "WEBP", quality=q, method=6)
                    fixed += 1
        c["variants"] = keep
    with open(cat_path, "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=1)
    print(json.dumps({"dropped": dropped, "rewritten": fixed, "drop_list": len(DROP)}))


if __name__ == "__main__":
    main()
