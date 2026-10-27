"""Grammar-built synthetic COMPOUNDING dictionaries for the hunspell-synthetic-dictionary-differential differential (seeded, reproducible).

Each dictionary (.aff/.dic, ISO-8859-1, no SET line = the hunspell default):
  * FLAG type default / long / num; every logical flag below is rendered in that type.
  * a compounding base: COMPOUNDFLAG, COMPOUNDBEGIN/END(/MIDDLE), COMPOUNDRULE, or two of them;
  * 1-2 modifiers (the unit's directive list): COMPOUNDPERMITFLAG, COMPOUNDFORBIDFLAG, CHECKCOMPOUNDPATTERN without
    and with replacement (optionally with /flag conditions and the 0 = unmodified-stem pattern), CHECKCOMPOUNDDUP,
    CHECKCOMPOUNDREP (+REP), CHECKCOMPOUNDCASE, CHECKCOMPOUNDTRIPLE (+SIMPLIFIEDTRIPLE), COMPOUNDMIN, COMPOUNDWORDMAX,
    ONLYINCOMPOUND, NEEDAFFIX, CIRCUMFIX, FORBIDDENWORD, KEEPCASE, FORCEUCASE, ICONV, OCONV;
  * COMPOUNDMIN 1 unless COMPOUNDMIN is the modifier (roots are 1-3 letters);
  * at most one PFX class (R) and one SFX class (S) (both only for CIRCUMFIX or by chance), 1-2 rules, strip 0,
    affix text 1-2 letters, condition '.' or one letter, continuation flags drawn from the compound flags in use;
  * 1-4 roots of length 1-3 over {a,b,c} (one may be capitalised for the case directives), each with a random
    subset of the flags in use; rarely a homonym.
Words: every string of length <= 6 over {a,b,c} (1,092), plus every concatenation of 2-3 forms (root, root+affix),
plus case variants (title, upper) for case directives and ICONV-source variants for ICONV.

usage: gen_cpd.py OUTDIR N SEED [focus]
  focus: any (default) or ccp (CHECKCOMPOUNDPATTERN crossed with CIRCUMFIX or NEEDAFFIX on compound parts)
"""
import itertools
import json
import os
import random
import sys

ALPHA = "abc"
MODIFIERS = ["PERMIT", "FORBID", "CCP", "CCPR", "DUP", "REP", "CASE", "TRIPLE", "STRIPLE", "CMIN", "CMAX",
             "ONLYIN", "NEEDAFFIX", "CIRCUMFIX", "FORBIDDEN", "KEEPCASE", "FORCEUCASE", "ICONV", "OCONV"]
# logical flags: C compoundflag, B begin, M middle, E end, P permit, F forbidflag, O onlyincompound, N needaffix,
# X circumfix, W forbiddenword, K keepcase, U forceucase, G/H/I compoundrule flags, R prefix class, S suffix class
LOGICAL = "CBMEPFONXWKUGHIRS"


def render_flags(ftype):
    if ftype == "default":
        return {f: f for f in LOGICAL}, ""
    if ftype == "long":
        return {f: f + f.lower() for f in LOGICAL}, ""
    return {f: str(i + 1) for i, f in enumerate(LOGICAL)}, ","


def flagstr(fl, rmap, sep):
    return sep.join(rmap[f] for f in fl)


def rule_flag(f, rmap, ftype):
    # COMPOUNDRULE syntax: parentheses for long/num flags
    return rmap[f] if ftype == "default" else "(" + rmap[f] + ")"


