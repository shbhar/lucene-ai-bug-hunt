"""
pointrange-rewrite-fieldexists-ignores-leaves-without-points solver model: soundness of PointRangeQuery.rewrite's CELL_INSIDE_QUERY branch (main e1faeba08bd).

Mapping table (model element -> Java line, lucene/core/src/java/org/apache/lucene/search/):
  covers_all (range contains global [min,max])  PointRangeQuery.java:559-560 (relate(globalMin, globalMax) == CELL_INSIDE_QUERY)
  some point exists (else MatchNoDocs)           PointRangeQuery.java:555-557
  can_match_all                                  PointRangeQuery.java:574-576 reader.maxDoc() == PointValues.getDocCount(reader, field)
  can_fe ORIGINAL                                PointRangeQuery.java:578-592 (false iff some leaf has a FieldInfo with
                                                 DocValuesType NONE, no norms and vector dimension 0)
  can_fe FIXED                                   the same, plus: false if a leaf's FieldInfo has getPointDimensionCount() == 0
  FieldExistsQuery per-leaf doc set              FieldExistsQuery.java:170-219 (no FieldInfo -> none; norms first, then
                                                 vectors, then doc values; none of the three -> IllegalStateException)
  truth                                          the documents with a point (every point is inside the range)
  schema STRICT per index                        LUCENE-9334 (FieldExistsQuery.java:144-145 comment): every document that has the
                                                 field has all of the field's data structures, and every leaf of ONE index that
                                                 has the field has the same FieldInfo flags
  MultiReader over separately built indexes      each leaf may come from a different index, so each leaf has its own schema
  LENIENT (LFV-490 route)                        an 8.x-created index rewritten by 9.x: per-document structures are any subset

Deleted documents are not modelled: both the truth and every rewritten form are filtered by the same live docs.
Bounds: L leaves x J documents per leaf. Each query is a bounded (small-scope) check; the property is per document
(a document's membership depends only on its own structures, its leaf's flags and the two reader-global booleans), so a
counterexample needs at most two leaves, which the bound covers.
"""
import itertools, json, sys
from z3 import Bool, Bools, And, Or, Not, Implies, If, Solver, sat, unsat, BoolVal, Sum, Sum as zsum

L, J = 3, 2

def build(schema, cond, mutant=None):
    s = Solver()
    ex = [Bool(f"ex{l}") for l in range(L)]
    P = [Bool(f"P{l}") for l in range(L)]; D = [Bool(f"D{l}") for l in range(L)]
    N = [Bool(f"N{l}") for l in range(L)]; V = [Bool(f"V{l}") for l in range(L)]
    has = [[Bool(f"h{l}_{j}") for j in range(J)] for l in range(L)]
    p = [[Bool(f"p{l}_{j}") for j in range(J)] for l in range(L)]
    d = [[Bool(f"d{l}_{j}") for j in range(J)] for l in range(L)]
    n = [[Bool(f"n{l}_{j}") for j in range(J)] for l in range(L)]
    v = [[Bool(f"v{l}_{j}") for j in range(J)] for l in range(L)]
    for l in range(L):
        # FieldInfo flags only when the FieldInfo exists, and a FieldInfo has at least one data structure
        s.add(Implies(Not(ex[l]), Not(Or(P[l], D[l], N[l], V[l]))))
        s.add(Implies(ex[l], Or(P[l], D[l], N[l], V[l])))
        for j in range(J):
            s.add(has[l][j] == Or(p[l][j], d[l][j], n[l][j], v[l][j]))
            s.add(Implies(p[l][j], P[l]), Implies(d[l][j], D[l]), Implies(n[l][j], N[l]), Implies(v[l][j], V[l]))
            if schema in ("multi", "single"):  # LUCENE-9334: all or nothing per document
                s.add(Implies(has[l][j], And(p[l][j] == P[l], d[l][j] == D[l], n[l][j] == N[l], v[l][j] == V[l])))
    if schema == "single":  # one index: every leaf that has the field has the same flags
        for a, b in itertools.combinations(range(L), 2):
            s.add(Implies(And(ex[a], ex[b]), And(P[a] == P[b], D[a] == D[b], N[a] == N[b], V[a] == V[b])))
    docs = [(l, j) for l in range(L) for j in range(J)]
    any_point = Or([p[l][j] for (l, j) in docs])
    npoints = Sum([If(p[l][j], 1, 0) for (l, j) in docs])
    can_all = npoints == len(docs)
    if cond == "orig":
        can_fe = And([Implies(ex[l], Or(D[l], N[l], V[l])) for l in range(L)])
    elif cond == "fix":
        can_fe = And([Implies(ex[l], And(P[l], Or(D[l], N[l], V[l]))) for l in range(L)])
    elif cond == "always":  # mutant: rewrite whenever the range covers every point
        can_fe = BoolVal(True)
    # FieldExistsQuery per document; a leaf with a FieldInfo but no norms/vectors/doc values throws: that leaf is
    # excluded from can_fe by both conditions, so under can_fe the 'throw' branch is unreachable
    def fe(l, j):
        return And(ex[l], If(N[l], n[l][j], If(V[l], v[l][j], If(D[l], d[l][j], BoolVal(False)))))
    def rewritten(l, j):
        return If(can_all, BoolVal(True), If(can_fe, fe(l, j), p[l][j]))
    s.add(any_point)
    return s, docs, p, rewritten, can_all, can_fe, (ex, P, D, N, V, has)

