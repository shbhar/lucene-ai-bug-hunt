"""packed-ints-monotonic: MonotonicBlockPackedWriter.flush (and, for contrast, PackedLongValues.monotonicBuilder) as BV64 SMT.

Model element -> Java line (lucene/core/src/java/org/apache/lucene/util/packed/, main ea57360912ed):
  g_i = (long) (avg * (long) i)          MonotonicBlockPackedReader.java:37-39 expected(origin, avg, i) = origin + g_i
     avg = (float) (v_last - v_0) / (off - 1) >= 0 for a non-decreasing block (MonotonicBlockPackedWriter.java:74),
     so g is non-decreasing in i, g_0 = 0 and 0 <= g_i <= Long.MAX_VALUE (the (long) cast saturates). The model lets
     g be ANY such sequence (over-approximation of the float pipeline; the concrete float witness is replayed on the
     jar by the JUnit repro).
  min adjust loop  MonotonicBlockPackedWriter.java:75-83   expected = min + g_i (wraps); if (expected > actual) min -= ...
  deltas           MonotonicBlockPackedWriter.java:85-89   d_i = v_i - (min + g_i) (wraps); maxDelta = signed max(0, d_i)
  bpv              MonotonicBlockPackedWriter.java:96      bitsRequired(maxDelta) (0 when maxDelta == 0)
  encoding         AbstractBlockPackedWriter.writeValues + BulkOperationPacked.encode: keeps d_i's low bpv bits (with
                   -da; with -ea BulkOperationPacked.java:222 asserts unsignedBitsRequired(v) <= bpv). Modelled as
                   stored_i = d_i & (2^bpv - 1) (for d_i that fit, exact; for a d_i that does not fit, the real encoder
                   may also corrupt the neighbour, which only makes the reader's result more wrong).
  read             MonotonicBlockPackedReader.java:119-123 get = expected(min, avg, i) + stored_i
Domain (class javadoc, MonotonicBlockPackedWriter.java:26 "monotonically increasing sequences of positive longs";
add() asserts l >= 0): 0 <= v_0 <= v_1 <= ... <= Long.MAX_VALUE.

Queries:
  Q1 (bug)    exists a block (k values) with get(i) != v_i                  -> expect SAT (counterexample)
  Q2 (fix)    same with the min loop rewritten as min = min(min, v_i - g_i) -> expect UNSAT for k = 2..KMAX
              (v_i - g_i never overflows: both are in [0, Long.MAX_VALUE]); also proves the fixed deltas fit in 63
              bits, so the reader's bpv == 64 path (maskRight = (1L << 64) - 1 == 0, Reader:87) stays unused.
  Q3 (trigger) the bug needs a value close to Long.MAX_VALUE: exists a failing block with v_last <= LIMIT? for
              LIMIT = Long.MAX_VALUE - 2^40 with the REAL float pipeline is checked on the jar by
              harness PackedDifferential section D; here we show the abstract condition: failure implies
              min + g_i > Long.MAX_VALUE (exact) for some i with the pre-adjust min > 0.
  Q5 (reach)  with the float error bound g_i <= (v_last - v_0) + 2^41 (three IEEE binary32 round-to-nearest steps,
              each with relative error <= 2^-24, on a line value <= 2^63, then truncation toward zero:
              g_i <= (v_last - v_0) * (1 + 2^-24)^3 < (v_last - v_0) + 2^41), every failure has
              v_last > Long.MAX_VALUE - 2^41. The jar scan harness/src/MonoBlockTriggerScan.java agrees (largest failing
              distance 2^39.4 in 30,000 random blocks; none at distance >= 2^41).
  Q4 (contrast) PackedLongValues.monotonicBuilder (MonotonicLongValues.java:79-87 + DeltaPackedLongValues.java:68-78
              + PackedLongValues.java:292-313): wrap-consistent; UNSAT for ANY 64-bit values and ANY g.
"""
import sys
import time
from z3 import BitVec, BitVecVal, If, And, Or, Not, Solver, LShR, ZeroExt, sat, unsat

W = 64
KMAX = int(sys.argv[1]) if len(sys.argv) > 1 else 4
KMIN = int(sys.argv[2]) if len(sys.argv) > 2 else 2
ONLY = sys.argv[3].split(",") if len(sys.argv) > 3 else ["Q1", "Q2", "Q3", "Q4", "Q5"]
MAXL = (1 << 63) - 1


def bv(x):
    return BitVecVal(x, W)


def bits_required(x):  # PackedInts.bitsRequired for x >= 0 ; 64 for x < 0
    r = bv(1)
    for b in range(1, 64):
        r = If(LShR(x, b) != 0, bv(b + 1), r)
    return If(x < 0, bv(64), r)


def mask(bpv):
    return If(bpv == 64, bv(-1), (bv(1) << (bpv & 63)) - 1)


