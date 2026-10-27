"""Z3 QF_FP proofs (all float32 inputs, bit-precise IEEE-754 RNE) for the float-only SimScorers.

Each check asserts the NEGATION of a property; `unsat` = the property holds for every input in the
stated domain. Domains are taken from the constructors' argument validation and from the
callers (see README):

BM25Similarity (BM25Similarity.java):
  cache[i] = 1f / (k1 * ((1 - b) + b * LENGTH_TABLE[i] / avgdl))           [float ops]
  doScore(freq, normInverse) = weight - weight / (1f + freq * normInverse)  [float ops]
  weight = boost * idf
StableTflSimilarity (StableTflSimilarity.java):
  score = weight - weight / (1 + freq / k1)  with weight = cache[norm]   [float ops]
ClassicSimilarity / TFIDFSimilarity:
  score = ((float) Math.sqrt(freq) * (boost * idf)) * normTable[norm]
  normTable[i] = (float) (1.0 / Math.sqrt(LENGTH_TABLE[i]))  (int argument)
"""
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import time
from z3 import (Solver, FP, sat, unsat, Or, And, Not, set_param, fpLT, fpGT, fpGEQ, fpLEQ, fpEQ,
                fpIsNaN, fpIsInf, fpToIEEEBV, fpBVToFP, fpPlusInfinity, fpIsZero, BitVec, ULE,
                fpIsNegative)
from fpjava import (F32, F64, f2d, d2f, add, sub, mul, div, sqrt, dconst, fconst, finite,
                    pos_finite, nonneg_finite)

LENGTH_TABLE = [int(x) for x in open(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                   'length_table.txt')).read().strip().split(',')]
RESULTS = []


SOLVER = os.environ.get('SOLVER', 'cvc5')


def check(name, s, timeout_s=1800):
    if SOLVER == 'cvc5':
        from smtrun import cvc5_check
        first, out, dt, fname = cvc5_check(s, name, timeout_s=timeout_s)
        verdict = {'unsat': 'PROVED', 'sat': 'COUNTEREXAMPLE'}.get(first, 'UNKNOWN(%s)' % first)
        print('%-78s %-14s %7.1fs  [cvc5 %s]' % (name, verdict, dt, os.path.basename(fname)),
              flush=True)
    else:
        s.set('timeout', timeout_s * 1000)
        t0 = time.time()
        r = s.check()
        dt = time.time() - t0
        verdict = {unsat: 'PROVED', sat: 'COUNTEREXAMPLE'}.get(r, 'UNKNOWN')
        print('%-78s %-14s %7.1fs  [z3]' % (name, verdict, dt), flush=True)
        if r == sat:
            print('    model:', s.model())
    RESULTS.append((name, verdict, dt))
    return verdict


_ctr = [0]


def fresh_pos_pair():
    """a float f given by free raw bits, and f2 = nextUp(f) (raw bits + 1) for positive f."""
    _ctr[0] += 1
    b = BitVec('fbits%d' % _ctr[0], 32)
    return fpBVToFP(b, F32), fpBVToFP(b + 1, F32)


def nonneg_or_inf(x):
    return And(Not(fpIsNaN(x)), fpGEQ(x, fconst(0.0)))


def bm25_doscore(freq, ni, weight):
    return sub(weight, div(weight, add(fconst(1.0), mul(freq, ni))))


def bm25_cache(k1, b, L, avgdl):
    one = fconst(1.0)
    return div(one, mul(k1, add(sub(one, b), div(mul(b, L), avgdl))))


