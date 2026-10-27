"""osq-quantization-new FP-SMT precondition chaining for FLOAT16 inputs of the Lucene104 OSQ pipeline (Z3 4.12.2, QF_FP/QF_BV/LIA).

Model element -> Java line (Lucene main @ ea57360912ed):
  h (Float16, finite)            KnnFloat16VectorField:69-73 + VectorUtil.checkFiniteFloat16:505-513 (rejects exp==0x1F)
  h != +-0 for COSINE            VectorUtil.isZeroVectorFloat16:529-536 ((value & 0x7FFF) != 0)
  x = (float) h                  Float.float16ToFloat in Float16FieldWriter.inflate (Writer:888-892),
                                 Float16AsFloatVectorValues.vectorValue (Writer:1044-1050), scorer (Scorer:88-91)
  dp = sum fl(x_i*x_i)           FieldWriter.accumulate dotProduct (Writer:769), any order, with or without FMA
  divisor = (float)sqrt(dp)      Writer:770 ; dimensionSums[i] += x_i / divisor (Writer:773)
  deQuantize(short[])            OptimizedScalarQuantizer:283-297: Float.floatToFloat16((float)(code*step + a + c))
  int4Unpack lanes               PanamaVectorUtilSupport.int4Unpack (LSHR 4 / AND 0x0F per byte lane, scalar tail),
                                 DefaultVectorUtilSupport.int4Unpack:222-227
  packed lengths                 QuantizedByteVectorValues.ScalarEncoding.getDiscreteDimensions/getDocPackedLength,
                                 OptimizedScalarQuantizer.discretize, Scorer:228, OffHeap*FloatVectorValues:92-96 / :85-92
Every result line is "PO-name: sat/unsat (expected ...) seconds".
"""
import sys, time
from z3 import *

F16 = FPSort(5, 11)
F32 = Float32()
F64 = Float64()
rm = RNE()
results = []


def run(name, constraints, expect, show=None, timeout_ms=600000):
    s = Solver()
    s.set("timeout", timeout_ms)
    s.add(constraints)
    t = time.time()
    r = s.check()
    dt = time.time() - t
    line = f"{name}: {r} (expected {expect}) {dt:.2f}s"
    if r == sat and show:
        m = s.model()
        line += "  witness: " + ", ".join(f"{str(v)}={m.eval(v, model_completion=True)}" for v in show)
    print(line, flush=True)
    results.append((name, str(r), expect))


def fin(x):
    return And(Not(fpIsNaN(x)), Not(fpIsInf(x)))


# ---- PO-H1: the COSINE accumulate/normalize preconditions hold for every legal fp16 vector -----------------
h = FP("h", F16)
x = fpToFP(rm, h, F32)  # exact widening
xx = fpMul(rm, x, x)
legal_nonzero = And(fin(h), Not(fpIsZero(h)))
# PO-H1a: the square of any non-zero finite fp16 value is a normal, non-zero float32 (no underflow, LFV-046's root)
run("PO-H1a fp16 square never underflows", [legal_nonzero, Or(fpIsZero(xx), fpIsSubnormal(xx))], "unsat", [h])
# PO-H1b: and never exceeds 65504^2 (so a 1024-term sum stays far below Float.MAX_VALUE)
run("PO-H1b fp16 square <= 65504^2", [fin(h), fpGT(xx, FPVal(65504.0 * 65504.0, F32))], "unsat", [h])
# PO-H1c: summing non-negative float32 terms (plain add or FMA) never decreases the running sum and never drops
# below the new term, so dp >= max_i fl(x_i^2) >= 2^-48 > 0 for any summation order / lane split.
a = FP("a", F32); b = FP("b", F32); y = FP("y", F32)
nonneg = lambda v: And(fin(v), Not(fpIsNegative(v)))
run("PO-H1c fl(a+b) >= max(a,b) for a,b >= 0", [nonneg(a), nonneg(b), fin(fpAdd(rm, a, b)),
     Or(fpLT(fpAdd(rm, a, b), a), fpLT(fpAdd(rm, a, b), b))], "unsat", [a, b])
