"""Per-operation IEEE-754 monotonicity lemmas, proved by cvc5 (QF_FP), for float32 (and float64 if
time permits). Composition of these lemmas (plus transitivity) proves the freq- and norm-
monotonicity of the float-only scorers (BM25 doScore, StableTfl score, Classic/TFIDF score),
because in those expressions every input occurs exactly once on each path (checked by hand and
by smt/models.py's direction typing). Each lemma is a 2-copy (relational) query over ONE op, so
the bit-blasted circuits are small.

Usage: python -I oplemmas.py <32|64>
"""
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import time
from z3 import FP, Solver, Or, And, Not, fpLEQ, fpGEQ, fpLT, fpGT, fpIsNaN, fpIsInf, FPVal, Float32, Float64
from fpjava import add, sub, mul, div, sqrt, f2d, d2f
from smtrun import cvc5_check

W = sys.argv[1] if len(sys.argv) > 1 else '32'
S = Float32() if W == '32' else Float64()


def z(v):
    return FPVal(v, S)


def notnan(x):
    return Not(fpIsNaN(x))


def nn(x):          # >= 0, possibly +inf
    return And(notnan(x), fpGEQ(x, z(0.0)))


def fin_nn(x):
    return And(nn(x), Not(fpIsInf(x)))


RESULTS = []


def lemma(name, hyps, concl, timeout_s=1500):
    s = Solver()
    s.add(*hyps)
    s.add(Not(concl))
    first, out, dt, fname = cvc5_check(s, 'lemma%s_%s' % (W, name), timeout_s=timeout_s)
    verdict = {'unsat': 'PROVED', 'sat': 'COUNTEREXAMPLE'}.get(first, 'UNKNOWN(%s)' % first)
    print('%-70s %-14s %7.1fs' % ('[fp%s] %s' % (W, name), verdict, dt), flush=True)
    if first == 'sat':
        print(out[:600])
    RESULTS.append((name, verdict, dt))


a, b, x1, x2 = FP('a', S), FP('b', S), FP('x1', S), FP('x2', S)
le = fpLEQ(x1, x2)

# L-ADD: x1 <= x2 (non-NaN), a non-NaN, no inf-inf  =>  fl(x1 + a) <= fl(x2 + a)
lemma('ADD  x1<=x2 => x1+a <= x2+a  (a,x finite or same-sign inf excluded)',
      [notnan(a), notnan(x1), notnan(x2), le, notnan(add(x1, a)), notnan(add(x2, a))],
      fpLEQ(add(x1, a), add(x2, a)))
# L-SUB2: x1 <= x2  =>  fl(a - x1) >= fl(a - x2)
lemma('SUB  x1<=x2 => a-x1 >= a-x2',
      [notnan(a), notnan(x1), notnan(x2), le, notnan(sub(a, x1)), notnan(sub(a, x2))],
      fpGEQ(sub(a, x1), sub(a, x2)))
# L-MUL: a >= 0, x1 <= x2, results not NaN  =>  fl(x1 * a) <= fl(x2 * a)
lemma('MUL  a>=0, x1<=x2 => x1*a <= x2*a',
      [nn(a), notnan(x1), notnan(x2), le, notnan(mul(x1, a)), notnan(mul(x2, a))],
      fpLEQ(mul(x1, a), mul(x2, a)))
# L-DIVN: a >= 0, 0 < x1 <= x2  =>  fl(a / x1) >= fl(a / x2)
lemma('DIVDEN a>=0, 0<x1<=x2 => a/x1 >= a/x2',
      [nn(a), fpGT(x1, z(0.0)), notnan(x2), le, notnan(div(a, x1)), notnan(div(a, x2))],
      fpGEQ(div(a, x1), div(a, x2)))
# L-DIVM: b > 0, x1 <= x2  =>  fl(x1 / b) <= fl(x2 / b)
lemma('DIVNUM b>0, x1<=x2 => x1/b <= x2/b',
      [fpGT(b, z(0.0)), notnan(x1), notnan(x2), le, notnan(div(x1, b)), notnan(div(x2, b))],
      fpLEQ(div(x1, b), div(x2, b)))
# L-SQRT: 0 <= x1 <= x2 => sqrt(x1) <= sqrt(x2)
lemma('SQRT 0<=x1<=x2 => sqrt x1 <= sqrt x2', [nn(x1), le], fpLEQ(sqrt(x1), sqrt(x2)))
if W == '64':
    # L-CAST: double -> float RNE is monotone
    lemma('D2F  x1<=x2 => (float)x1 <= (float)x2', [notnan(x1), notnan(x2), le],
          fpLEQ(d2f(x1), d2f(x2)))

# NaN-freedom side conditions of the BM25/StableTfl shape  w - w / (1 + p), p in [0, +inf]
w, p = FP('w', S), FP('p', S)
lemma('BM25-shape: w>=0 finite, p in [0,inf] => w - w/(1+p) in [0, w], not NaN',
      [fin_nn(w), nn(p)],
      And(notnan(sub(w, div(w, add(z(1.0), p)))), fpGEQ(sub(w, div(w, add(z(1.0), p))), z(0.0)),
          fpLEQ(sub(w, div(w, add(z(1.0), p))), w)))
f, ni = FP('f', S), FP('ni', S)
lemma('BM25-shape: f>0 finite, ni in [0,inf] => f*ni in [0,inf] (not NaN)',
      [fin_nn(f), fpGT(f, z(0.0)), nn(ni)], nn(mul(f, ni)))
k1 = FP('k1', S)
lemma('StableTfl-shape: f>0 finite, k1>=0 finite => f/k1 in [0,inf] (not NaN)',
      [fin_nn(f), fpGT(f, z(0.0)), fin_nn(k1)], nn(div(f, k1)))

print('SUMMARY fp%s' % W)
for r in RESULTS:
    print('  %-70s %-14s %7.1fs' % r)
