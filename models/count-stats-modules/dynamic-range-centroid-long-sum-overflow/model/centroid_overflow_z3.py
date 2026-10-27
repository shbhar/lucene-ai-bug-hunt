# Z3 bit-vector model (exact Java long wrap-around) of the centroid in
# DynamicRangeUtil.computeDynamicNumericRanges (lucene/facet/.../range/DynamicRangeUtil.java @ ea57360912ed):
#   :237 long valueSum = 0;   :245 valueSum += values[i];   :251, :263 centroid = (double) valueSum / count
# model element -> Java: v[i] = values[i] of one range (sorted, so min = v[0], max = v[n-1]);
#   wrapped = valueSum after n additions (64-bit two's complement); wide = the exact sum (72-bit, cannot overflow
#   for n <= 6).  "centroid in [min, max]"  <=>  n*min <= wrapped <= n*max (evaluated exactly in 72 bits).
# Q1 (counterexample engine, SAT expected): the centroid leaves [min, max] with every value in [floor, Long.MAX].
# Q2 (proof, UNSAT expected): if the exact sum fits in a long, the centroid is always in [min, max].
from z3 import BitVec, BitVecVal, Solver, SignExt, Extract, Or, And, sat

W = 72
LMAX = (1 << 63) - 1
LMIN = -(1 << 63)

def run(n, floor, require_fit):
    v = [BitVec(f"v{i}", 64) for i in range(n)]
    s = Solver()
    for i in range(n):
        s.add(v[i] >= floor)
        if i > 0:
            s.add(v[i - 1] <= v[i])   # sorted, as computeDynamicNumericRanges sorts before summing
    wrapped = BitVecVal(0, 64)
    for x in v:
        wrapped = wrapped + x
    wide = BitVecVal(0, W)
    for x in v:
        wide = wide + SignExt(W - 64, x)
    if require_fit:
        s.add(wide <= BitVecVal(LMAX, W), wide >= BitVecVal(LMIN, W))
    w = SignExt(W - 64, wrapped)
    lo = SignExt(W - 64, v[0]) * n
    hi = SignExt(W - 64, v[-1]) * n
    s.add(Or(w < lo, w > hi))        # centroid outside [min, max]
    r = s.check()
    m = s.model() if r == sat else None
    def sg(b):
        x = m.eval(b).as_long()
        return x - (1 << 64) if x >= (1 << 63) else x
    return r, ([sg(x) for x in v], sg(wrapped)) if m is not None else None

for n, floor in [(2, 1 << 62), (5, 1_761_480_000_000_000_000), (6, 1_761_480_000_000_000_000)]:
    r, ex = run(n, floor, False)
    print(f"Q1 n={n} values>={floor}: {r} {ex if ex else ''}")
for n in range(1, 7):
    r, ex = run(n, LMIN, True)
    print(f"Q2 n={n} exact sum fits in a long => centroid in [min,max]: {r} (unsat = proved) {ex if ex else ''}")
