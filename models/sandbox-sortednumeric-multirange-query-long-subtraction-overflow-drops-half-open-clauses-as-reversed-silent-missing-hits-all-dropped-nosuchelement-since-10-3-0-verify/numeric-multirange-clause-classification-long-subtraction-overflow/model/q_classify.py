# sandbox-sortednumeric-multirange-query-long-subtraction-overflow-drops-half-open-clauses-as-reversed-silent-missing-hits-all-dropped-nosuchelement-since-10-3-0-verify SMT model: clause classification in SortedNumericDocValuesMultiRangeQuery.resolveOverlaps.
#
# Model element -> Java line (main f794ff5a505c):
#   cls_java(lo, up)  -> SortedNumericDocValuesMultiRangeQuery.java:98-106
#                        long cmp = r.lower - r.upper;  cmp == 0 -> point; cmp < 0 -> range (2 edges); else dropped
#   cls_ref(lo, up)   -> the intended classification (the comment ':105 // else drop reverse ranges' and the
#                        single-clause path newSlowRangeQuery(lower, upper), DocValuesMultiRangeQuery.java:178-181):
#                        lo == up -> point; lo < up -> range; lo > up -> reversed, dropped
#   cls_fix(lo, up)   -> the proposed fix: int cmp = Long.compare(r.lower, r.upper)
# Java long = 64-bit two's complement with wrap-around: z3 BitVec(64), bvsub, signed comparisons (bvslt).
#
# Queries:
#   Q1 (sat expected)   exists (lo, up): cls_java != cls_ref                         -> a misclassification exists
#   Q2 (unsat expected) cls_java != cls_ref  and the exact lo - up IS a Java long (66-bit sign-extended arithmetic)
#   Q2b(unsat expected) the exact lo - up is NOT a Java long and cls_java == cls_ref
#                       -> exact characterisation: misclassified <=> lo - up overflows <=> up - lo > 2^63 (a legal
#                          range, dropped) or lo - up > Long.MAX_VALUE (a reversed clause, kept as a range)
#                       (first attempt used 'up - lo > MAX': Q2b refuted it with [MIN, 0]; logged as a model fix)
#   Q3 (sat expected)   lo < up (a legal range) and cls_java == DROPPED                -> legal ranges dropped
#   Q4 (sat expected)   lo > up (reversed) and cls_java == RANGE                      -> reversed kept as a range
#   Q5 (unsat expected) cls_java == POINT and lo != up                                -> no false points
#   Q6 (unsat expected) cls_fix != cls_ref                                            -> the fix is exact
#   Q7 (unsat expected) lo < up, lo == Long.MIN_VALUE, up <= 0, cls_java != RANGE     -> '<= x' kept for x <= 0
#   Q8 (unsat expected) lo < up, up == Long.MAX_VALUE, lo >= -1, cls_java != RANGE    -> '>= x' kept for x >= -1
#   Q9 (unsat expected) lo < up, lo == Long.MIN_VALUE, up >= 1, cls_java != DROPPED   -> every '<= x', x >= 1, dropped
#   Q10(unsat expected) lo < up, up == Long.MAX_VALUE, lo <= -2, cls_java != DROPPED  -> every '>= x', x <= -2, dropped
#   Vacuity: Q1, Q3, Q4 sat with models; each model is re-executed in plain Java-semantics Python below.
import sys
from z3 import BitVec, BitVecVal, If, Solver, Not, Or, sat, unsat, SignExt

POINT, RANGE, DROPPED = 0, 1, 2
MIN = -(1 << 63)
MAX = (1 << 63) - 1

lo = BitVec('lo', 64)
up = BitVec('up', 64)


def cls_java(lo, up):
    cmp = lo - up  # bvsub = Java long subtraction (wraps)
    return If(cmp == 0, POINT, If(cmp < 0, RANGE, DROPPED))  # '<' on BitVec is signed


def cls_ref(lo, up):
    return If(lo == up, POINT, If(lo < up, RANGE, DROPPED))


