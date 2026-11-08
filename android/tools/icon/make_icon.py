"""v30 uygulama simgesi: "rehberli çizim" — kesik çizgili kılavuz eğrisi, kılavuzu izleyen renkli çizgi, kalem ve
yapay zekâ parıltısı. Uyarlanabilir simge (Android 8+) için vektörler ve eski sürümler için PNG'ler üretir."""
import math, os, sys
import cairosvg

RES = sys.argv[1]  # app/src/main/res
OUT = sys.argv[2] if len(sys.argv) > 2 else None

# 108×108 tuval; güvenli alan 21..87
OX, OY = -1, 4  # görsel ortalama
def sh(p): return (p[0] + OX, p[1] + OY)
P0, C1, C2, P1 = sh((27, 75)), sh((23, 45)), sh((50, 29)), sh((58, 49))
C3, C4, P2 = sh((65, 65)), sh((86, 58)), sh((83, 31))

def cubic(a, b, c, d, t):
    mt = 1 - t
    return (mt**3*a[0] + 3*mt*mt*t*b[0] + 3*mt*t*t*c[0] + t**3*d[0], mt**3*a[1] + 3*mt*mt*t*b[1] + 3*mt*t*t*c[1] + t**3*d[1])

def sample():
    pts = [cubic(P0, C1, C2, P1, i / 200) for i in range(201)] + [cubic(P1, C3, C4, P2, i / 200) for i in range(1, 201)]
    return pts

def dashes(pts, dash=4.6, gap=4.2, start=0.0):
    out, cur, acc, on = [], [pts[0]], -start, True
    for a, b in zip(pts, pts[1:]):
        seg = math.dist(a, b); acc += seg
        lim = dash if on else gap
        if on: cur.append(b)
        if acc >= lim:
            if on and len(cur) > 1: out.append(cur)
            cur, acc, on = [b], 0.0, not on
    if on and len(cur) > 1: out.append(cur)
    return out

def path_d(points):
    return 'M' + ' L'.join(f'{x:.2f},{y:.2f}' for x, y in points)

traced = 'M%.1f,%.1f C%.1f,%.1f %.1f,%.1f %.1f,%.1f' % (P0 + C1 + C2 + P1)
# Kılavuzun izlenmemiş kısmı (ikinci eğri) kesik çizgi
guide = dashes([cubic(P1, C3, C4, P2, i / 300) for i in range(301)], start=2.0)

# Kalem: ucu P1'de, gövdesi sağ üste (45°)
ang = math.radians(-50)
ux, uy = math.cos(ang), math.sin(ang)          # uçtan gövdeye
nx, ny = -uy, ux
def at(s, w): return (P1[0] + ux * s + nx * w, P1[1] + uy * s + ny * w)
W = 5.2
tip = [at(0, 0), at(9, W), at(9, -W)]
lead = [at(0, 0), at(3.2, W * 0.36), at(3.2, -W * 0.36)]
body = [at(9, W), at(30, W), at(30, -W), at(9, -W)]
band = [at(30, W), at(33, W), at(33, -W), at(30, -W)]
eraser = [at(33, W), at(38, W), at(38, -W), at(33, -W)]
stripe = [at(9, W * 0.33), at(30, W * 0.33), at(30, -W * 0.33), at(9, -W * 0.33)]
def poly(p): return 'M' + ' L'.join(f'{x:.2f},{y:.2f}' for x, y in p) + ' Z'

# Yapay zekâ parıltısı (dört köşeli yıldız)
def sparkle(cx, cy, r, k=0.28):
    pts = []
    for i in range(8):
        a = -math.pi / 2 + i * math.pi / 4
        rr = r if i % 2 == 0 else r * k
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return poly(pts)
spark1 = sparkle(36 + OX, 33 + OY, 7.5)
spark2 = sparkle(46 + OX, 22 + OY, 3.6)

BG1, BG2 = '#4F46E5', '#9333EA'
STROKE1, STROKE2 = '#FDE68A', '#FB7185'

def svg(fg=True, bg=True, shape=None, mono=False, size=108):
    s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 108 108">',
         '<defs>',
         f'<linearGradient id="bg" x1="0" y1="0" x2="108" y2="108" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{BG1}"/><stop offset="1" stop-color="{BG2}"/></linearGradient>',
         '<radialGradient id="hl" cx="30" cy="22" r="60" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#FFFFFF" stop-opacity="0.22"/><stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/></radialGradient>',
         f'<linearGradient id="st" x1="{P0[0]}" y1="{P0[1]}" x2="{P1[0]}" y2="{P1[1]}" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{STROKE1}"/><stop offset="1" stop-color="{STROKE2}"/></linearGradient>',
         '</defs>']
    if shape == 'square': s.append('<clipPath id="c"><rect x="6" y="6" width="96" height="96" rx="24"/></clipPath><g clip-path="url(#c)">')
    elif shape == 'circle': s.append('<clipPath id="c"><circle cx="54" cy="54" r="48"/></clipPath><g clip-path="url(#c)">')
    else: s.append('<g>')
    if bg:
        s.append('<rect width="108" height="108" fill="url(#bg)"/><rect width="108" height="108" fill="url(#hl)"/>')
    if fg:
        white = '#FFFFFF'
        for d in guide:
            s.append(f'<path d="{path_d(d)}" fill="none" stroke="{white}" stroke-opacity="{1 if mono else 0.75}" stroke-width="3.4" stroke-linecap="round"/>')
        s.append(f'<path d="{traced}" fill="none" stroke="{white if mono else "url(#st)"}" stroke-width="7.5" stroke-linecap="round"/>')
        if mono:
            s.append(f'<path d="{poly(tip)}" fill="{white}"/><path d="{poly(body)}" fill="{white}"/><path d="{poly(eraser)}" fill="{white}"/>')
        else:
            s.append(f'<path d="{poly(body)}" fill="#FFFFFF"/><path d="{poly(stripe)}" fill="#E0E7FF"/>')
            s.append(f'<path d="{poly(tip)}" fill="#FCD9B8"/><path d="{poly(lead)}" fill="#1F2937"/>')
            s.append(f'<path d="{poly(band)}" fill="#C7D2FE"/><path d="{poly(eraser)}" fill="{STROKE2}"/>')
        s.append(f'<path d="{spark1}" fill="{white}"/><path d="{spark2}" fill="{white}" fill-opacity="{1 if mono else 0.85}"/>')
    s.append('</g></svg>')
    return '\n'.join(s)

