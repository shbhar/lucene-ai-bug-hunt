"""SMT-8: the quantized scorer implements the same similarity as the float scorer (exact real
arithmetic, Z3 nlsat/nla), i.e. the correction formula is an algebraic identity once the codes are
replaced by their dequantized values. Transcribed from Lucene104ScalarQuantizedVectorScorer
.quantizedScore (:283-316) and OptimizedScalarQuantizer.scalarQuantize (:232-236).
  dequantized component: a + l*k  with l = (b - a)/(2^bits - 1)  (SCALE_LUT[bits-1] = 1/(2^bits-1))
  score = ax*ay*d + ay*lx*x1 + ax*ly*y1 + lx*ly*qcDist,  x1 = sum kx, y1 = sum ky, qcDist = sum kx*ky
  EUCLIDEAN : |q-c|^2 + |x-c|^2 - 2*score        (additionalCorrection = norm2 of the CENTERED vector)
  DOT/COS/MIP: score + q.c + x.c - c.c           (additionalCorrection = raw-vector . centroid)
Checked for d = 1..6 with all quantities symbolic (any centroid c, any intervals, any codes).
"""
import time
from z3 import Reals, Real, Solver, Sum, unsat, And, Not

def prove(name, goal):
    s = Solver(); s.set("timeout", 120000); s.add(Not(goal))
    t = time.time(); r = s.check()
    print(f"[{'PROVED' if r == unsat else r}] {name} ({time.time() - t:.2f}s)", flush=True)

for d in range(1, 7):
    ax, lx, ay, ly = Reals("ax lx ay ly")
    kx = [Real(f"kx{i}") for i in range(d)]
    ky = [Real(f"ky{i}") for i in range(d)]
    q = [Real(f"q{i}") for i in range(d)]
    x = [Real(f"x{i}") for i in range(d)]
    c = [Real(f"c{i}") for i in range(d)]
    dq = [ay + ly * ky[i] for i in range(d)]   # dequantized centered query
    dx = [ax + lx * kx[i] for i in range(d)]   # dequantized centered doc
    score = ax * ay * d + ay * lx * Sum(kx) + ax * ly * Sum(ky) + lx * ly * Sum([kx[i] * ky[i] for i in range(d)])
    prove(f"d={d}: score == <dequant(q-c), dequant(x-c)>", score == Sum([dq[i] * dx[i] for i in range(d)]))
    dot = lambda u, v: Sum([u[i] * v[i] for i in range(d)])
    qc = [q[i] - c[i] for i in range(d)]
    xc = [x[i] - c[i] for i in range(d)]
    prove(f"d={d}: q.x == (q-c).(x-c) + q.c + x.c - c.c   (DOT_PRODUCT / COSINE / MIP branch)",
          dot(q, x) == dot(qc, xc) + dot(q, c) + dot(x, c) - dot(c, c))
    prove(f"d={d}: |q-x|^2 == |q-c|^2 + |x-c|^2 - 2 (q-c).(x-c)   (EUCLIDEAN branch)",
          dot([q[i] - x[i] for i in range(d)], [q[i] - x[i] for i in range(d)])
          == dot(qc, qc) + dot(xc, xc) - 2 * dot(qc, xc))
