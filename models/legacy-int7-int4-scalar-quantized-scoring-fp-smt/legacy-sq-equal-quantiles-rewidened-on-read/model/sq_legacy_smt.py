"""legacy-int7-int4-scalar-quantized-scoring-fp-smt: QF_FP (binary32, RNE) model of the legacy (Lucene99, int7/int4) scalar quantizer and its scores.

Model element -> Java source (main @ a9779ef3c193, paths relative to the checkout):
  ctor(min,max,bits)        lucene/core/src/java/org/apache/lucene/util/quantization/ScalarQuantizer.java:91-112
      divisor = (1<<bits)-1   :101
      min==max widening       :102-105   (main only; absent from every release 9.9.0..10.5.2 and branch_10_6)
      scale = divisor/(max'-min')  :110 ; alpha = (max'-min')/divisor :111
  constMultiplier           ScalarQuantizer.java:180-182 (alpha*alpha)
  quantizeFloat(v)          lucene/core/src/java/org/apache/lucene/internal/vectorization/DefaultVectorUtilSupport.java:437-459
      dx=v-min; dxc=max(min,min(max,v))-min; r=round(scale*dxc); dxq=r*alpha;
      corr = min*(v - min/2) + (dx-dxq)*dxq
  EUCLIDEAN score           ScalarQuantizedVectorSimilarity.java:65-70, Lucene99ScalarQuantizedVectorScorer.java:161-166
                            1/(1 + (float)sq * cm)
  DOT/COSINE score          ScalarQuantizedVectorSimilarity.java:84-91 ; Lucene99ScalarQuantizedVectorScorer.java:198-207
                            max((1 + ((float)dot*cm + qo + vo))/2, 0)
  MIP score                 ScalarQuantizedVectorSimilarity.java:105-112 ; VectorUtil.java:444-449
  MemorySegment scorer      lucene/core/src/java25/.../Lucene99MemorySegmentScalarQuantizedVectorScorer.java:171-175
                            scaler((float)s*cm + nodeOffset + queryOffset)   (addition order differs: vectorutil-providers twin)

Every query prints sat/unsat and, for sat, a model that is then re-executed as Java floats by
harness/ReplaySmt.java (no mixed FP/real arithmetic is used here).
"""
import sys, os, time, json
sys.path.insert(0, os.path.join(os.environ.get("FVROOT", ""), "smt"))
from fpjava import *  # noqa: F401,F403  (f32, c32, jmax, jmin, finite, rm, F32, ...)
from z3 import (Solver, sat, unsat, BitVec, BitVecVal, fpToFP, fpToSBV, fpBVToFP, Int, IntVal, Int2BV,
                fpFPToFP, fpSignedToFP, simplify, fpIsInf, fpIsNaN, fpIsZero, fpEQ, fpLT, fpLEQ, fpGT, fpGEQ,
                fpAdd, fpSub, fpMul, fpDiv, fpNeg, And, Or, Not, If, ULE, ULT, set_param, fpToIEEEBV, BV2Int,
                Extract)
import struct

set_param("timeout", 180000)

RESULTS = []


def fval(m, x):
    v = m.eval(x, model_completion=True)
    bv = m.eval(fpToIEEEBV(x), model_completion=True).as_long()
    return struct.unpack(">f", struct.pack(">I", bv))[0], hex(bv)


def run(name, s, show):
    t = time.time()
    r = s.check()
    dt = time.time() - t
    rec = {"query": name, "result": str(r), "secs": round(dt, 2)}
    if r == sat:
        m = s.model()
        rec["model"] = {k: fval(m, v) for k, v in show.items()}
    RESULTS.append(rec)
    print(json.dumps(rec), flush=True)
    return rec


def i2f(i):
    """Java int -> float conversion (binary numeric promotion), RNE."""
    return fpSignedToFP(rm, i, F32)


def ctor(mn, mx, bits, widen=True):
    d = c32(float((1 << bits) - 1))
    eq = fpEQ(mn, mx)
    if widen:
        mn2 = If(eq, fpSub(rm, mn, d), mn)
        mx2 = If(eq, fpAdd(rm, mx, d), mx)
    else:
        mn2, mx2 = mn, mx
    rng = fpSub(rm, mx2, mn2)
    scale = fpDiv(rm, d, rng)
    alpha = fpDiv(rm, rng, d)
    return mn2, mx2, scale, alpha, fpMul(rm, alpha, alpha)


