# SMT model of Lucene range-facet counting (lucene/facet/.../facet/range), main @ ea57360912ed.
#
# Model element -> Java line mapping (all paths relative to lucene/facet/src/java/org/apache/lucene/facet/range/):
#   O1  long_range_ctor()          LongRange.java:54-80        (exclusive-bound +/-1 adjust, failNoMatch, accept() :83-85)
#   O2  double_range_pipeline()    DoubleRange.java:51-82 (nextUp / nextAfter(-inf) adjust, NaN checks, accept :85-87)
#                                  DoubleRangeFacetCounts.java:255-270 (getLongRanges: doubleToSortableLong of min/max)
#                                  DoubleRangeFacetCounts.java:182,225,231 (value -> NumericUtils.doubleToSortableLong)
#                                  DoubleRangeFacetCounts.java:273-275 (field path: mapDocValue = sortableDoubleBits(raw))
#                                  core NumericUtils.doubleToSortableLong / sortableDoubleBits
#   O3  binary_search()            LongRangeCounter.java:77-114 (addSingleValued bsearch), :117-161 (addMultiValued bsearch)
#   O4  exclusive_intervals()      ExclusiveLongRangeCounter.java:275-305 (boundaries/rangeNums), :370-393 (buildElementaryIntervals)
#                                  LongRangeCounter.java:194-215 (hasOverlappingRanges)
#   O5  overlapping_intervals()    OverlappingLongRangeCounter.java:237-308 (buildElementaryIntervals)
#   O6  multivalued_order()        LongRangeCounter.java:117-161 (early-out "v <= boundaries[lastSeen]" assumes ascending values)
#                                  MultiLongValues.java:42-46 / MultiDoubleValues.java:42-46 (no ordering contract on nextValue)
#   O7  totcount_fastmatch()       LongRangeFacetCounts.java:135-167, DoubleRangeFacetCounts.java:162-194,
#                                  RangeFacetCounts.java:128-150,190-191 (single-valued: totCount += totalHits; minus visited misses)
#                                  RangeFacetCounts.java:153-188 (multi-valued: totCount++ per visited matching doc)
#
# Every query is either an expected-UNSAT safety obligation (a proof for all 64-bit inputs within the stated bounds) or
# an expected-SAT counterexample search; each SAT model is printed and is re-executed on the real jar by the JUnit
# harness (harness/RangeCounterExhaustiveTest.java, candidates/*/repro).
#
# Run: python3 -I model/range_counter_smt.py > model/range_counter_smt.log
import itertools
import sys
import time

from z3 import (And, BitVec, BitVecVal, Bool, BoolVal, Extract, If, Implies, Not, Or, Solver, sat, unsat,
                fpBVToFP, Float64, fpGEQ, fpLEQ, fpGT, fpLT, fpIsNaN, fpIsZero, fpIsInf, fpIsNegative, fpIsPositive,
                LShR, ULT, UGT, simplify, is_true, Int, IntVal, Sum)

W = 64
MIN = BitVecVal(-(1 << 63), W)
MAX = BitVecVal((1 << 63) - 1, W)
ONE = BitVecVal(1, W)

RESULTS = []
import os
MUT = os.environ.get('H52_MUT', '')   # mutation for vacuity checks: lr_noguard | bs_lt | ex_gap | ov_flag3 | ov_flag1


def check(name, s, expect, show=None, timeout_ms=600000):
    s.set("timeout", timeout_ms)
    t0 = time.time()
    r = s.check()
    dt = time.time() - t0
    ok = (r == expect)
    line = f"[{'OK ' if ok else 'BAD'}] {name}: {r} (expected {expect}) in {dt:.2f}s"
    print(line)
    if r == sat and show is not None:
        m = s.model()
        for label, e in show:
            v = m.eval(e, model_completion=True)
            if hasattr(v, 'as_signed_long'):
                print(f"      {label} = {v.as_signed_long()} (0x{v.as_long():016x})")
            else:
                print(f"      {label} = {v}")
    RESULTS.append((name, str(r), str(expect), ok, round(dt, 2)))
    sys.stdout.flush()
    return r


def sgn(bv):
    """signed value of a model BV (for printing)"""
    return bv


