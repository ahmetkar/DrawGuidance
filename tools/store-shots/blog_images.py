#!/usr/bin/env python3
"""v50 Yenilikler yazısının görselleri (WebP): her dilde 4 görsel (tablet ham görüntülerinin üst bölümü) ve dilden
bağımsız kapak (bitmiş sayfanın kâğıt bölümü, mor gradyan üstünde).

Kullanım: python3 blog_images.py <ham klasör> <çıkış klasörü (public/blog/resimden-cizim)>
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

RAW, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
LANGS = ["tr", "en", "ar", "ja", "ko", "ru", "zh"]
SCENES = [(1, "08-vektorlestir", "vektorlestir"), (2, "09-kalipla-ciz", "kalipla-ciz"),
          (3, "10-kutuphane-kalibi", "kutuphane-kalibi"), (4, "11-resimden-cizim", "resimden-cizim")]

for lang in LANGS:
    for n, scene, name in SCENES:
        src = os.path.join(RAW, f"{lang}-tablet-{scene}.png")
        if not os.path.exists(src):
            print("eksik:", src)
            continue
        im = Image.open(src).convert("RGB")
        # Tablet görüntüsünün üst %74'ü (araç satırları ve çizim); 9-10'da açıklama kutusu altta kalır, onlar tam
        h = im.height if n in (2, 3) else int(im.height * 0.74)
        im = im.crop((0, 0, im.width, h))
        im.thumbnail((1200, 1600), Image.LANCZOS)
        im.save(os.path.join(OUT, f"{lang}-{n}-{name}.webp"), "WEBP", quality=86, method=6)

# Kapak 1280×960: bitmiş sayfanın kâğıdı (yazısız) gradyan üstünde
src = Image.open(os.path.join(RAW, "en-tablet-11-resimden-cizim.png")).convert("RGB")
px = src.load()
w, h = src.size
# Beyaz kâğıdın sınırları: ortadaki sütunda ve satırda saf beyaz bölge
xs = [x for x in range(w) if px[x, h // 2] == (255, 255, 255)]
ys = [y for y in range(h) if px[w // 2, y] == (255, 255, 255)]
paper = src.crop((min(xs), min(ys), max(xs) + 1, max(ys) + 1))
cover = Image.new("RGB", (1280, 960))
top, bottom = (55, 48, 163), (129, 140, 248)
for y in range(960):
    t = y / 959
    ImageDraw.Draw(cover).line([(0, y), (1280, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
paper.thumbnail((1180, 860), Image.LANCZOS)
# Kâğıdın üst kısmı (demlikler) ve kedi sığsın diye kâğıt yatay kırpılmaz; ortalanır, gölgeli
x, y = (1280 - paper.width) // 2, (960 - paper.height) // 2
shadow = Image.new("RGBA", (1280, 960), (0, 0, 0, 0))
ImageDraw.Draw(shadow).rounded_rectangle((x + 8, y + 16, x + paper.width + 8, y + paper.height + 16), 18, fill=(0, 0, 0, 120))
shadow = shadow.filter(ImageFilter.GaussianBlur(18))
cover = Image.alpha_composite(cover.convert("RGBA"), shadow)
mask = Image.new("L", paper.size, 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, paper.width - 1, paper.height - 1), 18, fill=255)
cover.paste(paper, (x, y), mask)
cover.convert("RGB").save(os.path.join(OUT, "kapak.webp"), "WEBP", quality=88, method=6)
print("ok")
