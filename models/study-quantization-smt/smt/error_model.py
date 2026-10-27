"""SMT-3b: quantize -> dequantize error bound in the standard IEEE rounding-error model,
for the in-range case of OptimizedScalarQuantizer.scalarQuantize (:220-231) + deQuantize (:253-267).

The bit-precise version (quantize_range.py R2) times out (20 min, Z3 4.12.2) because it chains two
float divisions, a double multiply-add and a 400-bit comparison, and the monolithic nonlinear-real
version times out in nlsat (10 min). So the proof is split into small nonlinear lemmas (Z3 nlsat)
and a final exact rational inequality.

Rounding model (IEEE 754 correct rounding, RNE):  fl(a op b) = (a op b)(1 + d) [+ e if subnormal]
   binary32: |d| <= u = 2^-24 ;  binary64: |d| <= v = 2^-53 ; subnormal absolute error <= 2^-150.
Everything is expressed in units of step = fl32(fl32(b - a) / n) (exact scaling by 2^k is not
needed; we just divide the real quantities by the real number step > 0).
Preconditions: a <= x <= b, b > a, step is a NORMAL float (>= 2^-126, so 2^-150/step <= 2^-24),
and the float-grid fact b - a >= |a| * 2^-24 (a and b are distinct floats).
Java (per component): D = fl32(b - a); step = fl32(D / n); E = fl32(x - a); q = fl32(E / step);
k = Math.round(q) = q + t with |t| <= 1/2; r = fl64(fl64(k * step) + a).
"""
import time
from fractions import Fraction as Fr
from z3 import Reals, Solver, And, Or, Not, RealVal, sat, unsat

u = Fr(1, 2 ** 24)
v = Fr(1, 2 ** 53)
eta = Fr(1, 2 ** 24)  # 2^-150 / 2^-126


def R(f):
    return RealVal(f"{f.numerator}/{f.denominator}")


def prove(name, hyps, goal):
    s = Solver()
    s.set("timeout", 300000)
    s.add(*hyps)
    s.add(Not(goal))
    t = time.time()
    r = s.check()
    tag = "PROVED" if r == unsat else ("CEX" if r == sat else "UNKNOWN")
    print(f"[{tag}] {name} ({r}, {time.time() - t:.2f}s)", flush=True)
    if r == sat:
        print("      model:", s.model())
    return r == unsat


def bnd(x, m):
    return And(x >= -R(m), x <= R(m))


W, d1, d2 = Reals("W d1 d2")
ok = True
for nn in [1, 3, 15, 127, 255]:
    ok &= prove(f"L1: W(1+d1)(1+d2) = n, |d1|,|d2| <= u, W > 0  ==>  W <= n/(1-u)^2  [n = {nn}]",
                [W > 0, bnd(d1, u), bnd(d2, u), W * (1 + d1) * (1 + d2) == nn],
                W <= R(Fr(nn) / (1 - u) ** 2))
Y, d3, d4, e3, e4, Ymax = Reals("Y d3 d4 e3 e4 Ymax")
E2 = Fr(1, 2 ** 150)  # absolute error if q itself is subnormal (q is dimensionless here)
qq = (Y * (1 + d3) + e3) * (1 + d4) + e4
lim = Ymax * R(2 * u + u * u) + R(eta * (1 + u) + E2)
ok &= prove("L2: 0 <= Y <= Ymax, q = (Y(1+d3)+e3)(1+d4)+e4  ==>  |q - Y| <= Ymax(2u+u^2) + eta(1+u) + 2^-150",
            [Y >= 0, Y <= Ymax, bnd(d3, u), bnd(d4, u), bnd(e3, eta), bnd(e4, E2)],
            And(qq - Y <= lim, qq - Y >= -lim))
k, A, d5, d6, Kmax, Amax = Reals("k A d5 d6 Kmax Amax")
ok &= prove("L3: |k| <= Kmax, |A| <= Amax  ==>  |k d5 + d6 (k(1+d5) + A)| <= v Kmax + v (Kmax(1+v) + Amax)",
            [k <= Kmax, k >= -Kmax, A <= Amax, A >= -Amax, bnd(d5, v), bnd(d6, v), Kmax >= 0, Amax >= 0],
            And(k * d5 + d6 * (k * (1 + d5) + A) <= R(v) * Kmax + R(v) * (Kmax * (1 + v) + Amax),
                k * d5 + d6 * (k * (1 + d5) + A) >= -(R(v) * Kmax + R(v) * (Kmax * (1 + v) + Amax))))
print("lemmas proved:", ok)

# final exact rational bound for every bit width
# r - x = (k - Y) + k d5 + d6 (k (1 + d5) + A),  Y = x - a in step units
for bits in [1, 2, 4, 7, 8]:
    n = (1 << bits) - 1
    Ym = Fr(n) / (1 - u) ** 2                      # L1 (generalises: the proof of L1 is linear in n)
    K1 = Fr(1, 2) + Ym * (2 * u + u * u) + eta * (1 + u) + Fr(1, 2 ** 150)   # L2 + |t| <= 1/2
    Km = Ym * (1 + u) ** 2 + eta * (1 + u) + Fr(1, 2 ** 150) + Fr(1, 2)
    Am = Ym * 2 ** 24 * (1 + u)                     # |a| <= (b - a) 2^24 and b - a = W*step
    total = K1 + v * Km + v * (Km * (1 + v) + Am)  # L3
    C = Fr(1, 2) + Fr(n + 2, 2 ** 22)
    print(f"bits={bits}: |deq(q(x)) - x| <= {float(total):.12f} * step ; claimed C = {float(C):.12f} ; "
          f"{'HOLDS' if total <= C else 'FAILS'} ; excess over 1/2 = {float(total - Fr(1, 2)):.3e} "
          f"= {float((total - Fr(1, 2)) * 2 ** 24 / n):.4f} * n * 2^-24")
