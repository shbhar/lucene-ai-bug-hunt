"""SMT (Z3 QF_FP) search for a freq-monotonicity violation in AxiomaticF2EXP / AxiomaticF2LOG.

Java source (lucene/core/.../similarities/AxiomaticF2EXP.java, tfln):
    return freq / (freq + this.s + this.s * docLen / stats.getAvgFieldLength());
Axiomatic.score:
    double score = tf * ln * tfln * idf - gamma;  // tf = ln = 1.0, gamma = 0.0 for F2*
    score *= stats.boost;
    return Math.max(0, score);
SimilarityBase.BasicSimScorer.score:
    return (float) SimilarityBase.this.score(stats, freq, getLengthValue(norm));
where freq is the float passed to SimScorer.score widened to double, and docLen is the float
LENGTH_TABLE[norm & 0xFF] widened to double.

The free variable `idf` stands for Math.pow((N + 1.0) / df, k) (F2EXP) or
Math.log((N + 1.0) / df) (F2LOG); for F2EXP with k == 0 it is exactly 1.0 (Math.pow(x, 0) == 1.0
is required by the Math.pow javadoc), which we use for the concrete reproduction.

Usage: python -I axiomatic_f2.py [mode]
  mode = double   : violation of monotonicity of the double value before the (float) cast
  mode = float    : violation of SimScorer.score (after the (float) cast), idf=1, boost=1
  mode = floatfree: violation after the cast, idf and boost free (boost float in [0, 2^31])
"""
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import time
from z3 import (Solver, FP, sat, unsat, Or, And, set_param, fpToReal, fpLT, fpGT, fpGEQ, fpLEQ,
                fpEQ, BitVecVal, fpBVToFP, Not, fpIsNaN)
from fpjava import (F32, F64, f2d, d2f, add, mul, div, dconst, fconst, jmax, finite, pos_finite,
                    is_integral)

LENGTH_TABLE = [int(x) for x in open(__file__.rsplit('/', 1)[0] + '/length_table.txt').read()
                .strip().split(',')]


def tfln(freq_d, s_f, dl_d, avgfl_d):
    s_d = f2d(s_f)
    # freq + this.s + this.s * docLen / avgfl  ==  (freq + s) + ((s * docLen) / avgfl)
    denom = add(add(freq_d, s_d), div(mul(s_d, dl_d), avgfl_d))
    return div(freq_d, denom)


def axiomatic_score(freq_f, s_f, dl_d, avgfl_d, idf_d, boost_d):
    freq_d = f2d(freq_f)
    one = dconst(1.0)
    score = mul(mul(mul(one, one), tfln(freq_d, s_f, dl_d, avgfl_d)), idf_d)
    score = add(score, dconst(-0.0)) if False else score  # "- gamma" with gamma = 0.0 below
    from fpjava import sub
    score = sub(score, dconst(0.0))
    score = mul(score, boost_d)
    return jmax(dconst(0.0), score)


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else 'double'
    set_param('timeout', 40 * 60 * 1000)
    s = Solver()
    f1, f2 = FP('f1', F32), FP('f2', F32)
    sp = FP('s', F32)
    dl = FP('dl', F64)
    T, D = FP('T', F64), FP('D', F64)  # sumTotalTermFreq, docCount as exact integers
    avgfl = div(T, D)
    s.add(pos_finite(f1), pos_finite(f2), fpLT(f1, f2))
    s.add(fpGEQ(sp, fconst(0.0)), fpLEQ(sp, fconst(1.0)))
    s.add(Or([fpEQ(dl, dconst(float(v))) for v in LENGTH_TABLE[1:]]))
    s.add(is_integral(T), is_integral(D), fpGEQ(D, dconst(1.0)), fpLEQ(D, dconst(2.0 ** 31)),
          fpGEQ(T, D), fpLEQ(T, dconst(2.0 ** 49)))
    if mode == 'floatfree':
        idf = FP('idf', F64)
        bf = FP('boost', F32)
        s.add(pos_finite(idf), fpLEQ(idf, dconst(64.0)))
        s.add(pos_finite(bf), fpLEQ(bf, fconst(2.0 ** 31)))
        boost = f2d(bf)
    else:
        idf = dconst(1.0)
        boost = dconst(1.0)
    sc1 = axiomatic_score(f1, sp, dl, avgfl, idf, boost)
    sc2 = axiomatic_score(f2, sp, dl, avgfl, idf, boost)
    if mode == 'double':
        s.add(fpGT(sc1, sc2))
    else:
        s.add(fpGT(d2f(sc1), d2f(sc2)))
    t0 = time.time()
    r = s.check()
    dt = time.time() - t0
    print('mode', mode, 'result', r, 'time %.1fs' % dt)
    if r == sat:
        m = s.model()

        def fv(x):
            v = m.eval(x, model_completion=True)
            return v

        import struct
        def f32bits(v):
            return v.as_string() if hasattr(v, 'as_string') else str(v)
        for name, x in [('f1', f1), ('f2', f2), ('s', sp), ('dl', dl), ('T', T), ('D', D),
                        ('avgfl', avgfl), ('idf', idf), ('boost', boost), ('score1', sc1),
                        ('score2', sc2), ('fscore1', d2f(sc1)), ('fscore2', d2f(sc2))]:
            v = fv(x)
            try:
                print(name, '=', repr(float(fpToReal(v).as_fraction()) if False else v), '|',
                      v.sexpr()[:200])
            except Exception as e:  # noqa
                print(name, '=', v)


if __name__ == '__main__':
    main()