def block(k, fixed):
    v = [BitVec(f"v{i}", W) for i in range(k)]
    g = [bv(0)] + [BitVec(f"g{i}", W) for i in range(1, k)]
    pre = [v[0] >= 0] + [v[i] >= v[i - 1] for i in range(1, k)]
    pre += [g[i] >= g[i - 1] for i in range(1, k)]  # g non-decreasing, g_0 = 0, all in [0, MAX] (signed >= 0)
    mn = v[0]
    trace = []
    for i in range(1, k):
        if fixed:
            mn = If(v[i] - g[i] < mn, v[i] - g[i], mn)
        else:
            expected = mn + g[i]
            trace.append(And(mn > 0, ZeroExt(1, mn) + ZeroExt(1, g[i]) > MAXL))  # exact sum overflows
            mn = If(expected > v[i], mn - (expected - v[i]), mn)
    d = [v[i] - (mn + g[i]) for i in range(k)]
    md = bv(0)
    for i in range(k):
        md = If(d[i] > md, d[i], md)
    bpv = If(md == 0, bv(0), bits_required(md))
    stored = [If(bpv == 0, bv(0), d[i] & mask(bpv)) for i in range(k)]
    get = [mn + g[i] + stored[i] for i in range(k)]
    return v, g, pre, get, d, bpv, trace


def plv_monotonic(k):
    v = [BitVec(f"v{i}", W) for i in range(k)]
    g = [bv(0)] + [BitVec(f"g{i}", W) for i in range(1, k)]  # ANY 64-bit values, no precondition at all
    r = [v[i] - g[i] for i in range(k)]  # values[i] -= expected(0, average, i)
    mn = r[0]
    for i in range(1, k):
        mn = If(r[i] < mn, r[i], mn)  # DeltaPacked: min, values[i] -= min
    s = [r[i] - mn for i in range(k)]
    lo, hi = s[0], s[0]
    for i in range(1, k):
        lo = If(s[i] < lo, s[i], lo)
        hi = If(s[i] > hi, s[i], hi)
    bpv = If(lo < 0, bv(64), bits_required(hi))  # PackedLongValues.Builder.pack
    stored = [If(And(lo == 0, hi == 0), bv(0), s[i] & mask(bpv)) for i in range(k)]
    get = [mn + g[i] + stored[i] for i in range(k)]  # MonotonicLongValues.get: expected(mins, avg, i) + values.get
    return v, get


def solve(name, k, s, expect):
    t = time.time()
    r = s.check()
    print(f"{name} k={k}: {r} ({time.time()-t:.2f}s)  [expect {expect}]", flush=True)
    return r


def main():
    ok = True
    found = None
    for k in range(KMIN, KMAX + 1):
        v, g, pre, get, d, bpv, trace = block(k, fixed=False)
        fail = Or(*[get[i] != v[i] for i in range(k)])
        if "Q1" in ONLY:
            s = Solver(); s.add(*pre); s.add(fail)
            r = solve("Q1 bug (abstract g)", k, s, "sat")
            if r == sat:
                m = s.model()
                print("   v=", [m.eval(x, model_completion=True).as_signed_long() for x in v])
                print("   g=", [m.eval(x, model_completion=True).as_signed_long() for x in g])
                print("   get=", [m.eval(x, model_completion=True).as_signed_long() for x in get], "bpv=", m.eval(bpv, model_completion=True))
                found = found or k
            ok &= r == sat
        if "Q3" in ONLY:
            s = Solver(); s.add(*pre); s.add(fail); s.add(Not(Or(*trace)))
            ok &= solve("Q3 failure without min+g_i overflow", k, s, "unsat") == unsat
        if "Q5" in ONLY:
            s = Solver(); s.add(*pre); s.add(fail)
            for i in range(1, k):
                s.add(ZeroExt(1, g[i]) <= ZeroExt(1, v[k - 1] - v[0]) + (1 << 41))
            s.add(v[k - 1] <= MAXL - (1 << 41))
            ok &= solve("Q5 failure with v_last <= MAX - 2^41 (float-bounded g)", k, s, "unsat") == unsat
        if "Q2" in ONLY:
            v2, g2, pre2, get2, d2, bpv2, _ = block(k, fixed=True)
            s = Solver(); s.add(*pre2)
            s.add(Or(Or(*[get2[i] != v2[i] for i in range(k)]), Or(*[d2[i] < 0 for i in range(k)])))
            ok &= solve("Q2 fixed writer: round trip exact, deltas in [0,2^63)", k, s, "unsat") == unsat
        if "Q4" in ONLY:
            v4, get4 = plv_monotonic(k)
            s = Solver(); s.add(Or(*[get4[i] != v4[i] for i in range(k)]))
            ok &= solve("Q4 PackedLongValues.monotonicBuilder round trip", k, s, "unsat") == unsat
    print("ALL OBLIGATIONS AS EXPECTED" if ok else "UNEXPECTED RESULT")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
