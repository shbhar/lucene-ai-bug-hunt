"""O1: soundness of MathUtil.sumUpperBound (MathUtil.java:177-194) as used for pruning.
Claim(k): for all non-negative finite floats m_0..m_{k-1} and every permutation sigma,
    seqsum_double(m[sigma]) <= sumUpperBound(seqsum_double(m), k)            (double comparison)
By monotonicity of RNE addition (proved in O4) this implies that any doc whose k clause scores
x_i <= m_i are summed in ANY order has (float)score <= (float)sumUpperBound(...), which is what
MaxScoreBulkScorer.partitionScorers (line 499-502), WANDScorer.getMaxScore (591),
DisjunctionSumScorer.getMaxScore and WANDScorer's constructor need.
k=3: the 6 orders give 3 distinct sums (first addition commutes); one query per pair suffices by renaming.
k=4: one query with a symbolic permutation (selector variables)."""
import sys, os, time, itertools, z3
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fpcommon import *

def check(k, timeout_ms):
    m = [f32('m%d' % i) for i in range(k)]
    s = z3.Solver(); s.set('timeout', timeout_ms)
    for x in m: s.add(nonneg_finite(x))
    # symbolic permutation p[i] in [0,k), all distinct
    p = [z3.Int('p%d' % i) for i in range(k)]
    s.add(z3.Distinct(*p)); s.add(*[z3.And(pi >= 0, pi < k) for pi in p])
    def sel(pi):
        e = m[k - 1]
        for j in range(k - 2, -1, -1):
            e = z3.If(pi == j, m[j], e)
        return e
    doc = seqsum([sel(pi) for pi in p])
    bound = sum_upper_bound(seqsum(m), k)
    s.add(z3.fpGT(doc, bound))           # counterexample to the claim
    t0 = time.time(); r = s.check()
    res = {'k': k, 'result': str(r), 'seconds': round(time.time() - t0, 1)}
    if r == z3.sat:
        mm = s.model(); res['model'] = [hexf(mm, x)[0] for x in m] + [str(mm.eval(pi)) for pi in p]
    return res

def check_float_level_without_ub(k, timeout_ms):
    """Control: WITHOUT sumUpperBound, does reordering change the FLOAT result? (expected SAT for k>=3)"""
    m = [f32('m%d' % i) for i in range(k)]
    s = z3.Solver(); s.set('timeout', timeout_ms)
    for x in m: s.add(nonneg_finite(x))
    a = narrow(seqsum(m)); b = narrow(seqsum([m[1], m[2], m[0]] + m[3:]))
    s.add(z3.fpGT(a, b))
    t0 = time.time(); r = s.check()
    res = {'k': k, 'control_without_ub': str(r), 'seconds': round(time.time() - t0, 1)}
    if r == z3.sat:
        mm = s.model(); res['model'] = [hexf(mm, x)[0] for x in m]
    return res

if __name__ == '__main__':
    ks = [int(a) for a in sys.argv[1:]] or [3]
    for k in ks:
        print(check_float_level_without_ub(k, 600000), flush=True)
        print(check(k, 2400000), flush=True)