run("PO-H1d fma(y,y,acc) >= max(acc, fl(y*y)) for acc >= 0", [fin(y), nonneg(a), fin(fpFMA(rm, y, y, a)),
     Or(fpLT(fpFMA(rm, y, y, a), a), fpLT(fpFMA(rm, y, y, a), fpMul(rm, y, y)))], "unsat", [y, a])
# PO-H1e: hence divisor = (float) sqrt((double) dp) >= 2^-24 > 0 and x_i / divisor is finite with |.| <= 1 + 2^-23
dp = FP("dp", F32)
div = fpToFP(rm, fpSqrt(rm, fpToFP(rm, dp, F64)), F32)
run("PO-H1e divisor normal and > 0 when dp >= 2^-48", [fin(dp), fpGEQ(dp, FPVal(2.0 ** -48, F32)),
     Or(fpIsZero(div), fpIsSubnormal(div), Not(fin(div)))], "unsat", [dp])
q = fpDiv(rm, x, div)
run("PO-H1f |x/divisor| <= 1+2^-23 when divisor >= fl(sqrt(fl(x*x)))", [fin(h), Not(fpIsZero(h)), fin(dp),
     fpGEQ(dp, xx), fpGT(fpAbs(q), FPVal(1.0 + 2.0 ** -23, F32))], "unsat", [h, dp])

# ---- PO-H2: deQuantize(short[]) narrows without saturation: a finite fp16 vector can reconstruct to +-Inf ---
# Model of one component: xi = (double) code * step + lower (double); out = floatToFloat16((float) (xi + c)).
# Reachability constraints (deliberately conservative): the centered value v = orig - c lies INSIDE [lo, up] (no
# clamping), `code` is its nearest grid point, the interval is no wider than the centered data can be (|lo|,|up| <=
# 2*65504), and |c| <= 65504 (c is a mean of fp16 values). The concrete jar run (repro) is the real witness; this
# query only shows the overflow needs no exotic interval.
code = BitVec("code", 32)
lo = FP("lo", F32); up = FP("up", F32); c = FP("c", F32); orig = FP("orig", F16)
origf = fpToFP(rm, orig, F32)
v = fpSub(rm, origf, c)
for bits, maxorig in ((1, 61000.0), (4, 65504.0), (8, 65504.0)):
    nsteps = (1 << bits) - 1
    step = fpDiv(rm, fpToFP(rm, fpSub(rm, up, lo), F64), FPVal(float(nsteps), F64))
    codef = fpSignedToFP(rm, code, F64)
    xi = fpAdd(rm, fpMul(rm, codef, step), fpToFP(rm, lo, F64))
    out16 = fpToFP(rm, fpToFP(rm, fpAdd(rm, xi, fpToFP(rm, c, F64)), F32), F16)
    cell = And(UGE(code, 0), ULE(code, nsteps), fpLT(lo, up), fin(lo), fin(up), fin(c),
               fpLEQ(fpAbs(c), FPVal(65504.0, F32)),
               fpGEQ(lo, FPVal(-131008.0, F32)), fpLEQ(up, FPVal(131008.0, F32)),
               fpLEQ(lo, v), fpLEQ(v, up),
               fpLEQ(fpAbs(fpSub(rm, fpToFP(rm, v, F64), xi)), fpDiv(rm, step, FPVal(2.0, F64))))
    run(f"PO-H2a ({bits} bits) in-interval fp16 component |orig|<={maxorig:g} reconstructs to +-Inf",
        [fin(orig), cell, fpIsInf(out16), fpLEQ(fpAbs(origf), FPVal(maxorig, F32))], "sat (the defect)",
        [orig, c, lo, up, code])