# ------------------------------------------------------------------------------------------------------------------
# O1 LongRange constructor (LongRange.java:54-80): for ALL 64-bit inputs, either the ctor throws and the
# requested set is empty, or it does not throw and accept(v) == requested predicate for every v.
def long_range_ctor():
    minIn, maxIn, v = BitVec('minIn', W), BitVec('maxIn', W), BitVec('v', W)
    minIncl, maxIncl = Bool('minIncl'), Bool('maxIncl')
    # Java code
    throw1 = And(Not(minIncl), minIn == MAX) if MUT != 'lr_noguard' else BoolVal(False)
    adjMin = If(minIncl, minIn, minIn + 1)
    throw2 = And(Not(maxIncl), maxIn == MIN)
    adjMax = If(maxIncl, maxIn, maxIn - 1)
    throws = Or(throw1, throw2, adjMin > adjMax)
    accept = And(v >= adjMin, v <= adjMax)
    # requested semantics (mathematical, no wrap: the BV comparisons below never add)
    pred = And(If(minIncl, v >= minIn, v > minIn), If(maxIncl, v <= maxIn, v < maxIn))

    s = Solver(); s.add(Not(throws), accept != pred)
    check("O1a LongRange: not-throw -> accept(v)==pred(v) for all v", s, unsat,
          [("minIn", minIn), ("minIncl", minIncl), ("maxIn", maxIn), ("maxIncl", maxIncl), ("v", v)])
    s = Solver(); s.add(throws, pred)
    check("O1b LongRange: throws -> requested set empty", s, unsat,
          [("minIn", minIn), ("minIncl", minIncl), ("maxIn", maxIn), ("maxIncl", maxIncl), ("v", v)])


# ------------------------------------------------------------------------------------------------------------------
# helpers for doubles, all on 64-bit IEEE bit patterns
SIGNMASK = BitVecVal(0x7fffffffffffffff, W)
CANON_NAN = BitVecVal(0x7ff8000000000000, W)
NEG_INF_BITS = BitVecVal(0xfff0000000000000, W)
POS_INF_BITS = BitVecVal(0x7ff0000000000000, W)
MIN_VALUE_BITS = BitVecVal(1, W)
NEG_MIN_VALUE_BITS = BitVecVal(0x8000000000000001, W)


def fp(bits):
    return fpBVToFP(bits, Float64())


def is_nan_bits(b):
    return fpIsNaN(fp(b))


def sortable_double_bits(b):
    # NumericUtils.sortableDoubleBits(bits) = bits ^ (bits >> 63) & 0x7fffffffffffffffL   (>> is arithmetic)
    return b ^ ((b >> 63) & SIGNMASK)


def double_to_long_bits(b):
    # Double.doubleToLongBits: canonicalises every NaN to 0x7ff8000000000000
    return If(is_nan_bits(b), CANON_NAN, b)


def double_to_sortable_long(b):
    return sortable_double_bits(double_to_long_bits(b))


def next_up_bits(b):
    # java.lang.Math.nextUp(double d) (JDK 25), non-NaN input:
    #   if (d < Double.POSITIVE_INFINITY) { final long transducer = Double.doubleToRawLongBits(d + 0.0D);
    #       return Double.longBitsToDouble(transducer + ((transducer >= 0L) ? 1L : -1L)); }
    #   else return d;   // +inf (and NaN)
    is_pos_inf = (b == POS_INF_BITS)
    t = If(fpIsZero(fp(b)), BitVecVal(0, W), b)   # d + 0.0 turns -0.0 into +0.0; exact for every other non-NaN d
    return If(is_pos_inf, b, t + If(t >= 0, ONE, BitVecVal(-1, W)))


def next_after_neg_inf_bits(b):
    # java.lang.Math.nextAfter(start, Double.NEGATIVE_INFINITY), non-NaN start:
    #   if (start > direction) { if (start != 0.0d) { bits = raw(start); return lbtd(bits + ((bits > 0) ? -1 : +1)); }
    #                            else return -Double.MIN_VALUE; }
    #   else if (start < direction) {...}   // impossible for direction=-inf
    #   else return direction;               // start == -inf  -> returns -inf
    is_neg_inf = (b == NEG_INF_BITS)
    is_zero = fpIsZero(fp(b))
    return If(is_neg_inf, NEG_INF_BITS,
              If(is_zero, NEG_MIN_VALUE_BITS, b + If(b > 0, BitVecVal(-1, W), ONE)))


