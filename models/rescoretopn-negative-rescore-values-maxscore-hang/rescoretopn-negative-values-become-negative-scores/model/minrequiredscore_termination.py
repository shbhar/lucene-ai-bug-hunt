"""
SMT model of ScorerUtil.minRequiredScore (Lucene main 645d4fe14a20) in pure IEEE-754 FP (Z3 QF_FP).

Mapping, model element -> Java line (lucene/core/src/java/org/apache/lucene/search/):
  start = (double) mcs - mrs            ScorerUtil.java:133  double minRequiredScore = minCompetitiveScore - maxRemainingScore;
  u = (double) Math.ulp(mcs)            ScorerUtil.java:135  double subtraction = Math.ulp(minCompetitiveScore);
  running(x) = x > 0 && cond(x)         ScorerUtil.java:136-138 while (minRequiredScore > 0 && (float) sumUpperBound(...) >= mcs)
  cond(x) = (float) sub(x + mrs, n) >= mcs
  sub(s, n) = s if n <= 2 else (1 + 2 * (n-1) * 2^-52) * s
                                        util/MathUtil.java:162-169 (sumRelativeErrorBound), :177-194 (sumUpperBound)
  step(x) = x - u                       ScorerUtil.java:139  minRequiredScore -= subtraction;
  callers: MaxScoreBulkScorer.java:457-458 (maxRemainingScore = maxScoreSums[i], numScorers = allScorers.length),
           BlockMaxConjunctionBulkScorer.java:187-188 (sumOfOtherClauses[i], scorers.length)

Documented limits used as preconditions: mcs finite (LFV-104 owns +Infinity / NaN), numScorers in
[1, 1024] (IndexSearcher default max clause count; a 2-clause BooleanQuery is the repro), and for the
"legal" queries maxRemainingScore >= 0 (a sum of Scorer#getMaxScore values, which LUCENE-7996 requires
to be >= 0).

Queries (each prints sat / unsat and, for sat, a model that is re-executed in Java by
model/ReplayMinRequiredScore.java):
  Q1  legal inputs (mrs >= 0): can the loop make no progress (step(x) == x while running(x))?  expect unsat
  Q2  legal inputs: can the loop still be running after K iterations?  expect unsat for small K
  Q3  mcs == 0, mrs < 0: stall (running(x) and step(x) == x)?  expect sat (the rescoretopn-negative-rescore-values-maxscore-hang hang)
  Q4  mcs == 0: the largest |mrs| for which the loop can still terminate (binary search over binades)
  Q5  mcs in [1, 2), mrs < 0: stall threshold on |mrs| (binary search over binades)
  Q6  mcs in [1, 2), -2^28 <= mrs < 0: still running after K iterations?  (fast exit below the stall region)
"""

import sys
import z3

F32 = z3.Float32()
F64 = z3.Float64()
RNE = z3.RNE()


def d(v):
    return z3.FPVal(v, F64)


def f(v):
    return z3.FPVal(v, F32)


def ulp_float(x):
    """Math.ulp(float) for finite x with |x| < Float.MAX_VALUE: next float above |x| minus |x| (exact)."""
    a = z3.fpAbs(x)
    bits = z3.fpToIEEEBV(a)
    nxt = z3.fpBVToFP(bits + 1, F32)
    return z3.fpSub(RNE, nxt, a)


def sum_upper_bound(s, n):
    # MathUtil.sumUpperBound; n is a Python int (we enumerate n) to keep the query in pure FP
    if n <= 2:
        return s
    b = z3.fpMul(RNE, z3.FPVal(float(n - 1), F64), d(2.0 ** -52))
    factor = z3.fpAdd(RNE, d(1.0), z3.fpMul(RNE, d(2.0), b))
    return z3.fpMul(RNE, factor, s)


def make(n, mcs, mrs):
    mcs_d = z3.fpToFP(RNE, mcs, F64)  # exact widening
    start = z3.fpSub(RNE, mcs_d, mrs)
    u = z3.fpToFP(RNE, ulp_float(mcs), F64)

    def cond(x):
        return z3.fpGEQ(z3.fpToFP(RNE, sum_upper_bound(z3.fpAdd(RNE, x, mrs), n), F32), mcs)

    def running(x):
        return z3.And(z3.fpGT(x, d(0.0)), cond(x))

    def step(x):
        return z3.fpSub(RNE, x, u)

    return start, u, running, step


def finite_float_below_max(x):
    return z3.And(z3.Not(z3.fpIsNaN(x)), z3.Not(z3.fpIsInf(x)), z3.fpLT(z3.fpAbs(x), f(3.4028234663852886e38)))


def finite_double(x):
    return z3.And(z3.Not(z3.fpIsNaN(x)), z3.Not(z3.fpIsInf(x)))


def solve(constraints, timeout_ms=120000):
    s = z3.SolverFor("QF_FP")
    s.set("timeout", timeout_ms)
    s.add(*constraints)
    r = s.check()
    return r, (s.model() if r == z3.sat else None)


