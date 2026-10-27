"""SMT-7: proof obligations of the COSINE normalisation chain, bit-precise binary32/binary64.

Code (main 7cecf79523):
  KnnFloatVectorField: rejects v only if VectorUtil.isZeroVector(v) (every component == 0)  [:53-56,:146-150]
  Lucene104ScalarQuantizedVectorsWriter.FieldWriter.accumulate (COSINE)                      [:767-775]
      dp = VectorUtil.dotProduct(v, v)            // float, scalar loop res = fma(v[i], v[i], res)
      divisor = (float) Math.sqrt(dp)
      dimensionSums[i] += v[i] / divisor
  computeCentroid: c[i] = dimensionSums[i] / count; VectorUtil.l2normalize(c)              [:786-797]
      -> DefaultVectorUtilSupport.l2normalize: throws IAE if dotProduct(c,c) == 0           [:479-498]
  VectorUtil.cosine (float, dims <= 32): sum/norm1/norm2 = fma loops in float,
      return (float) (sum / Math.sqrt((double) norm1 * (double) norm2))                     [Default:79-117]
      and VectorUtil.cosine asserts Float.isFinite(r)                                        [VectorUtil:104-105]
The scalar `fma` is Math.fma when Constants.HAS_FAST_SCALAR_FMA, else a*b+c; both are modelled.
"""
import sys, time
sys.path.insert(0, __file__.rsplit('/', 1)[0])
from fpjava import *
from z3 import Solver, sat, unsat, fpAbs

res = []
USE_FMA = True


def run(name, cs, expect, timeout_ms=600000):
    s = Solver()
    s.set("timeout", timeout_ms)
    s.add(*cs)
    t = time.time()
    r = s.check()
    dt = time.time() - t
    tag = ("PROVED" if r == unsat else "CEX/WITNESS" if r == sat else "UNKNOWN")
    line = f"[{tag}] {name} ({r}, {dt:.1f}s; expected {expect})"
    if r == sat:
        m = s.model()
        line += "\n      model: " + ", ".join(f"{d.name()}={m[d]}" for d in m.decls())
    print(line, flush=True)
    res.append((name, str(r), round(dt, 1)))


def fma(a, b, c):
    return fpFMA(rm, a, b, c) if USE_FMA else fpAdd(rm, fpMul(rm, a, b), c)


def dot(u, v):
    acc = c32(0.0)
    for a, b in zip(u, v):
        acc = fma(a, b, acc)
    return acc


def nonzero_vec(v):  # NOT VectorUtil.isZeroVector(v): some component != 0 (IEEE !=, so -0 counts as 0)
    return Or(*[Not(fpIsZero(c)) for c in v])


def jsqrt_f(x):  # (float) Math.sqrt((double) x)
    return to32(fpSqrt(rm, to64(x)))


for USE_FMA in (True, False):
    tagm = "fma" if USE_FMA else "mul+add"
    print(f"===== scalar fma model: {tagm}", flush=True)
    x0, x1 = f32("x0"), f32("x1")
    v = [x0, x1]
    dp = dot(v, v)
    div = jsqrt_f(dp)
    run(f"[{tagm}] PO-A1 'accumulate divisor > 0' for every vector KnnFloatVectorField accepts (dim 2)",
        [finite(x0), finite(x1), nonzero_vec(v), Not(fpGT(div, c32(0.0)))], "sat (refuted)")
    run(f"[{tagm}] PO-A1 witness with one exact-zero component -> 0/0 = NaN enters dimensionSums",
        [finite(x0), finite(x1), nonzero_vec(v), fpIsZero(x1), fpIsNaN(fpDiv(rm, x1, div))], "sat")
    # positive version: once the computed squared norm is a normal float, normalisation is safe
    q0, q1 = fpDiv(rm, x0, div), fpDiv(rm, x1, div)
    run(f"[{tagm}] PO-A1' dp normal & finite -> divisor in (0,inf) and |v_i/divisor| <= 1 + 2^-21",
        [finite(x0), finite(x1), fpIsNormal(dp),
         Not(And(fpGT(div, c32(0.0)), finite(div),
                 fpLEQ(fpAbs(q0), c32(1.0 + 2.0 ** -21)), fpLEQ(fpAbs(q1), c32(1.0 + 2.0 ** -21))))],
        "unsat (proved)")
    # exact underflow threshold for a 1-hot vector {x, 0}
    run(f"[{tagm}] threshold: some x > 2^-75 with fl(x*x) == 0 ?",
        [finite(x0), fpGT(x0, c32(2.0 ** -75)), fpIsZero(dot([x0], [x0]))], "unsat")
    run(f"[{tagm}] threshold: some 0 < x <= 2^-75 with fl(x*x) == 0 ?",
        [fpGT(x0, c32(0.0)), fpLEQ(x0, c32(2.0 ** -75)), fpIsZero(dot([x0], [x0]))], "sat")

    # PO-A2: computeCentroid's l2normalize precondition (sum of normalised vectors != 0)
    u = [f32("u0"), f32("u1")]
    w = [f32("w0"), f32("w1")]

    def normed(vec):
        d = jsqrt_f(dot(vec, vec))
        return [fpDiv(rm, c, d) for c in vec]

    nu, nw = normed(u), normed(w)
    sums = [fpAdd(rm, nu[i], nw[i]) for i in range(2)]
    pre = [finite(c) for c in u + w] + [fpIsNormal(dot(u, u)), fpIsNormal(dot(w, w))]
    run(f"[{tagm}] PO-A2 'centroid of 2 well-scaled COSINE vectors is non-zero' (dim 2)",
        pre + [fpIsZero(sums[0]), fpIsZero(sums[1])], "sat (refuted: antipodal pair)")
    # can it happen WITHOUT the two inputs being exactly antiparallel? (rounded cross products differ)
    run(f"[{tagm}] PO-A2 refuted by a NON-antiparallel pair? (fl(u0*w1) != fl(u1*w0))",
        pre + [fpIsZero(sums[0]), fpIsZero(sums[1]),
               Not(fpEQ(fpMul(rm, u[0], w[1]), fpMul(rm, u[1], w[0])))], "sat or unsat")

    # PO-C: VectorUtil.cosine(float[],float[]) result finite (its own assert) for accepted inputs
    a = [f32("a0"), f32("a1")]
    b = [f32("b0"), f32("b1")]
    s_ = dot(a, b)
    n1 = dot(a, a)
    n2 = dot(b, b)
    cosr = to32(fpDiv(rm, to64(s_), fpSqrt(rm, fpMul(rm, to64(n1), to64(n2)))))
    pre_c = [finite(c) for c in a + b] + [nonzero_vec(a), nonzero_vec(b)]
    run(f"[{tagm}] PO-C 'VectorUtil.cosine is finite' for non-zero finite inputs (dim 2)",
        pre_c + [Not(finite(cosr))], "sat (refuted)")
    run(f"[{tagm}] PO-C' norms normal & finite -> cosine finite and |cos| <= 1 + 2^-20",
        pre_c + [fpIsNormal(n1), fpIsNormal(n2), finite(n1), finite(n2),
                 Not(And(finite(cosr), fpLEQ(fpAbs(cosr), c32(1.0 + 2.0 ** -20))))], "unsat (proved)")
    run(f"[{tagm}] can float cosine exceed 1 at all (well-scaled inputs)?",
        pre_c + [fpIsNormal(n1), fpIsNormal(n2), finite(n1), finite(n2), fpGT(cosr, c32(1.0))], "sat or unsat")

print("SUMMARY", res)
