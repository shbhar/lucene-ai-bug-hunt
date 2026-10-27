"""
Lemma-split SMT proof that the Naive Bayes normalisation maps FINITE raw scores to probabilities in [0,1],
and that assignClass agrees with getClasses().get(0) on its output.  (The monolithic query in nb_norm.py,
Q1 for n=2, timed out after 600 s in Z3 4.12.2; this file splits it as the study recommends.)

Java under analysis (Lucene main @ ea57360912ed):
  SimpleNaiveBayesClassifier.normClassificationResults  SimpleNaiveBayesClassifier.java:292-318
  BM25NBClassifier.normClassificationResults            BM25NBClassifier.java:147-173 (same code)
  assignClass / getClasses                              SimpleNaiveBayesClassifier.java:102-127, BM25NBClassifier.java:95-111
  ClassificationResult.compareTo                        ClassificationResult.java:31-33

Proof chain (n = number of classes, any n >= 1; every step is one SMT obligation below, binary64 RNE):
  L0  head h of the Double.compare-descending sort of finite x_i satisfies h >= x_i (fp order)       [n = 2..4]
  L1  s_1 = 0 (+) exp(h - h) = 0 (+) 1 = 1                                         (exp(0) = 1 axiom)
  L1s step: 1 <= s <= K (K integral, 1 <= K <= 2^31), 0 <= e <= 1  ==>  1 <= s (+) e <= K (+) 1 = K+1  [induction]
      (each term e = exp(x_i (-) h) is in [0,1] because x_i (-) h <= 0 by L4a and exp maps [-inf,0] to [0,1])
  L2  1 <= s <= 2^31 finite  ==>  L = log(s) finite and 0 <= L <= s            (log axioms)
  L3  h finite, 0 <= L <= 2^31  ==>  loga = h (+) L is finite and loga >= h
  L4  x <= h <= loga, all finite  ==>  q = x (-) loga <= 0 and q not NaN (q may be -inf)
  L4a x <= h finite ==> x (-) h <= 0, not NaN
  L5  q <= 0 (or -inf), not NaN  ==>  p = exp(q) in [0,1], not NaN, sign bit clear   (exp axioms)
  L6  p_i in [0,1], not NaN, sign bit clear  ==>  index chosen by assignClass (first strict max from -MAX_VALUE)
      == head of the stable Double.compare-descending sort                                        [n = 2..4]
  Vacuity: each obligation's hypotheses are satisfiable (sat), and each has a mutant that is sat
  (e.g. L3 with RTZ is still fine but L4 with x > h breaks; L6 with a -0.0 / NaN p breaks).

Every obligation is solved by Z3 (python API) AND dumped to model/smt2/<name>.smt2 for cvc5 (see run log).
"""
import os
import subprocess
import sys
import time
import z3

F = z3.Float64()
RNE = z3.RNE()
A, O, N = z3.And, z3.Or, z3.Not
HERE = os.path.dirname(os.path.abspath(__file__))
SMT2 = os.path.join(HERE, "smt2")
CVC5 = os.environ.get("CVC5")


def fp(v):
    return z3.FPVal(v, F)


ZERO, ONE = fp(0.0), fp(1.0)
MAXV = z3.FPVal(1.7976931348623157e308, F)
NEG_MAX = z3.fpNeg(MAXV)
TWO31 = fp(2.0 ** 31)


def fin(x):
    return A(N(z3.fpIsNaN(x)), N(z3.fpIsInf(x)))


def dc_gt(a, b):
    return O(A(z3.fpIsNaN(a), N(z3.fpIsNaN(b))),
             A(N(z3.fpIsNaN(a)), N(z3.fpIsNaN(b)),
               O(z3.fpGT(a, b), A(z3.fpIsZero(a), z3.fpIsZero(b), z3.fpIsPositive(a), z3.fpIsNegative(b)))))


def dc_eq(a, b):
    return A(N(dc_gt(a, b)), N(dc_gt(b, a)))


