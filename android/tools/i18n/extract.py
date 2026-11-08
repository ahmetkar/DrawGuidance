import re,os,sys,json,collections
import os as _os
_A=_os.environ.get('DG_ANDROID', _os.path.abspath(_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), '..', '..')))
ROOTS=[_os.path.join(_A,p) for p in ['app/src/main/java','core/content/src/main/kotlin','core/model/src/main/kotlin','core/shapenet/src/main/kotlin']]
pat=re.compile(r'(?<![\w$])"((?:[^"\\\n]|\\.)*)"')
TR=re.compile('[çğıöşüÇĞİÖŞÜâîû]')
def natural(t):
    if not re.search(r'[A-Za-zçğıöşüÇĞİÖŞÜ]',t): return False
    if '\\\\' in t or t.startswith('\\'): return False        # LaTeX
    if re.fullmatch(r'[a-z0-9_]+([.\-/][a-z0-9_]+)+',t): return False  # ids/paths
    if re.fullmatch(r'[a-z0-9_]+',t) and not TR.search(t): return False  # snake ids
    if re.fullmatch(r'[A-Z0-9_]+',t): return False  # CONSTS
    if re.fullmatch(r'#?[0-9A-Fa-f]{6,8}',t): return False
    if re.search(r'https?://|\.(png|jpg|json|svg|gz|tsv|glb|gltf|pdf|txt)\b',t): return False
    if t.startswith('@') or '@{' in t: return False
    if re.fullmatch(r'[a-z][A-Za-z0-9]*',t) and not TR.search(t): return False  # camelCase ids
    if re.fullmatch(r'%[^ ]*',t): return False
    if TR.search(t): return True
    if ' ' in t and re.search(r'[a-z]{2,}',t): return True
    if re.fullmatch(r'[A-ZÇĞİÖŞÜ][a-zçğıöşü]+',t): return True   # Capitalized word
    if re.fullmatch(r'[A-ZÇĞİÖŞÜ][a-zçğıöşü]+[\-:]?\s?[A-Za-z0-9 ()\-]*',t): return True
    return False
out=collections.OrderedDict(); dyn=[]
for root in ROOTS:
  for dp,dn,fn in os.walk(root):
    for f in sorted(fn):
      if not f.endswith('.kt'): continue
      p=os.path.join(dp,f)
      src=open(p,encoding='utf-8').read()
      for i,line in enumerate(src.split('\n')):
        s=line.strip()
        if s.startswith('//') or s.startswith('*') or s.startswith('/*') or s.startswith('import ') or s.startswith('package '): continue
        if re.search(r'\bLog\.[dwiev]\(|println\(|@Query\(|execSQL\(|testTag\(|@SerialName|Regex\(|\.toRegex',s): continue
        code=re.sub(r'//.*$','',line) if '"' not in line.split('//')[-1] else line
        for m in pat.finditer(line):
          t=m.group(1)
          if not natural(t): continue
          if '$' in t: dyn.append((p,i+1,t)); continue
          out.setdefault(t,[]).append(f"{f}:{i+1}")
json.dump({'static':out,'dyn':dyn},open(sys.argv[1],'w'),ensure_ascii=False,indent=0)
print('static',len(out),'dyn',len(dyn))
