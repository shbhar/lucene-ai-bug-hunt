"""SMT model of PointInSetQuery's segment early exit (#14268), 2..3 dimensions, unsigned 8-bit dimension values
(the packed bytes are compared unsigned per dimension, so 8-bit unsigned bit-vectors model the comparator exactly
for one-byte dimensions; the argument does not depend on the width).

Model element -> Java (lucene/core/src/java/org/apache/lucene/search/PointInSetQuery.java @ ea57360912ed):
  set S = {s_0 < ... < s_{k-1}} in lexicographic order      -> constructor requires sorted packed points (l.72-122)
  lower = s_0, upper = s_{k-1}                                -> lowerPoint / upperPoint (l.110-114, l.128-134)
  segment box [mn, mx], points p_j inside it, p_0 = mn ...     -> values.getMinPackedValue/getMaxPackedValue (l.191-192)
  skip(i) = lower[i] > mx[i] or upper[i] < mn[i]  for any i   -> early exit (l.193-200)
Q1 (bug): exists S, a segment containing some s in S, and skip  -> SAT expected (counterexample)
Q2 (fix): same with lower/upper = per-dimension min/max of S     -> UNSAT expected (the check is then sound)
Q3 (vacuity): Q2 without the 'segment contains s' constraint     -> SAT expected (the fixed check can still skip)
"""
import itertools
from z3 import BitVec, BitVecVal, Solver, Or, And, ULT, UGT, ULE, UGE, If, sat, unsat

def lex_lt(a, b):
    # lexicographic unsigned order of packed points (dimension 0 most significant)
    res = False
    for i in reversed(range(len(a))):
        res = Or(ULT(a[i], b[i]), And(a[i] == b[i], res))
    return res

def run(dims, k, variant, contains=True):
    s = Solver()
    S = [[BitVec(f"s{j}_{i}", 8) for i in range(dims)] for j in range(k)]
    for j in range(k - 1):
        s.add(lex_lt(S[j], S[j + 1]))
    mn = [BitVec(f"mn{i}", 8) for i in range(dims)]
    mx = [BitVec(f"mx{i}", 8) for i in range(dims)]
    for i in range(dims):
        s.add(ULE(mn[i], mx[i]))
    if contains:
        # some set point lies in the segment's bounding box (a segment holding that point has such a box)
        s.add(Or([And([And(UGE(S[j][i], mn[i]), ULE(S[j][i], mx[i])) for i in range(dims)]) for j in range(k)]))
    if variant == "main":
        lower, upper = S[0], S[k - 1]
    else:
        def umin(xs):
            m = xs[0]
            for x in xs[1:]:
                m = If(ULT(x, m), x, m)
            return m
        def umax(xs):
            m = xs[0]
            for x in xs[1:]:
                m = If(UGT(x, m), x, m)
            return m
        lower = [umin([S[j][i] for j in range(k)]) for i in range(dims)]
        upper = [umax([S[j][i] for j in range(k)]) for i in range(dims)]
    s.add(Or([Or(UGT(lower[i], mx[i]), ULT(upper[i], mn[i])) for i in range(dims)]))
    r = s.check()
    w = None
    if r == sat:
        m = s.model()
        w = {"set": [[m.eval(x).as_long() for x in p] for p in S], "segment_min": [m.eval(x).as_long() for x in mn],
             "segment_max": [m.eval(x).as_long() for x in mx]}
    return r, w

if __name__ == "__main__":
    ok = True
    for dims, k in [(2, 2), (2, 3), (3, 2), (3, 4)]:
        r1, w1 = run(dims, k, "main")
        r2, _ = run(dims, k, "fix")
        r3, _ = run(dims, k, "fix", contains=False)
        print(f"dims={dims} |S|={k}: Q1(main skips a segment holding a set point)={r1} witness={w1}; "
              f"Q2(fixed check unsound)={r2}; Q3(fixed check can skip, vacuity)={r3}")
        ok &= (r1 == sat and r2 == unsat and r3 == sat)
    # dims == 1 control: lexicographic order == numeric order, the main check is sound
    r1, _ = run(1, 3, "main")
    print(f"dims=1 |S|=3 control: Q1(main)={r1} (expected unsat)")
    ok &= (r1 == unsat)
    print("ALL EXPECTED" if ok else "UNEXPECTED RESULT")
