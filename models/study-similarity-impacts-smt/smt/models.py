"""Transcriptions of every SimilarityBase scorer (operation order as in the Java source on main
7cecf79523) into the monotype abstract domain, and the driver that checks, for every model and
every parameter combination:
   MF : (float) score is non-decreasing in freq        (SimScorer#score javadoc)
   MN : (float) score is non-increasing in docLen = LENGTH_TABLE[norm & 0xFF]  (unsigned norm)
   RNG: score is finite and >= 0 (Indri: finite only) for freq <= 2^31, boost <= 2^31
        (the BaseSimilarityTestCase ranges), and not NaN for freq = Float.MAX_VALUE.
Usage: python -I models.py [maxhyper]   (maxhyper: hyper-parameters c, mu up to Float.MAX_VALUE
       instead of the test bound Integer.MAX_VALUE)
"""
import math
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from monotype import (V, const, var, add, sub, mul, div, log, log2, pow_, sqrt, jmax, jmin, neg,
                      tofloat, step, down, up, INF, comb, flip)

FMAX = 3.4028234663852886e38
FMIN = 1.401298464324817e-45
LEN_LO, LEN_HI = 1.0, 2013265944.0   # LENGTH_TABLE[1], LENGTH_TABLE[255]
NMAX, TMAX = 2.0 ** 48, 2.0 ** 49    # BaseSimilarityTestCase MAXDOC/MAXTOKENS_FORTESTING
HYPER_MAX = FMAX if 'maxhyper' in sys.argv else 2.0 ** 31


class Ctx:
    """stats intervals (independent) + derived LEMMA leaves for correlated stats."""
    def __init__(self):
        self.N = V(1, NMAX)                 # docCount
        self.n = V(1, NMAX)                 # docFreq (<= N)
        self.F = V(1, TMAX)                 # totalTermFreq (>= n)
        self.T = V(1, TMAX)                 # sumTotalTermFreq (>= N, >= F)
        self.avgfl = V(1, TMAX)             # T / (double) N >= 1
        self.boost = V(0, 2.0 ** 31)
        self.notes = []

    def lemma(self, lo, hi, why):
        self.notes.append(why)
        return V(lo, hi, False, 'C', why)


# ---------------- DFR ----------------
def norm_none(cx, tf, dl, h):
    return tf


def norm_h1(cx, tf, dl, h):    # tf * c * (avgfl / len)
    return mul(mul(tf, V(0, HYPER_MAX)), div(cx.avgfl, dl))


def norm_h2(cx, tf, dl, h):    # tf * log2(1 + c * avgfl / len)
    return mul(tf, log2(add(const(1.0), div(mul(V(0, HYPER_MAX), cx.avgfl), dl))))


def norm_h3(cx, tf, dl, h):    # (tf + mu * ((F + 1F) / (T + 1F))) / (len + mu) * mu
    mu = V(0, HYPER_MAX)
    # float ops: (F+1F)/(T+1F) in [0,1]: F <= T so the exact ratio <= 1 and float rounding of
    # F+1F <= T+1F is monotone => quotient <= 1  (LEMMA H3P)
    p = cx.lemma(0.0, 1.0, 'H3P: (F+1F)/(T+1F) in [0,1] since F <= T')
    mup = V(0, HYPER_MAX)              # float mu * p <= mu
    return mul(div(add(tf, mup), add(dl, mu)), mu)


def norm_z(cx, tf, dl, h):     # tf * Math.pow(avgfl / len, z), z in (0, 0.5)
    return mul(tf, pow_(div(cx.avgfl, dl), V(FMIN, 0.5)))


NORMS = {'NoNorm': norm_none, 'H1': norm_h1, 'H2': norm_h2, 'H3': norm_h3, 'Z': norm_z}


def ae_L(cx):
    return const(1.0)


def ae_B(cx):                  # (F + 1 + 1.0) / (n + 1)
    return div(add(add(cx.F, const(1)), const(1.0)), add(cx.n, const(1)))


AES = {'L': ae_L, 'B': ae_B}


