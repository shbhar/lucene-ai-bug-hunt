# Exact int32 overflow thresholds of the byte-vector kernels versus the bounds their code comments state.
#
# File:line mapping (Lucene main @ ea57360912ed):
#   DefaultVectorUtilSupport.java:186-192  dotProduct(byte):       total += a[i] * b[i]              (int)
#   DefaultVectorUtilSupport.java:195-201  uint8DotProduct:        total += ub(a[i]) * ub(b[i])     (int)
#   DefaultVectorUtilSupport.java:241-256  cosine(byte):           comment L243 "this will not overflow if dim < 2^18,
#                                          since max(byte * byte) = 2^14"; sum/norm1/norm2 are int
#   DefaultVectorUtilSupport.java:258-267  squareDistance(byte):   comment L260 "this will not overflow if dim < 2^18,
#                                          since max(byte * byte) = 2^14"; diff = a[i]-b[i]; squareSum += diff*diff
#   DefaultVectorUtilSupport.java:305-314  uint8SquareDistance:    comment L307 "this will not overflow if dim < 2^16,
#                                          since max(ubyte * ubyte) = 2^16"
#   VectorUtil.java:434-438                dotProductScore:        denom = (float) (a.length * (1 << 15))   (int)
#   PanamaVectorUtilSupport (byte bodies): int lanes + reduceLanes(ADD) = the same sum mod 2^32 (study proof
#                                          for n <= 2049; the wrap is order-independent), so both providers agree.
#
# Method: exhaustive evaluation over all 2^16 operand pairs gives the extremes of one term (attained), and a Z3
# QF_BV query proves no pair lies outside [lo, hi] (signed comparison). With every term in [lo, hi], an n-term int32 accumulator
# cannot wrap iff n*hi <= 2^31-1 and n*lo >= -2^31, and the all-extreme input attains the bound, so the first
# overflowing dimension is exact (not just an upper bound).
import sys
import z3

INT_MAX, INT_MIN = 2**31 - 1, -2**31


def term_range(name, f):
    """Exact [lo, hi] of one 32-bit kernel term over all 2^16 byte pairs: extremes by exhaustive evaluation of
    the Z3 term, then a Z3 proof (signed bvsgt / bvslt) that no pair exceeds them."""
    a, b = z3.BitVec("a", 8), z3.BitVec("b", 8)
    t = f(a, b)
    best = {"max": None, "min": None}
    for x in range(256):
        for y in range(256):
            v = z3.simplify(z3.substitute(t, (a, z3.BitVecVal(x, 8)), (b, z3.BitVecVal(y, 8)))).as_signed_long()
            sx, sy = (x - 256 if x > 127 else x), (y - 256 if y > 127 else y)
            if best["max"] is None or v > best["max"][0]:
                best["max"] = (v, sx, sy)
            if best["min"] is None or v < best["min"][0]:
                best["min"] = (v, sx, sy)
    for kind in ("max", "min"):
        v = best[kind][0]
        s = z3.Solver()
        s.add(t > v if kind == "max" else t < v)  # z3py > and < on BitVec are signed
        assert s.check() == z3.unsat, (name, kind)
    return best


def s32(x):
    return z3.SignExt(24, x)


def u32(x):
    return z3.ZeroExt(24, x)


KERNELS = [
    # name, term, comment-claimed safe bound (dim < bound), comment location
    ("dotProduct(byte) term a*b", lambda a, b: s32(a) * s32(b), None, "no comment"),
    ("cosine(byte) norm term a*a", lambda a, b: s32(a) * s32(a), 2**18, "DefaultVectorUtilSupport.java:243"),
    ("cosine(byte) sum term a*b", lambda a, b: s32(a) * s32(b), 2**18, "DefaultVectorUtilSupport.java:243"),
    ("squareDistance(byte) term (a-b)^2", lambda a, b: (s32(a) - s32(b)) * (s32(a) - s32(b)), 2**18,
     "DefaultVectorUtilSupport.java:260"),
    ("uint8DotProduct term ua*ub", lambda a, b: u32(a) * u32(b), None, "no comment"),
    ("uint8SquareDistance term (ua-ub)^2", lambda a, b: (u32(a) - u32(b)) * (u32(a) - u32(b)), 2**16,
     "DefaultVectorUtilSupport.java:307"),
]


def main():
    wrong = 0
    print(f"{'kernel term':40s} {'term range [lo,hi]':22s} {'first overflow dim':>18s} {'comment claims safe below':>26s}")
    for name, f, claim, where in KERNELS:
        r = term_range(name, f)
        hi, lo = r["max"][0], r["min"][0]
        n_hi = INT_MAX // hi + 1 if hi > 0 else None
        n_lo = (-INT_MIN) // (-lo) + (1 if (-INT_MIN) % (-lo) else 0) if lo < 0 else None
        # first n where n*lo < INT_MIN
        if lo < 0:
            n_lo = (-INT_MIN) // (-lo) + 1 if ((-INT_MIN) % (-lo)) == 0 else -(-(-INT_MIN) // (-lo))
            while n_lo * lo >= INT_MIN:
                n_lo += 1
        first = min(x for x in (n_hi, n_lo) if x is not None)
        assert (first - 1) * hi <= INT_MAX and (first - 1) * lo >= INT_MIN
        assert first * hi > INT_MAX or first * lo < INT_MIN
        verdict = ""
        if claim is not None:
            if first < claim:
                verdict = f"WRONG ({where}: overflow possible for {first} <= dim < {claim})"
                wrong += 1
            else:
                verdict = f"ok ({where})"
        print(f"{name:40s} [{lo},{hi}] at {r['min'][1:]} / {r['max'][1:]}  first overflow dim={first:>7d}  "
              f"claim={claim if claim else '-'}  {verdict}")
    # dotProductScore denominator: a.length * (1 << 15) as int
    n = INT_MAX // (1 << 15) + 1
    print(f"{'VectorUtil.dotProductScore denom':40s} a.length*(1<<15) overflows int at a.length={n} (= 2^16)")
    print(f"comments contradicted: {wrong}")
    sys.exit(0)


if __name__ == "__main__":
    main()
