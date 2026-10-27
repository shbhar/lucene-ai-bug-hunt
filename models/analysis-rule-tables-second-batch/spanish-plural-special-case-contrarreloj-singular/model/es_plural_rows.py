"""Independent reference for SpanishPluralStemmer (analysis-rule-tables-second-batch).

Rule rows recorded verbatim (paraphrased in English) from the page the class javadoc cites:
http://www.wikilengua.org/index.php/Plural_(formacion), revision oldid=187354, fetched 2026-10-27.
Row ids are the page's section numbers 3 and 4.1-4.27.

The stemmer is the inverse map plural -> singular, with accents removed first (its removeAccents
step, which the upstream vocabulary test pins).  Oracle used here:
  O1 (non-plural words): a word whose last letter is not 's' is never produced by any plural row
      except as an invariant plural (unchanged form), so stem(w) must equal strip(w).
  O2 (plural rows): stem(plural(w)) == strip(w) for each row's singular w; a mismatch is
      AMBIGUOUS if the accent-stripped plural string is also produced by another row from the
      singular the stemmer returned (inherent ambiguity of an inverse map), else UNAMBIGUOUS.
Usage: python3 -I es_plural_rows.py gen OUT.txt ; python3 -I es_plural_rows.py check JAVA_OUT.txt
"""
import itertools, sys, collections

ACC = {'à':'a','á':'a','â':'a','ä':'a','ò':'o','ó':'o','ô':'o','ö':'o','è':'e','é':'e','ê':'e','ë':'e',
       'ù':'u','ú':'u','û':'u','ü':'u','ì':'i','í':'i','î':'i','ï':'i'}
def strip(w): return ''.join(ACC.get(c, c) for c in w)

# rows: (row id, singular ending, function singular->list of plural forms the page allows)
def plus_s(w): return [w + 's']
def plus_es(w): return [w + 'es']
ROWS = [
  ('4.1 a', 'a', plus_s), ('4.1 a-stressed', 'á', plus_s), ('4.5 e', 'e', plus_s), ('4.5 e-stressed', 'é', plus_s),
  ('4.15 o', 'o', plus_s), ('4.15 o-stressed', 'ó', plus_s),
  ('4.9 i-unstressed', 'i', plus_s), ('4.9 i-stressed', 'í', lambda w: [w + 'es', w + 's']),
  ('4.21 u-unstressed', 'u', plus_s), ('4.21 u-stressed', 'ú', lambda w: [w + 'es', w + 's']),
  ('4.2 b', 'b', plus_s), ('4.3 c', 'c', plus_s), ('4.4 d', 'd', plus_es), ('4.6 f', 'f', plus_s),
  ('4.7 g', 'g', plus_s), ('4.10 j', 'j', plus_es), ('4.11 k', 'k', plus_s), ('4.12 l', 'l', plus_es),
  ('4.13 m', 'm', lambda w: [w + 's', w + 'es']), ('4.14 n', 'n', plus_es), ('4.16 p', 'p', plus_s),
  ('4.18 r', 'r', plus_es), ('4.19 s-aguda', 's', plus_es), ('4.20 t', 't', plus_s), ('4.22 v', 'v', plus_s),
  ('4.24 x-aguda', 'x', plus_es), ('4.25 y-traditional', 'y', plus_es),
  ('4.25 y-recent', 'y', lambda w: [w[:-1] + 'is']), ('4.26 z', 'z', lambda w: [w[:-1] + 'ces']),
]
# stems: a prefix (both sides of the len<4 guard) + every 0..2-letter tail over an alphabet that
# hits every letter test of the code (q, g, u, r, d, l, n, x, y, i, s, c, vowels)
ALPHA = 'aeioubcdglnqrstxyz'
PREFIX = ['', 'b', 'ba', 'bar', 'barc']
def stems():
    out = set()
    for p in PREFIX:
        for k in range(0, 3):
            for t in itertools.product(ALPHA, repeat=k):
                s = p + ''.join(t)
                if 1 <= len(s) <= 6: out.add(s)
    return sorted(out)

def gen(path):
    words = set()
    for st in stems():
        for rid, end, f in ROWS:
            sg = st + end
            words.add(sg)
            for pl in f(sg): words.add(pl)
    # the class's own special and invariant words and their singular/plural partners
    for w in ['yoes','noes','sies','clubes','faralaes','albalaes','itemes','albumes','sandwiches','relojes','bojes',
              'contrarreloj','contrarrelojes','carcajes','reloj','boj','carcaj','club','album','item','sandwich',
              'farala','albala','yo','no','si','CONTRARRELOJ','Contrarreloj']:
        words.add(w)
    with open(path, 'w', encoding='utf-8') as fo:
        for w in sorted(words): fo.write(w + '\n')
    print('generated', len(words), 'words from', len(stems()), 'stems x', len(ROWS), 'rows')

def unesc(s):
    out, i = [], 0
    while i < len(s):
        if s[i] == '\\' and s[i+1:i+2] == 'u': out.append(chr(int(s[i+2:i+6], 16))); i += 6
        else: out.append(s[i]); i += 1
    return ''.join(out)

def check(path):
    stem = {}
    for line in open(path, encoding='utf-8'):
        a, b = line.rstrip('\n').split('\t')
        stem[unesc(a)] = unesc(b)
    # O1
    # documented guard (code comment at the len < 4 check): words of 1-3 chars are returned unchanged,
    # before accent removal
    exp1 = lambda w: w if len(w) < 4 else strip(w)
    o1 = [(w, s) for w, s in stem.items() if w and w[-1] not in 'sS' and s != exp1(w)]
    print('O1 non-plural words checked:', sum(1 for w in stem if w and w[-1] not in 'sS'), 'violations:', o1)
    # O2
    def produced_by_some_row(got, pl):
        # is strip(pl) a plural form (per some row) of the singular `got` (any accent variant of its last letter)?
        if not got: return False
        for rid, end, f in ROWS:
            if strip(end) != got[-1]: continue
            for form in f(got[:-1] + end):
                if strip(form) == strip(pl): return True
        return False
    tally = collections.Counter(); amb = collections.Counter(); unamb = collections.defaultdict(list)
    guard = collections.Counter()
    for st in stems():
        for rid, end, f in ROWS:
            sg = st + end
            for pl in f(sg):
                if len(pl) < 4: guard[rid] += 1; continue   # documented len < 4 guard
                tally[(rid, 'checked')] += 1
                got = stem[pl]
                if got == strip(sg): continue
                if produced_by_some_row(got, pl): amb[rid] += 1
                else: unamb[rid].append((pl, got, strip(sg)))
    print('O2 per row: checked(len>=4) / ambiguous mismatches / unambiguous mismatches [examples: plural, stem, expected]')
    for rid, _, _ in ROWS:
        print(f'  {rid:22s} {tally[(rid,"checked")]:6d} {amb[rid]:6d} {len(unamb[rid]):6d}', unamb[rid][:4])

if __name__ == '__main__':
    {'gen': gen, 'check': check}[sys.argv[1]](sys.argv[2])
