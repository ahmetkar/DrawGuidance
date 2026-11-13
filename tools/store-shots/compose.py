#!/usr/bin/env python3
"""v50 mağaza tanıtım görselleri: Robolectric ham ekran görüntülerine (V50StoreShotsTest) başlık, alt yazı ve cihaz
çerçevesi eklenir; Google Play (telefon, 7" ve 10" tablet) ve App Store (iPhone 6.9", iPhone 6.5", iPad 13") boyutlarında
her dil için PNG yazılır.

Kullanım: python3 compose.py <ham klasör> <çıkış klasörü> <font klasörü>
Font klasöründe Noto Sans / JP / KR / SC / Arabic (Regular + Bold) TTF dosyaları olmalı.
"""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

RAW, OUT, FONTS = sys.argv[1], sys.argv[2], sys.argv[3]
HERE = os.path.dirname(os.path.abspath(__file__))
CAPTIONS = json.load(open(os.path.join(HERE, "captions.json"), encoding="utf-8"))
LANGS = ["tr", "en", "ar", "ja", "ko", "ru", "zh"]

# (klasör, dosya ön eki, genişlik, yükseklik, ham görüntü türü)
TARGETS = [
    ("google-play/telefon", "telefon", 1080, 1920, "phone"),
    ("google-play/tablet-7", "tablet7", 1200, 1920, "tablet"),
    ("google-play/tablet-10", "tablet10", 1600, 2560, "tablet"),
    ("app-store/iphone-6.9", "iphone69", 1320, 2868, "phone"),
    ("app-store/iphone-6.5", "iphone65", 1284, 2778, "phone"),
    ("app-store/ipad-13", "ipad13", 2064, 2752, "tablet"),
]

# Setler: bütün modlar (madde 4.1) ve Resimden çizim (madde 4)
SETS = {
    "tum-modlar": ["01-cizgi-roman", "02-resim", "03-not", "04-cocuk", "05-basit-resim", "07-basit-not", "06-3b", "11-resimden-cizim"],
    "resimden-cizim": ["08-vektorlestir", "09-kalipla-ciz", "10-kutuphane-kalibi", "11-resimden-cizim"],
}

# Sahneye göre arka plan gradyanı (üst, alt)
PALETTE = {
    "01-cizgi-roman": ("#2B1055", "#7597DE"),
    "02-resim": ("#0F4C75", "#3FC1C9"),
    "03-not": ("#1E3A8A", "#60A5FA"),
    "04-cocuk": ("#FF8A00", "#FFD54F"),
    "05-basit-resim": ("#2E7D32", "#9CCC65"),
    "06-3b": ("#37474F", "#90A4AE"),
    "07-basit-not": ("#4527A0", "#9575CD"),
    "08-vektorlestir": ("#5B21B6", "#C084FC"),
    "09-kalipla-ciz": ("#1565C0", "#64B5F6"),
    "10-kutuphane-kalibi": ("#AD1457", "#F48FB1"),
    "11-resimden-cizim": ("#3730A3", "#818CF8"),
}


def font(lang, bold, size):
    fam = {"ja": "NotoSansJP", "ko": "NotoSansKR", "zh": "NotoSansSC", "ar": "NotoSansArabic"}.get(lang, "NotoSans")
    path = os.path.join(FONTS, f"{fam}-{'Bold' if bold else 'Regular'}.ttf")
    kw = {}
    if lang == "ar":
        kw = {"layout_engine": ImageFont.Layout.RAQM}
    return ImageFont.truetype(path, size, **kw)


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def gradient(w, h, top, bottom):
    a, b = hex_rgb(top), hex_rgb(bottom)
    col = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / max(h - 1, 1)
        col.putpixel((0, y), tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)))
    return col.resize((w, h))


def wrap(draw, text, fnt, max_w, lang):
    """Satırlara böler (CJK harf harf, öteki diller sözcük sözcük)."""
    units = list(text) if lang in ("ja", "zh") else text.split(" ")
    sep = "" if lang in ("ja", "zh") else " "
    lines, cur = [], ""
    for u in units:
        cand = (cur + sep + u) if cur else u
        if draw.textlength(cand, font=fnt, direction="rtl" if lang == "ar" else None) <= max_w or not cur:
            cur = cand
        else:
            lines.append(cur)
            cur = u
    if cur:
        lines.append(cur)
    return lines


def text_block(draw, lines, fnt, cx, y, fill, lang, gap):
    for line in lines:
        kw = {"direction": "rtl", "language": "ar"} if lang == "ar" else {}
        w = draw.textlength(line, font=fnt, **({"direction": "rtl"} if lang == "ar" else {}))
        draw.text((cx - w / 2, y), line, font=fnt, fill=fill, **kw)
        y += int(fnt.size * gap)
    return y


def compose(raw_path, scene, lang, W, H):
    shot = Image.open(raw_path).convert("RGB")
    top, bottom = PALETTE[scene]
    img = gradient(W, H, top, bottom)
    draw = ImageDraw.Draw(img)
    title, sub = CAPTIONS[scene][lang]
    unit = min(W, H * 0.6)
    margin = int(W * 0.07)
    tf = font(lang, True, int(unit * 0.085))
    sf = font(lang, False, int(unit * 0.042))
    y = int(H * 0.045)
    y = text_block(draw, wrap(draw, title, tf, W - 2 * margin, lang), tf, W / 2, y, "#FFFFFF", lang, 1.22)
    y += int(unit * 0.012)
    y = text_block(draw, wrap(draw, sub, sf, W - 2 * margin, lang), sf, W / 2, y, (255, 255, 255, 230), lang, 1.35)
    y += int(H * 0.03)
    # Cihaz: ekran görüntüsü yuvarlak köşeli koyu çerçevede; alt kenardan taşabilir (kırpılır)
    avail_h = H - y + int(H * 0.06)
    max_w = W - 2 * margin
    scale = min(max_w / shot.width, avail_h / shot.height)
    sw, sh = int(shot.width * scale), int(shot.height * scale)
    bezel = max(int(sw * 0.028), 8)
    fw, fh = sw + 2 * bezel, sh + 2 * bezel
    fx, fy = (W - fw) // 2, y
    radius = int(fw * 0.075)
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((fx + 6, fy + 18, fx + fw + 6, fy + fh + 18), radius, fill=(0, 0, 0, 110))
    shadow = shadow.filter(ImageFilter.GaussianBlur(int(W * 0.02)))
    img = Image.alpha_composite(img.convert("RGBA"), shadow)
    frame = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle((0, 0, fw - 1, fh - 1), radius, fill="#15171C")
    screen = shot.resize((sw, sh), Image.LANCZOS)
    mask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, sw - 1, sh - 1), max(radius - bezel, 4), fill=255)
    frame.paste(screen, (bezel, bezel), mask)
    img.alpha_composite(frame, (fx, fy))
    return img.convert("RGB")


def main():
    made = 0
    for lang in LANGS:
        for folder, prefix, W, H, kind in TARGETS:
            for set_name, scenes in SETS.items():
                for i, scene in enumerate(scenes, 1):
                    raw = os.path.join(RAW, f"{lang}-{kind}-{scene}.png")
                    if not os.path.exists(raw):
                        print("eksik:", raw)
                        continue
                    out_dir = os.path.join(OUT, set_name, lang, folder)
                    os.makedirs(out_dir, exist_ok=True)
                    name = f"{prefix}-{i:02d}-{scene[3:]}.png"
                    compose(raw, scene, lang, W, H).save(os.path.join(out_dir, name), optimize=True)
                    made += 1
    print("yazıldı:", made)


if __name__ == "__main__":
    main()
