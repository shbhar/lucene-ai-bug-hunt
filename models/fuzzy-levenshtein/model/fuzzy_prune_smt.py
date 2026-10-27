"""
fuzzy-levenshtein SMT model (Z3, IEEE binary32 + linear integer arithmetic) of FuzzyTermsEnum's maxEdits pruning.

Question: can FuzzyTermsEnum.bottomChanged lower maxEdits from m to m-1 while a term at edit distance m
could still be (or already is) in TopTermsRewrite's priority queue? Such a term would be lost: never
collected, or (if collected in an earlier segment) its postings in later segments never registered.

Model element -> Java (lucene main ea57360912ed):
  maxBoost(m, L) = 1.0f - ((float) m / (float) L)          FuzzyTermsEnum.java:205
  reduce  = !(bottom < maxBoost || (bottom == maxBoost && !termAfter))   FuzzyTermsEnum.java:204-210
  termAfter = bottomTerm == null || (lastTerm != null && lastTerm >= bottomTerm)   FuzzyTermsEnum.java:199-200
  boost(t) = ed == 0 ? 1.0f : 1.0f - (float) ed / (float) min(cp(t), L)   FuzzyTermsEnum.java:250-257
  bottom/bottomTerm are the values read in next() for the PREVIOUS collect and applied at the following
  next() (one-step lag)                                       FuzzyTermsEnum.java:225-228, 260-271
  PQ: when full, reject boost < bottom, or boost == bottom and term > bottomTerm; else insert + poll worst
                                                              TopTermsRewrite.java:117-121, 138-153
  PQ order: lower boost is worse; equal boost: larger term is worse      TopTermsRewrite.java:200-203
  new segment: FuzzyTermsEnum ctor calls bottomChanged(null) with the attribute's current values
                                                              FuzzyTermsEnum.java:157-159
  attribute default before the queue is full: bottom = -Infinity, bottomTerm = null
                                                              MaxNonCompetitiveBoostAttributeImpl.java:30-31
Documented limits used as preconditions: query/term length <= 32766 (IndexWriter.MAX_TERM_LENGTH bytes, so
<= 32766 code points); maxEdits <= LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE = 2.

Abstractions (sound over-approximations for an UNSAT answer): bottom boosts are ARBITRARY non-NaN floats
(not only reachable boosts); term order is an integer rank; the bottom after the lag is any PQ-order
successor of the stale one.
"""
import sys
import time
from z3 import (FPSort, RNE, fpToFP, fpSignedToFP, fpDiv, fpSub, FPVal, fpLT, fpGT, fpEQ, fpIsNaN, fpIsInf, fpIsNegative,
                fpLEQ, BitVec, BitVecVal, Int, Bool, And, Or, Not, If, Implies, Solver, sat, unsat, Int2BV,
                fpMinusInfinity, BV2Int, ULE, UGE, SignExt, Extract)

F32 = FPSort(8, 24)
ONE = FPVal(1.0, F32)
NEG_INF = fpMinusInfinity(F32)
MAX_LEN = 32766


def i2f(bv32):
    """Java (float) int: exact for |x| < 2^24 (all lengths here)."""
    return fpSignedToFP(RNE(), bv32, F32)


def frac_boost(m_bv, n_bv):
    """1.0f - (float) m / (float) n, Java float semantics (n >= 1)."""
    return fpSub(RNE(), ONE, fpDiv(RNE(), i2f(m_bv), i2f(n_bv)))


def java_boost(m_bv, n_bv):
    """1.0f - (float) m / (float) n incl. n == 0 (m >= 1): m/0 = +Inf, 1 - Inf = -Inf."""
    return If(n_bv == BitVecVal(0, 32), NEG_INF, frac_boost(m_bv, n_bv))


def bv(name):
    return BitVec(name, 32)


def in_range(x, lo, hi):
    return And(x >= BitVecVal(lo, 32), x <= BitVecVal(hi, 32))  # signed comparisons


def run(name, s, expect, timeout_ms=600000):
    s.set("timeout", timeout_ms)
    t0 = time.time()
    r = s.check()
    dt = time.time() - t0
    model = ""
    if r == sat:
        mdl = s.model()
        model = " model: " + ", ".join(f"{d.name()}={mdl[d]}" for d in sorted(mdl.decls(), key=lambda d: d.name()))
    ok = (str(r) == expect)
    print(f"[{'OK ' if ok else 'UNEXPECTED'}] {name}: {r} (expected {expect}) {dt:.1f}s{model}", flush=True)
    return r


def q1_monotone_consecutive():
    """Q1a: n -> fl(1 - fl(m/n)) is non-decreasing on n in [1, 32765] for m in {1,2}."""
    s = Solver()
    m, n = bv("m"), bv("n")
    s.add(Or(m == 1, m == 2), in_range(n, 1, MAX_LEN - 1))
    s.add(fpGT(frac_boost(m, n), frac_boost(m, n + 1)))
    return run("Q1a monotone in n (consecutive)", s, "unsat")


def q1_bound():
    """Q1b: for every candidate with ed = m, boost(m, min(n, L)) <= maxBoost(m, L) (direct form)."""
    s = Solver()
    m, n, L = bv("m"), bv("n"), bv("L")
    s.add(Or(m == 1, m == 2), in_range(L, 1, MAX_LEN), in_range(n, 0, MAX_LEN))
    mn = If(n < L, n, L)
    s.add(fpGT(java_boost(m, mn), frac_boost(m, L)))
    return run("Q1b candidate boost <= maxBoost(m, L)", s, "unsat")