def query(schema, cond, prop):
    s, docs, p, rw, can_all, can_fe, flags = build(schema, cond)
    if prop == "wrong":        # some document differs from the truth
        s.add(Or([rw(l, j) != p[l][j] for (l, j) in docs]))
    elif prop == "extra":      # a document without a point is returned
        s.add(Or([And(rw(l, j), Not(p[l][j])) for (l, j) in docs]))
    elif prop == "lost":       # a document with a point is not returned
        s.add(Or([And(Not(rw(l, j)), p[l][j]) for (l, j) in docs]))
    elif prop == "fe_fires":   # non-vacuity: the FieldExistsQuery rewrite is taken and is correct
        s.add(Not(can_all), can_fe, And([rw(l, j) == p[l][j] for (l, j) in docs]))
    elif prop == "false":      # vacuity probe on the constraint set itself: must be SAT (constraints consistent)
        pass
    r = s.check()
    out = {"schema": schema, "cond": cond, "prop": prop, "result": str(r)}
    if r == sat:
        m = s.model()
        ex, P, D, N, V, has = flags
        leaves = []
        for l in range(L):
            if m.eval(ex[l], model_completion=True):
                fl = "".join(k for k, x in (("P", P[l]), ("D", D[l]), ("N", N[l]), ("V", V[l])) if m.eval(x, model_completion=True))
            else:
                fl = "-"
            dd = []
            for j in range(J):
                dd.append(("pt" if m.eval(p[l][j], model_completion=True) else "nopt") + ("/hit" if m.eval(rw(l, j), model_completion=True) else "/miss"))
            leaves.append(f"leaf{l}[{fl}] " + " ".join(dd))
        out["witness"] = "; ".join(leaves) + f"; can_all={m.eval(can_all, model_completion=True)} can_fe={m.eval(can_fe, model_completion=True)}"
    return out

RUNS = [
    ("multi", "orig", "wrong", "sat"),     # the bug: MultiReader + original condition
    ("multi", "orig", "lost", "unsat"),    # positive queries only over-match (extra hits); lost hits need MUST_NOT
    ("multi", "fix", "wrong", "unsat"),    # the fix is sufficient on MultiReaders of strict indexes
    ("multi", "fix", "fe_fires", "sat"),   # and the FieldExists rewrite still fires (non-vacuous)
    ("single", "orig", "wrong", "unsat"),  # one strict index: the original condition is sound (why tests pass)
    ("single", "orig", "fe_fires", "sat"),
    ("lenient", "fix", "wrong", "sat"),    # LFV-490 route: a leaf with points AND doc-values-only documents (other root)
    ("lenient", "fix", "extra", "sat"),
    ("multi", "always", "wrong", "sat"),   # mutant kill: dropping the per-leaf check is caught
    ("single", "always", "wrong", "sat"),  # mutant kill on a single index (FieldInfo with points only -> throws/none)
    ("multi", "fix", "false", "sat"),      # constraint-set consistency probe
]
res = []
ok = True
for schema, cond, prop, want in RUNS:
    o = query(schema, cond, prop); o["expected"] = want; o["as_expected"] = (o["result"] == want)
    ok &= o["as_expected"]; res.append(o)
    print(json.dumps(o))
print("ALL_AS_EXPECTED" if ok else "MISMATCH")
sys.exit(0 if ok else 1)
