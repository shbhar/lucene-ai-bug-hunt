"""Closed form of Word2VecSynonymProvider's acceptance test, over reals and over float32.

Model element -> Java line (a9779ef3c193):
  d       = VectorUtil.dotProduct(query, vector) of two L2-normalized float vectors
            (Word2VecSynonymProvider.java:45-46, 80-81; DOT_PRODUCT.compare VectorSimilarityFunction.java:59-62)
  score   = VectorUtil.normalizeToUnitInterval(d) = Math.max((1 + d) / 2, 0)   (VectorUtil.java:461-463)
            Java float semantics: (1 + d) is a float add (round-to-nearest-even), / 2 a float divide
  accept  = score >= minAcceptedSimilarity                                       (Word2VecSynonymProvider.java:99)
  m       = minAcceptedSimilarity; the factory accepts m in (0, 1]               (Word2VecSynonymFilterFactory.java:68-71)
  javadoc = accept iff cosine >= m                                               (Word2VecSynonymFilter.java:59-60)

Queries (each prints sat/unsat; 'unsat' of a negated claim = the claim is proved):
  R1  reals:  accept(c, m) <=> c >= 2m - 1                       (proof)
  R2  reals:  c >= m  =>  accept(c, m)                           (proof: never stricter than the javadoc)
  R3  reals:  for every m < 1 some c < m is accepted             (proof: strictly looser than the javadoc)
  R4  reals:  m <= 1/2 => c = 0 accepted; m < 1/2 => some c < 0 accepted   (orthogonal / anti-correlated)
  F1  float32: accept(d, m) <=> fl(1 + d) >= 2m  (2m exact)      (proof over all floats d in [-1,1], m in (0,1])
  F2  float32: d >= m => accept(d, m)                            (proof)
  F3  float32: the default m = 0.8f accepts exactly d >= 0.6f (proof)
  F4  float32: some m in (0,1] admits a d below the real 2m - 1 (rounding band; witness)
  F5  float32: m = 0.5f accepts some d < 0 (witness)
  (F3 first expected a rounding band at 0.8f; z3 said unsat: 2*0.8f - 1 == 0.6f exactly and the tie rounds to even.)
"""
import sys
from z3 import (Real, Solver, And, Or, Not, Implies, sat, unsat, FP, Float32, RNE, fpAdd, fpDiv, fpGEQ, fpLT,
                fpLEQ, fpGT, FPVal, fpIsNaN, fpMul, fpToReal, set_param, If,
                ForAll, Exists)

results = {}


def run(name, s, expect):
    r = s.check()
    ok = (str(r) == expect)
    results[name] = (str(r), expect, ok)
    print(f"{name}: {r} (expected {expect}) {'OK' if ok else 'MISMATCH'}")
    if r == sat:
        print("   model:", s.model())
    sys.stdout.flush()
    return s


def accept_real(c, m):
    return (1 + c) / 2 >= m  # max(.,0) is irrelevant for m > 0


# ---- reals ----
c, m = Real('c'), Real('m')
dom = And(c >= -1, c <= 1, m > 0, m <= 1)
s = Solver(); s.add(dom, Not(accept_real(c, m) == (c >= 2 * m - 1))); run("R1 accept <=> c >= 2m-1", s, "unsat")
s = Solver(); s.add(dom, c >= m, Not(accept_real(c, m))); run("R2 c >= m => accept", s, "unsat")
# R3: for a fixed m < 1 show c = (m + (2m-1))/2 is accepted and < m (constructive), as a universal claim:
s = Solver(); s.add(m > 0, m < 1, Not(And(accept_real((3 * m - 1) / 2, m), (3 * m - 1) / 2 < m, (3 * m - 1) / 2 >= -1)))
run("R3 every m<1 accepts some c<m (witness c=(3m-1)/2)", s, "unsat")
s = Solver(); s.add(m > 0, m <= 0.5, Not(accept_real(0, m))); run("R4a m<=1/2 => c=0 accepted", s, "unsat")
s = Solver(); s.add(m > 0, m < 0.5, Not(And(accept_real(2 * m - 1, m), 2 * m - 1 < 0)))
run("R4b m<1/2 => c=2m-1<0 accepted", s, "unsat")

# ---- float32 ----
F = Float32()
d = FP('d', F)
mf = FP('mf', F)
one = FPVal(1.0, F)
two = FPVal(2.0, F)
zero = FPVal(0.0, F)
onep = fpAdd(RNE(), one, d)
score = fpDiv(RNE(), onep, two)
score = If(fpGEQ(score, zero), score, zero)  # Math.max(x, 0) for non-NaN x
fdom = And(fpGEQ(d, FPVal(-1.0, F)), fpLEQ(d, one), fpGT(mf, zero), fpLEQ(mf, one))
accept_f = fpGEQ(score, mf)
twom = fpMul(RNE(), two, mf)  # exact for mf in (0,1] (no overflow; subnormal*2 is exact)
s = Solver(); s.set("timeout", 600000)
s.add(fdom, Not(accept_f == fpGEQ(onep, twom)))
run("F1 float: accept <=> fl(1+d) >= 2m", s, "unsat")
s = Solver(); s.set("timeout", 600000)
s.add(fdom, fpGEQ(d, mf), Not(accept_f))
run("F2 float: d >= m => accept", s, "unsat")
# F3: default 0.8f: exact float closed form accept <=> d >= 0.6f (0.6f == 2*0.8f - 1 exactly)
m08 = FPVal(0.8, F)
s = Solver(); s.set("timeout", 600000)
s.add(fpGEQ(d, FPVal(-1.0, F)), fpLEQ(d, one), Not(fpGEQ(score, m08) == fpGEQ(d, FPVal(0.6, F))))
run("F3 float: m=0.8f accepts exactly d >= 0.6f", s, "unsat")
# F4: is there ANY m in (0,1] whose float acceptance admits a d below the real 2m-1 (a rounding band)?
s = Solver(); s.set("timeout", 600000)
# pure FP (no reals): widen to float64, where 2m - 1 and the comparison are exact for float32 m, d
from z3 import Float64, fpToFP, fpSub
D = Float64()
d64 = fpToFP(RNE(), d, D)
r64 = fpSub(RNE(), fpMul(RNE(), FPVal(2.0, D), fpToFP(RNE(), mf, D)), FPVal(1.0, D))
s.add(fdom, accept_f, fpLT(d64, r64))
run("F4 float: some m admits d < 2m-1 (rounding band; witness)", s, "sat")
# F5: m = 0.5f accepts d = 0 (orthogonal) and some d < 0 (rounding: 1 + d rounds to 1)
s = Solver(); s.set("timeout", 600000)
s.add(fpGEQ(d, FPVal(-1.0, F)), fpLT(d, zero), fpGEQ(score, FPVal(0.5, F)))
run("F5 float: m=0.5f accepts some d < 0 (witness)", s, "sat")
bad = [k for k, v in results.items() if not v[2]]
print("ALL AS EXPECTED" if not bad else f"UNEXPECTED: {bad}")
sys.exit(1 if bad else 0)
