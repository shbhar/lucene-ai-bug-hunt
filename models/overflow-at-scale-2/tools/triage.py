#!/usr/bin/env python3
"""Group the Scan.java inventory by origin expression and attach the source lines.

usage: python3 -I triage.py INV.tsv LUCENE_ROOT OUT.tsv [--tier A|B|all]
Each output row: tier, module, class, method, originKind, originLine, originSrc, sinks (kind@line;...), sinkSrcs
"""
import os
import re
import sys
from collections import defaultdict

inv, root, outp = sys.argv[1], sys.argv[2], sys.argv[3]
tier_sel = sys.argv[5] if len(sys.argv) > 5 and sys.argv[4] == '--tier' else 'all'

# index source files by package path + basename
src = {}
for d, _, fs in os.walk(os.path.join(root, 'lucene')):
    if '/src/java' not in d or '/sandbox/' in d or '/test-framework/' in d:
        continue
    for f in fs:
        if f.endswith('.java'):
            p = os.path.join(d, f)
            if '/src/java/' not in p:
                continue
            rel = p.split('/src/java/', 1)[1]
            src.setdefault(rel, p)

cache = {}


def lines_of(cls):
    top = cls.split('$')[0]
    rel = top + '.java'
    p = src.get(rel)
    if p is None:  # non-public top-level class in another file of the package
        pkg = os.path.dirname(top)
        simple = os.path.basename(top)
        for r, pp in src.items():
            if os.path.dirname(r) == pkg:
                txt = cache.get(pp)
                if txt is None:
                    txt = open(pp, encoding='utf-8', errors='replace').read().split('\n')
                    cache[pp] = txt
                if any(re.search(r'\b(class|interface|enum|record)\s+' + re.escape(simple) + r'\b', l) for l in txt):
                    p = pp
                    break
    if p is None:
        return None, None
    if p not in cache:
        cache[p] = open(p, encoding='utf-8', errors='replace').read().split('\n')
    return p, cache[p]


SIZING = re.compile(r'(ArrayUtil\.(grow|oversize|growExact|growNoCopy|copyOf)|java/util/Arrays\.copyOf|System\.arraycopy|'
                    r'ByteBuffer\.allocate|BitSet\.<init>|BytesRef\.<init>|IntsRef\.<init>|readBytes|writeBytes|readInts|'
                    r'readLongs|readFloats|PackedInts|DirectWriter|getWriter|getMutable|\.<init>\(I|newBuffer|allocate|'
                    r'IndexInput\.slice|setLength|FixedBitSet|grow)')

groups = defaultdict(lambda: {'sinks': set()})
for row in open(inv):
    mod, cls, meth, desc, sk, sline, sdet, ok, oline, odet = row.rstrip('\n').split('\t')
    g = groups[(mod, cls, meth, ok, oline, odet)]
    g['sinks'].add((sk, sline, sdet))


def tier(ok, sinks):
    kinds = {s[0] for s in sinks}
    calls = [s[2] for s in sinks if s[0] == 'call']
    sizing = any(SIZING.search(c) for c in calls)
    if ok in ('l2i-arith', 'i2s'):
        return 'A'
    if 'newarray' in kinds or 'i2l' in kinds or sizing:
        if ok in ('mul-var', 'mul-c', 'shl-k', 'shl-var', 'add-C', 'add-var'):
            return 'A'
    if ok in ('mul-var', 'mul-c', 'shl-k', 'shl-var', 'add-C') and kinds & {'aload-index', 'astore-index', 'cmp', 'cmp0'}:
        return 'B'
    return 'C'


rows = []
for (mod, cls, meth, ok, oline, odet), g in groups.items():
    t = tier(ok, g['sinks'])
    if tier_sel != 'all' and t != tier_sel:
        continue
    p, ls = lines_of(cls)

    def L(n):
        n = int(n)
        if ls is None or n <= 0 or n > len(ls):
            return '?'
        return ls[n - 1].strip()[:160]

    sinks = sorted(g['sinks'], key=lambda s: int(s[1]))
    rows.append((t, mod, cls, meth, ok, odet, oline, L(oline),
                 '; '.join(f'{s[0]}@{s[1]}{("(" + s[2].split("/")[-1][:60] + ")") if s[2] else ""}' for s in sinks),
                 ' || '.join(sorted({L(s[1]) for s in sinks if s[1] != oline}))[:400]))
rows.sort(key=lambda r: (r[0], r[1], r[2], int(r[6])))
with open(outp, 'w') as f:
    for r in rows:
        f.write('\t'.join(r) + '\n')
from collections import Counter
print(Counter(r[0] for r in rows))
