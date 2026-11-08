import json, os, sys, random
raw, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
rnd = random.Random(30)
tot = {'train': 0, 'test': 0}; strokes = []
for fn in sorted(os.listdir(raw)):
    if not fn.endswith('.ndjson'): continue
    ds = []
    with open(os.path.join(raw, fn)) as f:
        for line in f:
            try: o = json.loads(line)
            except Exception: continue
            if not o.get('recognized'): continue
            d = o.get('drawing')
            if not isinstance(d, list) or not d: continue
            ok = all(isinstance(s, list) and len(s) == 2 and len(s[0]) == len(s[1]) and len(s[0]) >= 1 for s in d)
            if ok: ds.append(d)
    rnd.shuffle(ds)
    test, train = ds[:150], ds[150:850]
    base = fn[:-7]
    for name, part in (('train', train), ('test', test)):
        with open(os.path.join(out, f'{base}.{name}.ndjson'), 'w') as g:
            for d in part: g.write(json.dumps(d, separators=(',', ':')) + '\n')
        tot[name] += len(part)
    strokes += [len(d) for d in ds]
print(tot)
import collections
c = collections.Counter(min(s, 6) for s in strokes)
print('çizgi sayısı dağılımı (6=6+):', sorted(c.items()))