def double_range_pipeline():
    """O2: DoubleRange(min,minIncl,max,maxIncl) -> counter LongRange in sortable space -> membership of a doc value,
    compared with (a) DoubleRange.accept (used by DoubleRange.getQuery drill-down) and (b) the requested IEEE
    predicate. Doc value reaches the counter as doubleToSortableLong(value) (DoubleValuesSource / MultiDoubleValuesSource
    paths) or sortableDoubleBits(rawBits) (String-field path, raw bits as written by DoubleDocValuesField)."""
    mn, mx, x = BitVec('minBits', W), BitVec('maxBits', W), BitVec('xBits', W)
    minIncl, maxIncl = Bool('minIncl'), Bool('maxIncl')
    pre = And(Not(is_nan_bits(mn)), Not(is_nan_bits(mx)))   # NaN bounds are rejected by the ctor (IAE)
    amin = If(minIncl, mn, next_up_bits(mn))
    amax = If(maxIncl, mx, next_after_neg_inf_bits(mx))
    ctor_throws = Not(fpLEQ(fp(amin), fp(amax)))              # "if (minIn > maxIn) failNoMatch()" (primitive >)
    lo = double_to_sortable_long(amin)
    hi = double_to_sortable_long(amax)
    counter_throws = lo > hi                                   # new LongRange(lo,true,hi,true) -> failNoMatch
    accept = And(fpGEQ(fp(x), fp(amin)), fpLEQ(fp(x), fp(amax)))   # DoubleRange.accept: value >= min && value <= max
    # requested IEEE predicate on the ORIGINAL bounds (primitive comparisons; false for NaN x)
    requested = And(If(minIncl, fpGEQ(fp(x), fp(mn)), fpGT(fp(x), fp(mn))),
                    If(maxIncl, fpLEQ(fp(x), fp(mx)), fpLT(fp(x), fp(mx))))
    sv = double_to_sortable_long(x)     # value-source paths
    sf = sortable_double_bits(x)        # String-field path (raw bits)
    in_counter_vs = And(sv >= lo, sv <= hi)
    in_counter_f = And(sf >= lo, sf <= hi)
    show = [("minBits", mn), ("minIncl", minIncl), ("maxBits", mx), ("maxIncl", maxIncl), ("xBits", x),
            ("fp(min)", fp(mn)), ("fp(max)", fp(mx)), ("fp(x)", fp(x))]

    s = Solver(); s.add(pre, Not(ctor_throws), accept != requested)
    # originally posed as an expected-UNSAT safety obligation; the solver returned SAT (finding: exclusive
    # bound at +/-Infinity), so it is now recorded as an expected-SAT counterexample search.
    check("O2a DoubleRange.accept == requested IEEE predicate (ctor did not throw)", s, sat, show)
    # O2a SAT (found by the solver): exclusive bound at an infinity is not excluded (nextUp(+inf) == +inf and
    # nextAfter(-inf, -inf) == -inf). Characterise it exactly and check nothing else breaks accept():
    inf_mismatch = Or(And(Not(minIncl), mn == POS_INF_BITS, x == POS_INF_BITS),
                      And(Not(maxIncl), mx == NEG_INF_BITS, x == NEG_INF_BITS))
    s = Solver(); s.add(pre, Not(ctor_throws), (accept != requested) != inf_mismatch)
    check("O2a2 accept != requested  <=>  (min=+inf exclusive & x=+inf) or (max=-inf exclusive & x=-inf)", s,
          unsat, show)
    s = Solver(); s.add(pre, ctor_throws, requested)
    check("O2b DoubleRange ctor throws -> requested set empty", s, unsat, show)
    s = Solver(); s.add(pre, Not(ctor_throws), Not(counter_throws), in_counter_vs != accept)
    r = check("O2c counter(sortable) membership == DoubleRange.accept  [value-source path]", s, sat, show)
    s = Solver(); s.add(pre, Not(ctor_throws), counter_throws)
    check("O2d ctor accepts but counter LongRange throws (failNoMatch)", s, sat, show)
    # refinement: exclude the signed-zero class (bounds or value are +/-0.0) -> expect no other discrepancy
    nz = And(Not(fpIsZero(fp(x))), Not(fpIsZero(fp(amin))), Not(fpIsZero(fp(amax))))
    s = Solver(); s.add(pre, Not(ctor_throws), Not(counter_throws), nz, in_counter_vs != accept)
    check("O2e same as O2c but no +/-0.0 among value/adjusted bounds -> no discrepancy", s, unsat, show)
    s = Solver(); s.add(pre, Not(ctor_throws), Not(counter_throws), nz, in_counter_f != accept)
    check("O2f String-field path (raw bits, any NaN payload) no +/-0.0 -> no discrepancy", s, unsat, show)
    s = Solver(); s.add(pre, Not(ctor_throws), counter_throws, Not(fpIsZero(fp(amin))))
    check("O2g counter-throws only when adjusted min is a zero (min=+0.0,max=-0.0)", s, unsat, show)
    # characterise O2c exactly: discrepancy <=> x is a zero and a bound is the opposite-signed zero, inclusive
    zero_mismatch = And(fpIsZero(fp(x)),
                        Or(And(fpIsZero(fp(amin)), fpIsPositive(fp(amin)), fpIsNegative(fp(x))),
                           And(fpIsZero(fp(amax)), fpIsNegative(fp(amax)), fpIsPositive(fp(x)))))
    s = Solver(); s.add(pre, Not(ctor_throws), Not(counter_throws), (in_counter_vs != accept) != zero_mismatch)
    check("O2h discrepancy <=> (x=-0.0 & adj.min=+0.0) or (x=+0.0 & adj.max=-0.0)", s, unsat, show)


