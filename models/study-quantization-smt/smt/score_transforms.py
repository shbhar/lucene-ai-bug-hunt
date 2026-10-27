"""SMT-1: bit-precise (QF_FP, binary32) proofs about Lucene's score transformations.

Transcribed from lucene/core/src/java/org/apache/lucene/util/VectorUtil.java (main 7cecf79523):
  scaleMaxInnerProductScore (:444-449), normalizeToUnitInterval (:461-463),
  normalizeDistanceToUnitInterval (:474-476)
and the quantized tails of Lucene104ScalarQuantizedVectorScorer.quantizedScore (:294-316):
  EUCLIDEAN : 1 / (1f + Math.max(score, 0f))
  DOT/COSINE: (1f + Math.clamp(score, -1, 1)) / 2f
  MIP       : scaleMaxInnerProductScore(score)
Every query asserts the NEGATION of the property; 'unsat' means proved for all 2^32 (or 2^64
for pairs) float inputs. A 'sat' answer prints the counterexample.
"""
import sys, time
sys.path.insert(0, __file__.rsplit('/', 1)[0])
from fpjava import *
from z3 import Solver, sat, unsat, Implies, BoolVal

results = []
from z3 import fpToIEEEBV, fpBVToFP, Extract, BitVecVal


def succ(v):
    """Next float above v in IEEE order (v non-NaN, v != +inf, v != -0). Positive (incl +0): bits+1;
    negative: bits-1 (toward -0). Monotonicity on all consecutive pairs plus f(-0)==f(+0) implies
    monotonicity on all pairs by transitivity (floats minus NaN are a finite total preorder in which
    only -0/+0 are tied)."""
    bv = fpToIEEEBV(v)
    neg = Extract(31, 31, bv) == BitVecVal(1, 1)
    return fpBVToFP(If(neg, bv - 1, bv + 1), F32)


def mono_inc(name, f, dom=lambda v: BoolVal(True)):
    v = f32("v")
    check(name + " [consecutive floats]",
          [Not(fpIsNaN(v)), Not(fpIsInf(v) & fpIsPositive(v)) if False else Not(And(fpIsInf(v), fpIsPositive(v))),
           Not(And(fpIsZero(v), fpIsNegative(v))), dom(v), dom(succ(v)), Not(fpLEQ(f(v), f(succ(v))))])
    check(name + " [f(-0) vs f(+0)]", [Not(fpEQ(f(fpMinusZero(F32)), f(fpPlusZero(F32))))] if True else [])


def mono_dec(name, f, dom=lambda v: BoolVal(True)):
    v = f32("v")
    check(name + " [consecutive floats]",
          [Not(fpIsNaN(v)), Not(And(fpIsInf(v), fpIsPositive(v))),
           Not(And(fpIsZero(v), fpIsNegative(v))), dom(v), dom(succ(v)), Not(fpGEQ(f(v), f(succ(v))))])
    check(name + " [f(-0) vs f(+0)]", [Not(fpEQ(f(fpMinusZero(F32)), f(fpPlusZero(F32))))])


def check(name, constraints, expect_unsat=True, timeout_ms=300000):
    s = Solver()
    s.set("timeout", timeout_ms)
    for c in constraints:
        s.add(c)
    t = time.time()
    r = s.check()
    dt = time.time() - t
    ok = (r == unsat) if expect_unsat else (r == sat)
    tag = "PROVED" if (r == unsat and expect_unsat) else ("CEX" if r == sat else str(r).upper())
    line = f"[{tag}] {name}  ({r}, {dt:.2f}s)"
    if r == sat:
        m = s.model()
        line += "\n      model: " + ", ".join(f"{d.name()}={m[d]}" for d in m.decls())
    print(line, flush=True)
    results.append((name, str(r), dt))
    return r


one = c32(1.0)
two = c32(2.0)
zero = c32(0.0)
mone = c32(-1.0)


def smips(x):  # VectorUtil.scaleMaxInnerProductScore
    return If(fpLT(x, zero), fpDiv(rm, one, fpAdd(rm, one, fpMul(rm, mone, x))), fpAdd(rm, x, one))


def n2u(x):  # VectorUtil.normalizeToUnitInterval
    return jmax(fpDiv(rm, fpAdd(rm, one, x), two), zero)


def d2u(d):  # VectorUtil.normalizeDistanceToUnitInterval
    return fpDiv(rm, one, fpAdd(rm, one, d))