def legal_quantiles(mn, mx):
    # ScalarQuantizer ctor: throws on NaN/Inf; asserts max >= min
    return And(finite(mn), finite(mx), fpLEQ(mn, mx))


def main():
    for bits in (7, 4):
        d = float((1 << bits) - 1)
        mn, mx = f32("mn%d" % bits), f32("mx%d" % bits)
        mn2, mx2, scale, alpha, cm = ctor(mn, mx, bits)

        # S1a: min == max branch: widening rounds back, scale infinite (the plan's lead)
        s = Solver(); s.add(legal_quantiles(mn, mx), fpEQ(mn, mx), Or(fpIsInf(scale), fpIsNaN(scale)))
        run("S1a bits=%d min==max and scale non-finite (any q)" % bits, s, {"q": mn, "scale": scale, "alpha": alpha})
        # minimal |q| witness: ask for q < 2^31 (7 bits) / 2^28 (4 bits) -> expect unsat
        thr = 2.0 ** 31 if bits == 7 else 2.0 ** 28
        s = Solver(); s.add(legal_quantiles(mn, mx), fpEQ(mn, mx), Or(fpIsInf(scale), fpIsNaN(scale)),
                            fpLT(mn, c32(thr)), fpGT(mn, c32(-thr)))
        run("S1a' bits=%d min==max, scale non-finite and |q| < %g (expect unsat)" % (bits, thr), s, {"q": mn})
        s = Solver(); s.add(legal_quantiles(mn, mx), fpEQ(mn, mx), Or(fpIsInf(scale), fpIsNaN(scale)),
                            fpLEQ(mn, c32(thr)), fpGEQ(mn, c32(-thr)))
        run("S1a'' bits=%d min==max, scale non-finite and |q| <= %g (boundary)" % (bits, thr), s, {"q": mn})

        # S1b: range overflow: finite min < max with max-min = +Inf -> alpha = Inf, scale = 0
        s = Solver(); s.add(legal_quantiles(mn, mx), fpLT(mn, mx), fpIsInf(alpha))
        run("S1b bits=%d finite quantiles, alpha infinite" % bits, s, {"min": mn, "max": mx, "alpha": alpha, "scale": scale})

        # S1c: alpha finite but constMultiplier = alpha^2 = +Inf
        s = Solver(); s.add(legal_quantiles(mn, mx), fpLT(mn, mx), finite(alpha), fpIsInf(cm))
        run("S1c bits=%d alpha finite, alpha^2 infinite" % bits, s, {"min": mn, "max": mx, "alpha": alpha})
        # smallest range with cm = Inf: is there one with range <= 2.3e21 (7) / 2.8e20 (4)?
        lim = 2.3e21 if bits == 7 else 2.7e20
        s = Solver(); s.add(legal_quantiles(mn, mx), fpLT(mn, mx), fpIsInf(cm),
                            fpLEQ(fpSub(rm, mx, mn), c32(lim)))
        run("S1c' bits=%d alpha^2 infinite with range <= %g (expect unsat)" % (bits, lim), s, {"min": mn, "max": mx})

        # S1d: tiny range: scale = +Inf although min < max (codes then saturate to (byte)Integer.MAX_VALUE)
        s = Solver(); s.add(legal_quantiles(mn, mx), fpLT(mn, mx), fpIsInf(scale))
        run("S1d bits=%d min<max but scale infinite (tiny range)" % bits, s, {"min": mn, "max": mx, "scale": scale})
        # S1e: alpha^2 underflows to 0 although min < max -> EUCLIDEAN scores all 1
        s = Solver(); s.add(legal_quantiles(mn, mx), fpLT(mn, mx), fpIsZero(cm))
        run("S1e bits=%d min<max but constMultiplier == 0" % bits, s, {"min": mn, "max": mx, "alpha": alpha})

        # S1f: cross-version decode: release writer (no widening) vs main reader (widening), same stored (q,q)
        q = f32("q%d" % bits)
        r_mn, r_mx, r_scale, r_alpha, r_cm = ctor(q, q, bits, widen=False)
        m_mn, m_mx, m_scale, m_alpha, m_cm = ctor(q, q, bits, widen=True)
        # dequantized value of stored code 0 differs between writer (alpha=0 -> q) and main reader
        deq_r = fpAdd(rm, fpMul(rm, r_alpha, c32(0.0)), r_mn)
        deq_m = fpAdd(rm, fpMul(rm, m_alpha, c32(0.0)), m_mn)
        s = Solver(); s.add(finite(q), Not(fpEQ(deq_r, deq_m)))
        run("S1f bits=%d stored (q,q): writer-decoded code 0 != main-decoded code 0" % bits, s,
            {"q": q, "writer_decode": deq_r, "main_decode": deq_m, "main_alpha": m_alpha, "writer_scale": r_scale})
        s = Solver(); s.add(finite(q), fpEQ(deq_r, deq_m))
        run("S1f' bits=%d stored (q,q): decodes agree (expect only |q| huge)" % bits, s, {"q": q})

    # S2: score branches for a legal (finite scale, alpha, cm) quantizer
    for bits in (7, 4):
        mn, mx = f32("smn%d" % bits), f32("smx%d" % bits)
        mn2, mx2, scale, alpha, cm = ctor(mn, mx, bits)
        good = And(legal_quantiles(mn, mx), finite(scale), finite(alpha), finite(cm))
        # EUCLIDEAN: sq in [0, 1024*d^2]; score finite, in (0,1]
        sq = BitVec("sq%d" % bits, 32)
        dmax = 1024 * ((1 << bits) - 1) ** 2
        euc = fpDiv(rm, c32(1.0), fpAdd(rm, c32(1.0), fpMul(rm, i2f(sq), cm)))
        s = Solver(); s.add(good, ULE(sq, BitVecVal(dmax, 32)),
                            Or(Not(finite(euc)), fpLEQ(euc, c32(0.0)), fpGT(euc, c32(1.0))))
        run("S2-euc bits=%d legal quantizer: EUCLIDEAN score not in (0,1]" % bits, s,
            {"min": mn, "max": mx, "score": euc, "cm": cm})
        # monotone non-increasing in sq
        sq2 = BitVec("sqb%d" % bits, 32)
        euc2 = fpDiv(rm, c32(1.0), fpAdd(rm, c32(1.0), fpMul(rm, i2f(sq2), cm)))
        s = Solver(); s.add(good, ULE(sq, BitVecVal(dmax, 32)), ULE(sq2, BitVecVal(dmax, 32)), ULT(sq, sq2),
                            fpLT(euc, euc2))
        run("S2-euc-mono bits=%d EUCLIDEAN increases with distance (expect unsat)" % bits, s, {"cm": cm})
        # DOT/MIP: dot in [0, 1024*d^2]; offsets finite floats
        dot, dot2 = BitVec("dot%d" % bits, 32), BitVec("dotb%d" % bits, 32)
        qo, vo = f32("qo%d" % bits), f32("vo%d" % bits)
        adj = lambda x: fpAdd(rm, fpAdd(rm, fpMul(rm, i2f(x), cm), qo), vo)
        dp = lambda x: jmax(fpDiv(rm, fpAdd(rm, c32(1.0), adj(x)), c32(2.0)), c32(0.0))
        mip = lambda x: If(fpLT(adj(x), c32(0.0)),
                           fpDiv(rm, c32(1.0), fpAdd(rm, c32(1.0), fpMul(rm, c32(-1.0), adj(x)))),
                           fpAdd(rm, adj(x), c32(1.0)))
        rng = And(ULE(dot, BitVecVal(dmax, 32)), ULE(dot2, BitVecVal(dmax, 32)), ULT(dot, dot2))
        s = Solver(); s.add(good, finite(qo), finite(vo), rng, fpLT(dp(dot2), dp(dot)))
        run("S2-dot-mono bits=%d DOT score decreases as dot grows (expect unsat)" % bits, s, {"cm": cm})
        s = Solver(); s.add(good, finite(qo), finite(vo), rng, fpLT(mip(dot2), mip(dot)))
        run("S2-mip-mono bits=%d MIP score decreases as dot grows (expect unsat)" % bits, s, {"cm": cm})
        s = Solver(); s.add(good, finite(qo), finite(vo), ULE(dot, BitVecVal(dmax, 32)),
                            Or(fpIsNaN(mip(dot)), fpLT(mip(dot), c32(0.0))))
        run("S2-mip-sign bits=%d MIP score NaN or negative with finite offsets" % bits, s,
            {"cm": cm, "qo": qo, "vo": vo, "score": mip(dot)})
        # per-component correction finiteness for a legal quantizer and an in-range value
        v = f32("v%d" % bits)
        dx = fpSub(rm, v, mn2)
        dxc = fpSub(rm, jmax(mn2, jmin(mx2, v)), mn2)
        sd = fpMul(rm, scale, dxc)
        r = BitVec("r%d" % bits, 32)
        # Math.round(float) on [0, d]: r = floor(sd + 1/2) ; encode via bounds on r
        rf = i2f(r)
        round_ok = And(fpLEQ(rf, fpAdd(rm, sd, c32(0.5))), fpGT(fpAdd(rm, rf, c32(1.0)), fpAdd(rm, sd, c32(0.5))))
        dxq = fpMul(rm, rf, alpha)
        corr = fpAdd(rm, fpMul(rm, mn2, fpSub(rm, v, fpDiv(rm, mn2, c32(2.0)))), fpMul(rm, fpSub(rm, dx, dxq), dxq))
        s = Solver(); s.add(good, finite(v), fpGEQ(v, mn2), fpLEQ(v, mx2), ULE(r, BitVecVal((1 << bits) - 1, 32)),
                            round_ok, Not(finite(corr)))
        run("S2-corr bits=%d in-range component correction non-finite" % bits, s,
            {"min": mn, "max": mx, "v": v, "corr": corr})

    # S4: dequantize error for a legal quantizer: |alpha*r + min - v| <= alpha/2 (+ rounding slack) for in-range v
    for bits in (7, 4):
        mn, mx = f32("dmn%d" % bits), f32("dmx%d" % bits)
        mn2, mx2, scale, alpha, cm = ctor(mn, mx, bits)
        good = And(legal_quantiles(mn, mx), fpLT(mn, mx), finite(scale), finite(alpha), finite(cm),
                   fpGT(fpSub(rm, mx, mn), c32(1e-30)), fpLT(fpSub(rm, mx, mn), c32(1e30)))
        v = f32("dv%d" % bits)
        dxc = fpSub(rm, jmax(mn2, jmin(mx2, v)), mn2)
        sd = fpMul(rm, scale, dxc)
        r = BitVec("dr%d" % bits, 32)
        rf = i2f(r)
        round_ok = And(fpLEQ(rf, fpAdd(rm, sd, c32(0.5))), fpGT(fpAdd(rm, rf, c32(1.0)), fpAdd(rm, sd, c32(0.5))))
        deq = fpAdd(rm, fpMul(rm, alpha, rf), mn2)
        err = fpSub(rm, deq, v)
        absErr = If(fpLT(err, c32(0.0)), fpNeg(err), err)
        # bound: 0.5*alpha*(1 + 2^-10) + 2 ulp-ish slack of the magnitudes
        mag = jmax(If(fpLT(mn, c32(0.0)), fpNeg(mn), mn), If(fpLT(mx, c32(0.0)), fpNeg(mx), mx))
        bound = fpAdd(rm, fpMul(rm, alpha, c32(0.5 * (1 + 2 ** -10))), fpMul(rm, mag, c32(2 ** -21)))
        s = Solver(); s.add(good, finite(v), fpGEQ(v, mn), fpLEQ(v, mx), ULE(r, BitVecVal((1 << bits) - 1, 32)),
                            round_ok, fpGT(absErr, bound))
        run("S4 bits=%d in-range dequantize error > alpha/2 (+slack)" % bits, s,
            {"min": mn, "max": mx, "v": v, "deq": deq})

    out = os.environ.get("SMT_OUT")
    if out:
        with open(out, "w") as f:
            json.dump(RESULTS, f, indent=1)


if __name__ == "__main__":
    main()
