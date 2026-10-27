"""Stage 2 for AxiomaticF2LOG: given a double-level violating pair (f1, nextUp(f1)) and fixed stats
(so tfln(f1) > tfln(f2) are fixed doubles and idf = Math.log((N + 1.0) / n) is a fixed double whose
value is taken from the real JVM), emit an SMT-LIB QF_FPBV query asking for a float boost in
(0, 2^31] such that the SimScorer.score contract fails AFTER the (float) cast:
      (float) score(f1) > (float) score(nextUp(f1)).

Usage: python -I axiomatic_f2_stage2.py <out.smt2> <f1_bits_int> <s_float> <dl> <avgfl_hex> <idf_hex>
"""
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from z3 import Solver, BitVec, BitVecVal, fpBVToFP, fpGT, fpLEQ
from fpjava import F32, F64, f2d, d2f, add, sub, mul, div, dconst, fconst, jmax, pos_finite


def score(freq_f, s_d, dl_d, avgfl_d, idf_d, boost_d):
    freq_d = f2d(freq_f)
    tfln = div(freq_d, add(add(freq_d, s_d), div(mul(s_d, dl_d), avgfl_d)))
    one = dconst(1.0)
    sc = sub(mul(mul(mul(one, one), tfln), idf_d), dconst(0.0))
    sc = mul(sc, boost_d)
    return jmax(dconst(0.0), sc)


def main():
    out, fbits, s_f, dl, avg_hex, idf_hex = sys.argv[1:7]
    fbits = int(fbits)
    f1 = fpBVToFP(BitVecVal(fbits, 32), F32)
    f2 = fpBVToFP(BitVecVal(fbits + 1, 32), F32)
    sd = dconst(float(s_f))
    dl = dconst(float(dl))
    avg = dconst(float.fromhex(avg_hex))
    idf = dconst(float.fromhex(idf_hex))
    bb = BitVec('bb', 32)
    boost = fpBVToFP(bb, F32)
    s = Solver()
    s.add(pos_finite(boost), fpLEQ(boost, fconst(2.0 ** 31)))
    s.add(fpGT(d2f(score(f1, sd, dl, avg, idf, f2d(boost))),
               d2f(score(f2, sd, dl, avg, idf, f2d(boost)))))
    with open(out, 'w') as fh:
        fh.write('(set-logic QF_FPBV)\n(set-option :produce-models true)\n')
        fh.write(s.to_smt2().replace('(check-sat)', ''))
        fh.write('(check-sat)\n(get-value (bb))\n')


if __name__ == '__main__':
    main()