def bm_G(cx, tfn, ae):
    # lambda = F'/(N+F') in (0,1]; A = log2(lambda+1) in [0,1]; B = log2((1+lambda)/lambda)
    A = cx.lemma(0.0, 1.0 + 1e-15, 'G-A: lambda in (0,1] => log2(lambda+1) in [0,1]')
    B = cx.lemma(0.0, 50.0, 'G-B: (1+l)/l in [2, 2^48+2] => B in [1, 49]')
    BmA = cx.lemma(0.0, 50.0, 'G-BA: fl((1+l)/l) >= fl(1+l) as l <= 1 => B - A >= 0')
    x = div(BmA, add(const(1.0), tfn))
    core = sub(B, x)
    # LEMMA G-core: 0 <= x <= B - A <= B  =>  B - x >= 0 (fl monotone, B - B == 0)
    core = V(0.0, core.hi, core.nan, core.d)
    cx.notes.append('G-core: B - (B-A)/(1+tfn) >= 0')
    return mul(core, ae)


def bm_IF(cx, tfn, ae):        # A = log2(1 + (N + 1) / (F + 0.5)); A * ae * (1 - 1 / (1 + tfn))
    A = log2(add(const(1.0), div(add(cx.N, const(1)), add(cx.F, const(0.5)))))
    return mul(mul(A, ae), sub(const(1.0), div(const(1.0), add(const(1.0), tfn))))


def bm_In(cx, tfn, ae):        # A = log2((N + 1) / (n + 0.5))
    A = cx.lemma(0.0, 49.0, 'In-A: n <= N => (N+1)/(n+0.5) > 1 => A >= 0 (log(1.0)=0)')
    return mul(mul(A, ae), sub(const(1.0), div(const(1.0), add(const(1.0), tfn))))


def bm_Ine(cx, tfn, ae):       # ne = N*(1-pow((N-1)/N,F)) in [0,N]; A = log2((N+1)/(ne+0.5))
    A = cx.lemma(0.0, 50.0, 'Ine-A: ne <= N => (N+1)/(ne+0.5) > 1 => A >= 0')
    return mul(mul(A, ae), sub(const(1.0), div(const(1.0), add(const(1.0), tfn))))


BMS = {'G': bm_G, 'IF': bm_IF, 'In': bm_In, 'Ine': bm_Ine}


def dfr(bm, aeff, nrm):
    def score(cx, freq, dl):
        tfn = NORMS[nrm](cx, freq, dl, None)
        ae = AES[aeff](cx)
        return mul(cx.boost, BMS[bm](cx, tfn, ae))
    return score


# ---------------- IB ----------------
def dist_LL(cx, tfn, lam):     # -Math.log(lambda / (tfn + lambda))
    r = div(lam, add(tfn, lam))
    # LEMMA LL: fl(tfn + l) >= l for tfn >= 0  =>  ratio <= 1
    r = V(max(r.lo, 0.0), min(r.hi, 1.0), r.nan, r.d)
    cx.notes.append('LL: lambda/(tfn+lambda) <= 1')
    return neg(log(r))


def dist_SPL(cx, tfn, lam, lam_lt_1):
    q = sub(const(1.0), div(const(1.0), add(tfn, const(1.0))))
    q = jmin(q, const(math.nextafter(1.0, 0)))           # if (q == 1) q = nextDown(1.0)
    q = V(max(q.lo, 0.0), q.hi, q.nan, q.d)
    p = pow_(lam, q)
    if lam_lt_1:
        # spec: pow(l, q) >= pow(l, 1) = l for q <= 1 (semi-monotone); hack: pow==l -> nextUp(l)
        p = V(lam.lo, 1.0, p.nan, p.d)
        num = sub(p, lam)
        num = V(0.0, num.hi, num.nan, num.d)
        den = sub(const(1.0), lam)
        frac = div(num, den)
    else:
        p = V(1.0, lam.hi, p.nan, p.d)
        num = sub(p, lam)
        num = V(num.lo, 0.0, num.nan, num.d)
        den = sub(const(1.0), lam)
        frac = div(num, den)
    # LEMMA SPL-frac: 0 < (pow - l)/(1 - l) <= 1 (pow strictly between l and 1 after the hack)
    frac = V(FMIN * 2 ** -120 if False else 4.9e-324, 1.0, frac.nan, frac.d)
    cx.notes.append('SPL-frac: (pow-l)/(1-l) in (0,1]')
    return jmax(const(0.0), neg(log(frac)))


