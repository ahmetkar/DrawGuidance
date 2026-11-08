#!/usr/bin/env python3
"""Kullanım: python3 validate.py <dil> — out/<dil>/*.tsv dosyalarını chunks/ ile karşılaştırır."""
import sys,os,re,collections
lang=sys.argv[1]; base=os.path.dirname(os.path.abspath(__file__))
PH=re.compile(r'%(\d+\$)?[sdf]|\{\d+\}')
bad=0; total=0
for f in sorted(os.listdir(os.path.join(base,'chunks'))):
    src=[l.rstrip('\n').split('\t') for l in open(os.path.join(base,'chunks',f),encoding='utf-8') if l.strip()]
    p=os.path.join(base,'out',lang,f)
    if not os.path.exists(p): print('EKSIK DOSYA',f); bad+=1; continue
    out={}
    for n,l in enumerate(open(p,encoding='utf-8')):
        l=l.rstrip('\n')
        if not l.strip(): continue
        if '\t' not in l: print(f'{f}:{n+1} SEKME YOK: {l[:60]}'); bad+=1; continue
        k,v=l.split('\t',1)
        if '\t' in v: print(f'{f}:{n+1} FAZLA SEKME'); bad+=1
        if k in out: print(f'{f}:{n+1} TEKRAR {k}'); bad+=1
        out[k]=v
    for k,c,t in src:
        total+=1
        if k not in out: print(f'{f} EKSIK {k}: {t[:60]}'); bad+=1; continue
        v=out[k]
        if not v.strip(): print(f'{f} BOS {k}'); bad+=1; continue
        if collections.Counter(m.group(0) for m in PH.finditer(t))!=collections.Counter(m.group(0) for m in PH.finditer(v)):
            print(f'{f} YER TUTUCU {k}: {t[:50]} => {v[:50]}'); bad+=1
        if t.count('\\n')!=v.count('\\n') and t.count('\\n')>0 and v.count('\\n')==0:
            print(f'{f} SATIR SONU KAYIP {k}'); bad+=1
    extra=set(out)-{s[0] for s in src}
    if extra: print(f'{f} FAZLA KIMLIK {sorted(extra)[:5]}'); bad+=1
print(f'{lang}: {total} satır, {bad} sorun')
sys.exit(1 if bad else 0)