# --- Vektör çizimler (uyarlanabilir simge, API 26+) ---
def vd(body):
    return ('<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n' + body + '</vector>\n')

def grad(x1, y1, x2, y2, c1, c2, kind='linear'):
    return (f'        <aapt:attr name="android:{kind}">\n            <gradient android:type="linear" android:startX="{x1}" android:startY="{y1}" '
            f'android:endX="{x2}" android:endY="{y2}">\n                <item android:offset="0" android:color="{c1}"/>\n'
            f'                <item android:offset="1" android:color="{c2}"/>\n            </gradient>\n        </aapt:attr>\n')

bg_xml = vd(
    '    <path android:pathData="M0,0h108v108h-108z">\n' + grad(0, 0, 108, 108, BG1, BG2, 'fillColor') + '    </path>\n'
    '    <path android:pathData="M0,0h108v108h-108z">\n        <aapt:attr name="android:fillColor">\n'
    '            <gradient android:type="radial" android:centerX="30" android:centerY="22" android:gradientRadius="60">\n'
    '                <item android:offset="0" android:color="#38FFFFFF"/>\n                <item android:offset="1" android:color="#00FFFFFF"/>\n'
    '            </gradient>\n        </aapt:attr>\n    </path>\n')

def fg_xml(mono=False):
    out = []
    for d in guide:
        out.append(f'    <path android:pathData="{path_d(d)}" android:strokeColor="{"#FFFFFFFF" if mono else "#BFFFFFFF"}" android:strokeWidth="3.4" android:strokeLineCap="round"/>\n')
    if mono:
        out.append(f'    <path android:pathData="{traced}" android:strokeColor="#FFFFFFFF" android:strokeWidth="7.5" android:strokeLineCap="round"/>\n')
        for p in (tip, body, eraser): out.append(f'    <path android:pathData="{poly(p)}" android:fillColor="#FFFFFFFF"/>\n')
    else:
        out.append(f'    <path android:pathData="{traced}" android:strokeWidth="7.5" android:strokeLineCap="round">\n' + grad(P0[0], P0[1], P1[0], P1[1], STROKE1, STROKE2, 'strokeColor') + '    </path>\n')
        for p, c in ((body, '#FFFFFF'), (stripe, '#E0E7FF'), (tip, '#FCD9B8'), (lead, '#1F2937'), (band, '#C7D2FE'), (eraser, STROKE2)):
            out.append(f'    <path android:pathData="{poly(p)}" android:fillColor="{c}"/>\n')
    out.append(f'    <path android:pathData="{spark1}" android:fillColor="#FFFFFFFF"/>\n')
    out.append(f'    <path android:pathData="{spark2}" android:fillColor="{"#FFFFFFFF" if mono else "#D9FFFFFF"}"/>\n')
    return vd(''.join(out))

open(os.path.join(RES, 'drawable/ic_launcher_background.xml'), 'w').write(bg_xml)
open(os.path.join(RES, 'drawable/ic_launcher_foreground.xml'), 'w').write(fg_xml())
open(os.path.join(RES, 'drawable/ic_launcher_monochrome.xml'), 'w').write(fg_xml(mono=True))

# --- Eski sürümler (API 21–25) için PNG ---
dens = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
for d, px in dens.items():
    folder = os.path.join(RES, f'mipmap-{d}'); os.makedirs(folder, exist_ok=True)
    cairosvg.svg2png(bytestring=svg(shape='square').encode(), write_to=os.path.join(folder, 'ic_launcher.png'), output_width=px, output_height=px)
    cairosvg.svg2png(bytestring=svg(shape='circle').encode(), write_to=os.path.join(folder, 'ic_launcher_round.png'), output_width=px, output_height=px)
if OUT:
    cairosvg.svg2png(bytestring=svg(shape='square').encode(), write_to=os.path.join(OUT, 'icon_512.png'), output_width=512, output_height=512)
    cairosvg.svg2png(bytestring=svg(shape='circle').encode(), write_to=os.path.join(OUT, 'icon_round_512.png'), output_width=512, output_height=512)
    cairosvg.svg2png(bytestring=svg(shape='circle', mono=True, bg=False).encode(), write_to=os.path.join(OUT, 'icon_mono_512.png'), output_width=512, output_height=512)
    open(os.path.join(OUT, 'icon.svg'), 'w').write(svg(shape='square'))
# Kotlin çizimi için geometri
print('P0', P0, 'C1', C1, 'C2', C2, 'P1', P1, 'C3', C3, 'C4', C4, 'P2', P2, 'pencil dir', round(ux, 4), round(uy, 4))