def ib(dist, lamb, nrm):
    def score(cx, freq, dl):
        tfn = NORMS[nrm](cx, freq, dl, None)
        if lamb == 'DF':   # (float)((n+1.0)/(N+1.0)) in (0,1), ==1 -> nextDown(1f)
            lam = V(1.0 / (NMAX + 1), float(math.nextafter(1.0, 0)) * 0 + (1 - 2 ** -24))
            lt1 = True
        else:              # TTF: (float)((F+1.0)/(N+1.0)) in (0, 2^49]; ==1 -> nextUp(1f)
            lam = V(1.0 / (NMAX + 1), TMAX)
            lt1 = None
        if dist == 'LL':
            return mul(cx.boost, dist_LL(cx, tfn, lam))
        if lt1 is None:    # split the TTF case on lambda < 1 / lambda > 1 (lambda != 1 enforced)
            a = mul(cx.boost, dist_SPL(cx, tfn, V(lam.lo, 1 - 2 ** -24), True))
            b = mul(cx.boost, dist_SPL(cx, tfn, V(1 + 2 ** -23, lam.hi), False))
            return V(min(a.lo, b.lo), max(a.hi, b.hi), a.nan or b.nan,
                     a.d if a.d == b.d else ('?' if 'C' not in (a.d, b.d) else comb(a.d, b.d)))
        return mul(cx.boost, dist_SPL(cx, tfn, lam, True))
    return score


# ---------------- LM / Indri / DFI ----------------
def lm_dirichlet(mu_zero):
    def score(cx, freq, dl):
        if mu_zero:   # mu == 0: log(1+freq/0)=+inf, log(0/dl)=-inf -> NaN -> "score > 0 ? : 0" = 0
            return const(0.0)
        mu = V(FMIN, HYPER_MAX)
        P = div(add(cx.F, const(1.0)), add(cx.T, const(1.0)))   # (F + 1D) / (T + 1D)
        P = V(max(P.lo, 0.0), min(P.hi, 1.0), P.nan, P.d)       # F <= T
        s = mul(cx.boost, add(log(add(const(1.0), div(freq, mul(mu, P)))),
                              log(div(mu, add(dl, mu)))))
        # return score > 0.0d ? score : 0.0d   (NaN -> 0)
        return V(max(0.0, s.lo), max(0.0, s.hi), False, s.d)
    return score


def lm_jm(cx, freq, dl):     # boost * log(1 + ((1 - lambda) * freq / docLen) / (lambda * P))
    lam = V(FMIN, 1.0)
    oml = V(0.0, 1.0)                                  # (1 - lambda) float in [0,1)
    P = div(add(cx.F, const(1.0)), add(cx.T, const(1.0)))
    P = V(max(P.lo, 0.0), min(P.hi, 1.0), P.nan, P.d)
    return mul(cx.boost, log(add(const(1.0), div(div(mul(oml, freq), dl), mul(lam, P)))))


def indri(cx, freq, dl):     # log((freq + mu * P) / (docLen + mu)), P = F/T, mu >= 0 assumed
    mu = V(0.0, HYPER_MAX)
    P = div(cx.F, cx.T)
    P = V(max(P.lo, 0.0), min(P.hi, 1.0), P.nan, P.d)
    return log(div(add(freq, mul(mu, P)), add(dl, mu)))


def dfi(indep):
    def score(cx, freq, dl):
        expected = div(mul(add(cx.F, const(1)), dl), add(cx.T, const(1)))
        # true branch: freq > expected  => fl(freq - expected) > 0 (IEEE: x-y==0 iff x==y)
        diff_dir = comb(freq.d, flip(expected.d))
        d = V(4.9e-324, sub(freq, expected).hi, False, diff_dir)
        e = V(max(expected.lo, 4.9e-324), expected.hi, expected.nan, expected.d)
        if indep == 'ChiSquared':
            m = div(mul(d, d), e)
        elif indep == 'Saturated':
            m = div(d, e)
        else:
            m = div(d, sqrt(e))
        val = mul(cx.boost, log2(add(m, const(1.0))))
        # predicate "freq > expected" turns true as freq grows ('+') / as docLen shrinks ('-')
        cond_dir = '+' if freq.d == '+' else ('-' if dl.d == '+' else 'C')
        return step(cond_dir, val)
    return score