# ------------------------------------------------------------------------------------------------------------------
# O3 binary searches of LongRangeCounter over a symbolic strictly increasing boundaries[] with boundaries[n-1]=MAX
def binary_search(nmax=16, nmin=1):
    """Java int arithmetic modelled exactly with 32-bit bit-vectors: mid = (lo + hi) >>> 1."""
    I = 32
    def iv(x):
        return BitVecVal(x, I)
    for n in range(nmin, nmax + 1):
        b = [BitVec(f'b{i}', W) for i in range(n)]
        v = BitVec('v', W)
        pre = [b[n - 1] == MAX] + [b[i] < b[i + 1] for i in range(n - 1)]

        def bsel(idx_expr):
            e = b[0]
            for i in range(1, n):
                e = If(idx_expr == iv(i), b[i], e)
            return e

        def inb(idx_expr):
            return And(idx_expr >= iv(0), idx_expr < iv(n))

        def run(lo0, first):
            lo, hi = lo0, iv(n - 1)
            done, res, oob = BoolVal(False), iv(-1), BoolVal(False)
            for _ in range(2 * (n.bit_length() + 1)):
                mid = LShR(lo + hi, 1)
                oob = Or(oob, And(Not(done), Not(inb(mid))))
                c1 = (v <= bsel(mid)) if MUT != 'bs_lt' else (v < bsel(mid))
                oob = Or(oob, And(Not(done), Not(c1), Not(inb(mid + 1))))
                c2 = v > bsel(mid + 1)
                hit_first = And(c1, mid == first)
                nres = If(c1, If(mid == first, mid, res), If(c2, res, mid + 1))
                ndone = Or(hit_first, And(Not(c1), Not(c2)))
                nlo = If(c1, lo, If(c2, mid + 1, lo))
                nhi = If(c1, If(mid == first, hi, mid - 1), hi)
                res = If(done, res, nres)
                lo = If(done, lo, nlo)
                hi = If(done, hi, nhi)
                done = Or(done, ndone)
            return done, res, oob

        done, res, oob = run(iv(0), iv(0))
        correct = And(inb(res), v <= bsel(res), Or(res == iv(0), bsel(res - 1) < v))
        s = Solver(); s.add(pre); s.add(Or(Not(done), oob, Not(correct)))
        r = check(f"O3a addSingleValued bsearch n={n}: terminates, in bounds, b[i-1]<v<=b[i]", s, unsat,
                  [("v", v)] + [(f"b{i}", b[i]) for i in range(n)], timeout_ms=900000)
        # ---- addMultiValued with lower bound: lastSeen in [-1, n-2] and v > b[lastSeen] (else early return)
        last = BitVec('last', I)
        first = last + 1
        done, res, oob = run(first, first)
        pre_m = And(last >= iv(-1), last <= iv(n - 2), Or(last == iv(-1), v > bsel(last)))
        correct = And(res >= first, inb(res), v <= bsel(res), Or(res == iv(0), bsel(res - 1) < v))
        s = Solver(); s.add(pre); s.add(pre_m, Or(Not(done), oob, Not(correct)))
        r = check(f"O3b addMultiValued bsearch n={n}: from lastSeen+1, same postcondition", s, unsat,
                  [("v", v), ("last", last)] + [(f"b{i}", b[i]) for i in range(n)], timeout_ms=900000)


