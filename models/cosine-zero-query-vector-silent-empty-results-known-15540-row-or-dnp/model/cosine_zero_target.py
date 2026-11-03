"""formal leg: a zero COSINE query target scores NaN against EVERY finite document vector (any dimension).

Model element -> Java line (main 5adff78bb168):
  scalar float loop        sum=fma(a,b,sum); norm1=fma(a,a,norm1); norm2=fma(b,b,norm2)
                           DefaultVectorUtilSupport.java:79-117 (tail loop 110-114; 2-way unrolled loop 87-107)
  unrolled merge           sum += sum1 + sum2; norm1 += n11 + n12; norm2 += n21 + n22   (:104-106)
  final                    (float) (sum / Math.sqrt((double) norm1 * (double) norm2))   (:116)
  normalizeToUnitInterval  Math.max((1 + value) / 2, 0)                                VectorUtil.java:461-463
                           (java.lang.Math.max: "If either value is NaN, then the result is NaN")
  byte cosine              int sum/norm1/norm2; (float) (sum / Math.sqrt((double) norm1 * (double) norm2))
                           DefaultVectorUtilSupport.java:242-; COSINE.compare(byte) = (1 + cos) / 2 (VectorSimilarityFunction.java:89)
  preconditions            doc vector finite (KnnFloatVectorField -> VectorUtil.checkFinite); query target all +-0
                           (accepted: KnnFloatVectorQuery.java:97 checks only finiteness)
Proof: inductive invariant over the loop (both loop shapes), then the final expression. Each obligation is an UNSAT
query (no counterexample). A vacuity probe (an intentionally false obligation) must be SAT.
"""
from z3 import *

F = Float32(); D = Float64(); RM = RNE()
def zero32(x): return fpIsZero(x)
def finite(x): return And(Not(fpIsNaN(x)), Not(fpIsInf(x)))
def nonneg_notnan(x): return And(Not(fpIsNaN(x)), Or(fpIsZero(x), fpIsPositive(x)))   # [+0/-0, +Inf]
def inv(s, n1, n2): return And(zero32(s), nonneg_notnan(n1), zero32(n2), Not(fpIsNegative(n2)))

results = []
def check(name, claim, expect_unsat=True):
    s = Solver(); s.set("timeout", 120000); s.add(Not(claim)); r = s.check()
    ok = (r == unsat) if expect_unsat else (r == sat)
    results.append((name, str(r), "OK" if ok else "UNEXPECTED"))
    print("%-62s %-7s %s" % (name, r, "OK" if ok else "UNEXPECTED"))

a, a2, b, b2 = FPs('a a2 b b2', F)
s, n1, n2, t1, t2, u1, u2, v1, v2 = FPs('s n1 n2 t1 t2 u1 u2 v1 v2', F)
pre = And(finite(a), finite(a2), zero32(b), zero32(b2))

# O1 init: (+0, +0, +0) satisfies the invariant
check("O1 invariant holds at init", inv(FPVal(0.0, F), FPVal(0.0, F), FPVal(0.0, F)))
# O2 step of the tail loop (fma, as in :110-114)
check("O2 tail-loop step preserves invariant",
      Implies(And(pre, inv(s, n1, n2)), inv(fpFMA(RM, a, b, s), fpFMA(RM, a, a, n1), fpFMA(RM, b, b, n2))))
# O3 the unrolled loop keeps two accumulator triples, each with the same invariant (same step as O2)
# O4 merge of the unrolled accumulators: sum += sum1 + sum2, etc. (:104-106)
check("O4 unrolled merge preserves invariant",
      Implies(And(inv(s, n1, n2), inv(t1, u1, v1), inv(t2, u2, v2)),
              inv(fpAdd(RM, s, fpAdd(RM, t1, t2)), fpAdd(RM, n1, fpAdd(RM, u1, u2)), fpAdd(RM, n2, fpAdd(RM, v1, v2)))))
# O5 final expression is NaN under the invariant
fin = fpToFP(RM, fpDiv(RM, fpToFP(RM, s, D), fpSqrt(RM, fpMul(RM, fpToFP(RM, n1, D), fpToFP(RM, n2, D)))), F)
check("O5 final (float)(sum/sqrt(n1*n2)) is NaN", Implies(inv(s, n1, n2), fpIsNaN(fin)))
# O6 normalizeToUnitInterval keeps NaN: (1 + NaN)/2 is NaN and Java Math.max(NaN, 0) = NaN (javadoc), so
#    only the first part needs a solver; Math.max is modelled by its javadoc rule
x = FP('x', F)
half = fpDiv(RM, fpAdd(RM, FPVal(1.0, F), x), FPVal(2.0, F))
check("O6 (1 + NaN) / 2 is NaN", Implies(fpIsNaN(x), fpIsNaN(half)))
# O7 byte cosine: norm2 = 0 (all-zero target) and sum = 0, norm1 >= 0 as double -> 0/sqrt(norm1*0) = NaN
ds, dn1 = FPs('ds dn1', D)
bfin = fpDiv(RM, ds, fpSqrt(RM, fpMul(RM, dn1, FPVal(0.0, D))))
check("O7 byte cosine 0/sqrt(norm1*0) is NaN (norm1 int>=0 finite)",
      Implies(And(fpIsZero(ds), Not(fpIsNegative(ds)), finite(dn1), Not(fpIsNegative(dn1))), fpIsNaN(fpToFP(RM, bfin, F))))
# Vacuity probe: claiming the score is NOT NaN must be refutable (SAT of the negation is unsat -> expect sat here)
check("V1 vacuity probe: 'final is never NaN' must FAIL (sat)", Implies(inv(s, n1, n2), Not(fpIsNaN(fin))), expect_unsat=False)
# Mutant kill: with a NON-zero target component the score need not be NaN (sat)
check("V2 mutant: non-zero target b=1 -> 'step keeps sum zero' must FAIL (sat)",
      Implies(And(finite(a), b == FPVal(1.0, F), inv(s, n1, n2)), zero32(fpFMA(RM, a, b, s))), expect_unsat=False)
bad = [r for r in results if r[2] != "OK"]
print("SUMMARY: %d obligations, %d unexpected" % (len(results), len(bad)))
