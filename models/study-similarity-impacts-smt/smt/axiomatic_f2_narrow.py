"""Narrowed SMT queries for AxiomaticF2* (constants fixed so the solver only searches freq bits,
then boost bits).

Stage 1 (double level): fixed s, docLen (= LENGTH_TABLE[norm]), avgfl = T/D; free float freq f.
   find f with tfln(f) > tfln(nextUp(f)).
Stage 2 (float level, the SimScorer.score contract): AxiomaticF2LOG with N=docCount, n=docFreq
   fixed, so idf = Math.log((N + 1.0) / n) is a fixed double (its value is supplied from the real
   JVM, not from an approximation); free float boost in [0, 2^31] and free freq f:
   find (f, boost) with (float) score(f) > (float) score(nextUp(f)).

Usage: python -I axiomatic_f2_narrow.py stage1 <s> <normByte> <T> <D>
       python -I axiomatic_f2_narrow.py stage2 <s> <normByte> <T> <D> <idf_hex_double> [f_bits]
"""
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import struct
import time
from z3 import (Solver, FP, BitVec, BitVecVal, sat, fpGT, fpGEQ, fpLEQ, fpBVToFP, fpToIEEEBV,
                set_param, FPVal)
from fpjava import (F32, F64, f2d, d2f, add, sub, mul, div, dconst, fconst, jmax, pos_finite)

LENGTH_TABLE = [int(x) for x in open(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                   'length_table.txt')).read().strip().split(',')]


def score(freq_f, s_d, dl_d, avgfl_d, idf_d, boost_d):
    freq_d = f2d(freq_f)
    tfln = div(freq_d, add(add(freq_d, s_d), div(mul(s_d, dl_d), avgfl_d)))
    one = dconst(1.0)
    sc = sub(mul(mul(mul(one, one), tfln), idf_d), dconst(0.0))
    sc = mul(sc, boost_d)
    return jmax(dconst(0.0), sc)


def bits_to_float(b):
    return struct.unpack('>f', struct.pack('>I', b))[0]


def main():
    stage = sys.argv[1]
    s_f = float(sys.argv[2])
    assert struct.unpack('>f', struct.pack('>f', s_f))[0] == s_f
    norm = int(sys.argv[3])
    T, D = int(sys.argv[4]), int(sys.argv[5])
    avgfl = T / D  # Python float division of ints is correctly rounded, same as (double)T/(double)D for T,D < 2^53
    dl = float(LENGTH_TABLE[norm])
    set_param('timeout', 20 * 60 * 1000)
    s = Solver()
    if len(sys.argv) > 7:
        fb = BitVecVal(int(sys.argv[7]), 32)
    else:
        fb = BitVec('fb', 32)
    f1 = fpBVToFP(fb, F32)
    f2 = fpBVToFP(fb + 1, F32)
    s.add(pos_finite(f1), pos_finite(f2))
    sd = dconst(s_f)
    if stage == 'stage1':
        sc1 = score(f1, sd, dconst(dl), dconst(avgfl), dconst(1.0), dconst(1.0))
        sc2 = score(f2, sd, dconst(dl), dconst(avgfl), dconst(1.0), dconst(1.0))
        s.add(fpGT(sc1, sc2))
        bb = None
    else:
        idf = float.fromhex(sys.argv[6])
        bb = BitVec('bb', 32)
        boost = fpBVToFP(bb, F32)
        s.add(pos_finite(boost), fpLEQ(boost, fconst(2.0 ** 31)))
        sc1 = score(f1, sd, dconst(dl), dconst(avgfl), dconst(idf), f2d(boost))
        sc2 = score(f2, sd, dconst(dl), dconst(avgfl), dconst(idf), f2d(boost))
        s.add(fpGT(d2f(sc1), d2f(sc2)))
    t0 = time.time()
    r = s.check()
    print('stage', stage, 's', s_f, 'norm', norm, 'dl', dl, 'T', T, 'D', D, 'avgfl', avgfl,
          'result', r, 'time %.1fs' % (time.time() - t0), flush=True)
    if r == sat:
        m = s.model()
        fbits = m.eval(fb, model_completion=True).as_long()
        print('f1_bits', fbits, 'f1', repr(bits_to_float(fbits)), 'f2', repr(bits_to_float(fbits + 1)))
        if bb is not None:
            bbits = m.eval(bb, model_completion=True).as_long()
            print('boost_bits', bbits, 'boost', repr(bits_to_float(bbits)))
        print('score1', m.eval(sc1), 'score2', m.eval(sc2))
        print('fscore1', m.eval(d2f(sc1)), 'fscore2', m.eval(d2f(sc2)))


if __name__ == '__main__':
    main()
