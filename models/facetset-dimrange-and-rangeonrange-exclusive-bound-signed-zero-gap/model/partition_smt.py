# Bit-precise Z3 model: do the two halves that DimRange.fromDoubles/fromFloats (and rangeonrange.DoubleRange) build
# around a split point b partition the line in Lucene's sortable order?
#
# Model element -> Java (main 5adff78bb168; paths relative to the checkout)
#   step_up_math(b)    -> Math.nextUp, used at DimRange.java:66 (double), :90 (float); rangeonrange/DoubleRange.java:47
#   step_down_math(b)  -> Math.nextDown, DimRange.java:73 (double), :97 (float); Math.nextAfter(b, -Inf) at
#                         rangeonrange/DoubleRange.java:55 (same function for non-NaN b, per the Math.nextDown javadoc)
#   step_up_pt / step_down_pt -> DoublePoint.nextUp/nextDown (DoublePoint.java:53-67), FloatPoint.nextUp/nextDown
#   sortable(bits)     -> NumericUtils.sortableDoubleBits / sortableFloatBits (NumericUtils.java:84-86), applied by
#                         DimRange.java:79-80 / :103 to the bounds and by DoubleFacetSet/FloatFacetSet.getComparableValues
#                         to the values; rangeonrange encodes both through core DoubleRange's sortable encoding
#   in_range(x,lo,hi)  -> RangeFacetSetMatcher.matches (RangeFacetSetMatcher.java: value < lower -> false,
#                         value > upper -> false), signed long/int comparison of sortable values
# Split forms (outer bounds -Inf / +Inf inclusive, so every non-NaN x lies in the covering range):
#   A: lo = [-Inf, b)  hi = [b, +Inf]     (exclusive max on the lower half)
#   B: lo = [-Inf, b]  hi = (b, +Inf]     (exclusive min on the upper half)
# Property P: every non-NaN x is in exactly one half. NaN bounds are rejected by the constructors; NaN values excluded.
import sys
from z3 import BitVec, BitVecVal, If, And, Or, Not, Solver, sat, unsat, Extract, simplify

def mk(width):
    if width == 64:
        E, M = 11, 52
    else:
        E, M = 8, 23
    W = width
    SIGN = BitVecVal(1 << (W - 1), W)
    ABS = BitVecVal((1 << (W - 1)) - 1, W)
    PINF = BitVecVal(((1 << E) - 1) << M, W)
    NINF = BitVecVal((1 << (W - 1)) | (((1 << E) - 1) << M), W)
    PZERO = BitVecVal(0, W)
    NZERO = SIGN
    ONE = BitVecVal(1, W)
    NMIN = BitVecVal((1 << (W - 1)) | 1, W)
    def is_nan(b):
        from z3 import UGT
        return UGT(b & ABS, PINF)
    def is_zero(b):
        return (b & ABS) == 0
    def neg(b):
        return (b & SIGN) != 0
    def step_up_math(b):
        return If(Or(is_nan(b), b == PINF), b, If(is_zero(b), ONE, If(neg(b), b - 1, b + 1)))
    def step_down_math(b):
        return If(Or(is_nan(b), b == NINF), b, If(is_zero(b), NMIN, If(neg(b), b + 1, b - 1)))
    def step_up_pt(b):
        return If(b == NZERO, PZERO, step_up_math(b))
    def step_down_pt(b):
        return If(b == PZERO, NZERO, step_down_math(b))
    def sortable(b):
        from z3 import LShR
        return b ^ ((b >> (W - 1)) & ABS)   # arithmetic shift, as Java '>>'
    def in_range(x, lo, hi):
        return And(sortable(x) >= sortable(lo), sortable(x) <= sortable(hi))   # signed compare, as Java long/int
    return dict(W=W, PINF=PINF, NINF=NINF, PZERO=PZERO, NZERO=NZERO, is_nan=is_nan, step_up_math=step_up_math,
                step_down_math=step_down_math, step_up_pt=step_up_pt, step_down_pt=step_down_pt, in_range=in_range)

def count(c, b):
    return If(c, 1, 0)

def run(width):
    m = mk(width)
    x = BitVec('x', width); b = BitVec('b', width)
    PINF, NINF, PZERO, NZERO = m['PINF'], m['NINF'], m['PZERO'], m['NZERO']
    base = And(Not(m['is_nan'](x)), Not(m['is_nan'](b)))
    results = []
    for form in ('A', 'B'):
        for step in ('math', 'point'):
            if form == 'A':
                down = m['step_down_math'] if step == 'math' else m['step_down_pt']
                in_lo = m['in_range'](x, NINF, down(b)); in_hi = m['in_range'](x, b, PINF)
                zero_cls = And(b == PZERO, x == NZERO)      # predicted signed-zero gap
                inf_cls = And(b == NINF, x == NINF)         # LFV-313 shape: [-Inf, -Inf) not empty
            else:
                up = m['step_up_math'] if step == 'math' else m['step_up_pt']
                in_lo = m['in_range'](x, NINF, b); in_hi = m['in_range'](x, up(b), PINF)
                zero_cls = And(b == NZERO, x == PZERO)
                inf_cls = And(b == PINF, x == PINF)         # LFV-313 shape: (+Inf, +Inf] not empty
            n = count(in_lo, b) + count(in_hi, b)
            mismatch = n != 1
            def q(name, extra, expect, with_mismatch=True):
                s = Solver(); s.add(base, *extra)
                if with_mismatch:
                    s.add(mismatch)
                r = s.check()
                line = f"[fp{width}] form {form} step={step:5s} {name}: {r} (expected {expect})"
                if r == sat:
                    mdl = s.model()
                    xv = mdl.eval(x).as_long(); bv = mdl.eval(b).as_long(); nv = mdl.eval(n).as_long()
                    line += f"  witness x=0x{xv:0{width//4}x} b=0x{bv:0{width//4}x} halves_containing_x={nv}"
                results.append((line, str(r) == expect))
            q("any mismatch", [], 'sat')
            q("mismatch with finite b", [b != PINF, b != NINF], 'sat' if step == 'math' else 'unsat')
            q("mismatch outside {zero class, infinity class}", [Not(zero_cls), Not(inf_cls)], 'unsat')
            # without the mismatch conjunct: is the opposite-signed zero ever in a half? (math: never, a gap; point: yes)
            q("zero class: x in some half", [zero_cls, n != 0], 'unsat' if step == 'math' else 'sat', with_mismatch=False)
            q("zero class: x in exactly one half", [zero_cls, n == 1], 'unsat' if step == 'math' else 'sat', with_mismatch=False)
            q("zero class reachable", [zero_cls], 'sat' if step == 'math' else 'unsat')
            q("infinity class reachable (LFV-313, unchanged by the fix)", [inf_cls], 'sat')
    return results

ok = True
for w in (64, 32):
    for line, good in run(w):
        print(line + ("" if good else "   <-- UNEXPECTED"))
        ok &= good
print("ALL AS EXPECTED" if ok else "SOME RESULT UNEXPECTED")
sys.exit(0 if ok else 1)
