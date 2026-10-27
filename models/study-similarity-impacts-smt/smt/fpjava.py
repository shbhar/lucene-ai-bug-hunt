"""Java (JLS 15.4 / JVMS 2.8) floating-point semantics on top of the Z3 FP theory.

Java float/double arithmetic is IEEE-754 binary32/binary64 with round-to-nearest-even and
gradual underflow, strictfp everywhere since Java 17 (JEP 306). We map:
  float  -> Float32(), double -> Float64()
  + - * / -> fpAdd/fpSub/fpMul/fpDiv with RNE
  (double) f -> fpFPToFP(RNE, f, Float64())  (exact)
  (float) d  -> fpFPToFP(RNE, d, Float32())  (RNE, overflow -> inf)
  Math.max   -> Java semantics (NaN-propagating, +0.0 > -0.0), NOT fp.max (IEEE maxNum)
  Math.sqrt  -> fpSqrt (Java requires correctly rounded sqrt)
Transcendental functions (Math.log, Math.pow, Math.exp) are NOT available in the FP theory;
callers abstract them (see transcendental.py).
"""
from z3 import (FPSort, Float32, Float64, RNE, fpAdd, fpSub, fpMul, fpDiv, fpFPToFP, fpIsNaN,
                fpIsZero, fpIsNegative, fpIsInf, fpGT, fpLT, fpGEQ, fpLEQ, fpEQ, fpNaN, fpPlusZero,
                fpMinusZero, fpPlusInfinity, If, Or, And, Not, FPVal, fpSqrt, fpNeg, fpIsNormal,
                fpIsSubnormal, fpRoundToIntegral, RTZ, fpToFP, BitVecVal, fpBVToFP, fpSignedToFP,
                fpToSBV, BitVec)

F32 = Float32()
F64 = Float64()
rm = RNE()


def f2d(x):
    return fpFPToFP(rm, x, F64)


def d2f(x):
    return fpFPToFP(rm, x, F32)


def add(a, b):
    return fpAdd(rm, a, b)


def sub(a, b):
    return fpSub(rm, a, b)


def mul(a, b):
    return fpMul(rm, a, b)


def div(a, b):
    return fpDiv(rm, a, b)


def sqrt(a):
    return fpSqrt(rm, a)


def neg(a):
    return fpNeg(a)


def dconst(v):
    return FPVal(v, F64)


def fconst(v):
    return FPVal(v, F32)


def jmax(a, b):
    """java.lang.Math.max(a, b) for same-sort FP terms."""
    srt = a.sort()
    nan = fpNaN(srt)
    pz = fpPlusZero(srt)
    both_zero = And(fpIsZero(a), fpIsZero(b))
    zero_case = If(And(fpIsNegative(a), fpIsNegative(b)), a, pz)
    return If(Or(fpIsNaN(a), fpIsNaN(b)), nan,
              If(both_zero, zero_case, If(fpGT(a, b), a, b)))


def finite(x):
    return And(Not(fpIsNaN(x)), Not(fpIsInf(x)))


def pos_finite(x):
    """finite and > 0 (strictly)."""
    return And(finite(x), fpGT(x, FPVal(0.0, x.sort())))


def nonneg_finite(x):
    return And(finite(x), fpGEQ(x, FPVal(0.0, x.sort())))


def is_integral(x):
    return fpEQ(fpRoundToIntegral(RTZ(), x), x)


def long_to_double(x):
    """x is already a Float64 holding an integer value <= 2^53: (double) long is exact."""
    return x


def java_lt(a, b):
    return fpLT(a, b)


def java_gt(a, b):
    return fpGT(a, b)


def java_le(a, b):
    return fpLEQ(a, b)


def java_ge(a, b):
    return fpGEQ(a, b)


def java_eq(a, b):
    return fpEQ(a, b)
