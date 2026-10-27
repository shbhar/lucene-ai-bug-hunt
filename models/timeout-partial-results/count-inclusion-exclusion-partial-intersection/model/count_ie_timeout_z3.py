"""timeout-partial-results: IndexSearcher#count two-clause term disjunction (inclusion-exclusion) under a fired timeout.

Mapping (model element -> Java, lucene main ea57360912ed):
  A, B (doc sets of the two terms)        BooleanQuery.isTwoClausePureDisjunctionWithTerms  BooleanQuery.java:170-175
  cA = |A|, cB = |B| (exact)              count(queries[0/1]) -> TermWeight.count = docFreq (no deletions)
                                          IndexSearcher.java:507-512 (hasDeletions()==false is a precondition)
  guard min/max < 0.1, both non-zero      IndexSearcher.java:513-518
  cAB_partial = |A & B & [0, w)|          count(queries[2]) on the conjunction: BooleanWeight.count is -1
                                          (neither clause matches all docs / none), so TotalHitCountCollector
                                          collects through TimeLimitingBulkScorer, which stops at a window
                                          boundary w when shouldExit() returns true
                                          (TimeLimitingBulkScorer.java:72-84, IndexSearcher.java:846-858)
  result = cA + cB - cAB_partial          IndexSearcher.java:518
  precondition cA, cB not in {0, maxDoc}  BooleanWeight.reqCount, BooleanWeight.java:206-231 (else exact count)
One segment is modelled (the prefix [0, w) of the doc-id space); several segments only make the collected set
a union of per-segment prefixes, which the single-prefix case already covers for the obligations below.

Obligations checked:
  O1 (lower bound): a timed-out count never exceeds the true count |A u B|.
  O2 (sanity bound): a timed-out count never exceeds maxDoc N.
  O3 (monotone): a later timeout (w2 >= w1) never yields a smaller count.
Controls:
  C1 untimed (w = N): O1 must be UNSAT (matches unit count-totalhits's soundness result).
  C2 candidate fix (use the shortcut only if the intersection count completed, else fall back to collecting the
     disjunction prefix, i.e. |(A u B) & [0, w)|): O1 must be UNSAT.
  V  vacuity: the guard and a fired timeout (w < N) are satisfiable together.
"""
import sys
from z3 import BitVec, BitVecVal, Extract, ZeroExt, Sum, Solver, And, Or, ULT, ULE, sat, unsat, If

N = int(sys.argv[1]) if len(sys.argv) > 1 else 24
W = 8  # bits for counts
# 8-bit unsigned counts are exact only while 10*N and 2*N stay below 256 (guard 10*min < max, sum cA + cB)
assert 10 * N < 256, "scope too large for 8-bit counts"


def popcount(bv, n):
    return Sum([ZeroExt(W - 1, Extract(i, i, bv)) for i in range(n)])


def prefix_mask(w, n):
    # bit i set iff i < w  (w is an int-valued z3 bitvector of width W)
    from z3 import Concat
    bits = [If(ULT(BitVecVal(i, W), w), BitVecVal(1, 1), BitVecVal(0, 1)) for i in range(n)]
    # build an n-bit vector with bit i = bits[i]
    v = bits[n - 1]
    for i in range(n - 2, -1, -1):
        v = Concat(v, bits[i])
    return v


def setup(s, name):
    A = BitVec("A_" + name, N)
    B = BitVec("B_" + name, N)
    cA = popcount(A, N)
    cB = popcount(B, N)
    union = popcount(A | B, N)
    mn = If(ULT(cA, cB), cA, cB)
    mx = If(ULT(cA, cB), cB, cA)
    guard = And(mn != 0, mx != 0, ULT(mn * 10, mx))  # min/max < 0.1  <=>  10*min < max (exact in integers)
    s.add(guard)
    # BooleanWeight.reqCount (BooleanWeight.java:206-231): if a term matches 0 or all docs of the segment, the
    # conjunction count is exact and no bulk scoring (and no timeout check) happens; require the -1 case.
    s.add(cA != N, cB != N)
    return A, B, cA, cB, union


def result_with_prefix(A, B, cA, cB, w):
    m = prefix_mask(w, N)
    return cA + cB - popcount(A & B & m, N)


def run(label, build, expect):
    s = Solver()
    s.set("timeout", 600000)
    build(s)
    r = s.check()
    ok = (r == expect)
    print(f"{label}: {r} (expected {expect}) -> {'OK' if ok else 'MISMATCH'}")
    if r == sat:
        m = s.model()
        vals = {d.name(): m[d] for d in m.decls()}
        print("   model:", {k: (bin(v.as_long())[2:].zfill(N)[::-1] if k.startswith(('A_', 'B_')) else v) for k, v in sorted(vals.items())})
    return ok


def o1(s):
    A, B, cA, cB, union = setup(s, "o1")
    w = BitVec("w", W)
    s.add(ULT(w, N))  # the timeout fired before the end of the segment
    s.add(ULT(union, result_with_prefix(A, B, cA, cB, w)))


def o2(s):
    A, B, cA, cB, union = setup(s, "o2")
    w = BitVec("w", W)
    s.add(ULT(w, N))
    s.add(ULT(BitVecVal(N, W), result_with_prefix(A, B, cA, cB, w)))


def o3(s):
    A, B, cA, cB, union = setup(s, "o3")
    w1 = BitVec("w1", W)
    w2 = BitVec("w2", W)
    s.add(ULT(w1, w2), ULE(w2, N))
    s.add(ULT(result_with_prefix(A, B, cA, cB, w2), result_with_prefix(A, B, cA, cB, w1)))


def c1(s):
    A, B, cA, cB, union = setup(s, "c1")
    s.add(result_with_prefix(A, B, cA, cB, BitVecVal(N, W)) != union)


def c2(s):
    A, B, cA, cB, union = setup(s, "c2")
    w = BitVec("w", W)
    s.add(ULE(w, N))
    fixed = If(w == N, result_with_prefix(A, B, cA, cB, w), popcount((A | B) & prefix_mask(w, N), N))
    s.add(ULT(union, fixed))


def vac(s):
    A, B, cA, cB, union = setup(s, "v")
    w = BitVec("w", W)
    s.add(ULT(w, N))


if __name__ == "__main__":
    print(f"N={N} docs (one segment), counts as {W}-bit unsigned")
    allok = True
    allok &= run("V  guard + fired timeout satisfiable", vac, sat)
    allok &= run("O1 timed-out count <= |A u B|", o1, sat)
    # exceeding maxDoc needs cA <= N-1 and 10*cB < cA with cA + cB > N, i.e. floor((N-2)/10) >= 2, i.e. N >= 22
    allok &= run("O2 timed-out count <= maxDoc", o2, sat if N >= 22 else unsat)
    allok &= run("O3 later timeout never gives smaller count", o3, sat)
    allok &= run("C1 untimed inclusion-exclusion == |A u B|", c1, unsat)
    allok &= run("C2 candidate fix keeps the lower bound", c2, unsat)
    print("ALL-AS-EXPECTED" if allok else "SOME-UNEXPECTED")
    sys.exit(0 if allok else 1)