# ---------------- Axiomatic ----------------
def axiomatic(kind, idfk):
    def score(cx, freq, dl):
        s = V(0.0, 1.0)
        if kind in ('F1', 'F3'):
            tf = add(const(1.0), log(add(const(1.0), log(add(freq, const(1))))))
        else:
            tf = const(1.0)
        if kind == 'F1':
            ln = div(add(cx.avgfl, s), add(cx.avgfl, mul(dl, s)))
            # LEMMA AX-LN: fl(avgfl + dl*s) >= avgfl and fl(avgfl + s) <= 2*avgfl (s <= 1 <= avgfl)
            ln = V(max(ln.lo, 0.0), min(ln.hi, 2.0), ln.nan, ln.d)
            cx.notes.append('AX-LN: ln in (0, 2]')
        else:
            ln = const(1.0)
        if kind == 'F2':
            tfln = div(freq, add(add(freq, s), div(mul(s, dl), cx.avgfl)))
            # LEMMA AX-TFLN (range only): denominator >= freq  =>  tfln in [0, 1]
            tfln = V(max(tfln.lo, 0.0), min(tfln.hi, 1.0), tfln.nan, tfln.d)
            cx.notes.append('AX-TFLN: tfln in [0, 1]')
        else:
            tfln = const(1.0)
        if idfk == 'EXP':   # pow((N + 1.0) / n, k): ratio >= 1 since n <= N  (LEMMA AX-R)
            idf = pow_(cx.lemma(1.0, NMAX + 1, 'AX-R: (N+1.0)/n >= 1'), V(0.0, 1.0))
        else:
            idf = log(cx.lemma(1.0, NMAX + 1, 'AX-R: (N+1.0)/n >= 1'))
        if kind == 'F3':    # (docLen - queryLen) * s * queryLen / avgfl
            ql = V(0.0, 2.0 ** 31 - 1)
            gamma = div(mul(mul(sub(dl, ql), s), ql), cx.avgfl)
        else:
            gamma = const(0.0)
        sc = sub(mul(mul(mul(tf, ln), tfln), idf), gamma)
        sc = mul(sc, cx.boost)
        return jmax(const(0.0), sc)
    return score


MODELS = {}
for bm in BMS:
    for ae in AES:
        for nr in NORMS:
            MODELS['DFR %s-%s-%s' % (bm, ae, nr)] = dfr(bm, ae, nr)
for ds in ('LL', 'SPL'):
    for lb in ('DF', 'TTF'):
        for nr in NORMS:
            MODELS['IB %s-%s-%s' % (ds, lb, nr)] = ib(ds, lb, nr)
MODELS['LMDirichlet mu=0'] = lm_dirichlet(True)
MODELS['LMDirichlet mu>0'] = lm_dirichlet(False)
MODELS['LMJelinekMercer'] = lm_jm
MODELS['IndriDirichlet (mu>=0)'] = indri
for ind in ('ChiSquared', 'Saturated', 'Standardized'):
    MODELS['DFI %s' % ind] = dfi(ind)
for k in ('F1', 'F2', 'F3'):
    for i in ('EXP', 'LOG'):
        MODELS['Axiomatic%s%s' % (k, i)] = axiomatic(k, i)


def run():
    rows = []
    for name, fn in MODELS.items():
        res = {}
        # MF: freq varies ('+'), docLen fixed in table range
        cx = Ctx()
        r = tofloat(fn(cx, var(FMIN, FMAX, '+'), V(LEN_LO, LEN_HI)))
        res['MF'] = r.d in ('C', '+')
        # MN: docLen varies ('+'), freq fixed
        cx = Ctx()
        r2 = tofloat(fn(cx, V(FMIN, FMAX), var(LEN_LO, LEN_HI, '+')))
        res['MN'] = r2.d in ('C', '-')
        # RNG: freq <= 2^31
        cx = Ctx()
        r3 = tofloat(fn(cx, V(FMIN, 2.0 ** 31), V(LEN_LO, LEN_HI)))
        indri_ = name.startswith('Indri')
        res['RNG'] = (not r3.nan) and r3.hi < INF and r3.lo > -INF and (indri_ or r3.lo >= 0)
        cx = Ctx()
        r4 = tofloat(fn(cx, V(FMAX, FMAX), V(1.0, 1.0)))
        res['MAXNAN'] = not r4.nan
        rows.append((name, res, r, r2, r3, sorted(set(cx.notes))))
    hdr = 'HYPER_MAX=%g' % HYPER_MAX
    print(hdr)
    print('%-28s %-7s %-7s %-7s %-7s  range(freq<=2^31)' % ('model', 'MF', 'MN', 'RNG', 'MAXNaN'))
    for name, res, r, r2, r3, notes in rows:
        f = lambda b: 'PROVED' if b else 'open'
        print('%-28s %-7s %-7s %-7s %-7s  [%.3g, %.3g]%s  dirF=%s dirN=%s' % (
            name, f(res['MF']), f(res['MN']), f(res['RNG']), f(res['MAXNAN']), r3.lo, r3.hi,
            ' NaN?' if r3.nan else '', r.d, r2.d))
    return rows


if __name__ == '__main__':
    run()
