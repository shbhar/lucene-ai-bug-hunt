# compare lucene.tsv (UTF-8 word\tverdict) with c.tsv (native-encoded word\tverdict), same order
import sys, os, json
root = sys.argv[1]
tot = {}
out = []
for n in sorted(os.listdir(root)):
    d = os.path.join(root, n)
    lp, cp = os.path.join(d, 'lucene.tsv'), os.path.join(d, 'c.tsv')
    if not os.path.exists(cp):
        continue
    L = open(lp, encoding='utf-8').read().split('\n')
    if L and L[0].startswith('LOADERR'):
        out.append((n, 'LOADERR', L[0])); continue
    L = [l for l in L if l]
    C = [l for l in open(cp, 'rb').read().split(b'\n') if l]
    if len(L) != len(C):
        out.append((n, 'LENMISMATCH', '%d vs %d' % (len(L), len(C)))); continue
    nd = 0
    for l, c in zip(L, C):
        w, lv = l.rsplit('\t', 1)
        cv = c.rsplit(b'\t', 1)[1].decode()
        if lv != cv:
            nd += 1
            out.append((n, 'lucene=%s c=%s' % (lv, cv), w))
    tot[n] = (len(L), nd)
for o in out:
    print('\t'.join(o))
print('fixtures=%d words=%d differing=%d fixtures_with_diffs=%d' % (len(tot), sum(v[0] for v in tot.values()), sum(v[1] for v in tot.values()), sum(1 for v in tot.values() if v[1])))
