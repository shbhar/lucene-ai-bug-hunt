"""Grammar-built micro-dictionaries for the hunspell-wordform-generator-munch-differential word-form differential (seeded, reproducible).

Each dictionary: 2-3 affix classes over flags A B C (PFX or SFX, cross product Y or N, 1-2 rules each),
rules: strip in {0,a,b} (length <= 1, so conditions always have >= as many patterns as the strip: avoids the
known LFV-374 short-condition bug), affix in {c,d,ca,dc,a,b,0}, condition from a dash-free set (avoids LFV-376),
optional continuation flags (other classes and the special flags in use), optional COMPLEXPREFIXES, FULLSTRIP,
NEEDAFFIX X, CIRCUMFIX Z, ONLYINCOMPOUND O, FORBIDDENWORD F (a second .dic line forbidding one generated word),
COMPOUNDBEGIN/COMPOUNDMIDDLE/COMPOUNDEND U/V/W (only on continuation or root flags; single-root dictionaries).
One root word over {a,b}. Candidate words = the root plus every string reachable by applying <= 3 rules
(<= 2 prefixes, <= 2 suffixes) honouring only the strip text (conditions and flags ignored): a superset of every
affixed form hunspell(5) can derive from the root.

usage: gen_micro.py OUTDIR N SEED [family]
  family: all (default), nocont (no continuation/special flags: the subset unmunch implements),
          rich (2-4 classes, 1-3 rules, colliding affix texts, a homonym entry for the root with other flags)
"""
import os, random, sys, itertools, json

ROOTS = ["ab", "ba", "aab", "abb", "bab", "aba"]
STRIPS = ["0", "0", "0", "a", "b"]
AFFIXES = ["c", "d", "ca", "dc", "a", "b", "0"]
CONDS = [".", ".", ".", "a", "b", "[ab]", "[^a]", "[^b]", "ab", "[ab]b", "a[^b]", "b."]

def gen_dict(rng, family):
    rich = family == "rich"
    nclasses = rng.choice([2, 3, 4]) if rich else rng.choice([2, 2, 3])
    flags = ["A", "B", "C", "D"][:nclasses]
    opts = {}
    special = []
    if family in ("all", "rich"):
        opts["COMPLEXPREFIXES"] = rng.random() < 0.2
        opts["FULLSTRIP"] = rng.random() < 0.1
        if rng.random() < 0.3: special.append("X")   # NEEDAFFIX
        if rng.random() < 0.2: special.append("Z")   # CIRCUMFIX
        if rng.random() < 0.15: special.append("O")  # ONLYINCOMPOUND
        if rng.random() < 0.15: special.append(rng.choice(["U", "V", "W"]))  # COMPOUNDBEGIN/MIDDLE/END
        if rng.random() < 0.2: special.append("F")   # FORBIDDENWORD
    classes = []
    for f in flags:
        kind = rng.choice(["PFX", "SFX"])
        cross = rng.choice(["Y", "N"])
        rules = []
        for _ in range(rng.choice([1, 2, 3]) if rich else rng.choice([1, 1, 2])):
            strip = rng.choice(STRIPS)
            affix = rng.choice(["c", "d", "c", "dc"]) if rich else rng.choice(AFFIXES)
            if strip == "0" and affix == "0" and rng.random() < 0.7:
                affix = rng.choice(["c", "d"])
            cond = rng.choice(CONDS)
            cont = ""
            if family in ("all", "rich") and rng.random() < 0.45:
                pool = [g for g in flags if g != f] + [s for s in special if s != "F"]
                k = rng.choice([1, 1, 2])
                cont = "".join(sorted(set(rng.sample(pool, min(k, len(pool)))))) if pool else ""
            rules.append((strip, affix, cont, cond))
        classes.append((kind, f, cross, rules))
    rootflags = set(rng.sample(flags, rng.choice([1, 2, nclasses])))
    if "X" in special and rng.random() < 0.4: rootflags.add("X")
    if "O" in special and rng.random() < 0.2: rootflags.add("O")
    for s in ("U", "V", "W"):
        if s in special and rng.random() < 0.5: rootflags.add(s)
    root = rng.choice(ROOTS)
    homonym = None
    if rich and rng.random() < 0.4:
        hf = set(rng.sample(flags + [s for s in special if s in "XOUVW"], rng.choice([1, 2])))
        homonym = "".join(sorted(hf))
    return dict(classes=classes, opts=opts, special=special, root=root, rootflags="".join(sorted(rootflags)),
                homonym=homonym)

