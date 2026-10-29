"""SMT obligations (z3 4.12, exact Java int/long semantics via BitVec) for unit rescorers-nearest-and-rescoretopn-presized-by-unbounded-topn.

Model element -> Java line (Lucene main a9779ef3c193):
  topN / n                   Rescorer.rescore(..., int topN)               search/Rescorer.java:42
  c_main = topN              new TopFieldCollectorManager(sort, topN, ...) search/SortRescorer.java:48-49
  c_main = n                 new HitQueue(n, false)                        search/RescoreTopNQuery.java:73
  c_main = n                 new NearestHitQueue(n)                        document/NearestNeighbor.java:254
  guard                      maxSize >= ArrayUtil.MAX_ARRAY_LENGTH -> IAE  util/PriorityQueue.java:121-125
  QueryRescorer contract     returns min(topN, hits.length) hits           search/QueryRescorer.java:119-122
  MAX_ARRAY_LENGTH           Integer.MAX_VALUE - 16 = 2147483631 (64-bit, compressed oops; value in the IAE text)
  MAX_DOCS                   Integer.MAX_VALUE - 128                       index/IndexWriter.java:208
  points (long)              PointValues.size() summed over leaves (multi-valued docs count more than once)

Documented limits are preconditions: hits.length < MAX_ARRAY_LENGTH (it is an array), maxDoc <= MAX_DOCS,
candidates <= maxDoc. Each obligation is stated so that UNSAT = proved, SAT = counterexample (printed).
Mutants of the fix must be SAT (vacuity / mutation evidence).
"""
from z3 import (BitVec, BitVecVal, If, Solver, And, Or, Not, SignExt, Extract, sat, unsat)

MAXI = 2**31 - 1
MAL = 2**31 - 1 - 16          # ArrayUtil.MAX_ARRAY_LENGTH
MAX_DOCS = 2**31 - 1 - 128


def I(v):
    return BitVecVal(v, 32)


def L(v):
    return BitVecVal(v, 64)


def jmin(a, b):
    return If(a < b, a, b)    # signed BitVec compare = Java Math.min


def jmax(a, b):
    return If(a > b, a, b)


results = []


def check(name, constraints, expect, show=()):
    s = Solver()
    s.set("timeout", 60000)
    s.add(*constraints)
    r = s.check()
    ok = (r == expect)
    line = f"{name}: {r} (expected {expect}) {'OK' if ok else 'UNEXPECTED'}"
    if r == sat:
        m = s.model()
        line += "  model: " + ", ".join(f"{str(v)}={m.eval(v, model_completion=True).as_signed_long()}" for v in show)
    print(line)
    results.append((name, str(r), str(expect), ok))


topN = BitVec("topN", 32)
h = BitVec("hits", 32)
n = BitVec("n", 32)
maxDoc = BitVec("maxDoc", 32)
cand = BitVec("cand", 32)
pts = BitVec("points", 64)

pre_rescore = [topN >= I(1), h >= I(0), h < I(MAL)]

# ---- S1: SortRescorer on main (c = topN) -------------------------------------------------------
check("S1a main: tiny first pass (<= 5 hits), collector size hits the PriorityQueue/VM limit",
      pre_rescore + [h <= I(5), topN >= I(MAL)], sat, (topN, h))
check("S1b main: tiny first pass, collector arrays (>= 12 bytes/slot) exceed a 1 GiB heap",
      pre_rescore + [h <= I(5), SignExt(32, topN) * L(12) > L(2**30)], sat, (topN, h))

# ---- S2: SortRescorer fix c = min(topN, max(1, hits)) ------------------------------------------
c = jmin(topN, jmax(I(1), h))
check("S2 fix: 1 <= c < MAX_ARRAY_LENGTH and min(c, hits) == min(topN, hits) for every legal input",
      pre_rescore + [Not(And(c >= I(1), c < I(MAL), jmin(c, h) == jmin(topN, h)))], unsat)
# mutants (must be SAT = killed)
c_m1 = jmin(topN, h)
check("S2-mutant1 c = min(topN, hits): numHits 0 for an empty first pass (IAE 'numHits must be > 0')",
      pre_rescore + [Not(c_m1 >= I(1))], sat, (topN, h))
c_m2 = jmax(I(1), h)
check("S2-mutant2 c = max(1, hits): ignores topN, returns more than topN hits",
      pre_rescore + [Not(jmin(c_m2, h) == jmin(topN, h))], sat, (topN, h))

# ---- Q: RescoreTopNQuery fix c = min(n, max(1, maxDoc)) ----------------------------------------
pre_q = [n >= I(1), maxDoc >= I(0), maxDoc <= I(MAX_DOCS), cand >= I(0), cand <= maxDoc]
cq = jmin(n, jmax(I(1), maxDoc))
check("Q1 main (c = n): 9-doc index, queue size at the PriorityQueue limit",
      pre_q + [maxDoc == I(9), n >= I(MAL)], sat, (n, maxDoc))
check("Q2 fix: 1 <= c < MAX_ARRAY_LENGTH and min(c, cand) == min(n, cand) (maxDoc <= MAX_DOCS)",
      pre_q + [Not(And(cq >= I(1), cq < I(MAL), jmin(cq, cand) == jmin(n, cand)))], unsat)
check("Q3 fix without the MAX_DOCS precondition (e.g. a future >2^31-doc index): guard can still fire",
      [n >= I(1), maxDoc >= I(0), cand >= I(0), cand <= maxDoc, Not(cq < I(MAL))], sat, (n, maxDoc))

# ---- N: LatLonPoint.nearest ---------------------------------------------------------------------
pre_n = [n >= I(1), maxDoc >= I(1), maxDoc <= I(MAX_DOCS), pts >= L(0), pts <= L(2**62)]
cn_naive = jmin(n, jmax(I(1), maxDoc))
check("N1 naive cap at maxDoc is WRONG for multi-valued points (nearest returns a doc once per point)",
      pre_n + [Not(SignExt(32, jmin(cn_naive, Extract(31, 0, jmin(pts, L(MAXI))))) == jmin(SignExt(32, n), pts))],
      sat, (n, maxDoc, pts))
n64 = SignExt(32, n)
cn = Extract(31, 0, jmin(n64, jmax(L(1), pts)))     # (int) Math.min(n, Math.max(1, totalPoints))
want = jmin(n64, pts)
check("N2 fix (cap at point count): when min(n, points) < MAX_ARRAY_LENGTH, 1 <= c < MAX_ARRAY_LENGTH and "
      "min(c, points) == min(n, points)",
      pre_n + [want < L(MAL), Not(And(cn >= I(1), cn < I(MAL), jmin(SignExt(32, cn), pts) == want))], unsat)
check("N3 residual of the fix: n = MAX with >= MAX_ARRAY_LENGTH points still trips the guard (extreme scale)",
      pre_n + [Not(cn < I(MAL))], sat, (n, pts))

bad = [r for r in results if not r[3]]
print(f"obligations={len(results)} unexpected={len(bad)}")
raise SystemExit(1 if bad else 0)