# ------------------------------------------------------------------------------------------------------------------
# O4 ExclusiveLongRangeCounter for k <= 3 symbolic non-overlapping ranges
def exclusive_intervals(kmax=3):
    for k in range(0, kmax + 1):
        mins = [BitVec(f'm{i}', W) for i in range(k)]
        maxs = [BitVec(f'M{i}', W) for i in range(k)]
        valid = [mins[i] <= maxs[i] for i in range(k)]
        # hasOverlappingRanges (LongRangeCounter.java:194-215) is false and ranges sorted by min: input in sorted
        # order WLOG for the interval build (sort permutation handled in O4c below)
        disjoint_sorted = [mins[i + 1] > maxs[i] for i in range(k - 1)]
        # enumerate the k+1 gap decisions concretely; each pattern adds its own guard constraints
        for pattern in itertools.product([False, True], repeat=k + 1):
            iv = []           # list of (start, end, rangeIdx or -1)
            guards = []
            prev = MIN
            for i in range(k):
                gap = pattern[i]
                guards.append(((mins[i] > prev) if MUT != 'ex_gap' else (mins[i] >= prev)) == gap)
                if gap:
                    iv.append((prev, mins[i] - 1, -1))
                iv.append((mins[i], maxs[i], i))
                prev = maxs[i] + 1   # Java: may wrap when max == MAX (then this is the last range)
            if k > 0:
                last_end = iv[-1][1]
                guards.append((last_end < MAX) == pattern[k])
                if pattern[k]:
                    iv.append((last_end + 1, MAX, -1))
            else:
                if not pattern[0]:
                    continue
                iv.append((MIN, MAX, -1))
            # properties: contiguous partition of [MIN, MAX], start<=end, rangeNums assignment exact
            props = [iv[0][0] == MIN, iv[-1][1] == MAX]
            props += [iv[j][0] <= iv[j][1] for j in range(len(iv))]
            props += [And(iv[j][1] < MAX, iv[j + 1][0] == iv[j][1] + 1) for j in range(len(iv) - 1)]
            # rangeNums loop (ExclusiveLongRangeCounter.java:294-304)
            curr = IntVal(0)
            rn_ok = []
            for j, (st, en, ridx) in enumerate(iv):
                assigned = BoolVal(False)
                which = IntVal(-1)
                for i in range(k):
                    hit = And(curr == i, en == maxs[i])
                    assigned = Or(assigned, hit)
                    which = If(hit, IntVal(i), which)
                rn_ok.append(which == IntVal(ridx))
                curr = If(assigned, curr + 1, curr)
            props += rn_ok
            s = Solver()
            s.add(valid + disjoint_sorted + guards)
            s.add(Not(And(props)))
            r = check(f"O4a exclusive k={k} gaps={''.join('1' if p else '0' for p in pattern)}: partition+rangeNums",
                      s, unsat, [(f"m{i}", mins[i]) for i in range(k)] + [(f"M{i}", maxs[i]) for i in range(k)],
                      timeout_ms=120000)
    # O4b hasOverlappingRanges == false  <=>  pairwise disjoint, for k<=3 with Java's stable sort by min
    for k in range(2, kmax + 1):
        mins = [BitVec(f'm{i}', W) for i in range(k)]
        maxs = [BitVec(f'M{i}', W) for i in range(k)]
        valid = [mins[i] <= maxs[i] for i in range(k)]
        pairwise_disjoint = And([Or(maxs[i] < mins[j], maxs[j] < mins[i]) for i in range(k) for j in range(i + 1, k)])
        for perm in itertools.permutations(range(k)):
            # perm is the stable sort order iff mins non-decreasing along perm and ties keep index order
            sorted_guard = And([Or(mins[perm[t]] < mins[perm[t + 1]],
                                   And(mins[perm[t]] == mins[perm[t + 1]], perm[t] < perm[t + 1]))
                                for t in range(k - 1)])
            overlap = BoolVal(False)
            prev_max = maxs[perm[0]]
            stop = BoolVal(False)
            for t in range(1, k):
                o = mins[perm[t]] <= prev_max
                overlap = Or(overlap, And(Not(stop), o))
                stop = Or(stop, o)
                prev_max = maxs[perm[t]]
            s = Solver()
            s.add(valid + [sorted_guard])
            s.add(Not(overlap) != pairwise_disjoint)
            check(f"O4b hasOverlappingRanges k={k} perm={perm}: false <=> pairwise disjoint", s, unsat,
                  [(f"m{i}", mins[i]) for i in range(k)] + [(f"M{i}", maxs[i]) for i in range(k)], timeout_ms=120000)


