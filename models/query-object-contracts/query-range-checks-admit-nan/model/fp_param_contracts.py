"""SMT (Z3, IEEE-754 binary32/binary64 theory) obligations for float/double query parameters.

File:line mapping (Lucene main ea57360912ed):
  R1  DisjunctionMaxQuery.java:65            if (tieBreakerMultiplier < 0 || tieBreakerMultiplier > 1) throw IAE("... must be in [0, 1]")
  R2  BayesianScoreQuery.java:69             if (baseRate < 0 || baseRate >= 1) throw IAE("baseRate must be in [0, 1)")
  R3  LatLonPointDistanceFeatureQuery.java:58 if (pivotDistance <= 0) throw IAE("pivotDistance must be > 0")   (double)
  E1  DisjunctionMaxQuery.java:353           equals: tieBreakerMultiplier == other.tieBreakerMultiplier   (primitive fcmp)
  H1  DisjunctionMaxQuery.java:365           hashCode: Float.floatToIntBits(tieBreakerMultiplier)
  (the same E/H pair, == in equals and bit-pattern hashing, is in LatLonPointDistanceFeatureQuery:79/87,
   LogOddsFusionQuery alpha, CommonTermsQuery low/highFreqMinNrShouldMatch, PatienceKnnVectorQuery
   saturationThreshold, and queries/function ConstValueSource, LinearFloatFunction, ScaleFloatFunction,
   RangeMapFloatFunction, ReciprocalFloatFunction, DoubleConstValueSource; list from tools/Scan.java FP-* flags)

Java semantics encoded exactly: a Java float comparison (<, >, <=, ==) is IEEE ordered comparison (false if either
operand is NaN; +0 == -0). Float.floatToIntBits canonicalises every NaN to 0x7fc00000 and keeps the sign of zero.

Obligations (expected result in brackets):
  V1  exists x accepted by R1 with not(0 <= x <= 1)                         [SAT  -> x = NaN]
  V1b same, with x not NaN                                                   [UNSAT: NaN is the only escape]
  V1f exists x accepted by the fixed check !(x >= 0 && x <= 1) and outside   [UNSAT]
  V2  exists x accepted by R2 outside [0,1)                                  [SAT  -> NaN];  V2b not NaN [UNSAT]
  V3  exists x accepted by R3 with not(x > 0)                                [SAT  -> NaN];  V3b not NaN [UNSAT]
  C1  exists x,y: E1(x,y) and H1(x) != H1(y)    (equal objects, different hashes)   [SAT -> {+0,-0}]
  C1b same, with x,y not zero                                                [UNSAT: signed zero is the only case]
  C2  exists x: not E1(x,x)                     (equals not reflexive)       [SAT -> NaN]
  C1f with equals by floatToIntBits (as BoostQuery does), exists x,y: eq and hash differ   [UNSAT]
  C2f with equals by floatToIntBits, exists x: not eq(x,x)                   [UNSAT]
"""
import sys
from z3 import (FP, Float32, Float64, Solver, Or, And, Not, fpLT, fpGT, fpLEQ, fpGEQ, fpEQ, fpIsNaN, fpIsZero,
                fpToIEEEBV, BitVecVal, If, FPVal, sat, unsat)

F = Float32()
D = Float64()
CANON_NAN32 = BitVecVal(0x7FC00000, 32)


def float_to_int_bits(x):
    # Java Float.floatToIntBits: all NaNs collapse to 0x7fc00000
    return If(fpIsNaN(x), CANON_NAN32, fpToIEEEBV(x))


results = []


def check(name, constraints, expect):
    s = Solver()
    s.add(*constraints)
    r = s.check()
    model = ""
    if r == sat:
        m = s.model()
        model = "; ".join("%s=%s" % (d.name(), m[d]) for d in m.decls())
    ok = (r == expect)
    results.append((name, str(r), str(expect), ok, model))
    print("%-4s %-6s expected %-6s %s %s" % (name, r, expect, "OK " if ok else "MISMATCH", model))


x = FP("x", F)
y = FP("y", F)
zero, one = FPVal(0.0, F), FPVal(1.0, F)

acc_r1 = Not(Or(fpLT(x, zero), fpGT(x, one)))
in_01 = And(fpLEQ(zero, x), fpLEQ(x, one))
check("V1", [acc_r1, Not(in_01)], sat)
check("V1b", [acc_r1, Not(in_01), Not(fpIsNaN(x))], unsat)
acc_fix = Not(Not(And(fpGEQ(x, zero), fpLEQ(x, one))))
check("V1f", [acc_fix, Not(in_01)], unsat)

acc_r2 = Not(Or(fpLT(x, zero), fpGEQ(x, one)))
in_01h = And(fpLEQ(zero, x), fpLT(x, one))
check("V2", [acc_r2, Not(in_01h)], sat)
check("V2b", [acc_r2, Not(in_01h), Not(fpIsNaN(x))], unsat)

xd = FP("xd", D)
zd = FPVal(0.0, D)
acc_r3 = Not(fpLEQ(xd, zd))
check("V3", [acc_r3, Not(fpGT(xd, zd))], sat)
check("V3b", [acc_r3, Not(fpGT(xd, zd)), Not(fpIsNaN(xd))], unsat)

eq_prim = fpEQ(x, y)          # Java float ==
check("C1", [eq_prim, float_to_int_bits(x) != float_to_int_bits(y)], sat)
check("C1b", [eq_prim, float_to_int_bits(x) != float_to_int_bits(y), Not(fpIsZero(x)), Not(fpIsZero(y))], unsat)
check("C2", [Not(fpEQ(x, x))], sat)
eq_bits = float_to_int_bits(x) == float_to_int_bits(y)
check("C1f", [eq_bits, float_to_int_bits(x) != float_to_int_bits(y)], unsat)
check("C2f", [Not(float_to_int_bits(x) == float_to_int_bits(x))], unsat)

bad = [r for r in results if not r[3]]
print("## %d obligations, %d as expected, %d mismatches" % (len(results), len(results) - len(bad), len(bad)))
sys.exit(1 if bad else 0)