# PO-H2b: Float.floatToFloat16 overflows exactly at |y| >= 65520 (RNE), so any saturation to +-65504 removes it
yy = FP("yy", F32)
run("PO-H2b fp16(y) is Inf iff |y| >= 65520", [fin(yy), Xor(fpIsInf(fpToFP(rm, yy, F16)), fpGEQ(fpAbs(yy), FPVal(65520.0, F32)))], "unsat", [yy])
# PO-H2c (fix lemma): clamping y to [-65504, 65504] never moves it away from a legal fp16 value x and keeps it finite
xc = FP("xc", F16)
xcf = fpToFP(rm, xc, F32)
yc = fpMin(fpMax(yy, FPVal(-65504.0, F32)), FPVal(65504.0, F32))
run("PO-H2c clamp(y) finite in fp16 and |clamp(y)-x| <= |y-x|", [fin(yy), fin(xc),
     Or(Not(fin(fpToFP(rm, yc, F16))), fpGT(fpAbs(fpSub(RTZ(), yc, xcf)), fpAbs(fpSub(RTZ(), yy, xcf))))], "unsat", [yy, xc])

# ---- PO-U: vectorized nibble unpack equals the scalar unpack (lane semantics + index coverage) --------------
bb = BitVec("bb", 8)
# Vector API LSHR on a byte lane: (a & 0xFF) >>> (n & 7); scalar: (byte) ((packed[i] >> 4) & 0x0F) on the sign-extended int
lshr_lane = LShR(bb, 4)
scalar_hi = Extract(7, 0, (SignExt(24, bb) >> 4) & 0x0F)
run("PO-U1 high nibble: LSHR lane == scalar", [lshr_lane != scalar_hi], "unsat", [bb])
run("PO-U2 low nibble: AND lane == scalar", [(bb & 0x0F) != Extract(7, 0, SignExt(24, bb) & 0x0F)], "unsat", [bb])
ln, j, L = Ints("len j L")
bound = ln - ln % L  # ByteVector species loopBound for a power-of-two lane count
k = Int("k")
cov = []
for lanes in (16, 32, 64):  # SPECIES 128/256/512 bits of bytes
    cons = [L == lanes, ln >= 0, ln <= 2 ** 31 - 1]
    # every vector iteration i = k*L < bound stays in bounds: i + L <= len (reads) and len + i + L <= 2*len (writes)
    run(f"PO-U3 L={lanes} vector chunk in bounds", cons + [k >= 0, k * L < bound, Or(k * L + L > ln)], "unsat", [ln, k])
    # every index j in [0,len) is written by exactly one chunk or by the tail
    run(f"PO-U4 L={lanes} coverage", cons + [j >= 0, j < ln, Not(Or(j >= bound, And(j < bound, (j / L) * L <= j, j < (j / L) * L + L, (j / L) * L < bound)))], "unsat", [ln, j])
# packed-length bookkeeping: scorer buffer discretize(dim,2) == 2*getDocPackedLength(dim) for PACKED_NIBBLE (all dims),
# and the dropped-raw views' new byte[dim] equals it iff dim is even (LFV-065, known)
dim = Int("dim")
disc = ((dim * 4 + 7) / 8) * 8 / 4
packed = (disc * 4 + 7) / 8
run("PO-U5 scorer unpack buffer == 2*packed (dims 1..1024)", [dim >= 1, dim <= 1024, ((dim + 1) / 2) * 2 != 2 * packed], "unsat", [dim])
run("PO-U6 view buffer new byte[dim] == 2*packed iff dim even", [dim >= 1, dim <= 1024, (dim == 2 * packed) != (dim % 2 == 0)], "unsat", [dim])

bad = [r for r in results if not r[2].startswith(r[1])]
print(f"SUMMARY {len(results)} obligations, {len(bad)} unexpected: {bad}")
sys.exit(1 if bad else 0)
