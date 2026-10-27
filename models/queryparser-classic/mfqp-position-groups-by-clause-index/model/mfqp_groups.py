"""Z3 model of MultiFieldQueryParser#getFieldQuery(String field=null, String text, boolean quoted).

Model element -> Java (lucene/queryparser/src/java/org/apache/lucene/queryparser/classic/):
  shape[f] in {NULL, TERM, POS, ALT}, size[f]  -> the per-field query returned by
        super.getFieldQuery(fields[i], text, quoted)  (MultiFieldQueryParser.java:150), i.e. QueryBuilder:
        NULL  : no token (stopword)                         QueryBuilder.createFieldQuery numTokens == 0
        TERM  : one TermQuery / SynonymQuery / PhraseQuery  analyzeTerm / analyzeBoolean / analyzePhrase
        POS   : BooleanQuery, one clause per term position, occur = default operator
                                                            analyzeMultiBoolean / analyzeGraphBoolean
        ALT   : BooleanQuery of SHOULD alternatives          analyzeGraphPhrase (quoted, or autoGeneratePhraseQueries)
  m[f][i] : "clause i of field f matches the document" (TERM uses m[f][0])
  mfqp()  : MultiFieldQueryParser.java:145-205 (maxTerms loop, termNum grouping) + getMultiTermPositionQuery 220-234
            + getMultiFieldQuery 306-315
  old()   : the same before #16446 (c71ef11c549): always getMultiFieldQuery (SHOULD of the groups)
  fixed() : the candidate fix (fix/fix.diff): position grouping only if all non-null field queries are POS of equal size
  ref()   : documented semantics (class javadoc 35-55: "(title:term1 body:term1) (title:term2 body:term2)",
            AND: "all the query's terms must appear, but it doesn't matter in what fields they appear"):
            - every field query TERM: OR over fields
            - every field query POS of the same size k (same analysis in every field): per position i, OR over fields;
              positions combined with the default operator (the #16443/#16446 contract)
            - otherwise (alternatives, or fields that analyse the text differently): the text matches in field f
              exactly when field f's own query matches (QueryParser on that field); OR over fields.
Bounds: F <= 3 fields, clause count k in [2, KMAX] (KMAX = 3). The documented limits do not bound these.
"""
import itertools
import json
import sys
from z3 import (And, Bool, BoolVal, If, Implies, Int, Not, Or, Solver, sat, unsat, Sum)

NULL, TERM, POS, ALT = 0, 1, 2, 3
NAMES = {NULL: "NULL", TERM: "TERM", POS: "POS", ALT: "ALT"}
KMAX = 3


def any_(xs):
    xs = list(xs)
    return Or(xs) if xs else BoolVal(False)


def all_(xs):
    xs = list(xs)
    return And(xs) if xs else BoolVal(True)


def model(F, shared):
    shape = [Int(f"shape_{f}") for f in range(F)]
    size = [Int(f"size_{f}") for f in range(F)]
    m = [[Bool(f"m_{f}_{i}") for i in range(KMAX)] for f in range(F)]
    andop = Bool("andop")
    cons = []
    for f in range(F):
        cons += [shape[f] >= NULL, shape[f] <= ALT]
        cons += [If(Or(shape[f] == POS, shape[f] == ALT), And(size[f] >= 2, size[f] <= KMAX), size[f] == 1)]
    if shared:  # one analyzer for every field: identical query structure
        for f in range(1, F):
            cons += [shape[f] == shape[0], size[f] == size[0]]
    cons += [any_(shape[f] != NULL for f in range(F))]

    def is_bq(f):
        return Or(shape[f] == POS, shape[f] == ALT)

    def nclauses(f):
        return If(shape[f] == NULL, 0, If(is_bq(f), size[f], 1))

    def clause(f, i):  # nestedClauses.get(i).query() matches / TermQuery at termNum 0
        has = If(is_bq(f), i < size[f], And(shape[f] == TERM, i == 0))
        return And(shape[f] != NULL, has, m[f][i])

    def present(f, i):
        return And(shape[f] != NULL, If(is_bq(f), i < size[f], i == 0))

    # maxTerms = max over fields of the clause count (MultiFieldQueryParser.java:148-159)
    mt = 0
    for k in range(1, KMAX + 1):
        mt = If(any_(nclauses(f) >= k for f in range(F)), k, mt)
    groups = []  # (exists, value) per termNum
    for i in range(KMAX):
        exists = And(i < mt, any_(present(f, i) for f in range(F)))
        groups.append((exists, any_(clause(f, i) for f in range(F))))

    def combine(op_and):
        # BooleanQuery of the existing groups with MUST (op_and) or SHOULD
        must = all_(Implies(e, v) for e, v in groups)
        should = any_(And(e, v) for e, v in groups)
        return If(op_and, must, should)

    mfqp = If(mt > 1, combine(andop), any_(And(e, v) for e, v in groups))
    old = any_(And(e, v) for e, v in groups)

    def field_sem(f):
        allpos = all_(Implies(i < size[f], m[f][i]) for i in range(KMAX))
        anypos = any_(And(i < size[f], m[f][i]) for i in range(KMAX))
        return If(shape[f] == NULL, False,
                  If(shape[f] == TERM, m[f][0],
                     If(shape[f] == ALT, anypos, If(andop, allpos, anypos))))

    nonnull = [shape[f] != NULL for f in range(F)]
    all_term = all_(Implies(nonnull[f], shape[f] == TERM) for f in range(F))
    k0 = Int("k0")
    aligned_pos = And(all_(Implies(nonnull[f], And(shape[f] == POS, size[f] == k0)) for f in range(F)))
    per_pos = []
    for i in range(KMAX):
        per_pos.append((i < k0, any_(And(nonnull[f], m[f][i]) for f in range(F))))
    aligned_val = If(andop, all_(Implies(e, v) for e, v in per_pos), any_(And(e, v) for e, v in per_pos))
    whole = any_(field_sem(f) for f in range(F))
    # k0 is the common size if aligned (pin it to the first non-null field's size)
    first_size = size[F - 1]
    for f in reversed(range(F)):
        first_size = If(nonnull[f], size[f], first_size)
    cons += [k0 == first_size]
    ref = If(all_term, any_(And(nonnull[f], m[f][0]) for f in range(F)), If(aligned_pos, aligned_val, whole))

    # candidate fix: termPositionsAligned (all non-null are POS-BooleanQuery of equal size; positional occur)
    fix_aligned = aligned_pos
    fixed = If(And(mt > 1, Not(fix_aligned)), whole, mfqp)
    return dict(shape=shape, size=size, m=m, andop=andop, cons=cons, mfqp=mfqp, old=old, ref=ref, fixed=fixed, mt=mt)


