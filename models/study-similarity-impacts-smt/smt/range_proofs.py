"""Single-trace (non-relational) QF_FP range/NaN-freedom proofs for the float-only scorers, run
with cvc5. (The relational monotonicity queries time out after 30 min in cvc5 and 10 min in
Z3 even for a single float32 addition; monotonicity is instead established from the monotone
rounding theorem + direction typing, see README.)

Usage: python -I range_proofs.py <group>   group in {bm25, bm25cache, tfl, classic}
"""
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from z3 import FP, Solver, Or, And, Not, fpLEQ, fpGEQ, fpLT, fpGT, fpEQ, fpIsNaN, fpIsInf, BitVec, fpBVToFP
from fpjava import F32, F64, add, sub, mul, div, sqrt, f2d, d2f, fconst, dconst, finite, pos_finite, nonneg_finite
from smtrun import cvc5_check

LENGTH_TABLE = [int(x) for x in open(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                   'length_table.txt')).read().strip().split(',')]


def nn_or_inf(x):
    return And(Not(fpIsNaN(x)), fpGEQ(x, fconst(0.0)))


def check(name, hyps, bad):
    s = Solver()
    s.add(*hyps)
    s.add(bad)
    first, out, dt, fname = cvc5_check(s, 'range_' + name, timeout_s=1500)
    verdict = {'unsat': 'PROVED', 'sat': 'COUNTEREXAMPLE'}.get(first, 'UNKNOWN(%s)' % first.strip())
    print('%-80s %-14s %7.1fs' % (name, verdict, dt), flush=True)
    if first == 'sat':
        print(out[:800])


def bm25():
    f, w, ni = FP('f', F32), FP('w', F32), FP('ni', F32)
    sc = sub(w, div(w, add(fconst(1.0), mul(f, ni))))
    check('BM25 doScore(f,ni)=w-w/(1+f*ni): finite, >=0, <=w  [f>0 finite, w>=0 finite, ni in [0,inf]]',
          [pos_finite(f), nonneg_finite(w), nn_or_inf(ni)],
          Or(Not(finite(sc)), fpLT(sc, fconst(0.0)), fpGT(sc, w)))
    boost, idf = FP('boost', F32), FP('idf', F32)
    check('BM25 weight=boost*idf finite >=0  [boost in [0,2^31], idf in [0,64]]',
          [fpGEQ(boost, fconst(0.0)), fpLEQ(boost, fconst(2.0 ** 31)), fpGEQ(idf, fconst(0.0)),
           fpLEQ(idf, fconst(64.0))],
          Not(nonneg_finite(mul(boost, idf))))


def bm25cache():
    k1, b, avgdl, L = FP('k1', F32), FP('b', F32), FP('avgdl', F32), FP('L', F32)
    one = fconst(1.0)
    c = div(one, mul(k1, add(sub(one, b), div(mul(b, L), avgdl))))
    check('BM25 cache=1f/(k1*((1-b)+b*L/avgdl)) never NaN, >=0  [k1>=0 finite, b in [0,1], avgdl>=1 finite, L in LENGTH_TABLE[1..255]]',
          [nonneg_finite(k1), fpGEQ(b, fconst(0.0)), fpLEQ(b, one), fpGEQ(avgdl, one), finite(avgdl),
           Or([fpEQ(L, fconst(float(v))) for v in LENGTH_TABLE[1:]])],
          Not(nn_or_inf(c)))


def tfl():
    f, w, k1 = FP('f', F32), FP('w', F32), FP('k1', F32)
    sc = sub(w, div(w, add(fconst(1.0), div(f, k1))))
    check('StableTfl score=w-w/(1+f/k1): finite, >=0, <=w  [f>0 finite, w>=0 finite, k1>=0 finite]',
          [pos_finite(f), nonneg_finite(w), nonneg_finite(k1)],
          Or(Not(finite(sc)), fpLT(sc, fconst(0.0)), fpGT(sc, w)))


def classic():
    f, qw, nt = FP('f', F32), FP('qw', F32), FP('nt', F32)
    tf = d2f(sqrt(f2d(f)))
    sc = mul(mul(tf, qw), nt)
    check('Classic score=((float)sqrt(f)*qw)*norm finite >=0  [f in (0,2^31], qw in [0,2^37], norm in [0,1]]',
          [pos_finite(f), fpLEQ(f, fconst(2.0 ** 31)), fpGEQ(qw, fconst(0.0)), fpLEQ(qw, fconst(2.0 ** 37)),
           fpGEQ(nt, fconst(0.0)), fpLEQ(nt, fconst(1.0))],
          Not(nonneg_finite(sc)))
    check('Classic score not NaN for f = Float.MAX_VALUE (maxScore)  [qw in [0,2^37], norm in [0,1]]',
          [fpEQ(f, fconst(3.4028234663852886e38)), fpGEQ(qw, fconst(0.0)), fpLEQ(qw, fconst(2.0 ** 37)),
           fpGEQ(nt, fconst(0.0)), fpLEQ(nt, fconst(1.0))],
          fpIsNaN(sc))


if __name__ == '__main__':
    globals()[sys.argv[1]]()