def fp_to_py(v):
    """Decode a Z3 FP numeral to a Python float (exact for F32/F64)."""
    import struct

    bv = z3.simplify(z3.fpToIEEEBV(v)).as_long()
    if v.sort() == F32:
        return struct.unpack(">f", bv.to_bytes(4, "big"))[0]
    return struct.unpack(">d", bv.to_bytes(8, "big"))[0]


NS = [1, 2, 3, 4, 16, 1024]
results = []


def record(name, n, r, detail=""):
    line = "%-4s n=%-5d %-7s %s" % (name, n, r, detail)
    print(line, flush=True)
    results.append(line)


def q1_legal_no_progress():
    for n in NS:
        mcs, mrs = z3.FP("mcs", F32), z3.FP("mrs", F64)
        start, u, running, step = make(n, mcs, mrs)
        r, m = solve(
            [finite_float_below_max(mcs), z3.fpGEQ(mcs, f(0.0)), finite_double(mrs), z3.fpGEQ(mrs, d(0.0)),
             running(start), z3.fpEQ(step(start), start)]
        )
        record("Q1", n, str(r), fp_model(m, mcs, mrs))


def fp_model(m, mcs, mrs):
    if m is None:
        return ""
    return "witness mcs=%r mrs=%r" % (fp_to_py(m.eval(mcs, model_completion=True)), fp_to_py(m.eval(mrs, model_completion=True)))


def q2_legal_iterations(K):
    for n in NS:
        mcs, mrs = z3.FP("mcs", F32), z3.FP("mrs", F64)
        start, u, running, step = make(n, mcs, mrs)
        xs = [start]
        for _ in range(K):
            xs.append(step(xs[-1]))
        r, m = solve(
            [finite_float_below_max(mcs), z3.fpGEQ(mcs, f(0.0)), finite_double(mrs), z3.fpGEQ(mrs, d(0.0))]
            + [running(x) for x in xs]
        )
        record("Q2", n, str(r), ("still running after %d iterations " % K) + fp_model(m, mcs, mrs))


def q3_zero_mcs_negative_stall():
    for n in NS:
        mcs, mrs = z3.FP("mcs", F32), z3.FP("mrs", F64)
        start, u, running, step = make(n, mcs, mrs)
        r, m = solve([z3.fpEQ(mcs, f(0.0)), finite_double(mrs), z3.fpLT(mrs, d(0.0)), running(start),
                      z3.fpEQ(step(start), start)])
        record("Q3", n, str(r), fp_model(m, mcs, mrs))
        # the exact repro input: mrs = -1.0, mcs = 0
        mcs2, mrs2 = z3.FP("mcs2", F32), z3.FP("mrs2", F64)
        start2, u2, running2, step2 = make(n, mcs2, mrs2)
        r2, _ = solve([z3.fpEQ(mcs2, f(0.0)), z3.fpEQ(mrs2, d(-1.0)), running2(start2), z3.fpEQ(step2(start2), start2)])
        record("Q3r", n, str(r2), "mcs=0 mrs=-1.0 (repro input) stalls")


def q4_zero_mcs_threshold():
    """For mcs = 0: does any mrs <= -2^e let the loop make progress at its first step? (sat = progress possible)"""
    n = 2
    for e in range(-110, -90, 2):
        mcs, mrs = z3.FP("mcs", F32), z3.FP("mrs", F64)
        start, u, running, step = make(n, mcs, mrs)
        r, m = solve([z3.fpEQ(mcs, f(0.0)), finite_double(mrs), z3.fpLEQ(mrs, d(-(2.0 ** e))), running(start),
                      z3.Not(z3.fpEQ(step(start), start))])
        record("Q4", n, str(r), "mcs=0, mrs <= -2^%d, first step makes progress? %s" % (e, fp_model(m, mcs, mrs)))


def q5_positive_mcs_threshold():
    """mcs in [1, 2): for which binade of |mrs| does a stall first become possible?"""
    for n in (2, 1024):
        for e in range(26, 33):
            mcs, mrs = z3.FP("mcs", F32), z3.FP("mrs", F64)
            start, u, running, step = make(n, mcs, mrs)
            r, m = solve([z3.fpGEQ(mcs, f(1.0)), z3.fpLT(mcs, f(2.0)), finite_double(mrs),
                          z3.fpLT(mrs, d(-(2.0 ** e))), z3.fpGEQ(mrs, d(-(2.0 ** (e + 1)))),
                          running(start), z3.fpEQ(step(start), start)])
            record("Q5", n, str(r), "mcs in [1,2), mrs in [-2^%d, -2^%d): stall? %s" % (e + 1, e, fp_model(m, mcs, mrs)))


