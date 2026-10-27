#!/usr/bin/env python3
"""Generate the SMT-LIB2 (QF_FP) queries for unit distance-feature-pruning.

Model element -> Java line mapping (lucene/core/src/java/org/apache/lucene/document/
LatLonPointDistanceFeatureQuery.java @ ea57360912ed):
  score(d)            -> line 280  (float) (boost * (pivotDistance / (pivotDistance + distance)))
                         boost is a float widened to double (exact), one RNE double mul/div/add,
                         then an RNE narrowing to float.
  early return        -> line 289  if (score(previousMaxDistance) >= minScore) return previous
  bisection loop      -> lines 294-304: min = 0, max = previous; while (max - min > 1)
                         { mid = (min + max) / 2; if (score(mid) >= minScore) min = mid else max = mid }
                         (x / 2 is modelled as x * 0.5: both are an exact scaling followed by one RNE
                         rounding, identical for every double including subnormals)
  return value        -> line 307  return min
  box radius          -> Rectangle.java:150  radDistance = (radiusMeters + 7E-2) / EARTH_MEAN_RADIUS_METERS,
                         i.e. the box reaches (to within lat quantization, 4.66 mm) radius + 0.07 m due north.
                         The query is satisfiable iff a distance d >= min + 0.075 m (box edge plus one
                         latitude quantization step) still has score(d) >= minScore.
Queries:
  q1_postcondition.smt2: from the loop exit state alone (invariant + exit test), try to prove
      score(min + 0.075) < minScore. UNSAT would prove the 7 cm pad sufficient; SAT is a counterexample.
  q2_reach_full.smt2: the real bisection unrolled (26 guarded steps; 25 are taken from the initial
      maxDistance = EARTH_MEAN_RADIUS_METERS * PI = 2.0015114352186374E7), concrete pivot 1000 m, boost 1,
      symbolic float minScore in (0, 1]. SAT means a reachable state of computeMaxDistance whose box
      excludes a competitive distance.
  q2_reach_2048.smt2: the same from previousMaxDistance = 2048 (a reachable later call), 11 steps.
"""
import sys, os

RNE = "RNE"
def d64(hexbits):
    v = int(hexbits, 16)
    s = v >> 63; e = (v >> 52) & 0x7FF; m = v & ((1 << 52) - 1)
    return f"(fp #b{s} #b{e:011b} #b{m:052b})"

PI_R = d64("41731680a5a28e2e")
HALF = "((_ to_fp 11 53) RNE 0.5)"
PAD = d64("3fb3333333333333")  # 0.075
ONE = d64("3ff0000000000000")
ZERO = "(_ +zero 11 53)"

def score(d, p="p", b="b"):
    return f"((_ to_fp 8 24) RNE (fp.mul RNE ((_ to_fp 11 53) RNE {b}) (fp.div RNE {p} (fp.add RNE {p} {d}))))"

def header(logic="QF_FP"):
    return f"(set-logic {logic})\n(set-option :produce-models true)\n"

def q1():
    s = header()
    s += "(declare-const p (_ FloatingPoint 11 53))\n(declare-const b (_ FloatingPoint 8 24))\n"
    s += "(declare-const m (_ FloatingPoint 8 24))\n(declare-const lo (_ FloatingPoint 11 53))\n(declare-const hi (_ FloatingPoint 11 53))\n"
    # legal inputs: pivot > 0 finite (constructor check), boost >= 0 finite (BoostQuery), minScore <= boost
    s += "(assert (fp.gt p (_ +zero 11 53)))\n(assert (not (fp.isInfinite p)))\n(assert (not (fp.isNaN p)))\n"
    s += "(assert (fp.gt b (_ +zero 8 24)))\n(assert (not (fp.isInfinite b)))\n(assert (not (fp.isNaN b)))\n"
    s += "(assert (fp.gt m (_ +zero 8 24)))\n(assert (fp.leq m b))\n"
    s += f"(assert (fp.geq lo {ZERO}))\n(assert (not (fp.isInfinite hi)))\n(assert (fp.lt lo hi))\n"
    # loop exit: !(max - min > 1); invariant: score(min) >= m, score(max) < m
    s += f"(assert (not (fp.gt (fp.sub RNE hi lo) {ONE})))\n"
    s += f"(assert (fp.geq {score('lo')} m))\n(assert (fp.lt {score('hi')} m))\n"
    # negated postcondition: a distance 7.5 cm beyond the returned radius is still competitive
    s += f"(assert (fp.geq {score(f'(fp.add RNE lo {PAD})')} m))\n"
    s += "(check-sat)\n(get-value (p b m lo hi))\n"
    return s

