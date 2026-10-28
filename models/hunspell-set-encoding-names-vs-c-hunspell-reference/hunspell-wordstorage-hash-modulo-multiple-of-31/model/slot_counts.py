# Independent check of the repro's slot arithmetic (no Lucene code): Java String.hashCode() (int, wraps mod 2^32) and
# WordStorage's slot = Math.abs(hash % tableSize), for the repro's word list (TestWordStorageEntryCountMultipleOf961.words).
# Prints the busiest slot per (word count, table size); WordStorage throws when a slot exceeds 20 entries.
from collections import Counter
C, V = "bdfgklmnprstvz", "aeiou"
syl = lambda k: C[k % 14] + V[k // 14]

def words(count):
    out, plain, ing = [], 0, 0
    while len(out) < count:
        if len(out) % 16 == 15:
            out.append(syl(ing) + "ing"); ing += 1
        else:
            out.append(syl(plain // 70 % 70) + syl(plain % 70)); plain += 1
    return out

def java_hash(s):
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return h - (1 << 32) if h >= 1 << 31 else h

def slot(h, n):  # Math.abs(h % n) with Java's truncating remainder
    r = abs(h) % n
    return r

for count, n in [(960, 960), (961, 961), (962, 962), (961, 1922), (961, 1441), (961, 2883)]:
    ws = words(count)
    assert len(set(ws)) == len(ws)
    c = Counter(slot(java_hash(w), n) for w in ws)
    busiest = max(c.values())
    print(f"words={count} table={n} ing={sum(w.endswith('ing') for w in ws)} busiest_slot={busiest} "
          f"{'over the limit of 20' if busiest > 20 else 'within the limit'}")
