"""v42 efekt/nesne ayırmasının inceleme resimleri: sayfa üstünde numaralı kutular ve damalı zeminde parçalar."""
from __future__ import annotations

import sys
from PIL import Image, ImageDraw


def checker(size, cell=10):
    w, h = size
    im = Image.new("RGB", size, (236, 236, 236))
    d = ImageDraw.Draw(im)
    for y in range(0, h, cell):
        for x in range((y // cell) % 2 * cell, w, cell * 2):
            d.rectangle((x, y, x + cell - 1, y + cell - 1), fill=(206, 206, 206))
    return im


def review(sheet_path: str, pieces, out: str, title: str = "", cols: int = 12, cell: int = 120, dropped=()):
    sheet = Image.open(sheet_path).convert("RGBA")
    base = checker(sheet.size, 16).convert("RGBA")
    base.alpha_composite(sheet)
    d = ImageDraw.Draw(base)
    for p in pieces:
        x0, y0, x1, y1 = p["box"]
        col = (255, 0, 0, 255) if p["i"] not in dropped else (120, 120, 120, 255)
        d.rectangle((x0, y0, x1, y1), outline=col, width=2)
        d.rectangle((x0, y0, x0 + 26, y0 + 14), fill=(255, 255, 255, 220))
        d.text((x0 + 2, y0 + 1), str(p.get("label", p["i"])), fill=col)
    top = base.convert("RGB").resize((sheet.width // 2 * 2 // 2, sheet.height // 2))
    rows = (len(pieces) + cols - 1) // cols
    grid = Image.new("RGB", (cols * cell, rows * (cell + 14)), "white")
    gd = ImageDraw.Draw(grid)
    for k, p in enumerate(pieces):
        im = p["img"]
        s = min((cell - 6) / im.width, (cell - 6) / im.height, 1.5)
        t = im.resize((max(1, round(im.width * s)), max(1, round(im.height * s))))
        bg = checker((cell - 4, cell - 4), 8).convert("RGBA")
        bg.alpha_composite(t, ((bg.width - t.width) // 2, (bg.height - t.height) // 2))
        x, y = (k % cols) * cell, (k // cols) * (cell + 14)
        grid.paste(bg.convert("RGB"), (x + 2, y + 14))
        gd.text((x + 3, y + 1), f"{p.get('label', p['i'])} {im.width}x{im.height}", fill=(200, 0, 0) if p["i"] not in dropped else (140, 140, 140))
    W = max(top.width, grid.width)
    out_im = Image.new("RGB", (W, top.height + grid.height + 20), "white")
    out_im.paste(top, (0, 20))
    ImageDraw.Draw(out_im).text((4, 4), title, fill=(0, 0, 0))
    out_im.paste(grid, (0, top.height + 20))
    out_im.save(out, quality=82)


if __name__ == "__main__":
    print("kütüphane olarak kullanılır", file=sys.stderr)
