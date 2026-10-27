# SMT (Z3, IEEE-754 FloatingPoint + BitVector theories) check of the exclusive<->inclusive bound
# helpers used for FloatPoint / DoublePoint range queries.
#
# Transcribed code (Lucene main 7cecf79523, JDK 25.0.3 src.zip):
#   FloatPoint.nextUp/nextDown      lucene/core/src/java/org/apache/lucene/document/FloatPoint.java:53-70
#   DoublePoint.nextUp/nextDown     lucene/core/src/java/org/apache/lucene/document/DoublePoint.java:53-70
#   Math.nextUp(float/double)       java.base/java/lang/Math.java:3162-3171, 3201-3210
#   Math.nextDown(float/double)     java.base/java/lang/Math.java:3240-3250, 3280-3290
#   Float/Double.compare            java.base/java/lang/Float.java:1289-1299 (Double analogous)
#   PointRangeQueryNodeBuilder      lucene/queryparser/.../builders/PointRangeQueryNodeBuilder.java:97-130 (uses Math.nextUp/nextDown)
#
# A float is a 32-bit pattern b (all 2^32 patterns, i.e. every NaN payload too); its value is
# fpBVToFP(b). Ordering is Java's Float.compare (NaN above +Inf, -0 below +0). Every query is
# quantifier-free over the bit patterns, so "unsat" is a proof for all inputs.
#
# run: python3 -I z3/fp_nextup.py
import sys
import time
from z3 import (BitVec, BitVecVal, FPSort, FPVal, If, Not, And, Or, Solver, fpBVToFP, fpToIEEEBV,
                fpAdd, fpLT, fpGT, fpEQ, fpIsNaN, RNE, sat, unsat, Implies)


class Kind:
    def __init__(self, name, ebits, sbits):
        self.name = name
        self.w = ebits + sbits
        self.sort = FPSort(ebits, sbits)
        self.canonNaN = BitVecVal(0x7fc00000 if self.w == 32 else 0x7ff8000000000000, self.w)
        self.negZeroBits = BitVecVal(1 << (self.w - 1), self.w)
        self.negMinBits = BitVecVal((1 << (self.w - 1)) | 1, self.w)

    def val(self, bits):
        return fpBVToFP(bits, self.sort)

    def posInf(self):
        return FPVal(float('inf'), self.sort)

    def negInf(self):
        return FPVal(float('-inf'), self.sort)

    def zero(self):
        return FPVal(0.0, self.sort)


# A Java float/double value = (fp term, raw bit pattern). Raw bits are kept explicitly because
# fpToIEEEBV is unspecified on NaN.
def toIntBits(k, x):  # Float.floatToIntBits / Double.doubleToLongBits
    fp, bits = x
    return If(fpIsNaN(fp), k.canonNaN, bits)


def jcompare(k, a, b):  # Float.compare / Double.compare
    fa, fb = a[0], b[0]
    ia, ib = toIntBits(k, a), toIntBits(k, b)
    return If(fpLT(fa, fb), -1, If(fpGT(fa, fb), 1, If(ia == ib, 0, If(ia < ib, -1, 1))))


def ite(c, x, y):
    return (If(c, x[0], y[0]), If(c, x[1], y[1]))


def mk(k, bits):
    return (k.val(bits), bits)


def math_nextUp(k, x):
    fp, bits = x
    # if (f < +Inf) { t = rawBits(f + 0.0); return bitsToFloat(t + (t >= 0 ? 1 : -1)); } else return f;
    t = fpToIEEEBV(fpAdd(RNE(), fp, k.zero()))  # f is not NaN on this branch, so this is defined
    one = BitVecVal(1, k.w)
    r = t + If(t >= 0, one, -one)
    return ite(fpLT(fp, k.posInf()), mk(k, r), x)


def math_nextDown(k, x):
    fp, bits = x
    one = BitVecVal(1, k.w)
    r = bits + If(fpGT(fp, k.zero()), -one, one)
    return ite(Or(fpIsNaN(fp), fpEQ(fp, k.negInf())), x,
               ite(fpEQ(fp, k.zero()), mk(k, k.negMinBits), mk(k, r)))


def lucene_nextUp(k, x):  # FloatPoint/DoublePoint.nextUp
    return ite(toIntBits(k, x) == k.negZeroBits, mk(k, BitVecVal(0, k.w)), math_nextUp(k, x))


def lucene_nextDown(k, x):  # FloatPoint/DoublePoint.nextDown
    return ite(toIntBits(k, x) == BitVecVal(0, k.w), mk(k, k.negZeroBits), math_nextDown(k, x))


def check(title, kind, formula_builder, expect):
    s = Solver()
    s.set("timeout", 1800 * 1000)
    fb, hb = BitVec('f', kind.w), BitVec('h', kind.w)
    f, h = mk(kind, fb), mk(kind, hb)
    s.add(formula_builder(kind, f, h))
    t0 = time.time()
    r = s.check()
    dt = time.time() - t0
    line = f"[{kind.name}] {title}: {r} ({dt:.1f}s) expected {expect}"
    if r == sat:
        m = s.model()
        fv = m.eval(fb, model_completion=True).as_long()
        hv = m.eval(hb, model_completion=True).as_long()
        line += f"  cex f=0x{fv:0{kind.w // 4}x} h=0x{hv:0{kind.w // 4}x}"
    status = "OK" if str(r) == expect else "UNEXPECTED"
    print(status, line, flush=True)
    return r


