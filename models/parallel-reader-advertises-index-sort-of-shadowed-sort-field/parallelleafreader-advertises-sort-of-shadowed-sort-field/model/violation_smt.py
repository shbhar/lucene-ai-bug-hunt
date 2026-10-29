"""parallel-reader-advertises-index-sort-of-shadowed-sort-field SMT leg: can a legal set of values violate the index sort a parallel reader advertises?

Input: compositions-main.jsonl from harness/CompositionEnum.java (stock jar observations: accepted or not,
the advertised sort, and the sub-reader that owns s and t in the composite with the kind it serves).
For every accepted composition with an advertised sort S, Z3 decides:

  exists values v[r][f][d] (r = sub-reader, f in {s, t} with doc values in r, d = 0..N-1)
  such that  (A) every sub-reader r that declares an index sort S_r has its own values in S_r order
                 (IndexWriter guarantees this for a sorted index; documents are added in the same order
                 to every index, which is the class javadoc's alignment rule, so doc d is the same document
                 in every sub-reader), and
             (B) the values the composite serves (owner's values for each field of S; a constant when the
                 owner serves no doc values or no reader has the field) violate S between two adjacent docs.

SAT = the advertised sort can be false for legal inputs (a witness is printed and replayed on the stock jar by
harness/WitnessReplay.java); UNSAT = the advertised sort is always true for this structure.

Mapping to Java (lucene/core/src/java/org/apache/lucene/index/ParallelLeafReader.java @ a9779ef3c193):
  advertised sort        <- constructor lines 130-143 (first non-null leaf sort; rejects a different non-null one)
  owner of a field       <- lines 154-170 (fieldToReader: "first reader having a given field wins")
  served doc values      <- getNumericDocValues, line ~396 (fieldToReader.get(field))
  (A)                    <- IndexWriter sorts each flushed/merged segment by its index sort (IndexingChain/Sorter)
  lexicographic order    <- SortField.Type.LONG ascending, missing value = constant (all-missing field ties)

Fix predicates evaluated on the same structures (soundness = never advertise when SAT):
  FIX_OWNER   advertise S iff some sub-reader R declares S and every field of S is owned by R or by no reader
  FIX_NARROW  (the plan's first sketch) advertise S iff every field of S is unowned or owned by a reader that
              declares S
  FIX_DVOWNER like FIX_OWNER but ownership taken from the reader that serves doc values (ignores a shadowing
              field of another kind)
"""
import json
import sys
from collections import Counter

import z3

N = 3
LO, HI = 0, 4
# vacuity mutant: "noA" drops constraint (A); then every structure whose served sort tuple has a doc-values
# component must become SAT, and the control (sort field kept in the sorted reader) must flip to SAT.
MUTANT = sys.argv[4] if len(sys.argv) > 4 else ""


def lex_lt(a, b):
    if not a:
        return z3.BoolVal(False)
    return z3.Or(a[0] < b[0], z3.And(a[0] == b[0], lex_lt(a[1:], b[1:])))


def parse_owner(o):
    if o == "-1":
        return (-1, None)
    if o == "?":
        return (-2, None)
    return (int(o[:-1]), o[-1])


def solve(readers, adv, owners):
    k = len(readers)
    v = {}
    cons = []
    for r in range(k):
        for fi, f in enumerate("st"):
            if readers[r]["fields"][fi] == "d":
                for d in range(N):
                    x = z3.Int(f"v_{r}_{f}_{d}")
                    v[(r, f, d)] = x
                    cons += [x >= LO, x <= HI]
    const = z3.IntVal(-1)

    def own_tuple(r, sort, d):
        return [v.get((r, f, d), const) for f in sort]

    for r in range(k):
        srt = readers[r]["sort"]
        if srt != "none" and MUTANT != "noA":
            for d in range(N - 1):
                cons.append(z3.Not(lex_lt(own_tuple(r, srt, d + 1), own_tuple(r, srt, d))))

    def served(d):
        out = []
        for f in adv:
            pos, kind = owners[f]
            out.append(v[(pos, f, d)] if kind == "d" else const)
        return out

    viol = z3.Or([lex_lt(served(d + 1), served(d)) for d in range(N - 1)])
    s = z3.Solver()
    s.add(cons)
    s.add(viol)
    res = s.check()
    if res == z3.sat:
        m = s.model()
        wit = {}
        for (r, f, d), x in v.items():
            wit.setdefault(str(r), {}).setdefault(f, [None] * N)[d] = m.eval(x, model_completion=True).as_long()
        return "sat", wit
    return str(res), None


