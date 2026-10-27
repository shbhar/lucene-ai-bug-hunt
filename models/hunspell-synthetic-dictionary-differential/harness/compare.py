"""compare.py DIR [lsuffix] : spell/stem differences between Lucene (DIR/id.<lsuffix>, default lfix) and C Hunspell
(DIR/id.c, and DIR/id.cnh = C with the 3+-part suggest heuristic off).

Writes DIR/diffs.tsv: id, kind, word, lucene, c, cnh, lucene_roots, c_stems, mods.
kinds: SPELL_L1_C0 / SPELL_L0_C1 (spell differs from stock C; suffix _HEUR when C without the heuristic agrees with
Lucene), STEM (both accept, Lucene has roots and C's stems differ), EXC (Lucene threw), CRASH (C crashed), LOADERR.
Prints a summary per kind and per modifier set.
"""
import collections
import json
import os
import sys


def load(path):
    out = {}
    if not os.path.exists(path):
        return None
    with open(path, encoding="latin-1") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line:
                continue
            p = line.split("\t")
            if p[0] == "LOADERR":
                return {"__LOADERR__": p[1] if len(p) > 1 else ""}
            out[p[0]] = (p[1] if len(p) > 1 else "", p[2] if len(p) > 2 else "")
    return out


def main():
    d = sys.argv[1]
    lsuf = sys.argv[2] if len(sys.argv) > 2 else "lfix"
    man = json.load(open(os.path.join(d, "manifest.json")))
    mods = {x["id"]: "+".join(x["base"]) + ":" + "+".join(sorted(x["mods"])) for x in man["dicts"]}
    rows = []
    kinds = collections.Counter()
    bymods = collections.Counter()
    for x in man["dicts"]:
        i = x["id"]
        L = load(os.path.join(d, f"{i}.{lsuf}"))
        C = load(os.path.join(d, f"{i}.c"))
        N = load(os.path.join(d, f"{i}.cnh")) or {}
        if L is None or C is None:
            kinds["MISSING"] += 1
            continue
        if "__LOADERR__" in L:
            rows.append((i, "LOADERR", "", L["__LOADERR__"], "", "", "", "", mods[i]))
            kinds["LOADERR"] += 1
            continue
        for w, (ls, lr) in L.items():
            cs, cst = C.get(w, ("?", ""))
            ns = N.get(w, ("?", ""))[0]
            kind = None
            if cs == "CRASH":
                kind = "CRASH"
            elif ls.startswith("EXC") or lr.startswith("EXC"):
                kind = "EXC"
            elif ls != cs:
                kind = "SPELL_L%s_C%s" % (ls, cs)
                if ns == ls:
                    kind += "_HEUR"
            elif ls == "1" and lr and set(lr.split()) != set(cst.split()):
                kind = "STEM"
            if kind:
                kinds[kind] += 1
                bymods[(kind, mods[i])] += 1
                rows.append((i, kind, w, ls, cs, ns, lr, cst, mods[i]))
    with open(os.path.join(d, "diffs.tsv"), "w", encoding="latin-1") as f:
        f.write("id\tkind\tword\tlucene\tc\tcnh\tlucene_roots\tc_stems\tmods\n")
        for r in rows:
            f.write("\t".join(r) + "\n")
    print("kinds:", dict(kinds))
    ids_per = collections.defaultdict(set)
    for r in rows:
        ids_per[r[1]].add(r[0])
    print("dictionaries per kind:", {k: len(v) for k, v in ids_per.items()})
    for (k, m), c in sorted(bymods.items(), key=lambda t: -t[1])[:60]:
        print(f"{c:7d} {k:22s} {m}")


if __name__ == "__main__":
    main()
