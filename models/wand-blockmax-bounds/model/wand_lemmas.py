"""
wand-blockmax-bounds - WANDScorer scaled-integer rejection test, split into SMT lemmas (Z3 FP theory, exact IEEE semantics).

Mapping table (model element -> Java line, lucene/core/src/java/org/apache/lucene/search/WANDScorer.java @ ea57360912ed)
  sf = scalingFactor(G)                       69-83   (G = (float) sumUpperBound(sum of global max scores, n), 186-193)
  scaleMaxScore(x, sf)  = min(ceil(x*2^sf), 2^24-1)  90-105
  scaleMinScore(m, sf)  = floor(m*2^sf)              111-120
  lead bound L = scaleMaxScore((float) sumUpperBound(A, 24), sf), A = double leadScore   333-337, 351-355
  sumUpperBound(x, 24) = (1 + 2*23*2^-52) * x        MathUtil.java:177-194
  rejection: L + tailMaxScore < minCompetitiveScore   342
  doc score: score() = (float) leadScore after advanceAllTail adds matching tail scores 538-565

Proof structure (potential-function argument, units u = 2^-sf):
  Phi(D) = D*2^sf + R(D*2^sf),  R(x) = 2^-29 - 2^(b(x)-52) for x in binade b(x) (x in [2^b, 2^(b+1)), b <= 23), R(0) = 2^-29.
  R bounds the total double rounding error that later additions of integer-valued terms can still introduce.
  BASE : for the double A that the leads sum to,            Phi(A)          <  L + 1/2
  STEP : for every further matching clause score s (float), Phi(fl(D + s))  <= Phi(D) + ceil(s*2^sf)
  FINAL: Phi(D) < K + 1/2  and  (float) D >= minScore  ==>  floor(minScore*2^sf) <= K
  Chaining BASE, STEP* and FINAL with K = L + sum of ceil(tail maxima) >= L + sum of ceil(matching tail scores)
  gives: the rejection test (342) never rejects a doc whose float score is >= minScore.
  Preconditions (encoded): every value <= the float G with G*2^sf in [2^23, 2^24) (scores <= local max <= global max,
  and (float)score <= G by the order-independence of sumUpperBound, proved in the 2026-10-22 study, Pruning.dfy);
  scores finite, non-negative, not NaN; D = 0 or D >= 2^-149 (a sum of positive floats).
  The potential inequality is evaluated in a wide FP sort (15, 240) where every sum/difference used is exact.

Usage: python3 -I wand_lemmas.py <lemma: base|step|final|scalb|noclip|all> <sf...>
"""
import sys, time, json
import z3

F32 = z3.Float32(); F64 = z3.Float64(); RNE = z3.RNE()
W = z3.FPSort(15, 240)   # wide sort: exact for all sums/differences below (exponents in [-1100, 30])

def dconst(v): return z3.FPVal(v, F64)
def fconst(v): return z3.FPVal(v, F32)
def wconst(v): return z3.FPVal(v, W)
def widen(x): return z3.fpToFP(RNE, x, F64)        # (double) float: exact
def narrow(d): return z3.fpToFP(RNE, d, F32)       # (float) double: RNE
def towide(d): return z3.fpToFP(RNE, d, W)         # exact (wide sort has more range/precision)
def nonneg_finite(x):
    return z3.And(z3.Not(z3.fpIsNaN(x)), z3.Not(z3.fpIsInf(x)), z3.Not(z3.fpIsNegative(x)))

def pow2_double(k):
    return z3.FPVal(2.0 ** k, F64)

def scale(d, sf):
    """Math.scalb(double, sf) == d * 2^sf (exact when no overflow/underflow; checked by lemma 'scalb')."""
    return z3.fpMul(RNE, d, pow2_double(sf))

MAXS = float((1 << 24) - 1)

def scale_max(fv, sf):
    sc = scale(widen(fv), sf)
    return z3.If(z3.fpGT(sc, dconst(MAXS)), dconst(MAXS), z3.fpRoundToIntegral(z3.RTP(), sc))

def scale_min(fv, sf):
    if MUTANT == 'ceilmin':
        return z3.fpRoundToIntegral(z3.RTP(), scale(widen(fv), sf))
    return z3.fpRoundToIntegral(z3.RTN(), scale(widen(fv), sf))

MUTANT = None   # vacuity checks: 'nopush' (drop sumUpperBound(.,24)), 'R0' (R := 0), 'ceilmin' (scaleMinScore rounds up)

def sum_ub24(a):
    if MUTANT == 'nopush':
        return a
    b = 23 * 2.0 ** -52
    return z3.fpMul(RNE, dconst(1.0 + 2 * b), a)

def R_of_units(xd):
    """R(x) in the wide sort, x a double (units) >= 0, x < 2^24."""
    bv = z3.fpToIEEEBV(xd)               # 64 bits
    E = z3.Extract(62, 52, bv)           # biased exponent
    # 2^(b-52) as a double: biased exponent E - 52 (valid when E >= 53, i.e. x >= 2^-970)
    p = z3.fpBVToFP(z3.Concat(z3.BitVecVal(0, 1), E - 52, z3.BitVecVal(0, 52)), F64)
    r_nonzero = z3.fpSub(RNE, wconst(2.0 ** -29), towide(p))
    if MUTANT == 'R0':
        return wconst(0.0)
    return z3.If(z3.fpIsZero(xd), wconst(2.0 ** -29), r_nonzero)