def q2(start_hex, steps, name):
    s = header()
    s += "(declare-const m (_ FloatingPoint 8 24))\n"
    s += f"(define-fun p () (_ FloatingPoint 11 53) {d64('408f400000000000')})\n"  # 1000.0
    s += "(define-fun b () (_ FloatingPoint 8 24) ((_ to_fp 8 24) RNE 1.0))\n"
    s += "(assert (fp.gt m (_ +zero 8 24)))\n(assert (fp.leq m b))\n"
    s += f"(define-fun lo0 () (_ FloatingPoint 11 53) {ZERO})\n"
    s += f"(define-fun hi0 () (_ FloatingPoint 11 53) {d64(start_hex)})\n"
    # line 289: early return not taken
    s += f"(assert (fp.lt {score('hi0')} m))\n"
    for i in range(steps):
        guard = f"(fp.gt (fp.sub RNE hi{i} lo{i}) {ONE})"
        s += f"(define-fun mid{i} () (_ FloatingPoint 11 53) (fp.mul RNE (fp.add RNE lo{i} hi{i}) {HALF}))\n"
        s += f"(define-fun up{i} () Bool (fp.geq {score(f'mid{i}')} m))\n"
        s += f"(define-fun lo{i+1} () (_ FloatingPoint 11 53) (ite (and {guard} up{i}) mid{i} lo{i}))\n"
        s += f"(define-fun hi{i+1} () (_ FloatingPoint 11 53) (ite (and {guard} (not up{i})) mid{i} hi{i}))\n"
    n = steps
    # loop must have terminated within the unrolling (unwinding assertion)
    s += f"(assert (not (fp.gt (fp.sub RNE hi{n} lo{n}) {ONE})))\n"
    s += f"(assert (fp.geq {score(f'(fp.add RNE lo{n} {PAD})')} m))\n"
    s += f"(check-sat)\n(get-value (m lo{n} hi{n}))\n"
    return s

if __name__ == "__main__":
    out = sys.argv[1]
    open(os.path.join(out, "q1_postcondition.smt2"), "w").write(q1())
    open(os.path.join(out, "q2_reach_full.smt2"), "w").write(q2("41731680a5a28e2e", 26, "full"))
    open(os.path.join(out, "q2_reach_2048.smt2"), "w").write(q2("40a0000000000000", 12, "2048"))
    # Vacuity / unwinding probes for q2 (expected results in run_smt.sh):
    #  p1: q2 path constraints without the goal            -> SAT   (the model is not vacuous)
    #  p2: after 26 guarded steps the loop is still running -> UNSAT (26 steps unwind the loop fully)
    #  p3: after 24 guarded steps the loop is still running -> SAT   (fewer steps would be unsound)
    base = q2("41731680a5a28e2e", 26, "full")
    goal = base[base.index("(assert (fp.geq ((_ to_fp 8 24) RNE (fp.mul RNE ((_ to_fp 11 53) RNE b) (fp.div RNE p (fp.add RNE p (fp.add RNE lo26"):base.index("(check-sat)")]
    term = "(assert (not (fp.gt (fp.sub RNE hi26 lo26) " + ONE + ")))\n"
    p1 = base.replace(goal, "")
    p2 = base.replace(goal, "").replace(term, "(assert (fp.gt (fp.sub RNE hi26 lo26) " + ONE + "))\n")
    b24 = q2("41731680a5a28e2e", 24, "p3")
    goal24 = b24[b24.index("(assert (fp.geq ((_ to_fp 8 24) RNE (fp.mul RNE ((_ to_fp 11 53) RNE b) (fp.div RNE p (fp.add RNE p (fp.add RNE lo24"):b24.index("(check-sat)")]
    term24 = "(assert (not (fp.gt (fp.sub RNE hi24 lo24) " + ONE + ")))\n"
    p3 = b24.replace(goal24, "").replace(term24, "(assert (fp.gt (fp.sub RNE hi24 lo24) " + ONE + "))\n")
    for name, q in (("p1_q2_no_goal", p1), ("p2_q2_26_steps_insufficient", p2), ("p3_q2_24_steps_insufficient", p3)):
        assert q != base
        open(os.path.join(out, name + ".smt2"), "w").write(q)