def gen_dict(rng, focus):
    ftype = rng.choices(["default", "long", "num"], [6, 2, 2])[0]
    rmap, sep = render_flags(ftype)
    base = rng.choice([["CF"], ["CF"], ["BE"], ["BME"], ["RULE"], ["CF", "BE"], ["CF", "RULE"], ["BME", "RULE"]])
    if focus == "ccp":
        base = rng.choice([["CF"], ["CF"], ["BE"], ["BME"], ["CF", "BE"]])
        mods = [rng.choice(["CCP", "CCPR", "CCPR"]), rng.choice(["CIRCUMFIX", "NEEDAFFIX", "NEEDAFFIX", "CIRCUMFIX"])]
        if rng.random() < 0.3:
            mods.append(rng.choice(["PERMIT", "FORBID", "ONLYIN", "DUP", "FORBIDDEN"]))
    else:
        mods = rng.sample(MODIFIERS, rng.choice([1, 2, 2]))
    if "STRIPLE" in mods and "TRIPLE" not in mods:
        mods.append("TRIPLE")
    aff = []
    special = set()
    cflags = set()  # flags that may go on roots/continuations for compounding
    if "CF" in base:
        aff.append("COMPOUNDFLAG " + rmap["C"]); cflags.add("C")
    if "BE" in base or "BME" in base:
        aff.append("COMPOUNDBEGIN " + rmap["B"]); aff.append("COMPOUNDEND " + rmap["E"]); cflags |= {"B", "E"}
    if "BME" in base:
        aff.append("COMPOUNDMIDDLE " + rmap["M"]); cflags.add("M")
    if "RULE" in base:
        pats = ["GH", "G*H", "GH?", "G?H*", "GHI", "G*", "GH*I", "HG?", "G?H?I?", "GG"]
        rules = rng.sample(pats, rng.choice([1, 1, 2]))
        aff.append(f"COMPOUNDRULE {len(rules)}")
        for r in rules:
            out = ""
            for ch in r:
                out += ch if ch in "*?" else rule_flag(ch, rmap, ftype)
            aff.append("COMPOUNDRULE " + out)
        cflags |= {"G", "H"} | ({"I"} if any("I" in r for r in rules) else set())
    cmin = 1
    if "CMIN" in mods:
        cmin = rng.choice([1, 2, 3])
    aff.append(f"COMPOUNDMIN {cmin}")
    if "CMAX" in mods:
        aff.append(f"COMPOUNDWORDMAX {rng.choice([1, 2, 3])}")
    if "PERMIT" in mods:
        aff.append("COMPOUNDPERMITFLAG " + rmap["P"]); special.add("P")
    if "FORBID" in mods:
        aff.append("COMPOUNDFORBIDFLAG " + rmap["F"]); special.add("F")
    if "ONLYIN" in mods:
        aff.append("ONLYINCOMPOUND " + rmap["O"]); special.add("O")
    if "NEEDAFFIX" in mods:
        aff.append("NEEDAFFIX " + rmap["N"]); special.add("N")
    if "CIRCUMFIX" in mods:
        aff.append("CIRCUMFIX " + rmap["X"]); special.add("X")
    if "FORBIDDEN" in mods:
        aff.append("FORBIDDENWORD " + rmap["W"]); special.add("W")
    if "KEEPCASE" in mods:
        aff.append("KEEPCASE " + rmap["K"]); special.add("K")
    if "FORCEUCASE" in mods:
        aff.append("FORCEUCASE " + rmap["U"]); special.add("U")
    if "DUP" in mods:
        aff.append("CHECKCOMPOUNDDUP")
    if "CASE" in mods:
        aff.append("CHECKCOMPOUNDCASE")
    if "TRIPLE" in mods:
        aff.append("CHECKCOMPOUNDTRIPLE")
    if "STRIPLE" in mods:
        aff.append("SIMPLIFIEDTRIPLE")
    reps = []
    if "REP" in mods:
        aff.append("CHECKCOMPOUNDREP")
        for _ in range(rng.choice([1, 2])):
            reps.append((rng.choice(["a", "b", "c", "ab", "bc"]), rng.choice(["a", "b", "c", "cc", "ba"])))
        reps = [r for r in reps if r[0] != r[1]] or [("a", "b")]
        aff.append(f"REP {len(reps)}")
        for a, b in reps:
            aff.append(f"REP {a} {b}")
    iconv = []
    if "ICONV" in mods:
        iconv = [(rng.choice(["d", "e"]), rng.choice(["a", "b", "c", "ab"]))]
        aff.append("ICONV 1"); aff.append("ICONV %s %s" % iconv[0])
    if "OCONV" in mods:
        aff.append("OCONV 1"); aff.append("OCONV %s %s" % (rng.choice(["a", "b"]), rng.choice(["d", "e"])))
    ccp = []
    if "CCP" in mods or "CCPR" in mods:
        n = rng.choice([1, 1, 2])
        for k in range(n):
            withrep = "CCPR" in mods and (k == 0 or rng.random() < 0.5)
            end = rng.choice(["a", "b", "c", "ab", "bc", "0", "0", ""])
            beg = rng.choice(["a", "b", "c", "ba", "ca", ""])
            fpool = sorted(cflags | (special & {"N", "X", "O", "P"})) + ["S", "R"]
            ef = rng.choice([""] * 3 + fpool)
            bf = rng.choice([""] * 3 + fpool)
            if end == "" and not ef: end = rng.choice(["a", "0"])
            if beg == "" and not bf: beg = rng.choice(["a", "b"])
            es = end + ("/" + rmap[ef] if ef else "")
            bs = beg + ("/" + rmap[bf] if bf else "")
            if end == "0" and not ef and rng.random() < 0.5:
                es = "0"
            line = f"CHECKCOMPOUNDPATTERN {es} {bs}"
            if withrep:
                rep = rng.choice(["a", "b", "c", "ab", "cc", "abc"])
                line += " " + rep
            ccp.append(line)
        aff.append(f"CHECKCOMPOUNDPATTERN {len(ccp)}")
        aff.extend(ccp)
    # affix classes
    classes = []
    want_p = rng.random() < 0.35 or "CIRCUMFIX" in mods
    want_s = rng.random() < 0.6 or "CIRCUMFIX" in mods or "NEEDAFFIX" in mods
    contpool = sorted((cflags - {"G", "H", "I"}) | (special & {"P", "F", "O", "N", "X"}))
    if "RULE" in base:
        contpool += ["G", "H"]
    for kind, fl in (("PFX", "R"), ("SFX", "S")):
        if not (want_p if kind == "PFX" else want_s):
            continue
        rules = []
        for _ in range(rng.choice([1, 1, 2])):
            strip = "0" if rng.random() < 0.85 else rng.choice(ALPHA)
            affix = rng.choice(["a", "b", "c", "ab", "ca", "cb"])
            cond = rng.choice([".", ".", ".", "a", "b", "c"])
            if strip != "0":
                cond = strip
            cont = set()
            if contpool and rng.random() < 0.6:
                cont |= set(rng.sample(contpool, rng.choice([1, 1, 2]) if len(contpool) > 1 else 1))
            if "X" in special and rng.random() < 0.7:
                cont.add("X")
            if "N" in special and rng.random() < 0.3:
                cont.add("N")
            rules.append((strip, affix, "".join(sorted(cont)), cond))
        cross = "Y" if rng.random() < 0.85 else "N"
        classes.append((kind, fl, cross, rules))
        aff.append("")
        aff.append(f"{kind} {rmap[fl]} {cross} {len(rules)}")
        for strip, affix, cont, cond in rules:
            c = ("/" + flagstr(cont, rmap, sep)) if cont else ""
            aff.append(f"{kind} {rmap[fl]} {strip} {affix}{c} {cond}")
    if ftype == "long":
        aff.insert(0, "FLAG long")
    elif ftype == "num":
        aff.insert(0, "FLAG num")
    # roots
    nroots = rng.choice([1, 2, 2, 3, 3, 4])
    roots = set()
    while len(roots) < nroots:
        roots.add("".join(rng.choice(ALPHA) for _ in range(rng.choice([1, 2, 2, 3, 3]))))
    roots = sorted(roots)
    if ("CASE" in mods or "KEEPCASE" in mods or "FORCEUCASE" in mods) and rng.random() < 0.5:
        i = rng.randrange(len(roots))
        roots[i] = roots[i][0].upper() + roots[i][1:]
    affflags = [c[1] for c in classes]
    pool = sorted(cflags | (special & {"P", "F", "O", "N", "W", "K", "U"}) | set(affflags))
    entries = []
    for r in roots:
        fl = set()
        for f in pool:
            p = 0.55 if f in cflags else (0.6 if f in affflags else 0.3)
            if rng.random() < p:
                fl.add(f)
        entries.append((r, fl))
    if rng.random() < 0.1 and entries:
        r = rng.choice(roots)
        entries.append((r, set(rng.sample(pool, min(len(pool), rng.choice([1, 2])))) if pool else set()))
    dic = []
    for r, fl in entries:
        dic.append(r + ("/" + flagstr(sorted(fl), rmap, sep) if fl else ""))
    return dict(aff="\n".join(aff) + "\n", dic=dic, roots=roots, classes=classes, mods=mods, base=base,
                ftype=ftype, iconv=iconv, ccp=ccp, reps=reps)


