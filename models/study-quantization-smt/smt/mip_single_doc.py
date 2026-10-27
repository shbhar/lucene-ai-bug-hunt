"""SMT-6: bit-precise model of the quantized MAXIMUM_INNER_PRODUCT score of a dim-1, single-document
segment (the shape of every tiny NRT flush), versus the float score of the same pair.

Derivation of the model from the code (main 7cecf79523), float32 unless noted:
 * computeCentroid (Writer:786-797): one vector -> c = x / 1 = x.
 * doc: scalarQuantize (OSQ:187-237): centered x - c = 0, norm2 = 0, so optimizeIntervals returns at
   'scale not finite' (:338-341); a = b = 0; code = round(0/0 = NaN) = 0;
   additionalCorrection = centroidDot = 0f + x*c  (:203, plain mul then add).
 * centroidDP = VectorUtil.dotProduct(c, c) = fma(x, x, 0) (Writer:173; scalar path for dim 1).
 * query q: centered qc = q - x; min = max = mean = qc and std = 0, so the initial interval is
   [qc, qc]; optimizeIntervals: stepInv = 15/0 = inf, k = round(0*inf = NaN) = 0, s = 0,
   m1 = m2 = 0 -> det = 0 -> return (:365-367). code 0; additionalCorrection = 0f + q*x.
 * quantizedScore (Scorer:290-311): with ax = lx = ly = 0, x1 = y1 = qcDist = 0:
     score = ax*ay*1 + ay*lx*x1 + ax*ly*y1 + lx*ly*qcDist      (= 0 unless qc is +-inf)
     score += qAdd + xAdd - centroidDP                          (Java: score + ((qAdd + xAdd) - cDP))
     return scaleMaxInnerProductScore(score)
 * float path: MAXIMUM_INNER_PRODUCT.compare(q, x) = scaleMaxInnerProductScore(fma(q, x, 0)).
The model is cross-checked against the real jar by ../junit/src/TestQuantizationSmtFindings.java
(testMaxInnerProduct*), which indexes exactly these vectors.
"""
import sys, time
sys.path.insert(0, __file__.rsplit('/', 1)[0])
from fpjava import *
from z3 import Solver, sat, unsat, fpAbs

zero, one, mone = c32(0.0), c32(1.0), c32(-1.0)


def smips(v):
    return If(fpLT(v, zero), fpDiv(rm, one, fpAdd(rm, one, fpMul(rm, mone, v))), fpAdd(rm, v, one))


def run(name, cs, timeout_ms=600000):
    s = Solver()
    s.set("timeout", timeout_ms)
    s.add(*cs)
    t = time.time()
    r = s.check()
    dt = time.time() - t
    tag = "PROVED" if r == unsat else ("CEX/WITNESS" if r == sat else "UNKNOWN")
    line = f"[{tag}] {name} ({r}, {dt:.1f}s)"
    if r == sat:
        m = s.model()
        line += "\n      model: " + ", ".join(f"{d.name()}={m[d]}" for d in m.decls())

    print(line, flush=True)


x, q = f32("x"), f32("q")
ax = zero
ay = fpSub(rm, q, x)
lx = fpMul(rm, fpSub(rm, zero, zero), c32(1.0 / 255))
ly = fpMul(rm, fpSub(rm, ay, ay), c32(1.0 / 255))
x1 = y1 = qc = zero
t1 = fpMul(rm, fpMul(rm, ax, ay), one)
t2 = fpMul(rm, fpMul(rm, ay, lx), x1)
t3 = fpMul(rm, fpMul(rm, ax, ly), y1)
t4 = fpMul(rm, fpMul(rm, lx, ly), qc)
score0 = fpAdd(rm, fpAdd(rm, fpAdd(rm, t1, t2), t3), t4)
qAdd = fpAdd(rm, zero, fpMul(rm, q, x))
xAdd = fpAdd(rm, zero, fpMul(rm, x, x))
cdp = fpFMA(rm, x, x, zero)
score = fpAdd(rm, score0, fpSub(rm, fpAdd(rm, qAdd, xAdd), cdp))
quant = smips(score)
flt = smips(fpFMA(rm, q, x, zero))
fin = [finite(x), finite(q)]

run("M1: float MIP score finite but quantized MIP score NaN?", fin + [finite(flt), fpIsNaN(quant)])
run("M2: quantized NaN  ==>  fl(x*x)=+inf or fl(q-x) not finite or fl(q*x) not finite",
    fin + [fpIsNaN(quant), finite(xAdd), finite(ay), finite(qAdd)])
run("M2': conversely, |x| < 2^64 and |q - x|, |q*x| finite  ==>  quantized score finite",
    fin + [fpLT(fpAbs(x), c32(2.0 ** 64)), finite(ay), finite(qAdd), Not(finite(quant))])
# cancellation: (qAdd + xAdd) - cDP loses qAdd when x*x >> q*x, with harmless magnitudes
mod = [fpLEQ(fpAbs(x), c32(1e4)), fpGEQ(fpAbs(x), c32(1.0)), fpLEQ(fpAbs(q), c32(1.0)),
       fpGEQ(fpAbs(q), c32(1e-3))]
rel = fpDiv(rm, fpAbs(fpSub(rm, quant, flt)), fpAbs(flt))
run("M3: 1 <= |x| <= 1e4, 1e-3 <= |q| <= 1: quantized vs float MIP score differ by > 10% ?",
    fin + mod + [fpGT(rel, c32(0.1))])
run("M3': same box, can the quantized score of the doc that IS the centroid be off by > 50%?",
    fin + mod + [fpGT(rel, c32(0.5))])
run("M4: with re-association qAdd + (xAdd - cDP) the dim-1 single-doc score is EXACT (== float)",
    fin + [finite(flt), finite(ay), Not(fpEQ(smips(fpAdd(rm, score0, fpAdd(rm, qAdd, fpSub(rm, xAdd, cdp)))), flt))])
run("M4': same, assuming |x| < 2^64 (x*x finite): re-associated single-doc score == float score",
    fin + [fpLT(fpAbs(x), c32(2.0 ** 64)), finite(flt), finite(ay),
           Not(fpEQ(smips(fpAdd(rm, score0, fpAdd(rm, qAdd, fpSub(rm, xAdd, cdp)))), flt))])