def bm25():
    f, f2 = fresh_pos_pair()
    w = FP('w', F32)
    ni, ni2 = FP('ni', F32), FP('ni2', F32)
    base = [pos_finite(f), pos_finite(f2), nonneg_finite(w), nonneg_or_inf(ni)]

    s = Solver(); s.add(base)
    s.add(fpGT(bm25_doscore(f, ni, w), bm25_doscore(f2, ni, w)))
    check('BM25 doScore non-decreasing in freq (all f, w>=0 finite, ni in [0,+inf])', s)

    s = Solver(); s.add(*base, nonneg_or_inf(ni2), fpLT(ni, ni2))
    s.add(fpGT(bm25_doscore(f, ni, w), bm25_doscore(f, ni2, w)))
    check('BM25 doScore non-decreasing in normInverse', s)

    s = Solver(); s.add(base)
    sc = bm25_doscore(f, ni, w)
    s.add(Or(Not(finite(sc)), fpLT(sc, fconst(0.0)), fpGT(sc, w)))
    check('BM25 doScore finite, >= 0 and <= weight', s)

    # BM25 normInverse cache: non-increasing in the decoded length, never NaN, >= 0
    k1, b, avgdl = FP('k1', F32), FP('b', F32), FP('avgdl', F32)
    L1, L2 = FP('L1', F32), FP('L2', F32)
    dom = [nonneg_finite(k1), fpGEQ(b, fconst(0.0)), fpLEQ(b, fconst(1.0)),
           fpGEQ(avgdl, fconst(1.0)), finite(avgdl)]
    tab = [fconst(float(v)) for v in LENGTH_TABLE[1:]]
    s = Solver(); s.add(*dom, Or([fpEQ(L1, t) for t in tab]), Or([fpEQ(L2, t) for t in tab]),
                        fpLT(L1, L2))
    s.add(fpLT(bm25_cache(k1, b, L1, avgdl), bm25_cache(k1, b, L2, avgdl)))
    check('BM25 cache[i] non-increasing in LENGTH_TABLE[i] (k1>=0,b in[0,1],avgdl>=1)', s)

    s = Solver(); s.add(*dom, Or([fpEQ(L1, t) for t in tab]))
    c = bm25_cache(k1, b, L1, avgdl)
    s.add(Not(nonneg_or_inf(c)))
    check('BM25 cache[i] is never NaN and >= 0 (may be +inf when k1 == 0)', s)

    # weight = boost * idf with boost in [0, 2^31] (BaseSimilarityTestCase) and idf in [0, 64]
    boost, idf = FP('boost', F32), FP('idf', F32)
    s = Solver(); s.add(fpGEQ(boost, fconst(0.0)), fpLEQ(boost, fconst(2.0 ** 31)),
                        fpGEQ(idf, fconst(0.0)), fpLEQ(idf, fconst(64.0)))
    s.add(Not(nonneg_finite(mul(boost, idf))))
    check('BM25 weight = boost*idf finite >= 0 for boost<=2^31, idf<=64', s)


def stabletfl():
    f, f2 = fresh_pos_pair()
    w, k1 = FP('w', F32), FP('k1', F32)

    def sc(fr):
        return sub(w, div(w, add(fconst(1.0), div(fr, k1))))
    base = [pos_finite(f), pos_finite(f2), nonneg_finite(w), nonneg_finite(k1)]
    s = Solver(); s.add(*base, fpGT(sc(f), sc(f2)))
    check('StableTfl score non-decreasing in freq (all f, w>=0, k1>=0 finite)', s)
    s = Solver(); s.add(base)
    v = sc(f)
    s.add(Or(Not(finite(v)), fpLT(v, fconst(0.0)), fpGT(v, w)))
    check('StableTfl score finite, >= 0, <= weight', s)
    w2 = FP('w2', F32)
    s = Solver(); s.add(*base, nonneg_finite(w2), fpLT(w2, w))
    s.add(fpGT(sub(w2, div(w2, add(fconst(1.0), div(f, k1)))), sc(f)))
    check('StableTfl score non-decreasing in weight (= cache[norm])', s)


def classic():
    f, f2 = fresh_pos_pair()
    qw, nt, nt2 = FP('qw', F32), FP('nt', F32), FP('nt2', F32)

    def sc(fr, n):
        tf = d2f(sqrt(f2d(fr)))
        return mul(mul(tf, qw), n)
    base = [pos_finite(f), pos_finite(f2), nonneg_finite(qw), nonneg_finite(nt)]
    s = Solver(); s.add(*base, fpGT(sc(f, nt), sc(f2, nt)))
    check('Classic/TFIDF score non-decreasing in freq', s)
    s = Solver(); s.add(*base, nonneg_finite(nt2), fpLT(nt, nt2), fpGT(sc(f, nt), sc(f, nt2)))
    check('Classic/TFIDF score non-decreasing in normTable value', s)
    # normTable[i] = (float)(1.0 / Math.sqrt(LENGTH_TABLE[i])) for the 255 legal norms is a
    # finite fact; it is checked by enumeration on the real class in java/TableFacts.java
    # weight finite
    boost, idf = FP('boost', F32), FP('idf', F32)
    s = Solver(); s.add(fpGEQ(boost, fconst(0.0)), fpLEQ(boost, fconst(2.0 ** 31)),
                        fpGEQ(idf, fconst(0.0)), fpLEQ(idf, fconst(64.0)), pos_finite(f),
                        fpLEQ(f, fconst(2.0 ** 31)), nonneg_finite(nt), fpLEQ(nt, fconst(1.0)))
    v = sc(f, nt)
    s.add(Not(nonneg_finite(mul(mul(d2f(sqrt(f2d(f))), mul(boost, idf)), nt))))
    check('Classic score finite >= 0 for freq,boost <= 2^31, idf <= 64, norm <= 1', s)


if __name__ == '__main__':
    which = sys.argv[1:] or ['bm25', 'stabletfl', 'classic']
    for w in which:
        globals()[w]()
    print('SUMMARY')
    for r in RESULTS:
        print('  %-78s %-14s %7.1fs' % r)