def fix_owner(readers, adv, owners, dv_only=False):
    for r, rd in enumerate(readers):
        if rd["sort"] != adv:
            continue
        ok = True
        for f in adv:
            pos, kind = owners[f]
            if dv_only and kind != "d":
                pos = -1
            if pos not in (-1, r):
                ok = False
        if ok:
            return True
    return False


def fix_narrow(readers, adv, owners):
    for f in adv:
        pos, kind = owners[f]
        if pos >= 0 and readers[pos]["sort"] != adv:
            return False
    return True


def main():
    src, out_path, wit_path = sys.argv[1], sys.argv[2], sys.argv[3]
    stats = Counter()
    cache = {}
    witnesses = []
    unsound = {"FIX_OWNER": [], "FIX_NARROW": [], "FIX_DVOWNER": []}
    conservative = {"FIX_OWNER": 0, "FIX_NARROW": 0, "FIX_DVOWNER": 0}
    examples = {}
    with open(src) as fh:
        for line in fh:
            c = json.loads(line)
            stats["compositions"] += 1
            leaf, comp = c["leaf"], c["composite"]
            if leaf != comp:
                stats["leaf_composite_disagree"] += 1
            if not leaf["accepted"]:
                stats["rejected_IAE"] += 1
                continue
            stats["accepted"] += 1
            adv = leaf["sort"]
            if adv == "none":
                stats["accepted_no_sort"] += 1
                continue
            stats["accepted_with_sort"] += 1
            owners = {"s": parse_owner(leaf["own_s"]), "t": parse_owner(leaf["own_t"])}
            if any(p == -2 for p, _ in owners.values()):
                stats["owner_unknown"] += 1
                continue
            readers = c["readers"]
            key = json.dumps([readers, adv, {f: owners[f] for f in adv}], sort_keys=True)
            if key not in cache:
                cache[key] = solve(readers, adv, owners)
                stats["smt_queries"] += 1
            res, wit = cache[key]
            stats["smt_" + res] += 1
            violating = res == "sat"
            if violating:
                shape = (c["k"], adv, "".join(owners[f][1] or "-" for f in adv),
                         tuple(r["sort"] for r in readers))
                if len(witnesses) < 100000:
                    witnesses.append({"readers": readers, "adv": adv, "owners": {f: list(owners[f]) for f in "st"},
                                      "witness": wit})
                cls = classify(readers, adv, owners)
                stats["violating_class:" + cls] += 1
                examples.setdefault(cls, {"readers": readers, "adv": adv,
                                          "owners": {f: list(owners[f]) for f in "st"}, "witness": wit})
            for name, pred in (("FIX_OWNER", lambda: fix_owner(readers, adv, owners)),
                               ("FIX_NARROW", lambda: fix_narrow(readers, adv, owners)),
                               ("FIX_DVOWNER", lambda: fix_owner(readers, adv, owners, dv_only=True))):
                keep = pred()
                if keep and violating:
                    if len(unsound[name]) < 5:
                        unsound[name].append({"readers": readers, "adv": adv,
                                              "owners": {f: list(owners[f]) for f in "st"}, "witness": wit})
                    stats[name + "_unsound"] += 1
                if not keep and not violating:
                    conservative[name] += 1
                if keep:
                    stats[name + "_keeps_sort"] += 1
    report = {"mutant": MUTANT, "N_docs": N, "value_domain": [LO, HI], "stats": dict(stats), "smt_cache_entries": len(cache),
              "fix_unsound_examples": unsound, "fix_conservative_drops": conservative,
              "violating_class_examples": examples}
    with open(out_path, "w") as fh:
        json.dump(report, fh, indent=1, sort_keys=True)
    with open(wit_path, "w") as fh:
        for w in witnesses:
            fh.write(json.dumps(w) + "\n")
    with open(out_path.replace(".json", "-verdicts.jsonl"), "w") as fh:
        for key, (res, _) in cache.items():
            fh.write(json.dumps({"key": key, "res": res}) + "\n")
    print(json.dumps(report["stats"], indent=1, sort_keys=True))
    print("conservative drops (advertised sort true but predicate drops it):", conservative)


def classify(readers, adv, owners):
    """Which trigger family a violating structure belongs to."""
    owner_sorts = set()
    kinds = set()
    for f in adv:
        pos, kind = owners[f]
        if pos >= 0:
            owner_sorts.add(readers[pos]["sort"])
            kinds.add(kind)
    if "none" in owner_sorts or any(s not in ("none", adv) for s in owner_sorts):
        base = "owner-does-not-declare-sort"
    else:
        owner_set = {owners[f][0] for f in adv if owners[f][0] >= 0}
        base = "owners-declare-sort-but-differ" if len(owner_set) > 1 else "single-declaring-owner"
    if "p" in kinds:
        base += "+non-dv-shadow"
    return base


if __name__ == "__main__":
    main()
