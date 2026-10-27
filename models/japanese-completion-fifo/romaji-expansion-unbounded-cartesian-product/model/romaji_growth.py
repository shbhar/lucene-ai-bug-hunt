"""Exact output-count model of KatakanaRomanizer.romanize (validated against the real class on 2,682,144 inputs,
section S3 of JcfTranslationValidation: COUNT_FORMULA_MISMATCH = 0, REFROM_MISMATCH = 0).

Model (KatakanaRomanizer.java:103-169 on main @ ea57360912ed):
  greedy longest-match segmentation s = k1 k2 ... km (+ unmatched remnant), each key ki has c(ki) candidates;
  romanize(s) returns the full product: count(s) = c(k1) * ... * c(km); the remnant only adds a suffix.
Claims checked here, from romaji_map.txt alone:
  (1) the worst per-character growth rate max_k c(k)^(1/len(k)) and the keys that reach it;
  (2) for every single-char key x with c(x) >= 2, x+x is not itself a key and no key starts with x+x,
      so greedy segments x^n into n copies of x and count(x^n) = c(x)^n exactly;
  (3) distinct vs emitted outputs for x^n (duplicates arise when one candidate is a prefix-power of another).
Usage: python3 -I romaji_growth.py <romaji_map.txt>
"""
import sys, itertools
m = {}
for line in open(sys.argv[1], encoding="utf-8"):
    if line.startswith("#"):
        continue
    cols = line.strip().split(",")
    if len(cols) < 2:
        continue
    m[cols[0]] = cols[1:]
print(f"keys={len(m)} max_key_len={max(map(len, m))}")
rate = sorted(((len(v) ** (1 / len(k)), k, v) for k, v in m.items()), reverse=True)
print("top growth rates per input char:")
for r, k, v in rate[:8]:
    print(f"  {k!r}: {len(v)} candidates over {len(k)} char(s) -> {r:.3f}^n   {v}")
singles = {k: v for k, v in m.items() if len(k) == 1 and len(v) >= 2}
print(f"single-char keys with >=2 candidates: {len(singles)}: {''.join(sorted(singles))}")
bad = [x for x in singles if any(key.startswith(x + x) for key in m)]
print(f"(2) single-char ambiguous keys x where some key starts with x+x: {bad or 'none'}")
for x in ["ン", "シ", "ヂ"] + [k for k in sorted(singles) if len(singles[k]) == 3 and k not in "ンシヂ"][:2]:
    c = len(m[x])
    for n in (8, 16, 20, 24, 32):
        outs = {""}
        emitted = 1
        if n <= 16:
            for _ in range(n):
                outs = {o + cand for o in outs for cand in m[x]}
            distinct = len(outs)
        else:
            distinct = None
        emitted = c ** n
        print(f"  {x}x{n}: emitted romaji = {c}^{n} = {emitted:,}" + (f", distinct = {distinct:,}" if distinct else ""))
