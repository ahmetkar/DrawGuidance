import json, os, sys
src = sys.argv[1]
for fn in os.listdir(src):
    if not fn.endswith('.ndjson'): continue
    with open(os.path.join(src, fn)) as f, open(os.path.join(src, fn[:-7] + '.txt'), 'w') as g:
        for line in f:
            d = json.loads(line)
            g.write(';'.join(' '.join(f'{x},{y}' for x, y in zip(s[0], s[1])) for s in d) + '\n')
