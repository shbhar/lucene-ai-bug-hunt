"""compare.py DIR [--sets SEED] : classify Lucene vs C Hunspell 1.7.2 vs unmunch per micro-dictionary.

Per dictionary (single root R; optional second entry W/F forbidding W):
  LF  = Lucene getAllWordForms(R) (set)
  LS  = Lucene spell(), LA = Lucene analyzeSimpleWord() analyses ("stem/flags:pfx:sfx")
  CS  = C Hunspell_spell(), CST = C Hunspell_stem()
  UM  = unmunch output words (text before '/', since unmunch prints continuation classes as '/F|')
Classes (word-level):
  GEN_MISS_SELF   w not in LF, LS(w)=1 and some LA(w) has stem R  (generator misses a form Lucene itself accepts)
  GEN_EXTRA       w in LF and (LS(w)=0 or CS(w)=0)
  SPELL_C_ONLY    CS(w)=1, LS(w)=0
  SPELL_L_ONLY    LS(w)=1, CS(w)=0
  UNMUNCH_ONLY / LF_NOT_UNMUNCH   set differences UM vs LF
  ERR             Lucene threw (any phase-1 exception)
Writes DIR/compare.json (counts, per-class examples) and, with --sets, DIR/<id>.sets for phase 2.
"""
import json, os, random, sys, collections

def load(d, id_):
    res = dict(LF=set(), L={}, C={}, UM=set(), err=None)
    lp = os.path.join(d, id_ + ".lucene")
    for line in open(lp, encoding="utf-8"):
        t = line.rstrip("\n").split("\t")
        if t[0] == "ERR":
            res["err"] = "\t".join(t[1:])
        elif t[0] == "FORMS":
            res["LF"] = set(t[1].split()) if len(t) > 1 and t[1] else set()
        elif t[0] == "W":
            res["L"][t[1]] = (t[2], [a for a in (t[3].split("|") if len(t) > 3 and t[3] else [])])
    cp = os.path.join(d, id_ + ".c")
    ep = os.path.join(d, id_ + ".enc")
    enc = open(ep).read().strip() if os.path.exists(ep) else "utf-8"
    if os.path.exists(cp):
        for line in open(cp, encoding=enc, errors="replace"):
            t = line.rstrip("\n").split("\t")
            if len(t) >= 2:
                res["C"][t[0]] = (t[1], t[2].split() if len(t) > 2 and t[2] else [])
    up = os.path.join(d, id_ + ".unmunch")
    if os.path.exists(up):
        for line in open(up, encoding="utf-8", errors="replace"):
            w = line.rstrip("\n").split("/")[0]
            if w and not w.startswith("UNMUNCH_EXIT"):
                res["UM"].add(w)
    return res

def root_of(d, id_):
    e = open(os.path.join(d, id_ + ".root.dic"), encoding="utf-8").read().split("\n")[1]
    return e.split("/")[0], (e.split("/")[1] if "/" in e else "")

MULTI = False

def main():
    global MULTI
    d = sys.argv[1]
    MULTI = "--multi" in sys.argv
    sets_seed = int(sys.argv[sys.argv.index("--sets") + 1]) if "--sets" in sys.argv else None
    rng = random.Random(sets_seed or 0)
    ids = [l.strip() for l in open(os.path.join(d, "IDS")) if l.strip()]
    counts = collections.Counter(); dicts_with = collections.Counter(); ex = collections.defaultdict(list)
    for id_ in ids:
        r = load(d, id_)
        root, rflags = root_of(d, id_)
        if r["err"]:
            counts["ERR"] += 1; dicts_with["ERR"] += 1; ex["ERR"].append((id_, r["err"][:300])); continue
        found = collections.Counter()
        for w, (ls, la) in r["L"].items():
            cs, cst = r["C"].get(w, ("?", []))
            attributed = any(a.split("/")[0] == root for a in la)
            if w not in r["LF"] and ls == "1" and attributed:
                found["GEN_MISS_SELF"] += 1; ex["GEN_MISS_SELF"].append((id_, w, la))
            if w in r["LF"] and (ls != "1" or cs != "1"):
                found["GEN_EXTRA"] += 1; ex["GEN_EXTRA"].append((id_, w, ls, cs))
            if cs == "1" and ls != "1" and (not MULTI or root in cst):
                found["SPELL_C_ONLY"] += 1; ex["SPELL_C_ONLY"].append((id_, w, cst))
            if ls == "1" and cs == "0" and (not MULTI or attributed):
                found["SPELL_L_ONLY"] += 1; ex["SPELL_L_ONLY"].append((id_, w, la))
            if ls.startswith("EXC") or any(a.startswith("EXC") for a in la):
                found["LEXC"] += 1; ex["LEXC"].append((id_, w, ls, la))
        um_only = r["UM"] - r["LF"]; lf_only = r["LF"] - r["UM"]
        if um_only: found["UNMUNCH_ONLY"] += len(um_only); ex["UNMUNCH_ONLY"].append((id_, sorted(um_only)))
        if lf_only: found["LF_NOT_UNMUNCH"] += len(lf_only); ex["LF_NOT_UNMUNCH"].append((id_, sorted(lf_only)))
        for k, v in found.items():
            counts[k] += v; dicts_with[k] += 1
        if sets_seed is not None:
            cf = sorted(w for w, (cs, _) in r["C"].items() if cs == "1")
            lines = []
            if r["LF"]: lines.append(" ".join(sorted(r["LF"])))
            if cf and set(cf) != r["LF"]: lines.append(" ".join(cf))
            for _ in range(2):
                if len(cf) >= 2:
                    k = rng.randint(2, len(cf)); lines.append(" ".join(sorted(rng.sample(cf, k))))
            if lines:
                open(os.path.join(d, id_ + ".sets"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
    out = dict(dictionaries=len(ids), word_counts=dict(counts), dicts_with=dict(dicts_with),
               examples={k: v[:40] for k, v in ex.items()})
    json.dump(out, open(os.path.join(d, "compare.json"), "w"), indent=1, default=list)
    print(json.dumps(dict(dictionaries=len(ids), word_counts=dict(counts), dicts_with=dict(dicts_with))))

if __name__ == "__main__":
    main()
