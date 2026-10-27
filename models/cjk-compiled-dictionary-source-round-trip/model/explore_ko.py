# Exploratory scan of mecab-ko-dic CSV rows for the shapes named by lead 9.1.
# Mirrors (only to locate leads; the oracle is the Java TV harness) TokenInfoDictionaryEntryWriter.putEntry.
import sys, os, glob, collections, csv
src = sys.argv[1]
rows = []
for p in sorted(glob.glob(os.path.join(src, '*.csv'))):
    with open(p, encoding='utf-8', newline='') as f:
        for line in f:
            line = line.rstrip('\n').rstrip('\r') if False else line.rstrip('\n')
            e = next(csv.reader([line]))
            rows.append((os.path.basename(p), e, line))
print('rows', len(rows))
def leftpos(e):
    t = e[8]
    if t in ('*',) or t.lower() in ('compound',) or e[9] == '*':
        return e[4].upper()
    return e[9].upper()
types = collections.Counter(e[8] for _, e, _ in rows)
print('types', types)
# 1. compound/preanalysis concat
bad_concat = []
for fn, e, line in rows:
    t = e[8]
    if t == '*' or e[11] == '*': continue
    parts = [p.split('/') for p in e[11].split('+')]
    if any(len(p) != 3 for p in parts):
        print('SPLIT!=3', fn, line); continue
    if t.lower() == 'inflect': continue
    surf = [p[0] for p in parts if p[0].strip() != '']
    if ''.join(surf) != e[0].strip():
        bad_concat.append((fn, line))
print('compound/preanalysis rows whose parts do not concatenate to the surface:', len(bad_concat))
for x in bad_concat[:40]: print('  ', x)
# 2. left-id collisions
byleft = collections.defaultdict(collections.Counter)
for fn, e, line in rows:
    byleft[int(e[1])][(leftpos(e), e[5])] += 1
coll = {k: v for k, v in byleft.items() if len(v) > 1}
print('leftIds with >1 (leftPOS,semclass):', len(coll))
collpos = {k: v for k, v in byleft.items() if len(set(a for a, _ in v)) > 1}
print('leftIds with >1 leftPOS:', len(collpos))
for k in sorted(collpos)[:30]: print('  ', k, dict(collpos[k]))
# 3. readings on non-morpheme rows
rd = [(fn, line) for fn, e, line in rows if e[8] != '*' and e[7] not in ('*', e[0])]
print('non-MORPHEME rows with a reading different from surface:', len(rd))
for x in rd[:10]: print('  ', x)
rdm = [(fn, line) for fn, e, line in rows if e[8] == '*' and e[7] not in ('*', e[0])]
print('MORPHEME rows with reading:', len(rdm))
# 4. whitespace
ws = [(fn, line) for fn, e, line in rows if e[0] != e[0].strip()]
print('surface with surrounding whitespace:', len(ws), ws[:5])
wsp = [(fn, line) for fn, e, line in rows if e[11] != '*' and any(p.split('/')[0] != p.split('/')[0].strip() for p in e[11].split('+'))]
print('expression parts with surrounding whitespace:', len(wsp), wsp[:5])
# 5. big lengths
big = [(fn, line) for fn, e, line in rows if len(e[7]) > 127 or len(e[11]) > 250]
print('long reading/expr:', len(big))
# 6. right POS for MORPHEME/COMPOUND with e[9]/e[10] != '*'
rp = [(fn, line) for fn, e, line in rows if (e[8] in ('*', 'Compound')) and (e[9] != '*' or e[10] != '*')]
print('MORPHEME/COMPOUND rows with left/right POS columns set:', len(rp)); 
for x in rp[:20]: print('  ', x)
# 7. INFLECT/PREANALYSIS with e[9]=='*'
ip = [(fn, line) for fn, e, line in rows if e[8] in ('Inflect','Preanalysis') and e[9] == '*']
print('Inflect/Preanalysis rows with left POS *:', len(ip), ip[:5])
# 8. rows with more than 12 fields
print('field counts', collections.Counter(len(e) for _, e, _ in rows))
