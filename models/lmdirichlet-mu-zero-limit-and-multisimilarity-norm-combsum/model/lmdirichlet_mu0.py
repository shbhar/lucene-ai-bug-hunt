"""
lmdirichlet-mu-zero-limit-and-multisimilarity-norm-combsum / LMDirichletSimilarity at mu = 0: SMT model (z3).

Model element -> Java line (lucene/core/src/java/org/apache/lucene/search/similarities/, @ a9779ef3c193)
  constructor accepts mu >= 0, finite          LMDirichletSimilarity.java:49-52, :58-61
  score = boost * (log(1 + f/(mu*P)) + log(mu/(dl+mu)))   LMDirichletSimilarity.java:77-80
  clamp: score > 0 ? score : 0                 LMDirichletSimilarity.java:81
  dl = LENGTH_TABLE[norm] (>= 1 for legal norms)   SimilarityBase.java:180-186 (norm 0 is not legal, Similarity.java:145)
  P = (ttf + 1) / (sumTotalTermFreq + 1)       LMSimilarity.java:150-151 (DefaultCollectionModel)
  f = freq, finite and positive                Similarity.java SimScorer.score javadoc

Part A (real arithmetic): the two-log form equals the single-log argument for mu > 0, and its value at
  mu = 0 is finite; the positive-score region f > P*dl does not depend on mu.
Part B (IEEE binary64, round-to-nearest): with mu = +0.0 the Java expression is NaN for EVERY legal input,
  so the clamp returns 0.0 for every document. Math.log is modelled as an uninterpreted function constrained
  only by the special cases its javadoc specifies (log(+Inf) = +Inf, log(+-0) = -Inf, log(NaN) = NaN,
  log(x < 0) = NaN) plus "finite for finite positive arguments" (javadoc: within 1 ulp of the exact result).
  Vacuity probes: the same constraints with mu = Float.MIN_VALUE admit a positive score (SAT), and the
  single-log rewrite (the fix sketch) is never NaN at mu = 0 (UNSAT).
"""
import sys
from z3 import *

results = []
def check(name, solver, expect):
    r = solver.check()
    ok = (str(r) == expect)
    results.append((name, str(r), expect, ok))
    print(f"{name}: {r} (expected {expect}) {'OK' if ok else 'MISMATCH'}")
    if r == sat:
        print("   model:", solver.model())
    sys.stdout.flush()

# ---------------- Part A: reals ----------------
mu, P, dl, f = Reals('mu P dl f')
dom = And(P > 0, P <= 1, dl >= 1, f > 0)

s = Solver(); s.set("timeout", 600000)
lhs = (1 + f / (mu * P)) * (mu / (dl + mu))      # exp(log(1+f/(mu P)) + log(mu/(dl+mu)))
rhs = (f + mu * P) / (P * (dl + mu))
s.add(dom, mu > 0, lhs != rhs)
check("A1 two-log product == single-log argument for all mu > 0", s, "unsat")

s = Solver(); s.set("timeout", 600000)
s.add(dom, mu >= 0, Not(P * (dl + mu) > 0))
check("A2 single-log denominator P*(dl+mu) > 0 for all mu >= 0 (rhs is a rational function continuous at mu = 0)", s, "unsat")

s = Solver(); s.set("timeout", 600000)
s.add(dom, mu >= 0, Not((rhs > 1) == (f > P * dl)))
check("A3 score > 0 iff f > P*dl, for every mu >= 0 (positive region independent of mu)", s, "unsat")

s = Solver(); s.set("timeout", 600000)
s.add(dom, f > P * dl)
check("A4 vacuity: documents with f > P*dl exist in the domain", s, "sat")

# ---------------- Part B: IEEE binary64 ----------------
D = Float64()
RM = RNE()
LOG = Function('Math_log', D, D)
def fp(x): return FPVal(x, D)
PINF, NINF = fpPlusInfinity(D), fpMinusInfinity(D)