# ------------------------------------------------------------------------------------------------------------------
# O5 OverlappingLongRangeCounter.buildElementaryIntervals over n <= 8 sorted distinct points with symbolic flags
def overlapping_intervals(nmax=8):
    for n in range(2, nmax + 1):
        p = [BitVec(f'p{i}', W) for i in range(n)]
        f = [Int(f'f{i}') for i in range(n)]
        pre = [p[0] == MIN, p[n - 1] == MAX] + [p[i] < p[i + 1] for i in range(n - 1)]
        pre += [Or(f[i] == 1, f[i] == 2, f[i] == 3) for i in range(n)]
        pre += [Or(f[0] == 1, f[0] == 3), Or(f[n - 1] == 2, f[n - 1] == 3)]   # MIN gets |1, MAX gets |2
        CAP = 2 * n
        # output as CAP slots with symbolic count c
        starts = [None] * CAP
        ends = [None] * CAP
        for j in range(CAP):
            starts[j] = BitVecVal(0, W)
            ends[j] = BitVecVal(0, W)
        c = IntVal(0)

        def append(cond, st, en, starts, ends, c):
            ns, ne = [], []
            for j in range(CAP):
                ns.append(If(And(cond, c == j), st, starts[j]))
                ne.append(If(And(cond, c == j), en, ends[j]))
            return ns, ne, If(cond, c + 1, c)

        v = p[0]
        starts, ends, c = append(f[0] == 3, v, v, starts, ends, c)
        prev = If(f[0] == 3, v + 1, v)
        for u in range(1, n):
            v = p[u]
            fl = f[u]
            # flags == 3
            starts, ends, c = append(And(fl == 3, v > prev), prev, v - 1, starts, ends, c)
            starts, ends, c = append(fl == 3, v, v, starts, ends, c)
            # flags == 1
            starts, ends, c = append(And(fl == 1, v > prev), prev, v - 1, starts, ends, c)
            # flags == 2
            starts, ends, c = append(fl == 2, prev, v, starts, ends, c)
            prev = If(fl == 3, (v + 1) if MUT != 'ov_flag3' else v, If(fl == 1, v, v + 1))
        props = [c >= 1, starts[0] == MIN]
        for j in range(CAP):
            props.append(Implies(c == j + 1, ends[j] == MAX))
            props.append(Implies(j < c, starts[j] <= ends[j]))
            if j + 1 < CAP:
                props.append(Implies(j + 1 < c, And(ends[j] < MAX, starts[j + 1] == ends[j] + 1)))
        # every start point (flag&1) starts an interval; every end point (flag&2) ends an interval
        for i in range(n):
            is_start = Or(f[i] == 1, f[i] == 3)
            is_end = Or(f[i] == 2, f[i] == 3)
            props.append(Implies(is_start, Or([And(j < c, starts[j] == p[i]) for j in range(CAP)])))
            props.append(Implies(is_end, Or([And(j < c, ends[j] == p[i]) for j in range(CAP)])))
        s = Solver()
        s.add(pre)
        s.add(Not(And(props)))
        check(f"O5 overlapping buildElementaryIntervals n={n} points: partition + every endpoint is a boundary", s,
              unsat, [(f"p{i}", p[i]) for i in range(n)] + [(f"f{i}", f[i]) for i in range(n)], timeout_ms=600000)


