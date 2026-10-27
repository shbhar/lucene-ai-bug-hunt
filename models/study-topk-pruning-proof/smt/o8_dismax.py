"""O8: DisjunctionMaxScorer.getMaxScore (84-109) is an upper bound of score (60-73) for every
topList order, tieBreakerMultiplier in [0,1].  n = 3 sub-scorers; non-matching sub = score 0 (exact)."""
import sys, os, time, itertools, z3
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fpcommon import *

def dismax(vals, tie, with_ub, n):
    smax = fconst(0.0); other = dconst(0.0)
    for v in vals:
        ge = z3.fpGEQ(v, smax)
        other = z3.If(ge, dadd(other, widen(smax)), dadd(other, widen(v)))
        smax = z3.If(ge, v, smax)
    if with_ub:
        b = (n - 1 - 1) * 2.0 ** -52          # sumRelativeErrorBound(subScorers.size() - 1)
        other = dmul(other, dconst(1 + 2 * b))
    return narrow(dadd(widen(smax), dmul(other, widen(tie))))

def run(perm, timeout_ms):
    n = 3
    x = [f32('x%d' % i) for i in range(n)]; m = [f32('m%d' % i) for i in range(n)]; tie = f32('tie')
    s = z3.Solver(); s.set('timeout', timeout_ms)
    for a, b in zip(x, m): s.add(nonneg_finite(a), nonneg_finite(b), z3.fpLEQ(a, b))
    s.add(nonneg_finite(tie), z3.fpLEQ(tie, fconst(1.0)), z3.Not(z3.fpIsZero(tie)))
    score = dismax([x[i] for i in perm], tie, False, n)
    bound = dismax(m, tie, True, n)
    s.add(z3.fpGT(score, bound))
    t0 = time.time(); r = s.check()
    res = {'perm': perm, 'result': str(r), 'seconds': round(time.time() - t0, 1)}
    if r == z3.sat:
        mm = s.model(); res['model'] = {str(v): hexf(mm, v)[0] for v in x + m + [tie]}
    return res

if __name__ == '__main__':
    perm = tuple(int(c) for c in sys.argv[1])
    print(run(perm, 2400000), flush=True)
