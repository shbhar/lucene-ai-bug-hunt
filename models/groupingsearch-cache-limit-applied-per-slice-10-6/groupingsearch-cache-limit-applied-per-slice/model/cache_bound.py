"""SMT model of the GroupingSearch two-pass cache decision (groupingsearch-cache-limit-applied-per-slice-10-6).

Question: since #16247 (10.6.0), does GroupingSearch.setCaching(maxDocsToCache) /
setCachingInMB(maxCacheRAMMB) still bound the WHOLE search's cache, as its javadoc says, or only
each slice's cache?

Model element -> Java line (Lucene main a9779ef3c193; branch_10_6 4ae946e5a9b has the same code)
----------------------------------------------------------------------------------------------
L (the configured limit)       GroupingSearch.java:250-255 (setCachingInMB), :267-272 (setCaching)
contract R (whole search)      GroupingSearch.java:241-244 and :258-261 javadoc: "The cache will not
                               contain more than the maximum specified documents ... If the cache
                               grows beyond the specified limit, then the cache is purged and not
                               used in the second pass search."
one cache per slice            GroupingSearch.java:169-173 (limit passed unchanged) ->
                               CachingCollectorManager.java:83-91 (newCollector = one
                               CachingCollector per call) <- IndexSearcher.java:752,770-772 (one
                               newCollector per slice)
slice s cached <=> m_s <= D    CachingCollector.java:189-199 (leaf invalidates when it would hold
                               more than the slice's remaining budget), :208-211 (budget decremented
                               by each finished leaf), :215-218 (an invalidated leaf invalidates
                               the slice)
replay <=> all slices cached   CachingCollectorManager.java:108-111 (isCached), GroupingSearch.java:
                               208-212 (replay when isCached, else re-run)
D from MB                      CachingCollector.java:307-313: D = (int)(MB * 1024 * 1024 / bpd),
                               bpd = 4, or 8 with cacheScores
one slice without executor     IndexSearcher.java:226-231
10.5.2 implementation          one CachingCollector over all leaves (GroupingSearch 10.5.2
                               groupByFieldOrFunction, CachingCollector.create + search(Query,
                               Collector)), i.e. replay <=> sum_s m_s <= D = contract R

Each query prints sat/unsat and, for sat, a witness. Expected:
  Q1 sat   (a replay that the contract forbids exists as soon as there are 2 slices)
  Q1min    smallest witness: D=1, two slices with one matching doc each
  Q2 unsat (per-slice implementation never holds more than S*D documents) and Q2t sat (S*D is reached)
  Q3 unsat (with one slice, per-slice == whole-search: sequential searchers are unaffected)
  Q4 sat   (setCachingInMB: replayed cache holds more bytes than the MB limit)
  Q4b unsat (RAM held <= S * MB * 2^20)
  Q5 unsat (the contract direction the implementation keeps: total <= D implies replay; the
            change only adds replays, it never removes one)
  Q6 sat   (vacuity probe: the encoding is not trivially unsat; the forbidden replay exists at S=2..8
            within IndexWriter.MAX_DOCS)
"""
import sys
from z3 import (And, Implies, Int, Ints, Not, Optimize, Or, Real, Solver, Sum, ToInt, sat,
                unsat)

MAX_DOCS = 2**31 - 129          # IndexWriter.MAX_DOCS: total matching docs of one search
INT_MAX = 2**31 - 1
results = {}


def per_slice(ms, D):
    return And([m <= D for m in ms])


def whole(ms, D):
    return Sum(ms) <= D


def base(ms, D):
    return And([And(m >= 0) for m in ms] + [D >= 0, D <= INT_MAX, Sum(ms) <= MAX_DOCS])


def check(name, s, expect):
    r = s.check()
    ok = (r == expect)
    w = ""
    if r == sat:
        mdl = s.model()
        w = " witness: " + ", ".join(f"{d}={mdl[d]}" for d in sorted(mdl.decls(), key=str))
    print(f"{name}: {r} (expected {expect}) {'OK' if ok else 'UNEXPECTED'}{w}")
    results[name] = (str(r), ok)


for S in (2, 3, 4, 8):
    ms = Ints(" ".join(f"m{i}" for i in range(S)))
    D = Int("D")
    s = Solver()
    s.add(base(ms, D), per_slice(ms, D), Not(whole(ms, D)))
    check(f"Q1[S={S}] replay although total > limit", s, sat)

# Q1min: smallest total over S=2..4
ms = Ints("m0 m1")
D = Int("D")
o = Optimize()
o.add(base(ms, D), per_slice(ms, D), Not(whole(ms, D)))
o.minimize(Sum(ms))
r = o.check()
mdl = o.model()
print(f"Q1min: {r} smallest witness: D={mdl[D]}, m0={mdl[ms[0]]}, m1={mdl[ms[1]]}")
results["Q1min"] = (str(r), r == sat)

for S in (2, 3, 4, 8, 64):
    ms = Ints(" ".join(f"m{i}" for i in range(S)))
    D = Int("D")
    s = Solver()
    s.add(base(ms, D), per_slice(ms, D), Not(Sum(ms) <= S * D))
    check(f"Q2[S={S}] cached docs > S*D", s, unsat)
    s = Solver()
    s.add(base(ms, D), per_slice(ms, D), Sum(ms) == S * D, D == 1000)
    check(f"Q2t[S={S}] cached docs == S*D reachable (D=1000)", s, sat)

ms = Ints("m0")
D = Int("D")
s = Solver()
s.add(base(ms, D), per_slice(ms, D) != whole(ms, D))
check("Q3[S=1] per-slice != whole-search", s, unsat)

for S in (2, 4):
    for bpd in (4, 8):
        ms = Ints(" ".join(f"m{i}" for i in range(S)))
        MB = Real("MB")
        D = Int("D")
        s = Solver()
        s.add(MB > 0, MB <= 1024, D == ToInt(MB * 1048576 / bpd), base(ms, D), per_slice(ms, D),
              Sum(ms) * bpd > MB * 1048576)
        check(f"Q4[S={S},bpd={bpd}] replayed cache bytes > MB limit", s, sat)
        s = Solver()
        s.add(MB > 0, MB <= 1024, D == ToInt(MB * 1048576 / bpd), base(ms, D), per_slice(ms, D),
              Sum(ms) * bpd > S * MB * 1048576)
        check(f"Q4b[S={S},bpd={bpd}] cached bytes > S*MB*2^20", s, unsat)

for S in (2, 3, 4, 8):
    ms = Ints(" ".join(f"m{i}" for i in range(S)))
    D = Int("D")
    s = Solver()
    s.add(base(ms, D), whole(ms, D), Not(per_slice(ms, D)))
    check(f"Q5[S={S}] total <= D but not replayed", s, unsat)

# Q6 vacuity: the base constraints admit a forbidden replay with every slice non-empty and D large
ms = Ints("m0 m1 m2 m3 m4 m5 m6 m7")
D = Int("D")
s = Solver()
s.add(base(ms, D), per_slice(ms, D), Not(whole(ms, D)), D == 262144, And([m >= 1 for m in ms]))
check("Q6 vacuity probe (S=8, D=262144 = 1 MB without scores)", s, sat)

bad = [k for k, (_, ok) in results.items() if not ok]
print("ALL OK" if not bad else f"UNEXPECTED: {bad}")
sys.exit(0 if not bad else 1)
