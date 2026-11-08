#!/usr/bin/env python3
"""v36 metinleri: Türkçe kaynak `values/strings_v36.xml` ve 6 dilin `values-<dil>/strings.xml` dosyalarındaki v36 bloğu
ile koddaki Türkçe adların çeviri tabloları (`resources/i18n/<dil>.tsv`). Metinler `strings_v36.py` (arayüz) ve
`code_v36.py` (kod adları: nesne, karakter, efekt, arka plan, şablon adları) dosyalarındadır.

Kullanım: python3 tools/i18n/v36/generate_v36.py   (android klasöründen ya da herhangi bir yerden)
"""
import importlib.util
import os
import re
import sys

sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
RES = os.path.join(ANDROID, "app", "src", "main", "res")
TSV = os.path.join(ANDROID, "app", "src", "main", "resources", "i18n")
LANGS = ["en", "zh", "ja", "ru", "ko", "ar"]
BEGIN = "    <!-- v36 başlangıç (tools/i18n/v36/generate_v36.py üretir) -->"
END = "    <!-- v36 son -->"


def load(name):
    path = os.path.join(HERE, name)
    if not os.path.exists(path):
        return None
    spec = importlib.util.spec_from_file_location(name[:-3], path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def esc(text):
    t = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    t = t.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    if t.startswith("@") or t.startswith("?"):
        t = "\\" + t
    return t


def line(name, text):
    fmt = ""
    if "%" in text and not re.search(r"%\d\$[sd]|%[sd]", text):
        fmt = ' formatted="false"'
    return f'    <string name="{name}"{fmt}>{esc(text)}</string>'


def main():
    strings = load("strings_v36.py")
    rows = strings.STRINGS if strings else []
    names = [r[0] for r in rows]
    dup = {n for n in names if names.count(n) > 1}
    if dup:
        sys.exit(f"yinelenen adlar: {sorted(dup)}")
    for r in rows:
        if len(r) != 8:
            sys.exit(f"eksik çeviri: {r[0]}")
    # Türkçe kaynak
    with open(os.path.join(RES, "values", "strings_v36.xml"), "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
        f.write("    <!-- v36: ayarlar sekmeleri, renk grupları, KVKK onayı, nesne/karakter/şablon kütüphaneleri, efektler -->\n")
        for r in rows:
            f.write(line(r[0], r[1]) + "\n")
        f.write("</resources>\n")
    # Diller
    for i, lang in enumerate(LANGS):
        path = os.path.join(RES, f"values-{lang}", "strings.xml")
        with open(path, encoding="utf-8") as f:
            text = f.read()
        text = re.sub(re.escape(BEGIN) + r".*?" + re.escape(END) + r"\n", "", text, flags=re.S)
        block = BEGIN + "\n" + "\n".join(line(r[0], r[2 + i]) for r in rows) + "\n" + END + "\n"
        idx = text.rindex("</resources>")
        text = text[:idx] + block + text[idx:]
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)
    # Kod adları
    code = load("code_v36.py")
    entries = code.entries() if code else []
    for i, lang in enumerate(LANGS):
        path = os.path.join(TSV, f"{lang}.tsv")
        table = {}
        order = []
        with open(path, encoding="utf-8") as f:
            for raw in f.read().split("\n"):
                if not raw or "\t" not in raw:
                    continue
                k, v = raw.split("\t", 1)
                if k not in table:
                    order.append(k)
                table[k] = v
        for e in entries:
            tr = e[0]
            val = e[1 + i]
            if not val:
                continue
            if tr not in table:
                order.append(tr)
            table[tr] = val
        with open(path, "w", encoding="utf-8") as f:
            f.write("\n".join(f"{k}\t{table[k]}" for k in order) + "\n")
    print(f"{len(rows)} arayüz metni, {len(entries)} kod adı yazıldı")


if __name__ == "__main__":
    main()
