import json,os,re,sys
import os
ANDROID=os.environ.get('DG_ANDROID', os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')))
import xml.etree.ElementTree as ET
APP=os.path.join(ANDROID,'app/src/main')
LANGS=['en','zh','ja','ru','ko','ar']
BRAND={'en':('DrawGuidance','Draw Guidance'),'zh':('绘画向导','绘画向导'),'ja':('お絵かきガイド','お絵かきガイド'),
       'ru':('Гид рисования','Гид рисования'),'ko':('그리기 가이드','그리기 가이드'),'ar':('دليل الرسم','دليل الرسم')}
src=json.load(open('src.json'))
cur={n:t for n,t,f in src['res']}
def read_out(path):
    m={}
    if not os.path.exists(path): return m
    for l in open(path,encoding='utf-8'):
        l=l.rstrip('\n')
        if '\t' in l:
            k,v=l.split('\t',1); m[k]=v
    return m
def xml_escape(v):
    out=[]; i=0
    while i<len(v):
        c=v[i]
        if c=='\\':
            if i+1<len(v) and v[i+1]=='n': out.append('\\n'); i+=2; continue
            out.append('\\\\'); i+=1; continue
        if c=="'": out.append("\\'")
        elif c=='"': out.append('\\"')
        elif c=='&': out.append('&amp;')
        elif c=='<': out.append('&lt;')
        elif c=='>': out.append('&gt;')
        else: out.append(c)
        i+=1
    s=''.join(out)
    if s[:1] in ('@','?'): s='\\'+s
    return s
litids=json.load(open('litids.json'))['lits']
w2lit={}
for l in open('wave2/chunks/lit7.tsv',encoding='utf-8'):
    i,c,t=l.rstrip('\n').split('\t',2); w2lit[i]=t
curlits={t for t,l in src['lits']}
report={}
for lang in LANGS:
    res={}
    for f in ['res1','res2','res3','res4']: res.update(read_out(f'out/{lang}/{f}.tsv'))
    res.update(read_out(f'wave2/out/{lang}/res5.tsv'))
    res['app_name'],res['welcome_title']=BRAND[lang]
    missing=[n for n in cur if n not in res]
    d=f'{APP}/res/values-{lang}'; os.makedirs(d,exist_ok=True)
    with open(f'{d}/strings.xml','w',encoding='utf-8') as fp:
        fp.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- v34 otomatik üretildi (çeviri tabloları): elle düzenlemeyin, kaynak Türkçe metinler values/ altında -->\n<resources>\n')
        for n in cur:
            if n in res:
                v=res[n]
                fmt='' if ('%' not in v or re.search(r'%(\d+\$)?[sdf]', cur[n])) else ' formatted="false"'
                fp.write(f'    <string name="{n}"{fmt}>{xml_escape(v)}</string>\n')
        fp.write('</resources>\n')
    lit={}
    for f in ['lit1','lit2','lit3','lit4','lit5','lit6']:
        for k,v in read_out(f'out/{lang}/{f}.tsv').items():
            if k in litids: lit[litids[k]]=v
    for k,v in read_out(f'wave2/out/{lang}/lit7.tsv').items():
        if k in w2lit: lit[w2lit[k]]=v
    os.makedirs(f'{APP}/resources/i18n',exist_ok=True)
    n=0
    with open(f'{APP}/resources/i18n/{lang}.tsv','w',encoding='utf-8') as fp:
        for k in sorted(lit):
            v=lit[k]
            if k not in curlits or v==k: continue
            fp.write(f'{k}\t{v}\n'); n+=1
    report[lang]=(len(cur)-len(missing),len(missing),n,len([k for k in curlits if k not in lit]))
for l,r in report.items(): print(l,'res ok',r[0],'missing',r[1],'lit entries',r[2],'lit untranslated',r[3])
