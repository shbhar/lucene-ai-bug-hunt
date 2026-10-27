"""O3: MaxScoreBulkScorer.partitionScorers single-essential 'required clause' test (MaxScoreBulkScorer.java:524-548).
Model: 4 clauses, allScorers = [T, A, J | E] (3 non-essential, 1 essential E).
Obligation: if (float)(maxRequiredScore + maxScoreSums[fRS-2]) < mcs   (line 540, NO sumUpperBound)
            then every doc NOT matching clause J has (float)score < mcs.
We search for a counterexample where the doc (matches E,A,T, not J) has pruned-path score >= mcs,
optionally also forcing every other summation order (exhaustive scorers) to be >= mcs.
"""
import sys, time, itertools, z3
sys.path.insert(0, __import__('os').path.dirname(__file__))
from fpcommon import *

def solve(require_orders, s0_from_EA=True, timeout_ms=600000):
    E, A, T, J, mcs = f32('E'), f32('A'), f32('T'), f32('J'), f32('mcs')
    s = z3.Solver(); s.set('timeout', timeout_ms)
    for x in (E, A, T, J, mcs): s.add(strictly_pos_finite(x))
    lt = lambda d: z3.fpLT(narrow(d), mcs)
    ge = lambda d: z3.fpGEQ(narrow(d), mcs)
    # partitionScorers, scratch order T, A, E, J  (MaxScoreBulkScorer.java:494-511)
    mss0 = dadd(dconst(0.0), widen(T))
    s.add(lt(sum_upper_bound(mss0, 1)))
    mss1 = dadd(mss0, widen(A))
    s.add(lt(sum_upper_bound(mss1, 2)))
    s.add(ge(sum_upper_bound(dadd(mss1, widen(E)), 3)))      # E does not fit -> essential
    mss2 = dadd(mss1, widen(J))
    s.add(lt(sum_upper_bound(mss2, 3)))                       # J fits -> non-essential, index 2
    # single-essential required-clause loop, first iteration (line 536-541): J becomes required
    s.add(lt(dadd(widen(E), mss1)))
    # the doc matches E, A, T but not J.  Pruned-path order: essential first, then i=1 (A), i=0 (T)
    pruned = seqsum([E, A, T])
    s.add(ge(pruned))
    V = {'E': E, 'A': A, 'T': T}
    for order in require_orders:
        s.add(ge(seqsum([V[c] for c in order])))
    if s0_from_EA:  # a doc matching only E and A sets top score s0, mcs = Math.nextUp(s0)
        s.add(mcs == next_up(narrow(seqsum([E, A]))))
    t0 = time.time(); r = s.check(); dt = time.time() - t0
    out = {'result': str(r), 'seconds': round(dt, 2)}
    if r == z3.sat:
        m = s.model()
        for nm, x in (('E', E), ('A', A), ('T', T), ('J', J), ('mcs', mcs)):
            out[nm] = hexf(m, x)
    return out

if __name__ == '__main__':
    print('--- O3a: counterexample using only the pruned-path summation order (E,A,T)')
    print(solve([]), flush=True)
    others = [('E','T','A'), ('T','E','A'), ('A','E','T')]
    print('--- O3b: additionally orders', others, 'must be >= mcs (exhaustive scorers agree the doc is competitive)')
    print(solve(others), flush=True)
    print('--- O3c: ALL 6 orders >= mcs (expected UNSAT: (T,A,E) is the bound order, monotonicity)')
    print(solve(list(itertools.permutations('EAT'))), flush=True)
