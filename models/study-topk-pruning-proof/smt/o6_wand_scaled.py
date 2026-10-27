"""O6: WANDScorer rejection test (WANDScorer.java:333-345, scaling 69-120, 186-193, 466-476).
Obligation: if scaledLeadScore + tailMaxScore < scaledMinCompetitiveScore then every completion of the
doc with tail scores t_j <= M_j yields (float)score < mcs, where score() = (float) of leadScore with the
matching tail scores added (advanceAllTail, 538-550; score 553-565).
WLOG (binary scale invariance, no subnormal/overflow): the float max-score sum G lies in [1,2) so
scalingFactor = 23 (scalingFactor() line 69-83)."""
import sys, os, time, z3
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fpcommon import *
TWO23 = dconst(2.0 ** 23); CAP = dconst(float((1 << 24) - 1))

def scale_max(fv):      # scaleMaxScore: ceil(scalb((double)f, 23)) capped at 2^24-1
    sc = dmul(widen(fv), TWO23)
    return z3.If(z3.fpGT(sc, CAP), CAP, z3.fpRoundToIntegral(z3.RTP(), sc))

def scale_min(fv):      # scaleMinScore: floor(scalb((double)f, 23))
    return z3.fpRoundToIntegral(z3.RTN(), dmul(widen(fv), TWO23))

def run(nlead, ntail, timeout_ms):
    L = [f32('l%d' % i) for i in range(nlead)]
    M = [f32('M%d' % i) for i in range(ntail)]; Tt = [f32('t%d' % i) for i in range(ntail)]
    g = [f32('g%d' % i) for i in range(nlead + ntail)]; mcs = f32('mcs')
    s = z3.Solver(); s.set('timeout', timeout_ms)
    for v in L + M + Tt + g: s.add(nonneg_finite(v))
    s.add(strictly_pos_finite(mcs))
    for a, b in zip(L + M, g): s.add(z3.fpLEQ(a, b))           # local max <= global max
    for t, mm in zip(Tt, M): s.add(z3.fpLEQ(t, mm))            # tail score <= tail window max
    G = narrow(sum_upper_bound(seqsum(g), nlead + ntail))       # constructor line 186-192
    s.add(z3.fpGEQ(G, fconst(1.0)), z3.fpLT(G, fconst(2.0)))
    lead = seqsum(L)                                            # moveToNextCandidate + addLead
    scaled_lead = scale_max(narrow(sum_upper_bound(lead, 24)))  # line 335-337 (FLOAT_MANTISSA_BITS=24)
    tail_max = dconst(0.0)
    for mm in M: tail_max = dadd(tail_max, scale_max(mm))       # exact: integers < 2^26
    s.add(z3.fpLT(dadd(scaled_lead, tail_max), scale_min(mcs)))  # rejection, line 342
    final = lead
    for t in reversed(Tt): final = dadd(final, widen(t))         # advanceAllTail order i = tailSize-1..0
    s.add(z3.fpGEQ(narrow(final), mcs))                          # but the doc is competitive
    t0 = time.time(); r = s.check()
    res = {'nlead': nlead, 'ntail': ntail, 'result': str(r), 'seconds': round(time.time() - t0, 1)}
    if r == z3.sat:
        m = s.model(); res['model'] = {str(v): hexf(m, v)[0] for v in L + M + Tt + [mcs]}
    return res

if __name__ == '__main__':
    nl, nt = int(sys.argv[1]), int(sys.argv[2])
    print(run(nl, nt, 2400000), flush=True)
