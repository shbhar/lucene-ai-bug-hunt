# Summarize a TLC JSON trace (tlc -dumpTrace json FILE): per step, action(context) + changed variables.
import json, sys
d = json.load(open(sys.argv[1]))['counterexample']
keys = sys.argv[2].split(',') if len(sys.argv) > 2 and sys.argv[2] else None
def short(v, n=300):
    s = json.dumps(v, separators=(',', ':'))
    return s if len(s) < n else s[:n] + '...'
def arrsum(arr):
    # arr: {node: {level: {n:[], s:[], srt:k}}}
    out = []
    for n, lv in sorted(arr.items(), key=lambda kv: int(kv[0])):
        for l, a in sorted(lv.items(), key=lambda kv: int(kv[0])):
            if a['srt'] == -1: continue
            out.append(f"{n}@L{l}:" + ",".join(f"{x}({s})" for x, s in zip(a['n'], a['s'])) + f"|srt={a['srt']}")
    return " ".join(out)
prev = d['state'][0][1]
print("  0 Init: " + arrsum(prev['arr']) + f" entry={prev['entry']} lvl={prev['lvl']}")
for i, (pre, act, post) in enumerate(d['action']):
    vals = post[1]
    ctx = act.get('context', {})
    ch = []
    for k, v in vals.items():
        if keys and k not in keys: continue
        if prev.get(k) != v:
            if k == 'arr': ch.append("arr=" + arrsum(v))
            elif k == 'lv':
                w = ctx.get('w')
                if w: 
                    p, q = prev['lv'][w-1], v[w-1]
                    diff = {kk: q[kk] for kk in q if p.get(kk) != q[kk] and kk != 's'}
                    if p.get('s') != q.get('s'): diff['s'] = {kk: q['s'][kk] for kk in ('level','k','res','cands','ret')}
                    ch.append(f"lv[{w}]=" + short(diff, 500))
            else: ch.append(f"{k}={short(v)}")
    print(f"{i+1:3d} {act['name']}{ctx}: " + "  ".join(ch))
    prev = vals