def phi(d, sf):
    x = scale(d, sf)
    return z3.fpAdd(RNE, towide(x), R_of_units(x)), x

def G_bounds(sf):
    """Largest float G with G*2^sf < 2^24 (G in the binade that yields this sf), as a float constant."""
    # G*2^sf in [2^23, 2^24): G in [2^(23-sf), 2^(24-sf)); largest float there is (2^24-1)*2^-sf.
    import struct
    gmax = (2.0 ** 24 - 1) * 2.0 ** (-sf)
    return gmax

def double_domain(d):
    """D is a double sum of non-negative floats: every float is a multiple of 2^-149, a double sum of such values is
    exact below 2^-96 and rounds to a coarser multiple above, so D is an integer multiple of 2^-149.
    (Model fix 2026-10-25: the first version only required D = 0 or D >= 2^-149, which admitted unreachable doubles
    such as 1.5*2^-149 and gave spurious SAT models for sf >= 149.)"""
    k = z3.fpMul(RNE, d, dconst(2.0 ** 149))   # exact for d < 2^874
    return z3.And(nonneg_finite(d), z3.fpLEQ(d, dconst(2.0 ** 200)), z3.fpEQ(z3.fpRoundToIntegral(RNE, k), k))

def mk_solver(timeout_s):
    s = z3.Solver(); s.set('timeout', int(timeout_s * 1000)); return s

def report(name, sf, s, extra_vals):
    t0 = time.time(); r = s.check(); dt = round(time.time() - t0, 1)
    out = {'lemma': name, 'sf': sf, 'result': str(r), 'seconds': dt}
    if r == z3.sat:
        m = s.model()
        out['model'] = {k: str(m.eval(v, model_completion=True)) for k, v in extra_vals.items()}
    print(json.dumps(out), flush=True)
    return out

def lemma_scalb(sf, timeout_s):
    """For every non-negative finite float f, scalb((double) f, sf) is exact (no overflow, no rounding)."""
    f = z3.FP('f', F32); s = mk_solver(timeout_s)
    s.add(nonneg_finite(f))
    sc = scale(widen(f), sf)
    back = z3.fpMul(RNE, sc, pow2_double(-sf))
    s.add(z3.Or(z3.fpIsInf(sc), z3.Not(z3.fpEQ(back, widen(f))),
                z3.And(z3.Not(z3.fpIsZero(f)), z3.fpIsSubnormal(sc))))
    return report('scalb', sf, s, {'f': f})

def lemma_noclip(sf, timeout_s):
    """Every float x <= G (G the largest float of sf's binade) scales to <= 2^24-1: the clip at line 98 is unreachable."""
    x = z3.FP('x', F32); s = mk_solver(timeout_s)
    s.add(nonneg_finite(x), z3.fpLEQ(widen(x), dconst(G_bounds(sf))))
    s.add(z3.fpGT(scale(widen(x), sf), dconst(MAXS)))
    return report('noclip', sf, s, {'x': x})

def lemma_base(sf, timeout_s):
    A = z3.FP('A', F64); s = mk_solver(timeout_s)
    s.add(double_domain(A))
    s.add(z3.fpLEQ(widen(narrow(A)), dconst(G_bounds(sf))))   # (float) A <= T <= G
    F = narrow(sum_ub24(A))
    L = scale_max(F, sf)
    ph, x = phi(A, sf)
    half = wconst(0.5)
    s.add(z3.Not(z3.fpLT(ph, z3.fpAdd(RNE, towide(L), half))))
    return report('base', sf, s, {'A': A, 'L': L, 'phi': ph})

def lemma_step(sf, timeout_s):
    D = z3.FP('D', F64); x = z3.FP('s', F32); s = mk_solver(timeout_s)
    s.add(double_domain(D), nonneg_finite(x))
    s.add(z3.fpLEQ(widen(x), dconst(G_bounds(sf))))            # s <= local max <= G
    D2 = z3.fpAdd(RNE, D, widen(x))                           # leadScore += score  (addLead 378 / advanceAllTail)
    s.add(z3.fpLEQ(widen(narrow(D2)), dconst(G_bounds(sf))))  # (float) D2 <= T <= G
    c = scale_max(x, sf)                                      # tail bound contribution (s <= max -> ceil(s) <= ceil(max))
    p1, _ = phi(D, sf); p2, _ = phi(D2, sf)
    s.add(z3.fpGT(z3.fpSub(RNE, p2, towide(c)), p1))
    return report('step', sf, s, {'D': D, 's': x, 'D2': D2})

