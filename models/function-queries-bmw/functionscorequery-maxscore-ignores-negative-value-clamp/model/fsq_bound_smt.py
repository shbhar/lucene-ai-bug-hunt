# SMT model of the FunctionScoreQuery block-max bound pipeline (Lucene main @ ea57360912ed).
#
# Model element -> Java line mapping (lucene/...):
#   score(v, b)            queries/.../function/FunctionScoreQuery.java:243-252
#                            double factor = scores.doubleValue(); if (factor >= 0) return (float) (factor * boost); return 0;
#   bound(innerMax, V, b)  queries/.../function/FunctionScoreQuery.java:262-269
#                            float innerMaxScore = in.getMaxScore(upTo); float valueMaxScore = scores.getMaxScore(upTo);
#                            if (Float.isInfinite(valueMaxScore)) return +Inf; return innerMaxScore * valueMaxScore * boost;
#   valueMaxScore = RN_f(V) core/.../search/DoubleValuesSource.java:572-584
#                            return (float) decoder.applyAsDouble(rawBound)   rawBound = skipper.maxValue(level) (INCREASING)
#   decoders               core/.../search/DoubleValuesSource.java:330-342
#                            fromDoubleField: Double::longBitsToDouble, INCREASING
#                            fromFloatField:  (double) Float.intBitsToFloat((int) v), INCREASING
#                            fromLongField:   (double) v, INCREASING
#   innerMax (TermQuery)   core/.../search/TermQuery.java:84-92 (non-scoring weight: dummy SimScorer returns 0f)
#                            -> TermScorer.getMaxScore -> MaxScoreCache -> 0f
#   innerMax (constant)    ConstantScoreWeight score = boost passed to createWeight = 1f (FunctionScoreQuery.java:117)
#
# Java semantics: float*float and double*double are IEEE-754 RNE (strict since JDK 17);
# (float) double and (double) long are RNE; the float boost is widened exactly to double in factor * boost.
#
# Each query prints sat/unsat and, for sat, a model that is re-executed in Java by ReplayModels.java.

import sys
import json
from z3 import *

RNE_ = RNE()
F32 = Float32()
F64 = Float64()
results = {}


def rn_f(x):
    return fpToFP(RNE_, x, F32)


def rn_d(x):
    return fpToFP(RNE_, x, F64)


def jscore(v, b):
    # (float) (factor * boost), factor >= 0 else 0
    prod = fpMul(RNE_, v, rn_d(b))
    return If(fpGEQ(v, FPVal(0.0, F64)), rn_f(prod), FPVal(0.0, F32))


def jbound(inner, V, b):
    vf = rn_f(V)
    t = fpMul(RNE_, fpMul(RNE_, inner, vf), b)
    return If(fpIsInf(vf), fpPlusInfinity(F32), t)


def finite(x):
    return And(Not(fpIsNaN(x)), Not(fpIsInf(x)))


ONLY = set(sys.argv[1:])


def run(name, s, vars_, expect):
    if ONLY and name not in ONLY:
        return
    # dump SMT-LIB (before solving) for a cvc5 cross-check
    with open(f"smt2/{name}.smt2", "w") as f:
        f.write("(set-logic QF_FPBV)\n")
        f.write(s.sexpr())
        f.write("\n(check-sat)\n")
    s.set("timeout", 120000)
    r = s.check()
    out = {"result": str(r), "expect": expect}
    if r == sat:
        m = s.model()
        mv = {}
        for nm, v in vars_.items():
            val = m.eval(v, model_completion=True)
            mv[nm] = str(val)
            try:
                if v.sort() == F64:
                    bv = m.eval(fpToIEEEBV(v), model_completion=True).as_long()
                    mv[nm + "_bits64"] = hex(bv)
                elif v.sort() == F32:
                    bv = m.eval(fpToIEEEBV(v), model_completion=True).as_long()
                    mv[nm + "_bits32"] = hex(bv)
                else:
                    mv[nm + "_int"] = m.eval(v, model_completion=True).as_signed_long()
            except Exception as e:  # noqa
                mv[nm + "_err"] = str(e)
        out["model"] = mv
    results[name] = out
    print(name, out)


