# IEEE-754 (Z3 FP + reals) check of the rounding step of minMaxScalarQuantize:
#   scalar  DefaultVectorUtilSupport.ScalarQuantizer.quantizeFloat:  Math.round(scale * dxc)
#   Panama  PanamaVectorUtilSupport.minMaxScalarQuantize (VECTOR_BITSIZE >= 256):
#           fma(dxc, scale, 0.5f).convert(F2I)            if Constants.HAS_FAST_VECTOR_FMA
#           dxc.mul(scale).add(0.5f).convert(F2I)          otherwise
# Java semantics: Math.round(float) = floor(x + 1/2) computed exactly (JLS / Math javadoc,
# JDK-6430675 fixed in JDK 7); (int) cast / F2I truncates toward zero (JLS 5.1.3).
import json
import z3

F = z3.Float32()
RNE = z3.RNE()


def fl(v):
    return z3.FPVal(v, F)


Q = z3.FPSort(15, 113)  # binary128: p + 1/2 is exact for every float32 p in [2^-112, 2^24]


def math_round(p):  # Math.round(float): floor(p + 1/2) evaluated exactly (pure FP encoding)
    # for 0 <= p < 2^-112 the binary128 sum rounds to 0.5 and the floor is 0, which is exact too
    w = z3.fpAdd(RNE, z3.fpFPToFP(RNE, p, Q), z3.FPVal(0.5, Q))
    return z3.fpToSBV(z3.RTN(), w, z3.BitVecSort(32))


def trunc_to_int(q):  # (int) q / VectorOperators.F2I for finite 0 <= q < 2^31: round toward zero
    return z3.fpToSBV(z3.RTZ(), q, z3.BitVecSort(32))


def check(use_fma, extra=(), label=""):
    x, y = z3.FP("dxc", F), z3.FP("scale", F)
    s = z3.Solver()
    s.set("timeout", 600000)
    s.add(z3.Or(z3.fpIsNormal(y), z3.fpIsSubnormal(y)), z3.fpGT(y, fl(0.0)))
    s.add(z3.Not(z3.fpIsNaN(x)), z3.Not(z3.fpIsInf(x)), z3.fpGEQ(x, fl(0.0)))
    p = z3.fpMul(RNE, y, x)  # scale * dxc
    s.add(z3.fpLEQ(p, fl(256.0)))
    q = z3.fpFMA(RNE, x, y, fl(0.5)) if use_fma else z3.fpAdd(RNE, z3.fpMul(RNE, x, y), fl(0.5))
    s.add(math_round(p) != trunc_to_int(q))
    for e in extra:
        s.add(e(x, y, p))
    r = s.check()
    out = {"variant": ("FMA" if use_fma else "mul+add") + label, "result": str(r)}
    if r == z3.sat:
        m = s.model()
        xv, yv = m.eval(x), m.eval(y)
        out["dxc"] = xv.as_string() if hasattr(xv, "as_string") else str(xv)
        out["scale"] = str(yv)
        out["exact_product(binary128)"] = str(m.eval(z3.fpMul(RNE, z3.fpFPToFP(RNE, x, Q), z3.fpFPToFP(RNE, y, Q))))
        out["fl_product"] = str(m.eval(p))
        out["dxc_bits"] = hex(m.eval(z3.fpToIEEEBV(x)).as_long())
        out["scale_bits"] = hex(m.eval(z3.fpToIEEEBV(y)).as_long())
        out["scalar_round"] = str(m.eval(math_round(p)))
        out["panama_trunc"] = str(m.eval(trunc_to_int(q)))
    print(json.dumps(out), flush=True)
    return r


if __name__ == "__main__":
    # 1. discrepancy exists?
    check(True)
    check(False)
    # 2. characterisation: every discrepancy has exact product in (1/2 - 2^-25, 1/2) for FMA, and
    #    fl(product) == 0.5 - 2^-25 (the float just below 0.5) for mul+add
    # exact product computed in binary128 (exact: 24+24 significant bits < 113)
    ex = lambda x, y: z3.fpMul(RNE, z3.fpFPToFP(RNE, x, Q), z3.fpFPToFP(RNE, y, Q))
    lo_q = z3.FPVal(0.5 - 2.0 ** -25, Q)
    half_q = z3.FPVal(0.5, Q)
    hi_q = z3.FPVal(0.5 - 2.0 ** -26, Q)
    check(True, [lambda x, y, p: z3.Not(z3.And(z3.fpGEQ(ex(x, y), lo_q), z3.fpLT(ex(x, y), hi_q)))],
          " with exact product outside [1/2-2^-25, 1/2-2^-26)")
    # the window is non-empty for realistic parameters: minQuantile = 0, maxQuantile = 1, 7 bits
    check(True, [lambda x, y, p: z3.fpEQ(y, fl(127.0)), lambda x, y, p: z3.fpLEQ(x, fl(1.0))],
          " with scale = 127/(1-0) (7-bit, quantiles [0,1])")
    check(True, [lambda x, y, p: z3.fpEQ(y, fl(255.0)), lambda x, y, p: z3.fpLEQ(x, fl(1.0))],
          " with scale = 255/(1-0) (8-bit, quantiles [0,1])")
    check(False, [lambda x, y, p: z3.Not(z3.fpEQ(p, fl(0.5 - 2.0 ** -25)))],
          " with fl(scale*dxc) != 0.5-2^-25")
