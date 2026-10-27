"""Z3 sweep of the int index arithmetic in the sorters-selectors sorters/selectors (exact Java int wrap via 32-bit bit-vectors).

Model element -> Java line map (Lucene main @ ea57360912ed):
  heapChild(from,i) = ((i - from) << 1) + 1 + from      Sorter.java:300-302, used by siftDown Sorter.java:276-294
  heapParent(from,i) = ((i - 1 - from) >>> 1) + from    Sorter.java:296-298, used by heapify Sorter.java:270-274
  IntroSorter pivot sampling: mid=(from+last)>>>1, range=size>>3, doubleRange=range<<1,
     from+doubleRange, last-doubleRange, mid-range, mid+range   IntroSorter.java:63-78 (same in IntroSelector.java:57-81)
  upper2: t += delta << 1 (TimSorter merge only)        Sorter.java:165-176
  lower2: f -= delta << 1 (TimSorter merge only)        Sorter.java:151-162
Preconditions: Sorter slots are Java ints, 0 <= from <= to <= Integer.MAX_VALUE (Sorter.java:79-86 only checks to>=from).
Each query asks for a state in which the Java (wrapped) value differs from the mathematical value AND the wrapped
value lets the loop continue (i.e. is used as an index). SAT = reachable overflow; UNSAT = proved safe for all ints.
"""
from z3 import BitVec, BitVecVal, Solver, ULE, ULT, ZeroExt, Extract, LShR, And, Or, Not, sat, unsat, Optimize, BV2Int, Int, If
import json, sys

W = 32
MAXI = 2**31 - 1
def bv(n): return BitVec(n, W)
def nonneg(x): return x >= 0          # signed compare
def wide(x): return ZeroExt(32, x)     # 64-bit view of a non-negative int (no wrap)

results = {}

def run(name, s, extra=None):
    r = s.check()
    out = {"result": str(r)}
    if r == sat:
        m = s.model()
        out["model"] = {str(d): m[d].as_signed_long() for d in m.decls()}
    results[name] = out
    print(name, out)

# Q1: heapChild overflow inside siftDown: from <= i < to, true child >= 2^31, wrapped child < to (loop continues)
frm, i, to = bv('from'), bv('i'), bv('to')
s = Solver()
s.add(nonneg(frm), nonneg(to), frm <= i, i < to)
child = ((i - frm) << 1) + 1 + frm
true_child = ((wide(i) - wide(frm)) << 1) + 1 + wide(frm)
s.add(true_child >= 2**31, child < to)
run("Q1_heapChild_overflow_reachable", s)

# Q1b: with from = 0, the smallest 'to' for which some i < to overflows
o = Optimize(); frm0, i0, to0 = bv('from'), bv('i'), bv('to')
o.add(frm0 == 0, nonneg(to0), 0 <= i0, i0 < to0)
o.add(((wide(i0)) << 1) + 1 >= 2**31)
h = o.minimize(BV2Int(to0))
o.check(); m = o.model()
results["Q1b_min_to_from0"] = {"to": m[to0].as_long(), "i": m[i0].as_long()}
print("Q1b_min_to_from0", results["Q1b_min_to_from0"])

# Q1c: smallest range size (to - from) for which an overflow is possible (any from)
o = Optimize(); f1, i1, t1 = bv('from'), bv('i'), bv('to')
o.add(nonneg(f1), nonneg(t1), f1 <= i1, i1 < t1)
o.add(((wide(i1) - wide(f1)) << 1) + 1 + wide(f1) >= 2**31)
o.minimize(BV2Int(t1 - f1))
o.check(); m = o.model()
results["Q1c_min_size_any_from"] = {"from": m[f1].as_long(), "to": m[t1].as_long(), "i": m[i1].as_long(),
                                    "size": m[t1].as_long() - m[f1].as_long()}
print("Q1c_min_size_any_from", results["Q1c_min_size_any_from"])

# Q1d: exact characterisation: overflow possible  <=>  2*to - from > 2^31 (for the largest node i = to-1)
f2, t2, i2 = bv('from'), bv('to'), bv('i')
s = Solver()
s.add(nonneg(f2), nonneg(t2), f2 < t2)
exists_overflow = ((wide(t2 - 1) - wide(f2)) << 1) + 1 + wide(f2) >= 2**31
s.add(exists_overflow != ((wide(t2) << 1) - wide(f2) > 2**31))
run("Q1d_characterisation_2to_minus_from_gt_2^31 (expect unsat)", s)

# Q1e: no overflow when to <= 2^30 (all from): proves the safe region
f3, i3, t3 = bv('from'), bv('i'), bv('to')
s = Solver()
s.add(nonneg(f3), nonneg(t3), f3 <= i3, i3 < t3, t3 <= 2**30)
s.add(((wide(i3) - wide(f3)) << 1) + 1 + wide(f3) >= 2**31)
run("Q1e_safe_if_to_le_2^30 (expect unsat)", s)

# Q2: heapParent(from, to-1) in heapify, to - from >= 2: no wrap
f, t = bv('from'), bv('to')
s = Solver()
s.add(nonneg(f), nonneg(t), t - f >= 2)
par = LShR(t - 1 - 1 - f, 1) + f
s.add(Or(par < f, par >= t))
run("Q2_heapParent_in_range (expect unsat)", s)

# Q3: IntroSorter/IntroSelector pivot sampling indices stay within [from, last]
f, t = bv('from'), bv('to')
s = Solver()
s.add(nonneg(f), nonneg(t), t - f > 16)
size = t - f; last = t - 1; mid = LShR(f + last, 1)
rng = size >> 3; drng = rng << 1
idx = [f, f + rng, f + drng, mid - rng, mid, mid + rng, last - drng, last - rng, last]
s.add(Or([Or(x < f, x > last) for x in idx]))
run("Q3_introsort_ninther_indices (expect unsat)", s)
s = Solver(); s.add(nonneg(f), nonneg(t), t - f > 3, t - f <= 40)
r2 = (t - f) >> 2; idx = [mid - r2, mid, mid + r2]
s.add(Or([Or(x < f, x > last) for x in idx]))
run("Q3b_single_median_indices (expect unsat)", s)

# Q4: upper2 (TimSorter): t += delta << 1 can wrap negative while t < to is then true
f, to = bv('from'), bv('to'); tt, ff = bv('t'), bv('f')
s = Solver()
s.add(nonneg(f), nonneg(to), f <= ff, ff < tt, tt < to)
delta = tt - ff
nt = tt + (delta << 1)
s.add(wide(tt) + (wide(delta) << 1) >= 2**31, nt < to)
# reachable shape: ff, tt follow f0=from, t0=from+1, t_{k+1}=t_k+2(t_k-f_k): delta is a power of two, t-from = 2^k - 1
k = BitVec('k', W)
s.add(ULT(k, 31), delta == (BitVecVal(1, W) << k), tt - f == (BitVecVal(1, W) << (k + 1)) - 1)
run("Q4_upper2_overflow (TimSorter only, out of unit scope)", s)

json.dump(results, open(sys.argv[1], "w"), indent=1)