def q_euclid(s):  # quantizedScore EUCLIDEAN tail
    return fpDiv(rm, one, fpAdd(rm, one, jmax(s, zero)))


def q_dot(s):  # quantizedScore DOT_PRODUCT / COSINE tail
    return fpDiv(rm, fpAdd(rm, one, jclamp(s, mone, one)), two)


x, y = f32("x"), f32("y")
notnan = lambda v: Not(fpIsNaN(v))

print("== scaleMaxInnerProductScore (MAXIMUM_INNER_PRODUCT float and quantized path)")
mono_inc("MIP: monotone non-decreasing on all non-NaN floats", smips)
check("MIP: finite input -> finite and strictly positive score",
      [finite(x), Not(And(finite(smips(x)), fpGT(smips(x), zero)))])
check("MIP: NaN input -> NaN score (cex expected = NaN propagates)",
      [fpIsNaN(x), Not(fpIsNaN(smips(x)))])
check("MIP: continuity point: f(-0.0)=f(+0.0)=1",
      [fpIsZero(x), Not(fpEQ(smips(x), one))])

print("== normalizeToUnitInterval (DOT_PRODUCT / COSINE float path)")
check("DOT: x in [-1,1] -> score in [0,1]",
      [fpGEQ(x, mone), fpLEQ(x, one), Not(And(fpGEQ(n2u(x), zero), fpLEQ(n2u(x), one)))])
check("DOT: monotone non-decreasing on all non-NaN floats [pairwise]",
      [notnan(x), notnan(y), fpLEQ(x, y), Not(fpLEQ(n2u(x), n2u(y)))])
check("DOT: every finite x -> score >= 0 (the Math.max floor)",
      [finite(x), Not(fpGEQ(n2u(x), zero))])
check("DOT: can a finite x give score > 1? (sat expected: javadoc says 'between 0 and 1')",
      [finite(x), fpGT(n2u(x), one)], expect_unsat=False)
check("DOT: NaN in -> NaN out (Math.max does not absorb NaN)",
      [fpIsNaN(x), Not(fpIsNaN(n2u(x)))])

print("== normalizeDistanceToUnitInterval (EUCLIDEAN float path)")
check("EUC: finite d >= 0 -> score in (0,1]",
      [finite(x), fpGEQ(x, zero), Not(And(fpGT(d2u(x), zero), fpLEQ(d2u(x), one)))])
mono_dec("EUC: monotone non-increasing on d >= 0", d2u, dom=lambda v: fpGEQ(v, zero))

print("== quantized tails (Lucene104ScalarQuantizedVectorScorer.quantizedScore:294-316)")
check("qEUC: any non-NaN raw score -> [0,1] and finite",
      [notnan(x), Not(And(fpGEQ(q_euclid(x), zero), fpLEQ(q_euclid(x), one)))])
check("qEUC: finite raw score -> strictly positive",
      [finite(x), Not(fpGT(q_euclid(x), zero))])
mono_dec("qEUC: monotone non-increasing in raw squared distance (all non-NaN)", q_euclid)
check("qEUC: NaN raw score -> NaN (only failure mode)",
      [fpIsNaN(x), Not(fpIsNaN(q_euclid(x)))])
check("qDOT: any non-NaN raw score (incl. +-inf) -> [0,1]",
      [notnan(x), Not(And(fpGEQ(q_dot(x), zero), fpLEQ(q_dot(x), one)))])
mono_inc("qDOT: monotone non-decreasing (all non-NaN)", q_dot)
check("qDOT: NaN raw score -> NaN (only failure mode)",
      [fpIsNaN(x), Not(fpIsNaN(q_dot(x)))])

print("== squareDistance terms are never negative (so the float EUCLIDEAN path never sees d<0)")
a, b, acc = f32("a"), f32("b"), f32("acc")
diff = fpSub(rm, a, b)
nxt = fpFMA(rm, diff, diff, acc)
check("fma(diff,diff,acc) >= 0 whenever acc >= 0 and no NaN (inductive step of the sum)",
      [finite(a), finite(b), notnan(acc), fpGEQ(acc, zero), Not(Or(fpGEQ(nxt, zero), fpIsNaN(nxt)))])
check("... and it can only become NaN if acc=+inf and diff^2=+inf? (should be unsat: inf+inf=inf)",
      [finite(a), finite(b), notnan(acc), fpGEQ(acc, zero), fpIsNaN(nxt)])

print("SUMMARY", len(results), "queries")
