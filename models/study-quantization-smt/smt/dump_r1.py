"""Dump R1'' (assignment range under 'step normal or a == b') as SMT-LIB2 (QF_FP) for cvc5."""
import sys
sys.path.insert(0, __file__.rsplit('/', 1)[0])
from fpjava import *
from z3 import Solver
outdir = __file__.rsplit('/', 1)[0] + '/smt2/'
for bits in [int(b) for b in sys.argv[1:]]:
    nint = (1 << bits) - 1
    a, b, x = f32("a"), f32("b"), f32("x")
    step = fpDiv(rm, fpSub(rm, b, a), c32(float(nint)))
    xi = jmin(jmax(x, a), b)
    q = fpDiv(rm, fpSub(rm, xi, a), step)
    oor = And(Not(fpIsNaN(q)), Or(fpLT(q, c32(-0.5)), fpGEQ(q, c32(nint + 0.5))))
    s = Solver()
    s.add(finite(a), finite(b), finite(x), Or(fpIsNormal(step), fpEQ(a, b)), oor)
    open(outdir + f'r1n_bits{bits}.smt2', 'w').write('(set-logic QF_FP)\n' + s.sexpr() + '(check-sat)\n')
    print('wrote', bits)
