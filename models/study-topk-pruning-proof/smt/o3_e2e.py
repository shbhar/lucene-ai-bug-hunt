"""O3f: counterexample to MaxScoreBulkScorer.java:540 that ALSO flips the exhaustive (COMPLETE/BooleanScorer)
top-1.  Measured (OrderProbe, junit/src/Explore4.java): BooleanScorer sums the target doc in order (T,E,A)
for 3 of 4 clause orders; MaxScoreBulkScorer without pruning sums (E,A,T).
Cost feasibility for the scratch order T,A,E,J used by partitionScorers (sort by max/cost, line 487-493)
with cost(T)=cost(J)=1 and cost(A)=cost(E)=C>=1101: T <= A/2048, A <= E, J >= E/1024."""
import sys, os, time, z3
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fpcommon import *

E, A, T, J, mcs = f32('E'), f32('A'), f32('T'), f32('J'), f32('mcs')
s = z3.Solver(); s.set('timeout', 2400000)
for x in (E, A, T, J, mcs): s.add(strictly_pos_finite(x))
s.add(z3.fpGEQ(mcs, fconst(1.0)), z3.fpLT(mcs, fconst(2.0)))
s.add(z3.fpGEQ(T, fconst(2.0 ** -100)))
s.add(z3.fpLEQ(T, z3.fpDiv(RNE, A, fconst(2048.0))), z3.fpLEQ(A, E), z3.fpGEQ(J, z3.fpDiv(RNE, E, fconst(1024.0))))
lt = lambda d: z3.fpLT(narrow(d), mcs); ge = lambda d: z3.fpGEQ(narrow(d), mcs)
mss0 = dadd(dconst(0.0), widen(T)); s.add(lt(sum_upper_bound(mss0, 1)))
mss1 = dadd(mss0, widen(A)); s.add(lt(sum_upper_bound(mss1, 2)))
s.add(ge(sum_upper_bound(dadd(mss1, widen(E)), 3)))
mss2 = dadd(mss1, widen(J)); s.add(lt(sum_upper_bound(mss2, 3)))
s.add(lt(dadd(widen(E), mss1)))                    # line 540: J becomes required
s.add(ge(seqsum([E, A, T])))                        # MaxScoreBulkScorer (unpruned) order
s.add(ge(seqsum([T, E, A])))                        # BooleanScorer order
s.add(mcs == next_up(narrow(seqsum([E, A]))))      # doc0 = {E,A}
t0 = time.time(); r = s.check(); out = {'result': str(r), 'seconds': round(time.time() - t0, 2)}
if r == z3.sat:
    m = s.model()
    for nm, x in (('E', E), ('A', A), ('T', T), ('J', J), ('mcs', mcs)): out[nm] = hexf(m, x)
print(out, flush=True)
