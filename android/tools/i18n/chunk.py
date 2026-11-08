import json,os,re
d=json.load(open('src.json'))
os.makedirs('chunks',exist_ok=True)
SKIP={'app_name','welcome_title'}
res=[(n,f,t) for n,t,f in d['res'] if n not in SKIP]
lits=[(f"L{i+1:04d}",loc.split(':')[0],t) for i,(t,loc) in enumerate(d['lits'])]
json.dump({'lits':{f"L{i+1:04d}":t for i,(t,loc) in enumerate(d['lits'])}},open('litids.json','w'),ensure_ascii=False)
def write(prefix,rows,n):
    size=(len(rows)+n-1)//n
    for k in range(n):
        part=rows[k*size:(k+1)*size]
        with open(f'chunks/{prefix}{k+1}.tsv','w') as fp:
            for i,c,t in part:
                assert '\t' not in t and '\n' not in t, (i,t)
                fp.write(f"{i}\t{c}\t{t}\n")
write('res',res,4); write('lit',lits,6)
for f in sorted(os.listdir('chunks')): print(f, sum(1 for _ in open('chunks/'+f)))