import os
os.makedirs("smt2", exist_ok=True)

# Q1a: inner non-scoring TermQuery (innerMax = 0), non-negative value, any positive finite boost.
s = Solver()
v, V = FP("v", F64), FP("V", F64)
b = FP("b", F32)
inner = FPVal(0.0, F32)
s.add(finite(v), finite(V), fpLEQ(v, V), fpGEQ(v, FPVal(0.0, F64)), finite(b), fpGT(b, FPVal(0.0, F32)))
s.add(fpGT(jscore(v, b), jbound(inner, V, b)))
run("Q1a_inner0", s, {"v": v, "V": V, "b": b}, "sat (bound unsound when the inner weight is non-scoring)")

# Q1b: constant-score inner (innerMax = 1), v <= V, v >= 0, boost != 1: rounding of the float bound.
s = Solver()
v, V = FP("v", F64), FP("V", F64)
b = FP("b", F32)
inner = FPVal(1.0, F32)
s.add(finite(v), finite(V), fpLEQ(v, V), fpGEQ(v, FPVal(0.0, F64)), finite(b), fpGT(b, FPVal(0.0, F32)))
s.add(fpGT(jscore(v, b), jbound(inner, V, b)))
run("Q1b_inner1_rounding", s, {"v": v, "V": V, "b": b}, "sat (1-ulp)")

# Q1b2: the same with an integral long value (fromLongField: v = (double) raw) and v == V, small magnitude.
s = Solver()
raw = BitVec("raw", 64)
b = FP("b", F32)
v = fpSignedToFP(RNE_, raw, F64)
s.add(raw >= 0, raw <= 1 << 40, finite(b), fpGT(b, FPVal(0.0, F32)), fpLT(b, FPVal(1000.0, F32)))
s.add(fpGT(jscore(v, b), jbound(FPVal(1.0, F32), v, b)))
run("Q1b2_long_rounding", s, {"raw": raw, "b": b}, "sat (1-ulp, long field)")

# Q1c: constant-score inner, boost == 1: expected UNSAT (proof that the bound is sound in this case).
s = Solver()
v, V = FP("v", F64), FP("V", F64)
inner = FPVal(1.0, F32)
bb = FPVal(1.0, F32)
s.add(finite(v), finite(V), fpLEQ(v, V), fpGEQ(v, FPVal(0.0, F64)))
s.add(fpGT(jscore(v, bb), jbound(inner, V, bb)))
run("Q1c_inner1_boost1", s, {"v": v, "V": V}, "unsat")

# Q1d: constant-score inner, negative block max: score() clamps to 0 but the bound stays negative.
s = Solver()
v, V = FP("v", F64), FP("V", F64)
b = FP("b", F32)
inner = FPVal(1.0, F32)
s.add(finite(v), finite(V), fpLEQ(v, V), finite(b), fpGT(b, FPVal(0.0, F32)))
s.add(fpGT(jscore(v, b), jbound(inner, V, b)))
s.add(fpLT(V, FPVal(0.0, F64)))
run("Q1d_negative", s, {"v": v, "V": V, "b": b}, "sat (negative bound < clamped score 0)")

# Q1e: candidate fix: bound' = max(0, RN_f(RN_d(up_f(V) * b))) with up_f(V) = smallest float >= V,
# independent of innerMax. Expected UNSAT for every innerMax.
s = Solver()
v, V = FP("v", F64), FP("V", F64)
b = FP("b", F32)
vf = rn_f(V)
vbits = fpToIEEEBV(vf)
# smallest float >= V: if RN_f(V) < V step one float up (for vf >= +0 increment the bits, for vf < 0 decrement, 0 -> min subnormal)
up = If(fpLT(fpToFP(RNE_, vf, F64), V),
        If(fpIsZero(vf), FPVal(1.401298464324817e-45, F32),
           If(fpIsNegative(vf), fpBVToFP(vbits - 1, F32), fpBVToFP(vbits + 1, F32))),
        vf)
