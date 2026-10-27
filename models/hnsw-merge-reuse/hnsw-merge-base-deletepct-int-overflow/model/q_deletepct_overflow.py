# Z3 query (linear integer arithmetic with an explicit 32-bit wrap) for
#   IncrementalHnswGraphMerger.addReader (Lucene main ea57360912ed, IncrementalHnswGraphMerger.java:133-139)
#     int deletePct = ((graphSize - candidateVectorCount) * 100) / graphSize;
#     if (deletePct <= DELETE_PCT_THRESHOLD /*40*/ && ...) largestGraphReader = graphReader;
# Contract (javadoc of DELETE_PCT_THRESHOLD, IncrementalHnswGraphMerger.java:57-65): "if more than 40% of the graph's
# original vectors have been deleted, the graph will not be selected as the base."
#
# Encoding (exact for Java int semantics):
#   d = g - l (no overflow: 0 <= d <= g < 2^31)
#   P = 100*d - k*2^32 with integer k and -2^31 <= P < 2^31      (Java's wrapping int multiply)
#   Java trunc(P / g) <= 40  <=>  P < 0  or  P < 41*g             (g > 0; truncation toward zero)
# Preconditions: 1 <= g <= IndexWriter.MAX_DOCS (2^31-129); 0 <= l <= g.
# (A 32-bit bit-vector version of this query did not finish within 900 s; this LIA form answers in seconds.)
from z3 import Int, Solver, And, Or, Not, sat
import json

MAX_DOCS = 2**31 - 129
g, l, k, P = Int('graphSize'), Int('live'), Int('k'), Int('P')
d = g - l
wrap = And(P == 100 * d - k * 2**32, P >= -2**31, P < 2**31)
pre = And(g >= 1, g <= MAX_DOCS, l >= 0, l <= g, wrap)
java_eligible = Or(P < 0, P < 41 * g)
out = {}


def run(name, *extra, minimize=None):
    s = Solver()
    s.add(pre, *extra)
    r = s.check()
    res = {"result": str(r)}
    if r == sat:
        m = s.model()
        gv, lv = m[g].as_long(), m[l].as_long()
        dv = gv - lv
        p = ((dv * 100 + 2**31) % 2**32) - 2**31
        q = abs(p) // gv * (1 if p >= 0 else -1)
        res.update({"graphSize": gv, "live": lv, "deleted": dv, "true_pct": round(100.0 * dv / gv, 4),
                    "java_product": p, "java_deletePct": q})
    out[name] = res
    return r


run("Q1_over40pct_but_eligible", 100 * d > 40 * g, java_eligible)
run("Q1b_atleast41pct_but_eligible", 100 * d >= 41 * g, java_eligible)
run("Q2_no_overflow_region_atleast41_eligible", d <= 21474836, 100 * d >= 41 * g, java_eligible)
run("Q2b_no_overflow_region_over40_eligible_only_floor_boundary", d <= 21474836, 100 * d > 40 * g,
    100 * d < 41 * g, java_eligible)
run("Q3_first_wrapping_deleted_21474837", d == 21474837, 100 * d >= 41 * g, java_eligible)
# smallest graph size for Q1b (binary search)
lo, hi = 1, MAX_DOCS
while lo < hi:
    mid = (lo + hi) // 2
    s = Solver()
    s.add(pre, 100 * d >= 41 * g, java_eligible, g <= mid)
    if s.check() == sat:
        hi = mid
    else:
        lo = mid + 1
run("Q4_min_graphSize_atleast41_eligible", 100 * d >= 41 * g, java_eligible, g == lo)
run("Q5_within40pct_but_rejected", 100 * d <= 40 * g, Not(java_eligible))
for pct in [99, 95, 90, 83, 60]:
    run(f"Q6_atleast{pct}pct_but_eligible", 100 * d >= pct * g, java_eligible)
gv, lv = 30_000_000, 5_000_000
dv = gv - lv
p = ((dv * 100 + 2**31) % 2**32) - 2**31
out["W_30M_5Mlive"] = {"java_product": p, "java_deletePct": abs(p) // gv * (1 if p >= 0 else -1),
                       "true_pct": round(100 * dv / gv, 2)}
print(json.dumps(out, indent=1))