def cls_fix(lo, up):
    # Long.compare(x, y) = (x < y) ? -1 : ((x == y) ? 0 : 1)
    c = If(lo < up, -1, If(lo == up, 0, 1))
    return If(c == 0, POINT, If(c < 0, RANGE, DROPPED))


def exact(bv):
    # exact (non-wrapping) value: sign-extend to 66 bits, so a difference of two 64-bit longs cannot wrap
    return SignExt(2, bv)


def java_long_sub(a, b):
    r = (a - b) & ((1 << 64) - 1)
    return r - (1 << 64) if r >= (1 << 63) else r


def py_java(a, b):
    c = java_long_sub(a, b)
    return POINT if c == 0 else (RANGE if c < 0 else DROPPED)


def py_ref(a, b):
    return POINT if a == b else (RANGE if a < b else DROPPED)


def as_long(m, v):
    x = m[v].as_long()
    return x - (1 << 64) if x >= (1 << 63) else x


fails = 0


def run(name, constraints, expect):
    global fails
    s = Solver()
    s.set('timeout', 600000)
    for c in constraints:
        s.add(c)
    r = s.check()
    ok = (r == sat and expect == 'sat') or (r == unsat and expect == 'unsat')
    line = f"{name}: {r} (expected {expect}) {'OK' if ok else 'UNEXPECTED'}"
    if r == sat:
        a, b = as_long(s.model(), lo), as_long(s.model(), up)
        line += f"  model lo={a} up={b}  java-semantics replay: java={py_java(a, b)} ref={py_ref(a, b)}"
    print(line)
    if not ok:
        fails += 1


cj, cr, cf = cls_java(lo, up), cls_ref(lo, up), cls_fix(lo, up)
MAX66 = BitVecVal(MAX, 66)
MIN66 = BitVecVal(MIN, 66)
# the exact difference lo - up is not a Java long: up - lo > 2^63 (legal range wider than 2^63) or lo - up > MAX
wide = Or(exact(lo) - exact(up) < MIN66, exact(lo) - exact(up) > MAX66)  # signed 66-bit compares

run("Q1 misclassification exists", [cj != cr], 'sat')
run("Q2 misclassified although the exact lo - up is a long", [cj != cr, Not(wide)], 'unsat')
run("Q2b exact lo - up overflows yet correctly classified", [wide, cj == cr], 'unsat')
run("Q3 legal range dropped", [lo < up, cj == DROPPED], 'sat')
run("Q4 reversed clause kept as a range", [lo > up, cj == RANGE], 'sat')
run("Q5 false point", [cj == POINT, lo != up], 'unsat')
run("Q6 fix differs from intended", [cf != cr], 'unsat')
run("Q7 [MIN, x<=0] not a range", [lo == BitVecVal(MIN, 64), up <= 0, lo < up, cj != RANGE], 'unsat')
run("Q8 [x>=-1, MAX] not a range", [up == BitVecVal(MAX, 64), lo >= -1, lo < up, cj != RANGE], 'unsat')
run("Q9 [MIN, x>=1] not dropped", [lo == BitVecVal(MIN, 64), up >= 1, cj != DROPPED], 'unsat')
run("Q10 [x<=-2, MAX] not dropped", [up == BitVecVal(MAX, 64), lo <= -2, cj != DROPPED], 'unsat')

# concrete replays of the documented half-open shapes (Java semantics, no solver)
for a, b in [(MIN, 10), (-2, MAX), (MIN, MAX), (MIN, 0), (-1, MAX), (MIN, -5), (900, MAX), (0, 10), (MAX, -2), (10, 5)]:
    print(f"replay [{a}, {b}]: java={['POINT','RANGE','DROPPED'][py_java(a, b)]} ref={['POINT','RANGE','DROPPED'][py_ref(a, b)]}")

print("RESULT", "OK" if fails == 0 else f"FAIL {fails}")
sys.exit(1 if fails else 0)
