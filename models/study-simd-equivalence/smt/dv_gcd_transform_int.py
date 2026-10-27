# gcd > 1 case of dv_gcd_transform.py, decided in integer arithmetic (Z3 NIA).
# Why integers are faithful here: in the gcd > 1 case the writer guarantees every value, hence
# delta = min, lies in [-2^62, 2^62 - 1], so v - min, (v - min)/g, g*s + min never overflow; the
# only long operations that can overflow are lo - delta and hi - delta, which are modelled exactly
# (overflow <=> mathematical result outside [MIN, MAX], as Math.subtractExact specifies).
# Math.floorDiv / Math.ceilDiv with a positive divisor are the mathematical floor / ceiling.
import z3, json
MIN, MAX = -(1 << 63), (1 << 63) - 1
lo, hi, v, m, g, s = z3.Ints("lo hi v m g s")
qlo, qhi = z3.Ints("qlo qhi")
def in_long(x): return z3.And(x >= MIN, x <= MAX)
sol = z3.Solver()
sol.set("timeout", 600000)
sol.add(in_long(lo), in_long(hi))
sol.add(g > 1, g <= MAX)
sol.add(v >= -(1 << 62), v <= (1 << 62) - 1, m >= -(1 << 62), m <= (1 << 62) - 1, v >= m)
sol.add(g * s == v - m, s >= 0)
# saturatingShiftLower / Upper
dl, du = lo - m, hi - m
emin0 = z3.If(in_long(dl), dl, z3.If(m > 0, MIN, MAX))
emax0 = z3.If(in_long(du), du, z3.If(m < 0, MAX, MIN))
# ceilDiv(emin0, g) = qlo ; floorDiv(emax0, g) = qhi  (characterised by inequalities)
sol.add(g * (qlo - 1) < emin0, emin0 <= g * qlo)
sol.add(g * qhi <= emax0, emax0 < g * (qhi + 1))
emin = z3.If(qlo > 0, qlo, 0)
encoded = z3.And(emin <= qhi, s >= emin, s <= qhi)
reference = z3.And(g * s + m >= lo, g * s + m <= hi)
sol.add(encoded != reference)
r = sol.check()
print(json.dumps({"case": "gcd>1 (NIA)", "result": str(r), **({"cex": {str(d): str(sol.model()[d]) for d in sol.model().decls()}} if r == z3.sat else {})}))

# (2) same, restricted to non-empty query ranges
sol.push(); sol.add(lo <= hi); r2 = sol.check()
print(json.dumps({"case": "gcd>1 (NIA), lo <= hi", "result": str(r2), **({"cex": {str(d): str(sol.model()[d]) for d in sol.model().decls()}} if r2 == z3.sat else {})}))
sol.pop()
# (3) excluding the single colliding packed value v - min == Long.MAX_VALUE: expect a proof
sol.push(); sol.add(v - m != MAX); r3 = sol.check()
print(json.dumps({"case": "gcd>1 (NIA), v - min != Long.MAX_VALUE", "result": str(r3)}))
sol.pop()
