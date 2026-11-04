"""
Z3 (FP + bit-vector) characterisation of how the Lucene expressions compiler decides the truth value of an operand
of !, &&, || and ?: (JavascriptCompiler @ ea57360912ed).

The code generator evaluates such an operand in the INT "expected type" (visitUnary 518-521, visitBooland 694-698,
visitBoolor 714-718, visitConditional 734-737) and branches with IFEQ/IFNE on the int:
  * a double-typed operand (decimal literal, variable, function call, arithmetic, comparison) is narrowed with d2i
    (gen.conversion(DOUBLE, INT), e.g. visitExternal 503, pushBinaryOp 773),
  * a long-typed operand (& | ^ ~ << >> >>> result, pushBinaryOp 773 / visitUnary 534) is narrowed with l2i,
  * a hex/octal literal is pushed as (int) of its long value (pushLong 795-796).

Java semantics encoded exactly:
  d2i(v): NaN -> 0; v >= 2^31 -> Integer.MAX_VALUE; v <= -2^31 -> Integer.MIN_VALUE; else round toward zero (JLS 5.1.3)
  l2i(w): low 32 bits (JLS 5.1.3)
  l2d(w): round to nearest even (JLS 5.1.2)

Queries (each prints sat/unsat and, for sat, a witness that is re-executed in Java by the JUnit repro):
  Q1  exists double v, v not NaN, v != 0, d2i(v) == 0                    -> SAT  (fractional values are 'false')
  Q2  exists double v, v not NaN, |v| >= 1, d2i(v) == 0                  -> UNSAT (so Q1's set is exactly 0<|v|<1)
  Q3  exists long w, w != 0, l2i(w) == 0                                 -> SAT  (long values that are 'false')
  Q4  forall long w: (w != 0 and l2i(w) == 0) <-> (w != 0 and w & 0xffffffff == 0)   -> proved (negation UNSAT)
  Q5  exists long w, w != 0, l2i(w) == 0, d2i(l2d(w)) == 0               -> UNSAT: the same VALUE written as a
      decimal literal / variable / arithmetic result is always 'true'; only the long-typed path makes it 'false'
  Q6  exists long x, int k in [32,62], (x & (1<<k)) != 0, l2i(x & (1<<k)) != 0   -> UNSAT: a single-bit test of
      any bit >= 32 written as (x & mask) is 'false' for every x
  Q7  same as Q6 for k in [0,31]                                         -> SAT negation check (vacuity probe):
      bits below 32 do test correctly
"""
import sys
from z3 import (BitVec, BitVecVal, Extract, FP, FPSort, Float64, Solver, And, Or, Not, If, fpIsNaN, fpAbs, fpGEQ,
                fpLEQ, fpToSBV, fpEQ, FPVal, RTZ, RNE, fpSignedToFP, ULE, UGE, sat, unsat, BitVecSort, Implies,
                ZeroExt, ForAll, LShR)

D = Float64()


def d2i(v):
    two31 = FPVal(2.0 ** 31, D)
    m_two31 = FPVal(-(2.0 ** 31), D)
    return If(fpIsNaN(v), BitVecVal(0, 32),
              If(fpGEQ(v, two31), BitVecVal(2 ** 31 - 1, 32),
                 If(fpLEQ(v, m_two31), BitVecVal(-(2 ** 31), 32),
                    fpToSBV(RTZ(), v, BitVecSort(32)))))


def l2i(w):
    return Extract(31, 0, w)


def l2d(w):
    return fpSignedToFP(RNE(), w, D)


def run(name, constraints, expect):
    s = Solver()
    s.set("timeout", 120000)
    s.add(*constraints)
    r = s.check()
    ok = (r == expect)
    line = f"{name}: {r} (expected {expect}) {'OK' if ok else 'UNEXPECTED'}"
    if r == sat:
        line += f"  witness: {s.model()}"
    print(line)
    return ok


def main():
    allok = True
    v = FP("v", D)
    allok &= run("Q1 fractional operand is false", [Not(fpIsNaN(v)), Not(fpEQ(v, FPVal(0.0, D))), d2i(v) == 0], sat)
    allok &= run("Q2 no |v|>=1 is false", [Not(fpIsNaN(v)), fpGEQ(fpAbs(v), FPVal(1.0, D)), d2i(v) == 0], unsat)

    w = BitVec("w", 64)
    allok &= run("Q3 nonzero long is false", [w != 0, l2i(w) == 0], sat)
    lowzero = Extract(31, 0, w) == 0
    allok &= run("Q4 characterisation (negation)",
                 [Not((And(w != 0, l2i(w) == 0)) == (And(w != 0, lowzero)))], unsat)
    allok &= run("Q5 same value via double path is never false", [w != 0, l2i(w) == 0, d2i(l2d(w)) == 0], unsat)

    x = BitVec("x", 64)
    k = BitVec("k", 64)
    mask = BitVecVal(1, 64) << k
    allok &= run("Q6 bit test of bit >=32 is always false",
                 [UGE(k, 32), ULE(k, 62), (x & mask) != 0, l2i(x & mask) != 0], unsat)
    allok &= run("Q7 vacuity: bit test of bit <32 can be true",
                 [ULE(k, 31), (x & mask) != 0, l2i(x & mask) != 0], sat)
    print("ALL-AS-EXPECTED" if allok else "SOME-UNEXPECTED")
    sys.exit(0 if allok else 1)


if __name__ == "__main__":
    main()
