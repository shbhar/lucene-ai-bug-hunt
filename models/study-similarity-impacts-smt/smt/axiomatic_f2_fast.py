"""Faster counterexample search for AxiomaticF2* freq monotonicity (double level).

Completeness argument for restricting to consecutive floats: if score(f1) > score(f2) for some
f1 < f2, then along the finite chain f1 = g0 < g1 = nextUp(g0) < ... < gm = f2 there is an i with
score(g_i) > score(g_{i+1}). So it suffices to search pairs (f, nextUp(f)).

Usage: python -I axiomatic_f2_fast.py <mode> [fixed_s]
   mode in {double, float}
"""
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import time
from z3 import (Solver, FP, BitVec, sat, Or, And, set_param, fpLT, fpGT, fpGEQ, fpLEQ, fpEQ,
                fpToIEEEBV, fpBVToFP, Extract, ULT, simplify)
from fpjava import (F32, F64, f2d, d2f, add, sub, mul, div, dconst, fconst, jmax, pos_finite,
                    is_integral)

LENGTH_TABLE = [int(x) for x in open(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                   'length_table.txt')).read().strip().split(',')]


def score(freq_f, s_f, dl_d, avgfl_d, idf_d, boost_d):
    freq_d = f2d(freq_f)
    s_d = f2d(s_f)
    tfln = div(freq_d, add(add(freq_d, s_d), div(mul(s_d, dl_d), avgfl_d)))
    one = dconst(1.0)
    sc = sub(mul(mul(mul(one, one), tfln), idf_d), dconst(0.0))
    sc = mul(sc, boost_d)
    return jmax(dconst(0.0), sc)


def next_up_pos(f):
    """nextUp for a positive finite float: raw bits + 1."""
    bv = fpToIEEEBV(f)
    return fpBVToFP(bv + 1, F32)


def main():
    mode = sys.argv[1]
    set_param('timeout', 30 * 60 * 1000)
    s = Solver()
    fb = BitVec('fb', 32)
    f1 = fpBVToFP(fb, F32)
    f2 = fpBVToFP(fb + 1, F32)
    sp = FP('s', F32)
    dli = BitVec('dli', 8)
    dl = FP('dl', F64)
    avgfl = FP('avgfl', F64)
    s.add(pos_finite(f1), pos_finite(f2))
    if len(sys.argv) > 2:
        s.add(fpEQ(sp, fconst(float(sys.argv[2]))))
    s.add(fpGT(sp, fconst(0.0)), fpLEQ(sp, fconst(1.0)))
    s.add(Or([fpEQ(dl, dconst(float(v))) for v in LENGTH_TABLE[1:]]))
    # avgfl = sumTotalTermFreq / docCount >= 1, <= 2^49 (BaseSimilarityTestCase bounds)
    s.add(fpGEQ(avgfl, dconst(1.0)), fpLEQ(avgfl, dconst(2.0 ** 49)))
    idf = dconst(1.0)
    boost = dconst(1.0)
    sc1 = score(f1, sp, dl, avgfl, idf, boost)
    sc2 = score(f2, sp, dl, avgfl, idf, boost)
    if mode == 'double':
        s.add(fpGT(sc1, sc2))
    else:
        s.add(fpGT(d2f(sc1), d2f(sc2)))
    t0 = time.time()
    r = s.check()
    print('mode', mode, 'result', r, 'time %.1fs' % (time.time() - t0), flush=True)
    if r == sat:
        m = s.model()
        for name, x in [('f1', f1), ('f2', f2), ('s', sp), ('dl', dl), ('avgfl', avgfl),
                        ('score1', sc1), ('score2', sc2), ('fscore1', d2f(sc1)),
                        ('fscore2', d2f(sc2))]:
            v = m.eval(x, model_completion=True)
            print(name, '=', v, '| bits', m.eval(fpToIEEEBV(x), model_completion=True))


if False:
    main()


def dump(mode, path):
    """Write the same query as SMT-LIB2 for cvc5."""
    s = Solver()
    fb = BitVec('fb', 32)
    f1 = fpBVToFP(fb, F32)
    f2 = fpBVToFP(fb + 1, F32)
    sp = FP('s', F32)
    dl = FP('dl', F64)
    avgfl = FP('avgfl', F64)
    s.add(pos_finite(f1), pos_finite(f2))
    s.add(fpGT(sp, fconst(0.0)), fpLEQ(sp, fconst(1.0)))
    s.add(Or([fpEQ(dl, dconst(float(v))) for v in LENGTH_TABLE[1:]]))
    s.add(fpGEQ(avgfl, dconst(1.0)), fpLEQ(avgfl, dconst(2.0 ** 49)))
    sc1 = score(f1, sp, dl, avgfl, dconst(1.0), dconst(1.0))
    sc2 = score(f2, sp, dl, avgfl, dconst(1.0), dconst(1.0))
    s.add(fpGT(sc1, sc2) if mode == 'double' else fpGT(d2f(sc1), d2f(sc2)))
    with open(path, 'w') as fh:
        fh.write('(set-logic QF_FPBV)\n(set-option :produce-models true)\n')
        fh.write(s.to_smt2().replace('(check-sat)', ''))
        fh.write('(check-sat)\n(get-value (fb s dl avgfl))\n')


if __name__ == '__main__':
    if sys.argv[1] == 'dump':
        dump(sys.argv[2], sys.argv[3])
    else:
        main()
