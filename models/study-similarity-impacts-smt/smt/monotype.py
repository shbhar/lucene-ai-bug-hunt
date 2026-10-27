"""Compositional monotonicity + range proof ("monotonicity typing", an abstract interpretation)
for the double-precision SimilarityBase scorers, where relational QF_FP SMT queries do not
terminate in reasonable time.

Soundness basis
  (R) IEEE-754 / JLS 15.17-15.18: every basic op (+ - * / sqrt, double->float cast) returns
      round(exact result) with round-to-nearest-even, and round() is monotone non-decreasing.
      Hence fl(a op b) is weakly monotone in each argument wherever the exact op is (with the
      sign side conditions tracked below).
  (S) java.lang.Math javadoc: Math.log and Math.pow results "must be semi-monotonic" (if the
      mathematical function is non-decreasing/non-increasing, so is the computed one); we read
      this per argument for pow. Math.pow(x, 0) == 1.0, Math.pow(x, 1.0) == x (javadoc special
      cases). Math.log(+inf) = +inf, Math.log(0) = -inf, Math.log(x<0 or NaN) = NaN.
  (I) Interval endpoints of + - * / are computed with the SAME IEEE double operation (Python
      float == binary64 RNE), which by (R) bounds the computed value exactly: x >= lo and
      y >= lo' imply fl(x+y) >= fl(lo+lo'). Endpoints of log/pow are widened outward by 2 ulps
      (Math.log/pow are within 1 ulp; Python's libm is used for the endpoint itself). NaN
      possibility is tracked explicitly (inf-inf, 0*inf, 0/0, inf/inf, log(negative)).
Every node gets (interval, may_nan, direction) with direction in {C (const), +, -, ?} w.r.t.
ONE varying input (freq, or docLen). The final verdict for a property is PROVED when the
root direction is the required one (or C) and the root interval/NaN flag satisfies the range
claim; otherwise UNPROVED (not necessarily a bug: the abstraction is incomplete) and the
offending node is reported.

Stat-only sub-expressions whose sign depends on correlations between statistics that intervals
lose (e.g. docFreq <= docCount) are introduced as LEMMA leaves with their range justified in
the comment; each such lemma is elementary (monotone rounding of an exact value > 1, etc.).
"""
import math
import sys

INF = math.inf


def down(x, k=2):
    """outward widening of a lower bound; rounding never changes the sign of an exact result,
    so a non-negative bound stays non-negative (RNE(x) >= 0 for x >= 0)."""
    y = x
    for _ in range(k):
        y = math.nextafter(y, -INF)
    return max(0.0, y) if x >= 0 else y


def up(x, k=2):
    y = x
    for _ in range(k):
        y = math.nextafter(y, INF)
    return min(0.0, y) if x <= 0 else y


class V:
    __slots__ = ('lo', 'hi', 'nan', 'd', 'why')

    def __init__(self, lo, hi, nan=False, d='C', why=''):
        self.lo, self.hi, self.nan, self.d, self.why = lo, hi, nan, d, why

    def __repr__(self):
        return '[%r, %r]%s dir=%s' % (self.lo, self.hi, ' NaN?' if self.nan else '', self.d)


def flip(d):
    return {'+': '-', '-': '+'}.get(d, d)


def comb(a, b):
    if a == 'C':
        return b
    if b == 'C' or a == b:
        return a
    return '?'


def const(c):
    return V(c, c)


def var(lo, hi, d):
    return V(lo, hi, False, d)


def _safe(f, *args):
    try:
        r = f(*args)
        return r
    except (OverflowError, ValueError, ZeroDivisionError):
        return math.nan


def _range(cands):
    c = [x for x in cands if not math.isnan(x)]
    return min(c), max(c)


def add(a, b, why=''):
    nan = a.nan or b.nan or (a.hi == INF and b.lo == -INF) or (a.lo == -INF and b.hi == INF)
    lo, hi = a.lo + b.lo if not (a.lo == -INF and b.lo == INF) else -INF, a.hi + b.hi
    if math.isnan(lo):
        lo = -INF
    if math.isnan(hi):
        hi = INF
    return V(lo, hi, nan, comb(a.d, b.d), why)


def neg(a):
    return V(-a.hi, -a.lo, a.nan, flip(a.d), a.why)


def sub(a, b, why=''):
    return add(a, neg(b), why)


def sign(a):
    if a.lo >= 0:
        return 1
    if a.hi <= 0:
        return -1
    return 0


def mul(a, b, why=''):
    zero_inf = ((a.lo <= 0 <= a.hi) and (b.lo == -INF or b.hi == INF)) or \
               ((b.lo <= 0 <= b.hi) and (a.lo == -INF or a.hi == INF))
    nan = a.nan or b.nan or zero_inf
    prods = [_safe(lambda x, y: x * y, x, y) for x in (a.lo, a.hi) for y in (b.lo, b.hi)]
    lo, hi = _range(prods + ([0.0] if zero_inf else []))

    def contrib(d, other):
        if d == 'C':
            return 'C'
        s = sign(other)
        return d if s == 1 else (flip(d) if s == -1 else '?')
    d = comb(contrib(a.d, b), contrib(b.d, a))
    return V(lo, hi, nan, d, why)


