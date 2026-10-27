# Translation validation (Z3, QF_BV 64-bit) of the "encoded-domain" range scan that feeds
# DocValuesRangeSupport (scalar or Panama SIMD) in Lucene90DocValuesProducer @7cecf79523:
#
#   reference (NumericDocValues#rangeIntoBitSet default / per-doc decode):
#        v = mul * s + delta   (Java long arithmetic, wraps)        match  <=>  lo <= v <= hi
#   optimized (rangeGcdDeltaIntoBitSet + transformGcdBounds, lines ~548-612):
#        [eMin, eMax] = transformGcdBounds(lo, hi, mul, delta)      match  <=>  bounds != null
#                                                                           && eMin <= s <= eMax  (signed)
#
# Reachable (s, mul, delta) are constrained by the writer, Lucene90DocValuesConsumer#writeValues
# (lines ~405-560): delta = field min, mul = gcd, stored s = (v - min) / gcd with long arithmetic;
# gcd != 1 only if every value is within [Long.MIN_VALUE/2, Long.MAX_VALUE/2]; the optimized
# reader branch is used when the encoding is not a table, not blocks, and !(gcd == 1 && min == 0).
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import z3

W = 64
MIN = -(1 << 63)
MAX = (1 << 63) - 1


def c(v):
    return z3.BitVecVal(v, W)


def sub_overflows(x, y):
    d = x - y
    return ((x ^ y) & (x ^ d)) < 0


def saturating_shift_lower(lo, delta):
    # try { return Math.subtractExact(minValue, delta); } catch (...) { return delta > 0 ? MIN : MAX; }
    return z3.If(sub_overflows(lo, delta), z3.If(delta > 0, c(MIN), c(MAX)), lo - delta)


def saturating_shift_upper(hi, delta):
    # ... catch { return delta < 0 ? Long.MAX_VALUE : Long.MIN_VALUE; }
    return z3.If(sub_overflows(hi, delta), z3.If(delta < 0, c(MAX), c(MIN)), hi - delta)


def java_div(x, y):  # Java long division truncates toward zero == SMT-LIB bvsdiv
    return x / y  # z3 '/' on BitVec is bvsdiv


def floor_div(x, y):
    q = java_div(x, y)
    return z3.If(z3.And((x ^ y) < 0, q * y != x), q - 1, q)


def ceil_div(x, y):
    q = java_div(x, y)
    return z3.If(z3.And((x ^ y) >= 0, q * y != x), q + 1, q)


def transform(lo, hi, mul, delta):
    emin = saturating_shift_lower(lo, delta)
    emax = saturating_shift_upper(hi, delta)
    emin = z3.If(mul != 1, ceil_div(emin, mul), emin)
    emax = z3.If(mul != 1, floor_div(emax, mul), emax)
    emin = z3.If(emin > 0, emin, c(0))  # Math.max(0, encodedMin)
    nonnull = z3.Not(emin > emax)
    return nonnull, emin, emax


def encoded_match(s, lo, hi, mul, delta):
    nonnull, emin, emax = transform(lo, hi, mul, delta)
    # DefaultDocValuesRangeSupport / PanamaDocValuesRangeSupport: v >= minValue && v <= maxValue
    return z3.And(nonnull, s >= emin, s <= emax)


def reference_match(s, lo, hi, mul, delta):
    v = mul * s + delta
    return z3.And(v >= lo, v <= hi)


def writer_reachable(v, m, g, s, case):
    """v: an indexed value, m: field min (delta), g: gcd (mul), s: the stored value."""
    cons = [v >= m]  # m is the minimum of all values
    if case == "gcd1":
        cons += [g == 1, s == v - m, m != 0]  # m == 0 && gcd == 1 uses the raw-value branch
    elif case == "gcd1-no-wrap":
        cons += [g == 1, s == v - m, m != 0, z3.Not(sub_overflows(v, m))]
    elif case == "gcd>1":
        lo2, hi2 = c(MIN // 2 + 1), c(MAX // 2)  # Long.MIN_VALUE / 2 truncates toward 0
        cons += [g > 1, v >= c(-(1 << 62)), v <= hi2, m >= c(-(1 << 62)), m <= hi2,
                 z3.URem(v - m, g) == 0, s == (v - m) / g]
    return cons


def run(case, extra=()):
    lo, hi, v, m, g, s = z3.BitVecs("lo hi v m g s", W)
    sol = z3.Solver()
    sol.set("timeout", 600000)
    sol.add(*writer_reachable(v, m, g, s, case))
    sol.add(*extra)
    # sanity: decode(s) == v for reachable states (writer/reader agree)
    sanity = z3.Solver()
    sanity.add(*writer_reachable(v, m, g, s, case))
    sanity.add(g * s + m != v)
    sres = sanity.check()
    sol.add(encoded_match(s, lo, hi, g, m) != reference_match(s, lo, hi, g, m))
    r = sol.check()
    out = {"case": case, "decode_sanity": str(sres), "result": str(r)}
    if r == z3.sat:
        mdl = sol.model()
        val = lambda x: mdl.eval(x, model_completion=True).as_signed_long()
        out["cex"] = {k: val(x) for k, x in dict(lo=lo, hi=hi, v=v, m=m, g=g, s=s).items()}
        out["cex"]["reference"] = z3.is_true(mdl.eval(reference_match(s, lo, hi, g, m)))
        out["cex"]["encoded"] = z3.is_true(mdl.eval(encoded_match(s, lo, hi, g, m)))
    return out


def enumerate_kinds():
    """Classify the gcd==1 counterexamples into false negatives / false positives."""
    lo, hi, v, m, g, s = z3.BitVecs("lo hi v m g s", W)
    base = writer_reachable(v, m, g, s, "gcd1")
    res = {}
    for kind, cond in [("false negative (doc in range but not set)",
                        z3.And(reference_match(s, lo, hi, g, m), z3.Not(encoded_match(s, lo, hi, g, m)))),
                       ("false positive (doc set but not in range)",
                        z3.And(z3.Not(reference_match(s, lo, hi, g, m)), encoded_match(s, lo, hi, g, m)))]:
        sol = z3.Solver()
        sol.add(*base, cond)
        r = sol.check()
        if r == z3.sat:
            mdl = sol.model()
            val = lambda x: mdl.eval(x, model_completion=True).as_signed_long()
            res[kind] = {k: val(x) for k, x in dict(lo=lo, hi=hi, v=v, m=m, s=s).items()}
        else:
            res[kind] = str(r)
        # also: is every such counterexample a stored value that is negative as a signed long?
        sol2 = z3.Solver()
        sol2.add(*base, cond, s >= 0)
        res[kind + " with s >= 0"] = str(sol2.check())
    return res


if __name__ == "__main__":
    import json
    for case in ("gcd1", "gcd1-no-wrap", "gcd>1"):
        print(json.dumps(run(case)), flush=True)
    for k, v in enumerate_kinds().items():
        print(k, "->", v, flush=True)
