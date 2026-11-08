import re,os,json,sys
import os
ANDROID=os.environ.get('DG_ANDROID', os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')))
import xml.etree.ElementTree as ET
RES=os.path.join(ANDROID,'app/src/main/res/values')
def decode_xml(s):
    out=[];i=0
    while i<len(s):
        c=s[i]
        if c=='\\' and i+1<len(s):
            n=s[i+1]
            if n=='n': out.append('\\n'); i+=2; continue
            if n=='t': out.append(' '); i+=2; continue
            if n=='u': out.append(chr(int(s[i+2:i+6],16))); i+=6; continue
            out.append(n); i+=2; continue
        out.append(c); i+=1
    t=''.join(out)
    if len(t)>=2 and t[0]=='"' and t[-1]=='"': t=t[1:-1]
    return t
res=[]
for f in sorted(os.listdir(RES)):
    if not f.startswith('strings'): continue
    root=ET.parse(os.path.join(RES,f)).getroot()
    for e in root:
        if e.tag!='string' or e.get('translatable')=='false': continue
        raw=ET.tostring(e,encoding='unicode')
        inner=re.sub(r'^<string[^>]*>','',raw); inner=re.sub(r'</string>\s*$','',inner)
        inner=inner.replace('&lt;','<').replace('&gt;','>').replace('&amp;','&').replace('&quot;','"').replace('&apos;',"'")
        res.append((e.get('name'),decode_xml(inner),f))
names=[r[0] for r in res]; assert len(names)==len(set(names)), 'dup'
d=json.load(open('lits.json'))
SKIPF={'Glyphs.kt','FormulaParser.kt','Lexicon.kt','DrawGuidanceApi.kt','AppLog.kt'}
lits=[]
for k,v in d['static'].items():
    f=v[0].split(':')[0]
    if f in SKIPF and all(x.split(':')[0] in SKIPF for x in v): continue
    if '`' in k or re.search(r'\b(CREATE|SELECT|INSERT|UPDATE|INTEGER|TEXT NOT NULL)\b',k): continue
    if len(k.strip())<2: continue
    # Kotlin kaçışları: \n kalsın, \" \' \\ çözülür
    t=k.replace('\\"','"').replace("\\'","'").replace('\\t',' ')
    lits.append((t,v[0]))
json.dump({'res':res,'lits':lits},open('src.json','w'),ensure_ascii=False)
print(len(res),len(lits))
