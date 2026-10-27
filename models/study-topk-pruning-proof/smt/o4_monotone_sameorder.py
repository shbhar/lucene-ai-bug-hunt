"""O4: same-order monotonicity of Java's double accumulation of float scores.
Claim(n): x_i <= m_i (non-negative finite floats) => (float)seqsum(x) <= (float)seqsum(m).
Used by BlockMaxConjunctionScorer (getMaxScore 225-231 vs score 202-208: same array order),
BlockMaxConjunctionBulkScorer (computeMaxScore 69-84 vs scoreWindowScoreFirst 163,175-196: lead
first then scorers[1..] in order), ConjunctionScorer (57-74), ReqOptSumScorer (float adds 242-279)."""
import sys, os, time, z3
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fpcommon import *

def check(n, timeout_ms=1200000):
    x = [f32('x%d' % i) for i in range(n)]; m = [f32('m%d' % i) for i in range(n)]
    s = z3.Solver(); s.set('timeout', timeout_ms)
    for a, b in zip(x, m):
        s.add(nonneg_finite(a), nonneg_finite(b), z3.fpLEQ(a, b))
    s.add(z3.fpGT(narrow(seqsum(x)), narrow(seqsum(m))))
    t0 = time.time(); r = s.check()
    return {'n': n, 'result': str(r), 'seconds': round(time.time() - t0, 1)}

def check_float_adds(n, timeout_ms=1200000):
    """ReqOptSumScorer: float score = req; score += opt  (float arithmetic, no double)."""
    x = [f32('x%d' % i) for i in range(n)]; m = [f32('m%d' % i) for i in range(n)]
    s = z3.Solver(); s.set('timeout', timeout_ms)
    for a, b in zip(x, m):
        s.add(nonneg_finite(a), nonneg_finite(b), z3.fpLEQ(a, b))
    sx, sm = x[0], m[0]
    for i in range(1, n):
        sx = z3.fpAdd(RNE, sx, x[i]); sm = z3.fpAdd(RNE, sm, m[i])
    s.add(z3.fpGT(sx, sm))
    t0 = time.time(); r = s.check()
    return {'n': n, 'float_adds_result': str(r), 'seconds': round(time.time() - t0, 1)}

if __name__ == '__main__':
    for n in [int(a) for a in sys.argv[1:]] or [2, 3, 4]:
        print(check_float_adds(n), flush=True)
        print(check(n), flush=True)
