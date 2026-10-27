"""SMT-2/3: bit-precise (binary32/binary64) analysis of one component of
OptimizedScalarQuantizer.scalarQuantize (OptimizedScalarQuantizer.java:220-231) and deQuantize (:253-267).

Per component, Java computes (a = lowerInterval, b = upperInterval, all float):
    nSteps     = (float) ((1 << bits) - 1)
    step       = (b - a) / nSteps                     // float
    xi         = (float) clamp((double) x, a, b)      // == Math.min(Math.max(x,a),b), exact
    assignment = Math.round((xi - a) / step)          // float ops, Math.round(float) -> int
    dest[h]    = (byte) assignment
deQuantize: step' = (b - a) / nSteps (float division, widened); r = (double)k * step' + a (double).

Encoding without reals (pure QF_FP, decidable by bit-blasting):
  * Math.round(q) < 0      <=> q < -1/2          (floor(q + 1/2) < 0)
  * Math.round(q) > n      <=> q >= n + 1/2      (also covers +inf -> Integer.MAX_VALUE)
  * Math.round(NaN) = 0 (in range)
  * for q >= 0, Math.round(q) = roundToIntegral(RNA, q)  [validated exhaustively against the JDK
    for all non-negative floats < 256 by ../junit/src/RoundModelCheck.java]
  * the error |r - x| and the bound C*step are computed in a 13/400-bit FP sort, wide enough that
    the subtraction of a double and a float and the product C*step are exact.
R1 (range): 0 <= assignment <= nSteps.
R2 (error): for a <= x <= b, |r - x| <= C * step
"""
import sys, time
sys.path.insert(0, __file__.rsplit('/', 1)[0])
from fpjava import *
from z3 import Solver, sat, unsat, FPSort, fpRoundToIntegral, RNA, fpAbs

MODE = sys.argv[1] if len(sys.argv) > 1 else "all"
BITS = [int(b) for b in sys.argv[2:]] or [1, 2, 4, 7, 8]
W = FPSort(13, 400)
res = []


def run(name, cs, timeout_ms=1200000):
    s = Solver()
    s.set("timeout", timeout_ms)
    s.add(*cs)
    t = time.time()
    r = s.check()
    dt = time.time() - t
    tag = "PROVED" if r == unsat else ("CEX" if r == sat else "UNKNOWN")
    line = f"[{tag}] {name} ({r}, {dt:.1f}s)"
    if r == sat:
        m = s.model()
        line += "\n      model: " + ", ".join(f"{d.name()}={m[d]}" for d in m.decls())
    print(line, flush=True)
    res.append((name, str(r), round(dt, 1)))
    return r


for bits in BITS:
    nint = (1 << bits) - 1
    a, b, x = f32("a"), f32("b"), f32("x")
    n = c32(float(nint))
    step = fpDiv(rm, fpSub(rm, b, a), n)
    xi = jmin(jmax(x, a), b)
    q = fpDiv(rm, fpSub(rm, xi, a), step)
    out_of_range = And(Not(fpIsNaN(q)), Or(fpLT(q, c32(-0.5)), fpGEQ(q, c32(nint + 0.5))))
    print(f"== bits={bits} (nSteps={nint})", flush=True)
    if MODE in ("all", "r1"):
        run(f"R1 bits={bits}: 0 <= assignment <= nSteps for ALL finite a,b,x (any order of a,b)",
            [finite(a), finite(b), finite(x), out_of_range])
        run(f"R1' bits={bits}: same, assuming NOT(step == 0 && b > a)",
            [finite(a), finite(b), finite(x), Not(And(fpIsZero(step), fpGT(b, a))), out_of_range])
    if MODE in ("all", "r1n"):
        run(f"R1'' bits={bits}: same, assuming step is a normal float or a == b",
            [finite(a), finite(b), finite(x), Or(fpIsNormal(step), fpEQ(a, b)), out_of_range])
    if MODE not in ("all", "r2"):
        continue
    # R2: reconstruction error for in-range x (then q >= 0, so Math.round == RNA rounding)
    kf = fpRoundToIntegral(RNA(), q)
    r = fpAdd(rm, fpMul(rm, to64(kf), to64(step)), to64(a))
    diff = fpAbs(fpSub(rm, fpToFP(rm, r, W), fpToFP(rm, x, W)))
    pre = [finite(a), finite(b), fpLEQ(a, x), fpLEQ(x, b), finite(fpSub(rm, b, a)), fpIsNormal(step)]
    for label, C in [("1/2", 0.5), (f"1/2+{nint + 2}*2^-22", 0.5 + (nint + 2) / 2.0 ** 22)]:
        bound = fpMul(rm, FPVal(C, W), fpToFP(rm, step, W))
        run(f"R2 bits={bits}: a<=x<=b, step normal -> |deq(q(x)) - x| <= ({label})*step",
            pre + [fpGT(diff, bound)])

print("SUMMARY", res)