def log_axioms(s, args):
    for a in args:
        s.add(Implies(And(fpIsInf(a), fpIsPositive(a)), LOG(a) == PINF))
        s.add(Implies(fpIsZero(a), LOG(a) == NINF))
        s.add(Implies(fpIsNaN(a), fpIsNaN(LOG(a))))
        s.add(Implies(And(fpLT(a, fp(0.0)), Not(fpIsZero(a))), fpIsNaN(LOG(a))))
        s.add(Implies(And(Or(fpIsNormal(a), fpIsSubnormal(a)), fpGT(a, fp(0.0))), And(Not(fpIsInf(LOG(a))), Not(fpIsNaN(LOG(a))))))

fF, PF, dlF, boostF = Consts('f P dl boost', D)
FLOAT_MAX = 3.4028234663852886e38
def legal(s):
    # freq: a positive finite float (widened to double); dl: decoded length 1 .. 2^31; P in [2^-63, 1];
    # boost: positive finite float
    s.add(fpGEQ(fF, fp(1.401298464324817e-45)), fpLEQ(fF, fp(FLOAT_MAX)))
    s.add(fpGEQ(dlF, fp(1.0)), fpLEQ(dlF, fp(2147483648.0)))
    s.add(fpGEQ(PF, fp(2.0 ** -63)), fpLEQ(PF, fp(1.0)))
    s.add(fpGEQ(boostF, fp(1.401298464324817e-45)), fpLEQ(boostF, fp(FLOAT_MAX)))

def java_score(muF):
    t1 = fpDiv(RM, fF, fpMul(RM, muF, PF))
    a1 = fpAdd(RM, fp(1.0), t1)
    a2 = fpDiv(RM, muF, fpAdd(RM, dlF, muF))
    sc = fpMul(RM, boostF, fpAdd(RM, LOG(a1), LOG(a2)))
    clamped = If(fpGT(sc, fp(0.0)), sc, fp(0.0))
    return a1, a2, sc, clamped

s = Solver(); s.set("timeout", 600000)
legal(s)
muZ = fpPlusZero(D)
a1, a2, sc, cl = java_score(muZ)
log_axioms(s, [a1, a2])
s.add(Not(fpIsNaN(sc)))
check("B1 mu = +0.0: unclamped Java score is NaN for every legal input", s, "unsat")

s = Solver(); s.set("timeout", 600000)
legal(s)
a1, a2, sc, cl = java_score(muZ)
log_axioms(s, [a1, a2])
s.add(Not(cl == fp(0.0)))
check("B2 mu = +0.0: returned (clamped) score is +0.0 for every legal input", s, "unsat")

s = Solver(); s.set("timeout", 600000)
legal(s)
muT = fp(1.401298464324817e-45)  # Float.MIN_VALUE widened to double
a1, a2, sc, cl = java_score(muT)
log_axioms(s, [a1, a2])
s.add(fpGT(cl, fp(0.0)))
check("B3 vacuity: mu = Float.MIN_VALUE admits a positive score under the same axioms", s, "sat")

# fix sketch: boost * log((f + mu*P) / ((dl + mu) * P)) at mu = +0.0
s = Solver(); s.set("timeout", 600000)
legal(s)
arg = fpDiv(RM, fpAdd(RM, fF, fpMul(RM, muZ, PF)), fpMul(RM, fpAdd(RM, dlF, muZ), PF))
log_axioms(s, [arg])
scf = fpMul(RM, boostF, LOG(arg))
s.add(Or(fpIsNaN(scf), fpIsInf(LOG(arg))))
check("B4 fix sketch at mu = +0.0: log argument finite positive, score never NaN, log never infinite", s, "unsat")

s = Solver(); s.set("timeout", 600000)
legal(s)
log_axioms(s, [arg])
s.add(Or(Not(Or(fpIsNormal(arg), fpIsSubnormal(arg))), Not(fpGT(arg, fp(0.0)))))
check("B5 fix sketch at mu = +0.0: log argument f/(dl*P) is a finite positive double", s, "unsat")

bad = [r for r in results if not r[3]]
print(f"SUMMARY: {len(results)} obligations, {len(bad)} mismatches")
sys.exit(1 if bad else 0)