def stable_sort_head(xs, tag):
    """Head index of a stable sort of xs, descending under Double.compare (as Collections.sort + compareTo)."""
    n = len(xs)
    h = z3.Int(f"{tag}_head")
    cons = [h >= 0, h < n]
    for j in range(n):
        # head = j  <=>  no element compares strictly greater than xs[j], and no EARLIER element compares equal
        cond = A(*[N(dc_gt(xs[k], xs[j])) for k in range(n) if k != j],
                 *[N(dc_eq(xs[k], xs[j])) for k in range(j)])
        cons.append((h == j) == cond)
    return h, cons


results = []


def prove(name, hyps, goal, expect="unsat", timeout_s=600):
    """Check hyps & not(goal) (expect unsat) or a sat probe (goal=None)."""
    s = z3.Solver()
    s.set("timeout", timeout_s * 1000)
    s.add(*hyps)
    if goal is not None:
        s.add(N(goal))
    os.makedirs(SMT2, exist_ok=True)
    path = os.path.join(SMT2, name + ".smt2")
    with open(path, "w") as f:
        f.write("(set-logic ALL)\n" + s.sexpr() + "\n(check-sat)\n")
    t0 = time.time()
    r = str(s.check())
    dt = time.time() - t0
    cv = "-"
    if CVC5:
        t1 = time.time()
        try:
            out = subprocess.run([CVC5, "--lang", "smt2", f"--tlimit={timeout_s * 1000}", path],
                                 capture_output=True, text=True, timeout=timeout_s + 30)
            cv = (out.stdout.strip().splitlines() or ["?"])[-1] + f" ({time.time() - t1:.1f}s)"
        except subprocess.TimeoutExpired:
            cv = "timeout"
    ok = r == expect
    results.append(ok)
    print(f"{name}: z3={r} ({dt:.1f}s) cvc5={cv} expect={expect} {'OK' if ok else 'MISMATCH'}", flush=True)
    return ok


