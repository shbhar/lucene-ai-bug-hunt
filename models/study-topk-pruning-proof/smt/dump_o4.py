import sys, os, z3
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fpcommon import *
n = int(sys.argv[1]); out = sys.argv[2]
x = [f32('x%d' % i) for i in range(n)]; m = [f32('m%d' % i) for i in range(n)]
s = z3.Solver()
for a, b in zip(x, m): s.add(nonneg_finite(a), nonneg_finite(b), z3.fpLEQ(a, b))
s.add(z3.fpGT(narrow(seqsum(x)), narrow(seqsum(m))))
dump_smt2(s, out)
