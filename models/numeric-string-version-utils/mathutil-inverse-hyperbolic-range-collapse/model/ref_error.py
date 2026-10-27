#!/usr/bin/env python3
"""Ground-truth error of MathUtil.asinh/acosh/atanh (stock jar dump) vs a decimal reference.

Reference: Python decimal at 200 significant digits; the closed forms
  asinh(x) = ln(x + sqrt(x^2 + 1)), acosh(x) = ln(x + sqrt(x^2 - 1)), atanh(x) = ln((1 + x) / (1 - x)) / 2
are evaluated exactly enough for |x| >= 1e-5 (every double is an exact decimal); for |x| < 1e-5 the Taylor series
asinh = x - x^3/6 + 3x^5/40 - 15x^7/336, atanh = x + x^3/3 + x^5/5 + x^7/7 (truncation < 1e-40 relative) is used.
Error is reported in ulps of the correctly rounded reference. Usage: ref_error.py DUMP > report.txt
"""
import math
import sys
from decimal import Decimal, getcontext

getcontext().prec = 200
getcontext().Emax = 10**6
getcontext().Emin = -10**6


def d(x):
    return Decimal(x)  # exact


def ref_asinh(x):
    X = d(x)
    if x < 1e-5:
        return X - X**3 / 6 + 3 * X**5 / 40 - 15 * X**7 / 336
    return (X + (X * X + 1).sqrt()).ln()


def ref_acosh(x):
    X = d(x)
    if x < 1:
        return None
    return (X + (X * X - 1).sqrt()).ln()


def ref_atanh(x):
    X = d(x)
    if x >= 1:
        return None
    if x < 1e-5:
        return X + X**3 / 3 + X**5 / 5 + X**7 / 7
    return ((1 + X) / (1 - X)).ln() / 2


def ulp_err(got, ref):
    if ref is None:
        return None
    r = float(ref)  # correctly rounded
    if math.isinf(got):
        return float("inf")
    if r == 0:
        return 0.0 if got == 0 else float("inf")
    u = math.ulp(r)
    return float(abs(Decimal(got) - ref) / Decimal(u))


def main():
    rows = []
    for line in open(sys.argv[1]):
        p = line.split()
        if len(p) != 4:
            continue
        x = float.fromhex(p[0])
        a, c, t = (float.fromhex(v) for v in p[1:])
        rows.append((x, ulp_err(a, ref_asinh(x)), a, ulp_err(c, ref_acosh(x)), c, ulp_err(t, ref_atanh(x)), t))
    print("# x  asinh_ulp_err  acosh_ulp_err  atanh_ulp_err   (inf = result infinite or zero where the true value is not)")
    # summary per decade
    import collections
    worst = collections.OrderedDict()
    for x, ea, a, ec, c, et, t in rows:
        dec = math.floor(math.log10(x))
        w = worst.setdefault(dec, [0.0, 0.0, 0.0, 0])
        for k, e in enumerate((ea, ec, et)):
            if e is not None and e > w[k]:
                w[k] = e
        w[3] += 1
    print("# decade  n  max_ulp_asinh  max_ulp_acosh(x>=1)  max_ulp_atanh(x<1)")
    for dec, w in worst.items():
        print(f"1e{dec:+04d} {w[3]:3d}  {w[0]:.3g}  {w[1]:.3g}  {w[2]:.3g}")
    # specific witnesses
    print("# witnesses")
    for x, ea, a, ec, c, et, t in rows:
        if x in (1e-17, 1e-10, 1e-8, 1e-5, 1e-3, 2.0**512, 1e200, 1234567.89, 2.5, 0.5):
            print(f"x={x!r} asinh={a!r} ({ea:.3g} ulp) acosh={c!r} ({'n/a' if ec is None else f'{ec:.3g} ulp'}) "
                  f"atanh={t!r} ({'n/a' if et is None else f'{et:.3g} ulp'})")
    def collapsed(got, ref):
        return ref is not None and float(ref) != 0 and (math.isinf(got) or got == 0)
    ca = sum(1 for x, ea, a, ec, c, et, t in rows if collapsed(a, ref_asinh(x)))
    cc = sum(1 for x, ea, a, ec, c, et, t in rows if collapsed(c, ref_acosh(x)))
    ct = sum(1 for x, ea, a, ec, c, et, t in rows if collapsed(t, ref_atanh(x)))
    big = lambda k: sum(1 for r in rows if r[k] is not None and r[k] > 1)
    print(f"# grid points={len(rows)}; collapsed (Inf or 0 where the true value is finite and nonzero): "
          f"asinh={ca} acosh={cc} atanh={ct}; error > 1 ulp: asinh={big(1)} acosh={big(3)} atanh={big(5)}")


if __name__ == "__main__":
    main()