def q1a_split(m_val, lo, hi):
    """Q1a restricted to one binade of n (lemma splitting, as the study recommends for FP UNSAT)."""
    s = Solver()
    m, n = BitVecVal(m_val, 32), bv("n")
    s.add(in_range(n, lo, hi))
    s.add(fpGT(frac_boost(m, n), frac_boost(m, n + 1)))
    return run(f"Q1a m={m_val} n in [{lo},{hi}]", s, "unsat", timeout_ms=300000)


def q2_pruning(min_len, variant):
    """Q2: reduce(m) while a term X with edit distance m still matters to the PQ.

    Lemma L1 (Q1a/Q1b, plus the exhaustive JVM check BoostBound.java) is used as an assumption:
    bX <= M for every X with edit distance m, where M = maxBoost(m, L). With L >= 1 and m <= 2,
    M = fl(1 - fl(m/L)) >= -1 > -Infinity (lemma L0, from m/L <= 2). For L = 0 (empty query term)
    the code computes M = 1 - m/0 = -Infinity and every non-exact boost is -Infinity.
    All remaining constraints are float comparisons and integer ranks (no division).

    variant: 'code' (as in Java); 'no-tie-guard' (mutant: reduce on bottom == maxBoost even when
    termAfter is false); 'no-lag' (bottom read after the latest collect: must also be UNSAT);
    'strict-new-only' (X must be a new competitive term, not one already in the PQ)."""
    from z3 import FP, fpGEQ
    s = Solver()
    b_s, b_c, bX, M = FP("b_stale", F32), FP("b_current", F32), FP("b_X", F32), FP("maxBoost", F32)
    r_s, r_c, r_l, r_X = Int("r_s"), Int("r_c"), Int("r_l"), Int("r_X")
    hasBottomTerm = Bool("staleQueueFull")       # bottomTerm != null at the stale read
    curFull = Bool("currentQueueFull")
    lastPresent = Bool("lastTermPresent")         # False: first bottomChanged(null) of a segment's enum
    for x in (b_s, b_c, bX, M):
        s.add(Not(fpIsNaN(x)))
    s.add(fpLEQ(bX, M))                           # lemma L1
    if min_len >= 1:
        s.add(fpGEQ(M, FPVal(-1.0, F32)))         # lemma L0
    else:
        s.add(fpEQ(M, NEG_INF), fpEQ(bX, NEG_INF))  # L = 0: 1 - m/0 = -Inf, min(n, 0) = 0
    # queue not full -> attribute default (-inf, null)
    s.add(Implies(Not(hasBottomTerm), fpEQ(b_s, NEG_INF)))
    s.add(Implies(hasBottomTerm, curFull))        # once full, the queue stays full
    # the PQ bottom only moves up in PQ order (insert-then-poll-worst)
    s.add(Implies(hasBottomTerm, Or(fpGT(b_c, b_s), And(fpEQ(b_c, b_s), r_c <= r_s))))
    if variant == "no-lag":
        s.add(Implies(hasBottomTerm, And(fpEQ(b_c, b_s), r_c == r_s)))
    termAfter = Or(Not(hasBottomTerm), And(lastPresent, r_l >= r_s))
    if variant == "no-tie-guard":
        reduce = Not(fpLT(b_s, M))
    else:
        reduce = Not(Or(fpLT(b_s, M), And(fpEQ(b_s, M), Not(termAfter))))
    s.add(reduce)
    # X: edit distance exactly m, not yet passed in this segment (rank > lastTerm), any boost <= M
    s.add(Implies(lastPresent, r_X > r_l))
    if variant == "strict-new-only":
        matters = Or(Not(curFull), fpGT(bX, b_c), And(fpEQ(bX, b_c), r_X < r_c))
    else:
        matters = Or(Not(curFull), fpGT(bX, b_c), And(fpEQ(bX, b_c), r_X <= r_c))
    s.add(matters)
    return run(f"Q2 pruning variant={variant} minQueryLen={min_len}", s,
               "unsat" if (variant != "no-tie-guard" and min_len >= 1) else "sat")


if __name__ == "__main__":
    which = sys.argv[1:] or ["q2", "q2-empty", "q2-mutant", "q2-nolag", "q2-strict", "q1a-split"]
    res = {}
    if "q1a" in which: res["q1a"] = q1_monotone_consecutive()
    if "q1b" in which: res["q1b"] = q1_bound()
    if "q2-strict" in which: res["q2-strict"] = q2_pruning(1, "strict-new-only")
    if "q1a-split" in which:
        for mv in (1, 2):
            lo = 1
            while lo <= MAX_LEN - 1:
                hi = min(2 * lo - 1, MAX_LEN - 1)
                res[f"q1a m={mv} [{lo},{hi}]"] = q1a_split(mv, lo, hi)
                lo = hi + 1
    if "q2" in which: res["q2"] = q2_pruning(1, "code")
    if "q2-nolag" in which: res["q2-nolag"] = q2_pruning(1, "no-lag")
    if "q2-empty" in which: res["q2-empty"] = q2_pruning(0, "code")       # LFV-024 must be rediscovered
    if "q2-mutant" in which: res["q2-mutant"] = q2_pruning(1, "no-tie-guard")
    print("DONE", {k: str(v) for k, v in res.items()})