def aff_text(d):
    out = ["SET UTF-8"]
    if d["opts"].get("COMPLEXPREFIXES"): out.append("COMPLEXPREFIXES")
    if d["opts"].get("FULLSTRIP"): out.append("FULLSTRIP")
    names = {"X": "NEEDAFFIX", "Z": "CIRCUMFIX", "O": "ONLYINCOMPOUND", "F": "FORBIDDENWORD",
             "U": "COMPOUNDBEGIN", "V": "COMPOUNDMIDDLE", "W": "COMPOUNDEND"}
    for s in d["special"]:
        out.append(f"{names[s]} {s}")
    for kind, f, cross, rules in d["classes"]:
        out.append("")
        out.append(f"{kind} {f} {cross} {len(rules)}")
        for strip, affix, cont, cond in rules:
            out.append(f"{kind} {f} {strip} {affix}{'/' + cont if cont else ''} {cond}")
    return "\n".join(out) + "\n"

def apply_rule(word, kind, strip, affix):
    s = "" if strip == "0" else strip
    a = "" if affix == "0" else affix
    if kind == "PFX":
        if not word.startswith(s): return None
        return a + word[len(s):]
    if not word.endswith(s): return None
    return word[:len(word) - len(s)] + a

def candidates(d):
    rules = [(k, s, a) for k, f, c, rs in d["classes"] for (s, a, cont, cond) in rs]
    seen = {d["root"]}
    frontier = [(d["root"], 0, 0)]
    for _ in range(3):
        nxt = []
        for w, np_, ns in frontier:
            for k, s, a in rules:
                if k == "PFX" and np_ >= 2: continue
                if k == "SFX" and ns >= 2: continue
                r = apply_rule(w, k, s, a)
                if r is None or r == "": continue
                nxt.append((r, np_ + (k == "PFX"), ns + (k == "SFX")))
                seen.add(r)
        frontier = nxt
    return sorted(seen)

def main():
    outdir, n, seed = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
    family = sys.argv[4] if len(sys.argv) > 4 else "all"
    rng = random.Random(seed)
    os.makedirs(outdir, exist_ok=True)
    manifest = []
    for i in range(n):
        d = gen_dict(rng, family)
        cands = candidates(d)
        dic = [f"{d['root']}/{d['rootflags']}" if d["rootflags"] else d["root"]]
        if d.get("homonym") is not None:
            dic.append(f"{d['root']}/{d['homonym']}")
        forb = None
        if "F" in d["special"]:
            others = [c for c in cands if c != d["root"]]
            if others:
                forb = rng.choice(others)
                dic.append(f"{forb}/F")
        name = f"d{i:05d}"
        base = os.path.join(outdir, name)
        open(base + ".aff", "w").write(aff_text(d))
        open(base + ".dic", "w").write(f"{len(dic)}\n" + "\n".join(dic) + "\n")
        open(base + ".words", "w").write("\n".join(cands) + "\n")
        open(base + ".root.dic", "w").write(f"1\n{dic[0]}\n")
        manifest.append(dict(id=name, root=d["root"], forbidden=forb))
    json.dump(dict(seed=seed, n=n, family=family, dicts=manifest), open(os.path.join(outdir, "manifest.json"), "w"), indent=0)

if __name__ == "__main__":
    main()
