"""Reduce the A-check mismatches of ExhaustiveDiff to the minimal ones (no contiguous proper substring mismatches).
usage: python3 -I minimal.py <tsv> <tokenizer> [exclude-hex,...]"""
import sys
tsv, tok = sys.argv[1], sys.argv[2]
excl = set(sys.argv[3].split(',')) if len(sys.argv) > 3 and sys.argv[3] else set()
rows = {}
for line in open(tsv, encoding='utf-8'):
    f = line.rstrip('\n').split('\t')
    if f[0] != 'A' or f[1] != tok:
        continue
    key = tuple(f[2].strip('<>').split())
    if excl & set(key):
        continue
    rows[key] = (f[3], f[4])
mins = []
for k in rows:
    n = len(k)
    sub = False
    for L in range(1, n):
        for i in range(0, n - L + 1):
            if k[i:i + L] in rows:
                sub = True
                break
        if sub:
            break
    if not sub:
        mins.append(k)
mins.sort(key=lambda k: (len(k), k))
print(f"{tok}: mismatches={len(rows)} minimal={len(mins)} (excluded {sorted(excl)})")
for k in mins:
    e, g = rows[k]
    print(' '.join(k), '| exp', e, '| got', g)
