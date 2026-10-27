# Formal validation of a candidate fix for the encoded-domain range scan (no Lucene build needed):
#  (1) do not use the encoded path when bitsPerValue == 64 (packed values may be >= 2^63, i.e.
#      negative as signed longs); fall back to the decoded per-value check;
#  (2) in transformGcdBounds, a lower bound whose shift overflows upwards (minValue - delta >
#      Long.MAX_VALUE) means the range is empty: return null instead of saturating to MAX.
# Claim: with (1)+(2) the encoded scan equals the reference for every reachable state.
import os, sys, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import z3
from dv_gcd_transform import *

def fixed_transform(lo, hi, mul, delta):
    lower_empty = z3.And(sub_overflows(lo, delta), delta < 0)   # fix (2)
    emin = saturating_shift_lower(lo, delta)
    emax = saturating_shift_upper(hi, delta)
    emin = z3.If(mul != 1, ceil_div(emin, mul), emin)
    emax = z3.If(mul != 1, floor_div(emax, mul), emax)
    emin = z3.If(emin > 0, emin, c(0))
    return z3.And(z3.Not(lower_empty), z3.Not(emin > emax)), emin, emax

lo, hi, v, m, g, s = z3.BitVecs("lo hi v m g s", W)
# gcd == 1 with bitsPerValue <= 63  <=>  max - min fits in a signed long  =>  every s = v - min >= 0
sol = z3.Solver(); sol.set("timeout", 600000)
sol.add(*writer_reachable(v, m, g, s, "gcd1-no-wrap"))
nonnull, emin, emax = fixed_transform(lo, hi, g, m)
sol.add(z3.And(nonnull, s >= emin, s <= emax) != reference_match(s, lo, hi, g, m))
r = sol.check()
print(json.dumps({"fixed transform, gcd == 1, bitsPerValue <= 63": str(r)}), flush=True)

# gcd > 1 in integers (same justification as dv_gcd_transform_int.py)
MINV, MAXV = -(1 << 63), (1 << 63) - 1
lo, hi, v, m, g, s, qlo, qhi = z3.Ints("lo hi v m g s qlo qhi")
inl = lambda x: z3.And(x >= MINV, x <= MAXV)
sol = z3.Solver(); sol.set("timeout", 600000)
sol.add(inl(lo), inl(hi), g > 1, g <= MAXV, v >= -(1 << 62), v <= (1 << 62) - 1, m >= -(1 << 62), m <= (1 << 62) - 1, v >= m, g * s == v - m, s >= 0)
dl, du = lo - m, hi - m
lower_empty = z3.And(z3.Not(inl(dl)), m < 0)
emin0 = z3.If(inl(dl), dl, z3.If(m > 0, MINV, MAXV))
emax0 = z3.If(inl(du), du, z3.If(m < 0, MAXV, MINV))
sol.add(g * (qlo - 1) < emin0, emin0 <= g * qlo, g * qhi <= emax0, emax0 < g * (qhi + 1))
emin = z3.If(qlo > 0, qlo, 0)
enc = z3.And(z3.Not(lower_empty), emin <= qhi, s >= emin, s <= qhi)
ref = z3.And(g * s + m >= lo, g * s + m <= hi)
sol.add(enc != ref)
print(json.dumps({"fixed transform, gcd > 1 (NIA)": str(sol.check())}), flush=True)
