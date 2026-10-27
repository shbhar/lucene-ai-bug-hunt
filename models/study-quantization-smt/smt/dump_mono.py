"""Dump the single-variable 'consecutive floats' monotonicity queries as SMT-LIB2 for cvc5."""
import sys
sys.path.insert(0, __file__.rsplit('/', 1)[0])
from fpjava import *
from z3 import Solver, fpToIEEEBV, fpBVToFP, Extract, BitVecVal
outdir = __file__.rsplit('/', 1)[0] + '/smt2/'
one, two, zero, mone = c32(1.0), c32(2.0), c32(0.0), c32(-1.0)
from z3 import BitVec
BV = BitVec("bv", 32)
def succ(v):
    # v is always fpBVToFP(BV); successor in IEEE order: positive bits+1, negative bits-1
    return fpBVToFP(If(Extract(31, 31, BV) == BitVecVal(1, 1), BV - 1, BV + 1), F32)
def smips(x): return If(fpLT(x, zero), fpDiv(rm, one, fpAdd(rm, one, fpMul(rm, mone, x))), fpAdd(rm, x, one))
def d2u(d): return fpDiv(rm, one, fpAdd(rm, one, d))
def q_euclid(s): return fpDiv(rm, one, fpAdd(rm, one, jmax(s, zero)))
def q_dot(s): return fpDiv(rm, fpAdd(rm, one, jclamp(s, mone, one)), two)
v = fpBVToFP(BV, F32)
base = [Not(fpIsNaN(v)), Not(And(fpIsInf(v), fpIsPositive(v))), Not(And(fpIsZero(v), fpIsNegative(v)))]
qs = {
 'mono_mip': base + [Not(fpLEQ(smips(v), smips(succ(v))))],
 'mono_euc': base + [fpGEQ(v, zero), Not(fpGEQ(d2u(v), d2u(succ(v))))],
 'mono_qeuc': base + [Not(fpGEQ(q_euclid(v), q_euclid(succ(v))))],
 'mono_qdot': base + [Not(fpLEQ(q_dot(v), q_dot(succ(v))))],
 # decomposition lemma for the hard case: 1/u is non-increasing on consecutive u >= 1
 'mono_recip': base + [fpGEQ(v, one), Not(fpGEQ(fpDiv(rm, one, v), fpDiv(rm, one, succ(v))))],
}
for name, cs in qs.items():
    s = Solver(); s.add(*cs)
    open(outdir + name + '.smt2', 'w').write('(set-logic QF_BVFP)\n' + s.sexpr() + '(check-sat)\n')
    print('wrote', name)