def classes(F, shared, impl, limit=50):
    """Enumerate the (shape, size, operator) classes where impl != ref."""
    M = model(F, shared)
    s = Solver()
    s.add(M["cons"])
    s.add(M[impl] != M["ref"])
    out = []
    while len(out) < limit and s.check() == sat:
        mo = s.model()
        sh = [mo.eval(x).as_long() for x in M["shape"]]
        sz = [mo.eval(x).as_long() for x in M["size"]]
        op = bool(mo.eval(M["andop"], model_completion=True))
        mm = [[bool(mo.eval(M["m"][f][i], model_completion=True)) for i in range(KMAX)] for f in range(F)]
        out.append({"shapes": [NAMES[x] + (f"({sz[i]})" if x in (POS, ALT) else "") for i, x in enumerate(sh)],
                     "op": "AND" if op else "OR",
                     "witness_m": mm,
                     impl: bool(mo.eval(M[impl])), "ref": bool(mo.eval(M["ref"]))})
        # block this structural class
        s.add(Or([M["shape"][f] != sh[f] for f in range(F)] + [M["size"][f] != sz[f] for f in range(F)]
                 + [M["andop"] != op]))
    return out, s.check() if len(out) < limit else "limit"


def main():
    report = {}
    for F in (1, 2, 3):
        for shared in (True, False):
            for impl in ("mfqp", "old", "fixed"):
                cl, last = classes(F, shared, impl)
                key = f"F={F} analyzer={'shared' if shared else 'per-field'} impl={impl}"
                report[key] = {"disagreeing_classes": cl, "final_check": str(last)}
                summary = sorted({(tuple(c["shapes"]), c["op"]) for c in cl})
                print(f"{key}: {len(cl)} disagreeing (shape,op) classes; exhausted={str(last)}")
                for sh, op in summary[:12]:
                    print(f"    {op} {list(sh)}")
    # vacuity probe: a deliberately wrong mutant (combine positions with SHOULD even under AND, i.e. old())
    # must disagree with ref for shared POS under AND (the #16443 bug that #16446 fixed)
    M = model(2, True)
    s = Solver(); s.add(M["cons"]); s.add(M["andop"], M["shape"][0] == POS, M["old"] != M["ref"])
    report["vacuity_old_disagrees_on_shared_POS_AND"] = str(s.check())
    print("vacuity: old (pre-#16446) disagrees with ref on shared POS under AND:", report["vacuity_old_disagrees_on_shared_POS_AND"])
    # sanity: ref is satisfiable both ways
    s = Solver(); s.add(M["cons"]); s.add(M["ref"]); r1 = s.check()
    s = Solver(); s.add(M["cons"]); s.add(Not(M["ref"])); r2 = s.check()
    report["ref_nontrivial"] = [str(r1), str(r2)]
    print("ref satisfiable true/false:", r1, r2)
    json.dump(report, open(sys.argv[1], "w"), indent=1)


if __name__ == "__main__":
    main()