# ------------------------------------------------------------------------------------------------------------------
# O5i Inductive (unbounded) proof of the same loop: an invariant over the loop state that holds initially, is
#     preserved by one iteration for ANY next point/flags, and implies the postcondition after the last point (MAX).
#     Abstract state: prev; none (nothing emitted yet); lastEnd (end of last emitted interval); pend (prev is a
#     processed start point still waiting to be emitted as an interval start); pl (last processed point).
#     J: (none & prev == MIN) | (!none & prev == lastEnd + 1 & lastEnd < MAX)        -- emitted = partition of [MIN, prev-1]
#        & (prev == pl | prev == pl + 1) & (pend -> prev == pl)
def overlapping_inductive():
    prev, lastEnd, pl, v = BitVec('prev', W), BitVec('lastEnd', W), BitVec('pl', W), BitVec('v', W)
    none, pend = Bool('none'), Bool('pend')
    f = Int('f')

    def J(prev, none, lastEnd, pl, pend):
        return And(Or(And(none, prev == MIN), And(Not(none), prev == lastEnd + 1, lastEnd < MAX)),
                   Or(prev == pl, prev == pl + 1), Implies(pend, prev == pl))

    # one iteration (OverlappingLongRangeCounter.java:279-305) for point v > pl with flags f
    def step(prev, none, lastEnd, pend, v, f):
        obligations = []
        emitted = []   # list of (cond, start, end)
        g3 = And(f == 3, v > prev); emitted.append((g3, prev, v - 1)); emitted.append((f == 3, v, v))
        g1 = And(f == 1, v > prev); emitted.append((g1, prev, (v - 1) if MUT != 'ov_flag1' else v))
        emitted.append((f == 2, prev, v))
        # emissions are in order; each must start at the current coverage frontier and be non-empty
        cur_prev, cur_none, cur_last = prev, none, lastEnd
        pend_emitted = BoolVal(False)
        for cond, st, en in emitted:
            obligations.append(Implies(cond, And(st == cur_prev, st <= en)))
            pend_emitted = Or(pend_emitted, And(cond, st == prev))
            cur_last = If(cond, en, cur_last)
            cur_none = If(cond, BoolVal(False), cur_none)
            cur_prev = If(cond, en + 1, cur_prev)
        nprev = If(f == 3, (v + 1) if MUT != 'ov_flag3' else v, If(f == 1, v, v + 1))
        # coverage frontier after emissions equals Java's new prev except for the pending flag-1 start
        obligations.append(If(f == 1, cur_prev == v, cur_prev == nprev))
        # start/end obligations for v
        is_start = Or(f == 1, f == 3)
        is_end = Or(f == 2, f == 3)
        v_started = Or(And(f == 3, BoolVal(True)), f == 1)        # f=3 emits [v,v]; f=1 leaves v pending as prev
        v_ended = Or(f == 3, f == 2)                              # f=3 emits [v,v]; f=2 emits [prev, v]
        obligations.append(Implies(is_start, v_started))
        obligations.append(Implies(is_end, v_ended))
        # the old pending start is emitted as an interval start in this iteration
        obligations.append(Implies(pend, pend_emitted))
        npend = (f == 1)
        return obligations, nprev, cur_none, cur_last, npend

    pre = And(J(prev, none, lastEnd, pl, pend), v > pl, Or(f == 1, f == 2, f == 3))
    obl, nprev, nnone, nlast, npend = step(prev, none, lastEnd, pend, v, f)
    # (a) step obligations + invariant preserved for a non-final point (v < MAX)
    s = Solver(); s.add(pre, v < MAX)
    s.add(Not(And(And(obl), J(nprev, nnone, nlast, v, npend))))
    check("O5i-a inductive step (v < MAX): emissions contiguous/non-empty, endpoints are boundaries, J preserved",
          s, unsat, [("prev", prev), ("lastEnd", lastEnd), ("pl", pl), ("v", v), ("f", f), ("none", none),
                     ("pend", pend)])
    # (b) final point v == MAX (flags 2 or 3): obligations hold and the emitted list ends exactly at MAX
    s = Solver(); s.add(pre, v == MAX, Or(f == 2, f == 3))
    s.add(Not(And(And(obl), Not(nnone), nlast == MAX)))
    check("O5i-b final point MAX: emitted intervals partition [MIN, MAX]", s, unsat,
          [("prev", prev), ("lastEnd", lastEnd), ("pl", pl), ("f", f)])
    # (c) initial state after the first point MIN (OverlappingLongRangeCounter.java:269-277) satisfies J
    f0 = Int('f0')
    init_prev = If(f0 == 3, MIN + 1, MIN)
    init_none = Not(f0 == 3)
    init_last = If(f0 == 3, MIN, BitVecVal(0, W))
    init_pend = (f0 == 1)
    s = Solver(); s.add(Or(f0 == 1, f0 == 3))
    s.add(Not(J(init_prev, init_none, init_last, MIN, init_pend)))
    check("O5i-c initial state (first point MIN, flags 1|3) satisfies J", s, unsat)
    # (d) MAX can only be the last point and points are distinct, so v < MAX steps + one final step cover every
    #     execution; a flag-1 MAX is impossible (endsMap.put(MAX, 2) then |= only).