fixbound = fpMax(FPVal(0.0, F32), rn_f(fpMul(RNE_, fpToFP(RNE_, up, F64), rn_d(b))))
s.add(finite(v), finite(V), fpLEQ(v, V), finite(b), fpGT(b, FPVal(0.0, F32)), Not(fpIsInf(rn_f(V))))
s.add(fpGT(jscore(v, b), fixbound))
run("Q1e_fix", s, {"v": v, "V": V, "b": b}, "unsat")

# Q2a: fromDoubleField decoder Double::longBitsToDouble declared INCREASING over signed raw longs.
s = Solver()
r1, r2 = BitVec("r1", 64), BitVec("r2", 64)
d1, d2 = fpBVToFP(r1, F64), fpBVToFP(r2, F64)
s.add(r1 < r2, Not(fpIsNaN(d1)), Not(fpIsNaN(d2)), fpGT(d1, d2))
run("Q2a_double_bits_monotone", s, {"r1": r1, "r2": r2}, "sat (decreasing on negative doubles)")

# Q2a2: same but restricted to blocks with a non-negative maximum: is the bound still correct?
# block max raw r2 decodes to x2 >= 0; any r1 <= r2 must decode to <= x2 (non-NaN).
s = Solver()
d1, d2 = fpBVToFP(r1, F64), fpBVToFP(r2, F64)
s.add(r1 <= r2, Not(fpIsNaN(d1)), Not(fpIsNaN(d2)), fpGEQ(d2, FPVal(0.0, F64)), fpGT(d1, d2))
run("Q2a2_double_bits_nonneg_max", s, {"r1": r1, "r2": r2}, "unsat")

# Q2b: fromFloatField decoder: raw = sign-extended int bits.
s = Solver()
i1, i2 = BitVec("i1", 32), BitVec("i2", 32)
f1, f2 = fpBVToFP(i1, F32), fpBVToFP(i2, F32)
s.add(i1 < i2, Not(fpIsNaN(f1)), Not(fpIsNaN(f2)), fpGT(f1, f2))
run("Q2b_float_bits_monotone", s, {"i1": i1, "i2": i2}, "sat")

# Q2c: fromLongField decoder (double) v declared INCREASING: expected UNSAT (monotone, RNE).
s = Solver()
d1, d2 = fpSignedToFP(RNE_, r1, F64), fpSignedToFP(RNE_, r2, F64)
s.add(r1 <= r2, fpGT(d1, d2))
run("Q2c_long_monotone", s, {"r1": r1, "r2": r2}, "unsat")

# Q2d: (float) cast is monotone: d1 <= d2 -> RN_f(d1) <= RN_f(d2). Expected UNSAT.
s = Solver()
x1, x2 = FP("x1", F64), FP("x2", F64)
s.add(fpLEQ(x1, x2), fpGT(rn_f(x1), rn_f(x2)))
run("Q2d_float_cast_monotone", s, {"x1": x1, "x2": x2}, "unsat")

# Q2e: the block max raw decodes to NaN while a positive finite value is in the block (fromDoubleField).
s = Solver()
d1, d2 = fpBVToFP(r1, F64), fpBVToFP(r2, F64)
s.add(r1 <= r2, Not(fpIsNaN(d1)), fpGT(d1, FPVal(0.0, F64)), fpIsNaN(d2))
run("Q2e_double_nan_max", s, {"r1": r1, "r2": r2}, "sat (bound NaN)")

with open("smt_results.json" if not ONLY else "smt_results_partial.json", "w") as f:
    json.dump(results, f, indent=1)