def div(a, b, why=''):
    nan = a.nan or b.nan or ((a.lo <= 0 <= a.hi) and (b.lo <= 0 <= b.hi)) or \
        ((a.lo == -INF or a.hi == INF) and (b.lo == -INF or b.hi == INF))
    if b.lo > 0 or b.hi < 0:
        qs = [_safe(lambda x, y: x / y, x, y) for x in (a.lo, a.hi) for y in (b.lo, b.hi)]
        lo, hi = _range(qs)
    else:
        lo, hi = -INF, INF
    # a/b : contribution of a keeps direction if b>0, flips if b<0
    ca = 'C' if a.d == 'C' else (a.d if b.lo > 0 else (flip(a.d) if b.hi < 0 else '?'))
    # contribution of b: for b>0, a>=0: decreasing in b; a<=0: increasing in b
    if b.d == 'C':
        cb = 'C'
    elif b.lo > 0 and a.lo >= 0:
        cb = flip(b.d)
    elif b.lo > 0 and a.hi <= 0:
        cb = b.d
    else:
        cb = '?'
    return V(lo, hi, nan, comb(ca, cb), why)


def log(a, why=''):
    nan = a.nan or a.lo < 0
    lo = -INF if a.lo <= 0 else math.log(a.lo)
    hi = INF if a.hi == INF else (-INF if a.hi <= 0 else math.log(a.hi))
    lo, hi = down(lo), up(hi)
    # semi-monotonicity + Math.log(1.0) == 0.0: x >= 1 => log(x) >= 0 ; x <= 1 => log(x) <= 0
    if a.lo >= 1:
        lo = max(lo, 0.0)
    if a.hi <= 1:
        hi = min(hi, 0.0)
    return V(lo, hi, nan, a.d, why)


LOG_2 = math.log(2)


def log2(a, why=''):
    return div(log(a), const(LOG_2), why)


def pow_(x, y, why=''):
    nan = x.nan or y.nan or x.lo < 0
    assert x.lo >= 0
    cands = [_safe(math.pow, xx, yy) for xx in (x.lo, x.hi) for yy in (y.lo, y.hi)]
    lo, hi = _range(cands + [_safe(math.pow, 1.0, y.lo)])
    # direction in x (y fixed sign): y>=0 keeps, y<=0 flips; in y: x>=1 keeps, x<=1 flips
    cx = 'C' if x.d == 'C' else (x.d if y.lo >= 0 else (flip(x.d) if y.hi <= 0 else '?'))
    cy = 'C' if y.d == 'C' else (y.d if x.lo >= 1 else (flip(y.d) if x.hi <= 1 else '?'))
    lo, hi = max(0.0, down(lo)), up(hi)
    # semi-monotonicity in y + pow(x, 0) == 1.0: (x>=1,y>=0) => >=1 ; (x<=1,y>=0) => <=1
    if y.lo >= 0 and x.lo >= 1:
        lo = max(lo, 1.0)
    if y.lo >= 0 and x.hi <= 1:
        hi = min(hi, 1.0)
    return V(lo, hi, nan, comb(cx, cy), why)


def sqrt(a):
    return V(math.sqrt(max(a.lo, 0)), math.sqrt(a.hi) if a.hi != INF else INF, a.nan or a.lo < 0, a.d)


def jmax(a, b):
    """Math.max: NaN-propagating; monotone in both."""
    return V(max(a.lo, b.lo), max(a.hi, b.hi), a.nan or b.nan, comb(a.d, b.d))


def jmin(a, b):
    return V(min(a.lo, b.lo), min(a.hi, b.hi), a.nan or b.nan, comb(a.d, b.d))


def tofloat(a):
    FMAX = 3.4028234663852886e38

    def f(x):
        if x > FMAX * (1 + 2 ** -25):
            return INF
        if x < -FMAX * (1 + 2 ** -25):
            return -INF
        return x
    return V(down(f(a.lo), 1) if abs(a.lo) != INF else a.lo, up(f(a.hi), 1) if abs(a.hi) != INF else a.hi, a.nan, a.d)


def step(cond_dir, value, why=''):
    """`cond ? value : 0.0` where cond is a monotone predicate that switches from false to true
    as the varying input moves in direction cond_dir, value >= 0 on the true branch.
    Monotone in cond_dir if value is monotone in cond_dir (or const)."""
    assert value.lo >= 0, 'step needs value >= 0'
    d = comb(cond_dir, value.d) if value.d in ('C', cond_dir) else '?'
    return V(0.0, value.hi, value.nan, d, why)
