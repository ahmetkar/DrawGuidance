#!/usr/bin/env python3
"""Android string kaynaklarını (app/src/main/res/values*/strings*.xml) iOS .strings tablolarına çevirir.

Çıktı: DrawGuidance/Resources/<dil>.lproj/Android.strings. Android'deki gibi bir dilde eksik anahtar varsayılan
(Türkçe) metinle doldurulur. Biçim belirteçleri iOS'a uyarlanır (%1$s -> %1$@, %s -> %@).

Kullanım: python3 convert.py <android/app/src/main/res> <ios/DrawGuidance/Resources>
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

LOCALES = {"values": "tr", "values-en": "en", "values-ar": "ar", "values-ja": "ja", "values-ko": "ko",
           "values-ru": "ru", "values-zh": "zh-Hans"}


def text_of(el):
    # İç biçimlendirme etiketleri (b, i, u) düz metne indirgenir
    return "".join(el.itertext())


def android_unescape(s):
    s = s.strip()
    if len(s) >= 2 and s[0] == '"' and s[-1] == '"':
        s = s[1:-1]
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            n = s[i + 1]
            if n == "u" and re.fullmatch(r"[0-9a-fA-F]{4}", s[i + 2:i + 6] or ""):
                out.append(chr(int(s[i + 2:i + 6], 16)))
                i += 6
                continue
            out.append({"n": "\n", "t": "\t", "'": "'", '"': '"', "\\": "\\", "@": "@", "?": "?"}.get(n, n))
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def to_ios_format(s):
    s = re.sub(r"%(\d+\$)?s", lambda m: "%" + (m.group(1) or "") + "@", s)
    return s


def ios_escape(s):
    return s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n").replace("\t", "\\t")


def load(dirpath):
    out = {}
    plurals = {}
    for f in sorted(glob.glob(os.path.join(dirpath, "strings*.xml"))):
        root = ET.parse(f).getroot()
        for el in root:
            name = el.get("name")
            if not name or el.get("translatable") == "false" and dirpath.endswith("values") is False:
                continue
            if el.tag == "string":
                out[name] = to_ios_format(android_unescape(text_of(el)))
            elif el.tag == "plurals":
                items = {it.get("quantity"): to_ios_format(android_unescape(text_of(it))) for it in el.findall("item")}
                plurals[name] = items
    return out, plurals


def main(res, dest):
    base, base_pl = load(os.path.join(res, "values"))
    for d, loc in LOCALES.items():
        strings, pl = load(os.path.join(res, d))
        merged = dict(base)
        merged.update(strings)
        plm = dict(base_pl)
        plm.update(pl)
        os.makedirs(os.path.join(dest, loc + ".lproj"), exist_ok=True)
        with open(os.path.join(dest, loc + ".lproj", "Android.strings"), "w", encoding="utf-8") as fh:
            fh.write("/* Android string kaynaklarından üretildi (ios/tools/strings-export). Elle düzenlemeyin. */\n")
            for k in sorted(merged):
                fh.write('"%s" = "%s";\n' % (k, ios_escape(merged[k])))
            # Çoğul biçimler: iOS'ta "other" biçimi tek anahtar olarak (adı .other ile) yazılır
            for k in sorted(plm):
                items = plm[k]
                for q, v in sorted(items.items()):
                    fh.write('"%s.%s" = "%s";\n' % (k, q, ios_escape(v)))
        print(loc, len(merged), "metin,", len(plm), "çoğul")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
