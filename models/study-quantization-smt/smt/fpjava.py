"""Bit-precise Z3 models of the Java float/double operations used by Lucene's vector scoring.

Java (since 17, JEP 306) evaluates float/double arithmetic strictly in IEEE 754 binary32/binary64
with round-to-nearest-even, which is exactly Z3's FP theory with RNE. The helpers below encode
the java.lang.Math methods whose semantics differ from the SMT-LIB primitives (fp.max/fp.min
leave +0/-0 unspecified; Math.round is floor(x + 1/2) with NaN -> 0).
"""
from z3 import (FPSort, FP, FPVal, RNE, RTZ, fpIsNaN, fpIsInf, fpIsZero, fpIsNegative,
                fpIsPositive, fpAdd, fpSub, fpMul, fpDiv, fpFMA, fpSqrt, fpNeg, fpGT, fpLT,
                fpGEQ, fpLEQ, fpEQ, fpToFP, fpToReal, fpNaN, fpPlusInfinity, fpMinusInfinity,
                fpPlusZero, fpMinusZero, If, Or, And, Not, ToInt, RealVal, Float32, Float64,
                fpIsNormal, fpIsSubnormal)

F32 = Float32()
F64 = Float64()
rm = RNE()


def f32(name):
    return FP(name, F32)


def f64(name):
    return FP(name, F64)


def c32(v):
    return FPVal(v, F32)


def c64(v):
    return FPVal(v, F64)


def jmax(a, b):
    """java.lang.Math.max(float,float): NaN if either is NaN; +0 > -0."""
    s = a.sort()
    both_zero = And(fpIsZero(a), fpIsZero(b))
    return If(Or(fpIsNaN(a), fpIsNaN(b)), fpNaN(s),
              If(both_zero, If(And(fpIsNegative(a), fpIsNegative(b)), fpMinusZero(s), fpPlusZero(s)),
                 If(fpGT(a, b), a, b)))


def jmin(a, b):
    """java.lang.Math.min(float,float): NaN if either is NaN; -0 < +0."""
    s = a.sort()
    both_zero = And(fpIsZero(a), fpIsZero(b))
    return If(Or(fpIsNaN(a), fpIsNaN(b)), fpNaN(s),
              If(both_zero, If(Or(fpIsNegative(a), fpIsNegative(b)), fpMinusZero(s), fpPlusZero(s)),
                 If(fpLT(a, b), a, b)))


def jclamp(v, lo, hi):
    """java.lang.Math.clamp(float value, float min, float max) (JDK 21+): min(max, max(value, min))."""
    return jmin(hi, jmax(v, lo))


def to64(x):
    return fpToFP(rm, x, F64)


def to32(x):
    return fpToFP(rm, x, F32)


def jround_real(q):
    """java.lang.Math.round(float) restricted to |q| < 2^31: NaN -> 0, else floor(q + 1/2)
    (computed exactly, as the JDK implementation does). Returns a z3 Int."""
    return If(fpIsNaN(q), 0, ToInt(fpToReal(q) + RealVal("1/2")))


def finite(x):
    return And(Not(fpIsNaN(x)), Not(fpIsInf(x)))
