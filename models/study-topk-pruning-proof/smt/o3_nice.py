"""O3d: same obligation as o3_required_clause.py O3b, but with 'human-sized' values
(E in [0.5,1), A in [2^-4,0.5], T >= 2^-70, J in [0.25,0.5], mcs in [1,2)) so that the repro uses plain boosts."""
import sys, os, time, z3
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fpcommon import *
import o3_required_clause as o3

def solve_nice(orders, timeout_ms=1800000):
    E, A, T, J, mcs = f32('E'), f32('A'), f32('T'), f32('J'), f32('mcs')
    s = z3.Solver(); s.set('timeout', timeout_ms)
    for x in (E, A, T, J, mcs): s.add(strictly_pos_finite(x))
    rng = lambda x, lo, hi: [z3.fpGEQ(x, fconst(lo)), z3.fpLT(x, fconst(hi))]
    s.add(*rng(E, 0.5, 1.0)); s.add(*rng(A, 2.0**-4, 0.5)); s.add(z3.fpGEQ(T, fconst(2.0**-70)))
    s.add(*rng(J, 0.25, 0.5)); s.add(*rng(mcs, 1.0, 2.0))
    lt = lambda d: z3.fpLT(narrow(d), mcs); ge = lambda d: z3.fpGEQ(narrow(d), mcs)
    mss0 = dadd(dconst(0.0), widen(T)); s.add(lt(sum_upper_bound(mss0, 1)))
    mss1 = dadd(mss0, widen(A)); s.add(lt(sum_upper_bound(mss1, 2)))
    s.add(ge(sum_upper_bound(dadd(mss1, widen(E)), 3)))
    mss2 = dadd(mss1, widen(J)); s.add(lt(sum_upper_bound(mss2, 3)))
    s.add(lt(dadd(widen(E), mss1)))                      # MaxScoreBulkScorer.java:540 -> J required
    s.add(ge(seqsum([E, A, T])))                          # pruned-path order
    V = {'E': E, 'A': A, 'T': T}
    for o in orders: s.add(ge(seqsum([V[c] for c in o])))
    s.add(mcs == next_up(narrow(seqsum([E, A]))))        # doc0 = {E,A}, mcs = nextUp(score(doc0))
    t0 = time.time(); r = s.check(); out = {'result': str(r), 'seconds': round(time.time() - t0, 2)}
    if r == z3.sat:
        m = s.model()
        for nm, x in (('E', E), ('A', A), ('T', T), ('J', J), ('mcs', mcs)): out[nm] = hexf(m, x)
    return out

if __name__ == '__main__':
    print('--- O3d (nice ranges, pruned order only)'); print(solve_nice([]), flush=True)
    print('--- O3e (nice ranges + orders (E,T,A),(T,E,A),(A,E,T))')
    print(solve_nice([('E','T','A'), ('T','E','A'), ('A','E','T')]), flush=True)
