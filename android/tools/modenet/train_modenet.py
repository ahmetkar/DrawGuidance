import numpy as np, sys, struct, time
d = sys.argv[1]; mode = sys.argv[2]; H = int(sys.argv[3]) if len(sys.argv) > 3 else 128
EPOCHS = int(sys.argv[4]) if len(sys.argv) > 4 else 30

def load(path):
    with open(path, 'rb') as f:
        n, dim = struct.unpack('>ii', f.read(8))
        X = np.frombuffer(f.read(n * dim * 4), dtype='>f4').astype(np.float32).reshape(n, dim)
        y = np.frombuffer(f.read(n * 4), dtype='>i4').astype(np.int64)
    return X, y

X, y = load(f'{d}/train_{mode}.bin'); Xt, yt = load(f'{d}/test_{mode}.bin')
K = len([l for l in open(f'{d}/keys_{mode}.txt').read().split('\n') if l.strip()])
D = X.shape[1]
mu = X.mean(0); sd = X.std(0) + 1e-3
Xs = (X - mu) / sd; Xts = (Xt - mu) / sd
rng = np.random.default_rng(30)
W1 = (rng.standard_normal((H, D)) * np.sqrt(2.0 / D)).astype(np.float32); b1 = np.zeros(H, np.float32)
W2 = (rng.standard_normal((K, H)) * np.sqrt(2.0 / H)).astype(np.float32); b2 = np.zeros(K, np.float32)
P = [W1, b1, W2, b2]; M = [np.zeros_like(p) for p in P]; V = [np.zeros_like(p) for p in P]
idx_by = [np.where(y == k)[0] for k in range(K)]
step = 0; B = 256; LS = 0.05; WD = 1e-4

def forward(x):
    h = np.maximum(x @ W1.T + b1, 0); z = h @ W2.T + b2
    z -= z.max(1, keepdims=True); e = np.exp(z); return h, e / e.sum(1, keepdims=True)

def evaluate():
    _, p = forward(Xts)
    order = np.argsort(-p, 1)[:, :3]
    return (order[:, 0] == yt).mean(), (order == yt[:, None]).any(1).mean()

t0 = time.time()
for ep in range(EPOCHS):
    sel = []
    for k in range(K):
        ids = idx_by[k]
        if len(ids) == 0: continue
        if len(ids) >= 400: sel.append(rng.choice(ids, 400, replace=False))
        else: sel.append(rng.choice(ids, max(len(ids), 200), replace=len(ids) < 200))
    sel = np.concatenate(sel); rng.shuffle(sel)
    lr = 2e-3 * 0.5 * (1 + np.cos(np.pi * ep / EPOCHS)) + 1e-5
    loss = 0.0
    for b in range(0, len(sel), B):
        bi = sel[b:b + B]; x = Xs[bi]; t = y[bi]
        # hafif girdi gürültüsü (genelleme)
        x = x + rng.standard_normal(x.shape).astype(np.float32) * 0.05
        h, p = forward(x)
        target = np.full_like(p, LS / K); target[np.arange(len(t)), t] += 1 - LS
        loss += -(target * np.log(p + 1e-9)).sum()
        g = (p - target) / len(t)
        gW2 = g.T @ h + WD * W2; gb2 = g.sum(0)
        dh = (g @ W2) * (h > 0)
        gW1 = dh.T @ x + WD * W1; gb1 = dh.sum(0)
        step += 1
        for i, gr in enumerate([gW1, gb1, gW2, gb2]):
            M[i] = 0.9 * M[i] + 0.1 * gr; V[i] = 0.999 * V[i] + 0.001 * gr * gr
            mh = M[i] / (1 - 0.9 ** step); vh = V[i] / (1 - 0.999 ** step)
            P[i] -= (lr * mh / (np.sqrt(vh) + 1e-8)).astype(np.float32)
    if ep % 5 == 4 or ep == EPOCHS - 1:
        a1, a3 = evaluate()
        print(f'{mode} dönem {ep+1}: kayıp {loss/len(sel):.3f} test ilk-1 {a1*100:.1f}% ilk-3 {a3*100:.1f}% ({time.time()-t0:.0f} sn)', flush=True)

# Ölçekleme ilk katmana katlanır
W1f = (W1 / sd[None, :]).astype(np.float32)
b1f = (b1 - (W1 * (mu / sd)[None, :]).sum(1)).astype(np.float32)
with open(f'{d}/weights_{mode}.bin', 'wb') as f:
    f.write(struct.pack('>iii', D, H, K))
    for a in (W1f, b1f, W2, b2): f.write(a.astype('>f4').tobytes())
# Katlama doğrulaması
h = np.maximum(Xt @ W1f.T + b1f, 0); z = h @ W2.T + b2
print(mode, 'katlanmış ağ ilk-1', (z.argmax(1) == yt).mean())