def q6_positive_mcs_fast_exit(K):
    for n in (2, 1024):
        mcs, mrs = z3.FP("mcs", F32), z3.FP("mrs", F64)
        start, u, running, step = make(n, mcs, mrs)
        xs = [start]
        for _ in range(K):
            xs.append(step(xs[-1]))
        r, m = solve([z3.fpGEQ(mcs, f(1.0)), z3.fpLT(mcs, f(2.0)), finite_double(mrs), z3.fpLT(mrs, d(0.0)),
                      z3.fpGEQ(mrs, d(-(2.0 ** 28)))] + [running(x) for x in xs])
        record("Q6", n, str(r), "mcs in [1,2), -2^28 <= mrs < 0, still running after %d iterations? %s" % (K, fp_model(m, mcs, mrs)))


def _main():
    if sys.argv[1:2] == ["export"]:
        export_all(sys.argv[2])
        return
    which = sys.argv[1:] or ["q1", "q2", "q3", "q4", "q5", "q6"]
    if "q1" in which:
        q1_legal_no_progress()
    if "q2" in which:
        for K in (1, 2, 3):
            q2_legal_iterations(K)
    if "q3" in which:
        q3_zero_mcs_negative_stall()
    if "q4" in which:
        q4_zero_mcs_threshold()
    if "q5" in which:
        q5_positive_mcs_threshold()
    if "q6" in which:
        for K in (2, 3, 4):
            q6_positive_mcs_fast_exit(K)


# ---- SMT-LIB export for cvc5 (the study's lesson: cvc5 is 5-15x faster on QF_FP) ----
# fp.to_ieee_bv is a Z3 extension, so the export declares mcs by its IEEE bits (standard
# ((_ to_fp 8 24) bv) reinterpretation) with the sign bit 0 (mcs >= +0; -0.0f is not covered here,
# the z3 queries above cover it), and Math.ulp(mcs) = bits(mcs)+1 reinterpreted, minus mcs.
def make_bits(n, mcs_bits, mrs):
    mcs = z3.fpBVToFP(mcs_bits, F32)
    mcs_d = z3.fpToFP(RNE, mcs, F64)
    start = z3.fpSub(RNE, mcs_d, mrs)
    u = z3.fpToFP(RNE, z3.fpSub(RNE, z3.fpBVToFP(mcs_bits + 1, F32), mcs), F64)

    def cond(x):
        return z3.fpGEQ(z3.fpToFP(RNE, sum_upper_bound(z3.fpAdd(RNE, x, mrs), n), F32), mcs)

    def running(x):
        return z3.And(z3.fpGT(x, d(0.0)), cond(x))

    def step(x):
        return z3.fpSub(RNE, x, u)

    return mcs, start, u, running, step


def bits_var():
    b = z3.BitVec("mcsbits", 32)
    # sign bit 0 and below Float.MAX_VALUE's bits (0x7f7fffff): finite, non-negative, nextUp finite
    return b, z3.ULT(b, z3.BitVecVal(0x7F7FFFFF, 32))


def constraints_q2(n, K):
    b, bok = bits_var()
    mrs = z3.FP("mrs", F64)
    mcs, start, u, running, step = make_bits(n, b, mrs)
    xs = [start]
    for _ in range(K):
        xs.append(step(xs[-1]))
    return [bok, finite_double(mrs), z3.fpGEQ(mrs, d(0.0))] + [running(x) for x in xs]


def constraints_q6(n, K):
    b, bok = bits_var()
    mrs = z3.FP("mrs", F64)
    mcs, start, u, running, step = make_bits(n, b, mrs)
    xs = [start]
    for _ in range(K):
        xs.append(step(xs[-1]))
    return [bok, z3.fpGEQ(mcs, f(1.0)), z3.fpLT(mcs, f(2.0)), finite_double(mrs), z3.fpLT(mrs, d(0.0)),
            z3.fpGEQ(mrs, d(-(2.0 ** 28)))] + [running(x) for x in xs]


def constraints_q5(n, e):
    b, bok = bits_var()
    mrs = z3.FP("mrs", F64)
    mcs, start, u, running, step = make_bits(n, b, mrs)
    return [bok, z3.fpGEQ(mcs, f(1.0)), z3.fpLT(mcs, f(2.0)), finite_double(mrs),
            z3.fpLT(mrs, d(-(2.0 ** e))), z3.fpGEQ(mrs, d(-(2.0 ** (e + 1)))),
            running(start), z3.fpEQ(step(start), start)]


def dump(path, constraints):
    s = z3.Solver()
    s.add(*constraints)
    body = s.to_smt2()
    with open(path, "w") as out:
        out.write("(set-option :produce-models true)\n(set-logic QF_BVFP)\n")
        out.write(body.replace("(check-sat)", "(check-sat)\n(get-value (mcsbits mrs))"))


def export_all(outdir):
    import os
    os.makedirs(outdir, exist_ok=True)
    for n in (2, 3, 1024):
        for K in (1, 2, 3):
            dump("%s/q2_n%d_K%d.smt2" % (outdir, n, K), constraints_q2(n, K))
            dump("%s/q6_n%d_K%d.smt2" % (outdir, n, K), constraints_q6(n, K))
    for n in (2, 1024):
        for e in range(26, 33):
            dump("%s/q5_n%d_e%d.smt2" % (outdir, n, e), constraints_q5(n, e))


if __name__ == "__main__":
    _main()