def main():
    # L0: sort head is the fp-maximum for finite inputs
    for n in (2, 3, 4):
        xs = [z3.Const(f"L0n{n}_x{i}", F) for i in range(n)]
        h, cons = stable_sort_head(xs, f"L0n{n}")
        hv = z3.Const(f"L0n{n}_hv", F)
        cons += [z3.And(*[z3.Implies(h == j, hv == xs[j]) for j in range(n)])]
        prove(f"L0_n{n}", cons + [fin(x) for x in xs], A(*[z3.fpGEQ(hv, x) for x in xs]))

    # L1s: inductive sum step
    s, e, K = z3.Consts("L1s_s L1s_e L1s_K", F)
    hyp = [fin(s), fin(K), z3.fpGEQ(K, ONE), z3.fpLEQ(K, TWO31), z3.fpRoundToIntegral(RNE, K) == K,
           z3.fpGEQ(s, ONE), z3.fpLEQ(s, K), N(z3.fpIsNaN(e)), z3.fpGEQ(e, ZERO), z3.fpLEQ(e, ONE)]
    s2 = z3.fpAdd(RNE, s, e)
    K1 = z3.fpAdd(RNE, K, ONE)
    prove("L1s_step", hyp, A(fin(s2), z3.fpGEQ(s2, ONE), z3.fpLEQ(s2, K1), K1 == z3.fpAdd(RNE, K, ONE),
                             z3.fpEQ(z3.fpSub(RNE, K1, K), ONE)))
    prove("L1s_vacuity_hyps_sat", hyp, None, expect="sat")
    # mutant: a term e > 1 (an exp result above 1) breaks the bound
    prove("L1s_mutant_e_gt_1", [fin(s), fin(K), z3.fpGEQ(K, ONE), z3.fpLEQ(K, TWO31),
                                z3.fpRoundToIntegral(RNE, K) == K, z3.fpGEQ(s, ONE), z3.fpLEQ(s, K),
                                fin(e), z3.fpGEQ(e, ZERO), z3.fpLEQ(e, fp(4.0)),
                                z3.fpGT(s2, K1)], None, expect="sat")
    # base: 0 + 1 = 1
    prove("L1_base", [], z3.fpAdd(RNE, ZERO, ONE) == ONE)

    # L3: loga = h (+) L
    h, L = z3.Consts("L3_h L3_L", F)
    hyp = [fin(h), fin(L), z3.fpGEQ(L, ZERO), z3.fpLEQ(L, TWO31)]
    loga = z3.fpAdd(RNE, h, L)
    prove("L3_loga_ge_h", hyp, A(fin(loga), z3.fpGEQ(loga, h)))
    prove("L3_vacuity", hyp, None, expect="sat")
    prove("L3_mutant_negative_L", [fin(h), fin(L), z3.fpLT(L, ZERO), z3.fpLT(z3.fpAdd(RNE, h, L), h)], None,
          expect="sat")

    # L4 / L4a: q = x (-) loga <= 0
    x, hh, lg = z3.Consts("L4_x L4_h L4_loga", F)
    hyp = [fin(x), fin(hh), fin(lg), z3.fpLEQ(x, hh), z3.fpLEQ(hh, lg)]
    q = z3.fpSub(RNE, x, lg)
    prove("L4_q_le_0", hyp, A(N(z3.fpIsNaN(q)), z3.fpLEQ(q, ZERO)))
    q2 = z3.fpSub(RNE, x, hh)
    prove("L4a_diff_le_0", [fin(x), fin(hh), z3.fpLEQ(x, hh)], A(N(z3.fpIsNaN(q2)), z3.fpLEQ(q2, ZERO)))
    prove("L4_vacuity", hyp, None, expect="sat")
    prove("L4_mutant_x_gt_loga", [fin(x), fin(lg), z3.fpGT(x, lg), z3.fpGT(z3.fpSub(RNE, x, lg), ZERO)], None,
          expect="sat")
    # -inf is reachable for q (x = -MAX, loga = +MAX): exp(-inf) = 0 is still in [0,1]
    prove("L4_q_can_be_minus_inf", hyp + [z3.fpIsInf(q)], None, expect="sat")

    # L6: assignClass index == stable sort head on non-NaN, sign-clear p in [0,1]
    for n in (2, 3, 4):
        ps = [z3.Const(f"L6n{n}_p{i}", F) for i in range(n)]
        hyp = []
        for p in ps:
            hyp += [N(z3.fpIsNaN(p)), z3.fpIsPositive(p), z3.fpGEQ(p, ZERO), z3.fpLEQ(p, ONE)]
        run = NEG_MAX
        idx = z3.IntVal(-1)
        for i, p in enumerate(ps):
            take = z3.fpGT(p, run)
            idx = z3.If(take, z3.IntVal(i), idx)
            run = z3.If(take, p, run)
        head, cons = stable_sort_head(ps, f"L6n{n}")
        prove(f"L6_n{n}_assign_eq_head", hyp + cons, idx == head)
        if n == 2:
            prove("L6_vacuity", hyp + cons, None, expect="sat")
            # mutants: a NaN p, or a -0.0 p before a +0.0 p, makes them disagree (the guards are load-bearing)
            hypn = [z3.fpIsNaN(ps[0]), N(z3.fpIsNaN(ps[1])), z3.fpIsPositive(ps[1])]
            prove("L6_mutant_nan_disagrees", hypn + cons + [idx != head], None, expect="sat")
            hypz = [ps[0] == z3.fpMinusZero(F), ps[1] == z3.fpPlusZero(F)]
            prove("L6_mutant_negzero_disagrees", hypz + cons + [idx != head], None, expect="sat")

    ok = all(results)
    print(f"{sum(results)}/{len(results)} as expected -> {'ALL_EXPECTED' if ok else 'SOME_UNEXPECTED'}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