# ------------------------------------------------------------------------------------------------------------------
# O6 multi-valued counting: elementary interval hit set equals the set of intervals containing some value
#    (a) under the ascending-values precondition  -> UNSAT (sound),  (b) without it -> SAT (values unsorted)
def multivalued_order():
    # 3 elementary intervals: (-inf, b0], (b0, b1], (b1, MAX]; doc with 2 values u1, u2 fed in that order
    b0, b1, u1, u2 = BitVec('b0', W), BitVec('b1', W), BitVec('u1', W), BitVec('u2', W)
    pre = And(b0 < b1, b1 < MAX)
    def idx(u):
        return If(u <= b0, IntVal(0), If(u <= b1, IntVal(1), IntVal(2)))
    bnd = lambda i: If(i == 0, b0, If(i == 1, b1, MAX))
    # Java: first value always processed (lastSeen=-1); second skipped iff u2 <= boundaries[lastSeen]
    #       (or lastSeen+1 == length, which implies the same since boundaries[last]=MAX)
    i1 = idx(u1)
    second_processed = Not(u2 <= bnd(i1))
    hit = lambda i: Or(i1 == i, And(second_processed, idx(u2) == i))
    truth = lambda i: Or(idx(u1) == i, idx(u2) == i)
    wrong = Or([hit(i) != truth(i) for i in range(3)])
    s = Solver(); s.add(pre, u1 <= u2, wrong)
    check("O6a addMultiValued with ascending values: hit set == true interval set", s, unsat)
    s = Solver(); s.add(pre, u1 != u2, wrong)
    check("O6b addMultiValued with values in arbitrary order (no ordering contract): mismatch exists", s, sat,
          [("b0", b0), ("b1", b1), ("u1", u1), ("u2", u2)])
    # O6c: raw-bit double encoding (SortedNumericDocValuesField(Double.doubleToRawLongBits(x))): SortedNumeric
    # returns values ascending as signed longs of RAW bits; the counter then sees doubleToSortableLong(x) (from
    # MultiDoubleValuesSource.fromDoubleField) or sortableDoubleBits(raw) (String path). Is that sequence ascending?
    r1, r2 = BitVec('raw1', W), BitVec('raw2', W)
    s = Solver()
    s.add(r1 < r2, Not(is_nan_bits(r1)), Not(is_nan_bits(r2)),
          Not(sortable_double_bits(r1) < sortable_double_bits(r2)))
    check("O6c raw-bit SortedNumeric order is not ascending in sortable space (non-NaN doubles)", s, sat,
          [("raw1", r1), ("raw2", r2), ("x1", fp(r1)), ("x2", fp(r2))])
    s = Solver()
    s.add(r1 < r2, Not(is_nan_bits(r1)), Not(is_nan_bits(r2)), Not(fpIsNegative(fp(r1))),
          Not(fpIsNegative(fp(r2))), Not(sortable_double_bits(r1) < sortable_double_bits(r2)))
    check("O6d ... but it IS ascending when both values are non-negative (why upstream tests pass)", s, unsat)


# ------------------------------------------------------------------------------------------------------------------
# O7 totCount accounting (LIA): per segment H hits, of which F pass the fastMatch filter (F <= H; F = H without
# fastMatch); among the F visited: N without a value, X with a value in no range, R matching >= 1 range.
def totcount_fastmatch():
    H, F, N, X, R = Int('H'), Int('F'), Int('N'), Int('X'), Int('R')
    pre = And(H >= 0, F >= 0, F <= H, N >= 0, X >= 0, R >= 0, N + X + R == F)
    single = H - N - X           # single-valued path: totCount += totalHits; totCount -= missing + finish()
    multi = R                    # multi-valued path: totCount++ per visited doc that matched a range
    documented = R               # FacetResult.value for range facets == #docs matching >= 1 range (upstream tests)
    s = Solver(); s.add(pre, F == H, single != documented)
    check("O7a no fastMatch: single-valued totCount == #docs matching a range", s, unsat)
    s = Solver(); s.add(pre, multi != documented)
    check("O7b multi-valued path totCount == #docs matching a range (any fastMatch)", s, unsat)
    s = Solver(); s.add(pre, F < H, single != documented)
    check("O7c fastMatch that excludes hits: single-valued totCount != #docs matching a range", s, sat,
          [("H", H), ("F", F), ("N", N), ("X", X), ("R", R)])
    s = Solver(); s.add(pre, F < H, single - documented != H - F)
    check("O7d overcount is exactly the number of fastMatch-excluded hits", s, unsat)


if __name__ == '__main__':
    which = sys.argv[1:] or ['O1', 'O2', 'O3', 'O4', 'O5', 'O6', 'O7']
    t0 = time.time()
    if 'O1' in which: long_range_ctor()
    if 'O2' in which: double_range_pipeline()
    if 'O3' in which: binary_search()
    if 'O3x' in which: binary_search(nmax=int(__import__('os').environ.get('H52_NMAX', '16')), nmin=int(__import__('os').environ.get('H52_NMIN', '1')))
    if 'O4' in which: exclusive_intervals()
    if 'O5' in which: overlapping_intervals()
    if 'O5i' in which: overlapping_inductive()
    if 'O6' in which: multivalued_order()
    if 'O7' in which: totcount_fastmatch()
    bad = [r for r in RESULTS if not r[3]]
    print(f"SUMMARY: {len(RESULTS)} obligations, {len(RESULTS) - len(bad)} as expected, {len(bad)} unexpected,"
          f" {time.time() - t0:.1f}s")
    for r in bad:
        print("UNEXPECTED:", r)
