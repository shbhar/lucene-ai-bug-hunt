# Z3 model of GeoEncodingUtils.encodeLatitudeCeil / encodeLongitudeCeil (lucene/core/.../geo/GeoEncodingUtils.java:68-110)
# file:line map (Lucene main @ ea57360912ed):
#   LAT_DECODE = 1 / ((1L<<32)/180.0)                      GeoEncodingUtils.java:31-32
#   encodeLatitudeCeil: checkLatitude; if (x == 90) x = nextDown(x); (int) Math.ceil(x / LAT_DECODE)   :68-75
#   (int) cast of a double >= 2^31 saturates to Integer.MAX_VALUE (JLS 5.1.3)
#   decodeLatitude(k) = k * LAT_DECODE                       :113-117
# Query Q1 (counterexample engine): exists double x in [-90, 90] with decode(encodeCeil(x)) < x  (ceil property broken)
# Query Q2: same but restricted to x <= decode(MAX_VALUE)   (expected UNSAT; cross-checked by EncodingExhaustive)
import sys
from z3 import *

def run(name, top, bits_scale):
    D64 = Float64()
    rne = RNE()
    two32 = 4294967296.0
    # Java computes the scale and decode constants in double arithmetic; replicate exactly in Python (IEEE binary64)
    scale = two32 / top * 0.5 * 1.0  # placeholder (overwritten below)
    scale = (1 << 32) / (2.0 * top)          # (0x1L << BITS) / 180.0D  (lat: top=90 -> 180.0)
    decode = 1 / scale                        # LAT_DECODE = 1 / LAT_SCALE
    D = FPVal(decode, D64)
    x = FP('x', D64)
    TOP = FPVal(top, D64)
    xe = If(fpEQ(x, TOP), fpNextDown(TOP), x) if False else If(fpEQ(x, TOP), FPVal(float.fromhex(top.hex()) - 0.0, D64), x)
    # nextDown(top) as an explicit constant
    import math
    nd = math.nextafter(top, 0.0)
    xe = If(fpEQ(x, TOP), FPVal(nd, D64), x)
    q = fpDiv(rne, xe, D)
    c = fpRoundToIntegral(RTP(), q)
    MAXI = FPVal(2147483647.0, D64)
    j = If(fpGEQ(c, FPVal(2147483648.0, D64)), MAXI, c)   # (int) saturating cast, upper side
    dec = fpMul(rne, j, D)
    dmax = 2147483647 * decode
    res = {}
    for q_name, extra in (("Q1", None), ("Q2", fpLEQ(x, FPVal(dmax, D64)))):
        s = SolverFor("QF_FP")
        s.set("timeout", 300000)
        s.add(fpGEQ(x, FPVal(-top, D64)), fpLEQ(x, TOP), Not(fpIsNaN(x)))
        s.add(fpLT(dec, x))
        if extra is not None:
            s.add(extra)
        r = s.check()
        line = f"{name} {q_name}: {r}"
        if r == sat:
            m = s.model()
            xv = m.eval(x)
            line += f"  x={xv}  (as double {float(eval_fp(xv))!r})"
        print(line, flush=True)
        res[q_name] = str(r)
    return res

def eval_fp(v):
    # convert a Z3 FP numeral to a Python float via its IEEE bits
    import struct
    s = v.sign(); e = v.exponent_as_long(); sig = v.significand_as_long()
    bits = (1 if s else 0) << 63 | (e + 1023) << 52 | sig
    return struct.unpack('>d', bits.to_bytes(8, 'big'))[0]

if __name__ == "__main__":
    out = {}
    out["lat"] = run("LAT", 90.0, 180)
    out["lon"] = run("LON", 180.0, 360)
    print(out)