def isNaN(x):
    return fpIsNaN(x[0])


def run(kind):
    K = kind
    pinf = lambda x: fpEQ(x[0], K.posInf())
    ninf = lambda x: fpEQ(x[0], K.negInf())
    up, down = lucene_nextUp, lucene_nextDown
    # --- FloatPoint.nextUp javadoc: "least float that compares greater than f (Float.compare)"
    # (a) strictly greater, for every f that has a strictly greater float (f not NaN):
    check("nextUp(f) > f for all non-NaN f", K,
          lambda k, f, h: And(Not(isNaN(f)), Not(jcompare(k, up(k, f), f) > 0)), "sat")
    check("nextUp(f) > f for all f except NaN and +Inf", K,
          lambda k, f, h: And(Not(isNaN(f)), Not(pinf(f)), Not(jcompare(k, up(k, f), f) > 0)), "unsat")
    # (b) least: no h with f < h < nextUp(f)
    check("no h strictly between f and nextUp(f) (all f)", K,
          lambda k, f, h: And(jcompare(k, h, f) > 0, jcompare(k, h, up(k, f)) < 0), "unsat")
    # (c) end-to-end exclusive->inclusive: x > f  <=>  x >= nextUp(f), for f not NaN/+Inf
    check("x > f <=> x >= nextUp(f), f not NaN/+Inf", K,
          lambda k, f, h: And(Not(isNaN(f)), Not(pinf(f)),
                              (jcompare(k, h, f) > 0) != (jcompare(k, h, up(k, f)) >= 0)), "unsat")
    check("x > f <=> x >= nextUp(f) at f = +Inf", K,
          lambda k, f, h: And(pinf(f), (jcompare(k, h, f) > 0) != (jcompare(k, h, up(k, f)) >= 0)), "sat")
    # --- FloatPoint.nextDown javadoc: "greatest float that compares less than f"
    check("nextDown(f) < f for all f except -Inf", K,
          lambda k, f, h: And(Not(ninf(f)), Not(jcompare(k, down(k, f), f) < 0)), "sat")
    check("nextDown(f) < f for all f except -Inf and NaN", K,
          lambda k, f, h: And(Not(ninf(f)), Not(isNaN(f)), Not(jcompare(k, down(k, f), f) < 0)), "unsat")
    # (vacuously true at f = NaN, because nextDown(NaN) = NaN; the NaN defect is caught above)
    check("no h strictly between nextDown(f) and f (all f)", K,
          lambda k, f, h: And(jcompare(k, h, f) < 0, jcompare(k, h, down(k, f)) > 0), "unsat")
    check("no h strictly between nextDown(f) and f, f not NaN", K,
          lambda k, f, h: And(Not(isNaN(f)), jcompare(k, h, f) < 0, jcompare(k, h, down(k, f)) > 0), "unsat")
    check("x < f <=> x <= nextDown(f), f not NaN/-Inf", K,
          lambda k, f, h: And(Not(isNaN(f)), Not(ninf(f)),
                              (jcompare(k, h, f) < 0) != (jcompare(k, h, down(k, f)) <= 0)), "unsat")
    check("x < f <=> x <= nextDown(f) at f = NaN", K,
          lambda k, f, h: And(isNaN(f), (jcompare(k, h, f) < 0) != (jcompare(k, h, down(k, f)) <= 0)), "sat")
    # --- Math.nextUp / Math.nextDown, as used by StandardQueryParser's PointRangeQueryNodeBuilder
    check("Math.nextUp: x > f <=> x >= Math.nextUp(f), f not NaN/+Inf", K,
          lambda k, f, h: And(Not(isNaN(f)), Not(pinf(f)),
                              (jcompare(k, h, f) > 0) != (jcompare(k, h, math_nextUp(k, f)) >= 0)), "sat")
    check("Math.nextUp: same, additionally f != -0", K,
          lambda k, f, h: And(Not(isNaN(f)), Not(pinf(f)), f[1] != K.negZeroBits,
                              (jcompare(k, h, f) > 0) != (jcompare(k, h, math_nextUp(k, f)) >= 0)), "unsat")
    check("Math.nextDown: x < f <=> x <= Math.nextDown(f), f not NaN/-Inf", K,
          lambda k, f, h: And(Not(isNaN(f)), Not(ninf(f)),
                              (jcompare(k, h, f) < 0) != (jcompare(k, h, math_nextDown(k, f)) <= 0)), "sat")
    check("Math.nextDown: same, additionally f != +0", K,
          lambda k, f, h: And(Not(isNaN(f)), Not(ninf(f)), f[1] != BitVecVal(0, K.w),
                              (jcompare(k, h, f) < 0) != (jcompare(k, h, math_nextDown(k, f)) <= 0)), "unsat")


if __name__ == "__main__":
    kinds = {"float": Kind("float", 8, 24), "double": Kind("double", 11, 53)}
    for name in (sys.argv[1:] or ["float", "double"]):
        run(kinds[name])