def apply_rule(word, kind, strip, affix):
    s = "" if strip == "0" else strip
    if kind == "PFX":
        if not word.startswith(s): return None
        return affix + word[len(s):]
    if not word.endswith(s): return None
    return word[:len(word) - len(s)] + affix


def words_for(d):
    out = set()
    for n in range(1, 7):
        for t in itertools.product(ALPHA, repeat=n):
            out.add("".join(t))
    forms = set(d["roots"])
    for r in d["roots"]:
        for kind, fl, cross, rules in d["classes"]:
            for strip, affix, cont, cond in rules:
                w = apply_rule(r, kind, strip, affix)
                if w: forms.add(w)
        # prefix + suffix
        pr = [x for x in d["classes"] if x[0] == "PFX"]
        sr = [x for x in d["classes"] if x[0] == "SFX"]
        for p in pr:
            for s in sr:
                for a in p[3]:
                    for b in s[3]:
                        w = apply_rule(r, "SFX", b[0], b[1])
                        w = w and apply_rule(w, "PFX", a[0], a[1])
                        if w: forms.add(w)
    forms = sorted(forms)
    for k in (2, 3):
        for t in itertools.product(forms, repeat=k):
            w = "".join(t)
            if len(w) <= 12:
                out.add(w)
    for e in d["ccp"]:
        parts = e.split()
        if len(parts) == 5:  # replacement: also try every root-pair boundary with the replacement text
            rep = parts[4]
            for a in forms:
                for b in forms:
                    for cut_a in range(0, len(a) + 1):
                        for cut_b in range(0, len(b) + 1):
                            out.add(a[:cut_a] + rep + b[cut_b:])
    if any(m in d["mods"] for m in ("CASE", "KEEPCASE", "FORCEUCASE")):
        for w in list(out):
            if len(w) <= 9:
                out.add(w[0].upper() + w[1:])
                out.add(w.upper())
        for r in d["roots"]:
            if r[0].isupper():
                for t in itertools.product(forms, repeat=2):
                    out.add("".join(t))
                    out.add("".join(t).lower())
    for src, dst in d["iconv"]:
        for w in list(out):
            if dst in w and len(w) <= 9:
                out.add(w.replace(dst, src, 1))
                out.add(w.replace(dst, src))
    return sorted(out)


def main():
    outdir, n, seed = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
    focus = sys.argv[4] if len(sys.argv) > 4 else "any"
    rng = random.Random(seed)
    os.makedirs(outdir, exist_ok=True)
    manifest = []
    ids = []
    for i in range(n):
        d = gen_dict(rng, focus)
        name = f"s{seed}_{i:05d}"
        base = os.path.join(outdir, name)
        with open(base + ".aff", "w", encoding="latin-1") as f:
            f.write(d["aff"])
        with open(base + ".dic", "w", encoding="latin-1") as f:
            f.write(f"{len(d['dic'])}\n" + "\n".join(d["dic"]) + "\n")
        with open(base + ".words", "w", encoding="latin-1") as f:
            f.write("\n".join(words_for(d)) + "\n")
        manifest.append(dict(id=name, mods=d["mods"], base=d["base"], ftype=d["ftype"]))
        ids.append(name)
    with open(os.path.join(outdir, "IDS"), "w") as f:
        f.write("\n".join(ids) + "\n")
    with open(os.path.join(outdir, "manifest.json"), "w") as f:
        json.dump(dict(seed=seed, n=n, focus=focus, dicts=manifest), f, indent=0)


if __name__ == "__main__":
    main()