def lemma_final(sf, timeout_s):
    D = z3.FP('D', F64); K = z3.FP('K', F64); ms = z3.FP('minScore', F32); s = mk_solver(timeout_s)
    s.add(double_domain(D), nonneg_finite(K), nonneg_finite(ms))
    s.add(z3.fpEQ(z3.fpRoundToIntegral(RNE, K), K), z3.fpLEQ(K, dconst(2.0 ** 40)))
    s.add(z3.fpLEQ(widen(narrow(D)), dconst(G_bounds(sf))))
    ph, _ = phi(D, sf)
    s.add(z3.fpLT(ph, z3.fpAdd(RNE, towide(K), wconst(0.5))))
    s.add(z3.fpGEQ(narrow(D), ms))                            # doc is competitive
    s.add(z3.fpLT(K, scale_min(ms, sf)))                      # but rejected (line 342)
    return report('final', sf, s, {'D': D, 'K': K, 'minScore': ms})

def lemma_stepni(sf, timeout_s):
    """STEP for a tail score s whose scaled value is not an integer: ceil(s*2^sf) - s*2^sf >= 2^-24 (units)."""
    x = z3.FP('s', F32); s = mk_solver(timeout_s)
    s.add(nonneg_finite(x), z3.fpLEQ(widen(x), dconst(G_bounds(sf))))
    su = scale(widen(x), sf)
    c = z3.fpRoundToIntegral(z3.RTP(), su)
    s.add(z3.Not(z3.fpEQ(c, su)))
    bound = 2.0 ** -23 if MUTANT == 'stepni_strong' else 2.0 ** -24   # mutant: a slack bound that is too strong
    s.add(z3.fpLT(z3.fpSub(RNE, c, su), dconst(bound)))
    return report('stepni', sf, s, {'s': x})

def lemma_stepi(sf, timeout_s):
    """STEP for an integer-valued tail score (units): if fl(D+s) stays in D's binade the add is exact; otherwise its
    error is at most half an ulp of the result (<= 2^(b'-53) <= R(D) - R(D'))."""
    D = z3.FP('D', F64); x = z3.FP('s', F32); s = mk_solver(timeout_s)
    s.add(double_domain(D), nonneg_finite(x), z3.fpLEQ(widen(x), dconst(G_bounds(sf))))
    su = scale(widen(x), sf)
    if MUTANT != 'stepi_nonint':   # mutant: drop the integer-valued precondition
        s.add(z3.fpEQ(z3.fpRoundToIntegral(RNE, su), su))
    D2 = z3.fpAdd(RNE, D, widen(x))
    s.add(z3.fpLEQ(widen(narrow(D2)), dconst(G_bounds(sf))))
    Du = scale(D, sf); D2u = scale(D2, sf)
    eD = z3.Extract(62, 52, z3.fpToIEEEBV(Du)); eD2 = z3.Extract(62, 52, z3.fpToIEEEBV(D2u))
    # exact case: same binade (and D non-zero) -> D2 - D == s exactly
    same = z3.And(z3.Not(z3.fpIsZero(D)), eD == eD2)
    s.add(same, z3.Not(z3.fpEQ(z3.fpSub(RNE, D2u, Du), su)))
    return report('stepi', sf, s, {'D': D, 's': x, 'D2': D2})

def lemma_e2e(sf, timeout_s, ntail=1):
    """End-to-end for a free double lead sum A and ntail matching tail clauses (scores = maxima, worst case)."""
    A = z3.FP('A', F64); ts = [z3.FP('t%d' % i, F32) for i in range(ntail)]; ms = z3.FP('minScore', F32)
    s = mk_solver(timeout_s)
    s.add(double_domain(A), nonneg_finite(ms))
    for t in ts: s.add(nonneg_finite(t), z3.fpLEQ(widen(t), dconst(G_bounds(sf))))
    L = scale_max(narrow(sum_ub24(A)), sf)
    K = L
    for t in ts: K = z3.fpAdd(RNE, K, scale_max(t, sf))
    D = A
    for t in reversed(ts): D = z3.fpAdd(RNE, D, widen(t))
    T = narrow(D)
    s.add(z3.fpLEQ(widen(T), dconst(G_bounds(sf))))
    s.add(z3.fpLT(K, scale_min(ms, sf)), z3.fpGEQ(T, ms))
    return report('e2e%d' % ntail, sf, s, {'A': A, 'minScore': ms, **{str(t): t for t in ts}})

LEMMAS = {'stepni': lemma_stepni, 'stepi': lemma_stepi, 'e2e1': lambda sf, to: lemma_e2e(sf, to, 1),
          'e2e2': lambda sf, to: lemma_e2e(sf, to, 2), 'e2e3': lambda sf, to: lemma_e2e(sf, to, 3), 'scalb': lemma_scalb, 'noclip': lemma_noclip, 'base': lemma_base, 'step': lemma_step, 'final': lemma_final}

if __name__ == '__main__':
    which = sys.argv[1]; args = sys.argv[2:]
    if args and args[0].startswith('--mutant='):
        MUTANT = args[0].split('=', 1)[1]; args = args[1:]
        print(json.dumps({'mutant': MUTANT}), flush=True)
    sfs = [int(v) for v in args]
    timeout = 1800
    names = list(LEMMAS) if which == 'all' else which.split(',')
    for sf in sfs:
        for n in names:
            LEMMAS[n](sf, timeout)
