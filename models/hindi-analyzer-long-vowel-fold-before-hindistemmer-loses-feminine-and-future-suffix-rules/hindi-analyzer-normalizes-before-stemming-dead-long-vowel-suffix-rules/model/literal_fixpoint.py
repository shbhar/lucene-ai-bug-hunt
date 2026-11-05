"""Exhaustive check over every suffix literal of HindiStemmer.stem (all 67, read from the source file given as argv[1])
against the unconditional character rewrites of HindiNormalizer.normalize (argv[2]).

Model (file:line mapping, main 5adff78bb168):
  HindiStemmer.java:31-110   groups by removal length 5,4,3,2,1; a literal L in group k fires iff the token ends with L
                             and len > k+1 (the guard of its group).
  HindiNormalizer.java:48-174 one left-to-right pass; every 'case X: s[i] = Y' with no condition rewrites X
                             unconditionally, every 'case X: delete' removes X; only U+0928 (dead n) is conditional.
  HindiAnalyzer.java:126-129 HindiNormalizationFilter runs before HindiStemFilter on every non-keyword token.
A literal that contains a character the normalizer always rewrites or deletes can never be a suffix of a normalized
token, so its rule never fires inside HindiAnalyzer (dead). A dead literal is harmless when its normalized spelling
N(L) is a literal of the same group (the same removal then happens through N(L)); otherwise the rule is lost.
"""
import re, sys
stem_src = open(sys.argv[1], encoding="utf-8").read()
norm_src = open(sys.argv[2], encoding="utf-8").read()

# groups: "// k" comment followed by endsWith literals until return len - k
groups = {}
for m in re.finditer(r"//\s*(\d)\s*\n(.*?)return len - (\d);", stem_src, re.S):
    k = int(m.group(1)); assert k == int(m.group(3))
    groups[k] = re.findall(r'endsWith\(buffer, len, "([^"]+)"\)', m.group(2))

# unconditional rewrites: parse switch cases in order; a run of 'case' labels shares the body
rewrite, delete = {}, set()
body = norm_src[norm_src.index("switch (s[i])"):]
for m in re.finditer(r"((?:case '\\u([0-9A-F]{4})':\s*(?://[^\n]*\n\s*)?)+)(.*?)break;", body, re.S):
    labels = re.findall(r"case '\\u([0-9A-F]{4})'", m.group(1))
    b = m.group(3)
    if "if (" in b:
        continue                       # conditional (dead n + virama): not an unconditional rewrite
    if "delete(" in b:
        for l in labels: delete.add(chr(int(l, 16)))
        continue
    t = re.search(r"s\[i\] = '\\u([0-9A-F]{4})'", b)
    if t:
        for l in labels: rewrite[chr(int(l, 16))] = chr(int(t.group(1), 16))

def N(s):
    return "".join("" if c in delete else rewrite.get(c, c) for c in s)

total = sum(len(v) for v in groups.values())
changed, harmless, lost = [], [], []
for k in sorted(groups, reverse=True):
    for L in groups[k]:
        if N(L) == L:
            continue
        changed.append(L)
        (harmless if N(L) in groups[k] else lost).append((k, L, N(L)))
print(f"literals={total} groups={ {k: len(v) for k, v in sorted(groups.items(), reverse=True)} }")
print(f"unconditional rewrites={len(rewrite)} deletes={len(delete)}")
print(f"changed by the normalizer={len(changed)} harmless(twin in same group)={len(harmless)} lost={len(lost)}")
for k, L, n in harmless: print(f"  harmless k={k} {L} -> {n}")
for k, L, n in lost: print(f"  LOST     k={k} {L} -> {n}")
# sanity: the normalizer never outputs a character it rewrites (fixed point), so a dead literal cannot reappear
assert not (set(rewrite.values()) & (set(rewrite) | delete)), "a rewrite target is itself rewritten"
print("check: no rewrite target is itself rewritten or deleted -> one pass reaches a fixed point: OK")
