"""
SMT model (QF_FP, IEEE-754 binary64, round-to-nearest-even) of the non-pole branch of
Rectangle.fromPointDistance (Rectangle.java:143-165) and of SloppyMath.cos/sin
(SloppyMath.java:92-114, 203-205), used as a counterexample engine for the question:

    For a legal centre latitude lat in [0, 90] and radius >= 0 that take the non-pole branch
    (minLat > MIN_LAT_RADIANS and maxLat < MAX_LAT_RADIANS), can the asin argument
    sin(radDistance) / cos(radLat) be > 1 (so asin returns NaN, the box gets NaN longitudes and the
    Rectangle constructor throws IllegalArgumentException)?

Mathematically the argument is < 1 on this branch (|radLat| + radDistance < pi/2), and the FP
precondition implies PIO2 - rd > radLat exactly; so a model needs SloppyMath.cos to be computed
larger at the larger argument (non-monotone), which can only happen where its table index changes.
Query per table knot i: radLat has table index i-1, |rd - PIO2| has table index i.

File:line mapping (lucene/core/src/java/... at a9779ef3c193):
  radLat        = Math.toRadians(centerLat)                       Rectangle.java:147 (JDK: angdeg * DEGREES_TO_RADIANS)
  rd            = (radiusMeters + 7E-2) / EARTH_MEAN_RADIUS_METERS Rectangle.java:150
  minLat/maxLat = radLat -/+ rd                                    Rectangle.java:151-152
  branch        = minLat > MIN_LAT_RADIANS && maxLat < MAX_LAT_RADIANS   Rectangle.java:156
  arg           = sin(rd) / cos(radLat)                            Rectangle.java:157 (SloppyMath.sin/cos)
  sin(a)        = cos(a - PIO2)                                    SloppyMath.java:203-205
  cos(a)        = table + 4th-order Taylor                         SloppyMath.java:92-114
  asin(arg>1)   = NaN                                              SloppyMath.java:156-177
  NaN lon -> IAE                                                   Rectangle.java:87-90 (checkLongitude)
Constants and tables: model/sloppy_cos_constants.json, produced by harness/src/SloppyCosConstants.java
from the same expressions as SloppyMath's static initializer and checked bit-for-bit against the real
SloppyMath.cos (5.2M arguments, 0 mismatches; logs/sloppy_cos_replica.log).

Usage: python3 -I box_nan_smt.py CONSTANTS.json KNOT_FROM KNOT_TO TIMEOUT_MS [--smt2-dir DIR]
Prints one JSON line per knot: {"knot": i, "result": "sat|unsat|unknown", "lat_bits", "radius_bits", ...}
Every 'sat' model must be replayed on the real jar (harness/src/BoxNaNReplay.java).
"""
import json
import struct
import sys

from z3 import (FP, FPVal, Float64, RNE, Solver, sat, unsat, fpAdd, fpSub, fpMul, fpDiv, fpNeg,
                fpAbs, fpLT, fpLEQ, fpGT, fpGEQ, fpIsNaN, fpIsInf, Not, And, BitVecVal, fpBVToFP, fpToIEEEBV,
                is_fp_value)

D = Float64()
rm = RNE()


def fromhex(h):
    return fpBVToFP(BitVecVal(int(h, 16), 64), D)


def dval(x):
    return FPVal(x, D)


def main():
    consts = json.load(open(sys.argv[1]))
    k_from, k_to, tmo = int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
    smt2_dir = None
    if len(sys.argv) > 6 and sys.argv[5] == '--smt2-dir':
        smt2_dir = sys.argv[6]
    C = {k: fromhex(v) for k, v in consts.items() if isinstance(v, str)}
    cosTab = consts['cosTab']
    sinTab = consts['sinTab']

    def sloppy_cos(x, idx):
        """SloppyMath.cos for an argument x >= 0 whose table index is idx (constraint returned)."""
        t = fpAdd(rm, fpMul(rm, x, C['SIN_COS_INDEXER']), dval(0.5))
        idx_ok = And(fpLEQ(dval(float(idx)), t), fpLT(t, dval(float(idx + 1))))
        fi = dval(float(idx))
        delta = fpSub(rm, fpSub(rm, x, fpMul(rm, fi, C['SIN_COS_DELTA_HI'])), fpMul(rm, fi, C['SIN_COS_DELTA_LO']))
        ic = fromhex(cosTab[idx])
        is_ = fromhex(sinTab[idx])
        inner4 = fpMul(rm, fpMul(rm, delta, ic), C['ONE_DIV_F4'])
        inner3 = fpAdd(rm, fpMul(rm, is_, C['ONE_DIV_F3']), inner4)
        inner2 = fpAdd(rm, fpMul(rm, fpNeg(ic), C['ONE_DIV_F2']), fpMul(rm, delta, inner3))
        inner1 = fpAdd(rm, fpNeg(is_), fpMul(rm, delta, inner2))
        val = fpAdd(rm, ic, fpMul(rm, delta, inner1))
        return val, idx_ok

    for knot in range(k_from, k_to + 1):
        lat = FP('lat', D)
        radius = FP('radius', D)
        s = Solver()
        s.set('timeout', tmo)
        s.add(fpLEQ(dval(0.0), lat), fpLEQ(lat, dval(90.0)))
        s.add(fpLEQ(dval(0.0), radius), Not(fpIsInf(radius)), Not(fpIsNaN(radius)))
        radLat = fpMul(rm, lat, C['DEGREES_TO_RADIANS'])
        rd = fpDiv(rm, fpAdd(rm, radius, C['PAD']), C['EARTH_MEAN_RADIUS_METERS'])
        minLat = fpSub(rm, radLat, rd)
        maxLat = fpAdd(rm, radLat, rd)
        s.add(fpGT(minLat, fpNeg(C['MAX_LAT_RADIANS'])), fpLT(maxLat, C['MAX_LAT_RADIANS']))
        a2 = fpAbs(fpSub(rm, rd, C['PIO2']))  # SloppyMath.cos negates a negative argument
        sinv, ok2 = sloppy_cos(a2, knot)
        cosv, ok1 = sloppy_cos(radLat, knot - 1)
        s.add(ok1, ok2)
        arg = fpDiv(rm, sinv, cosv)
        s.add(fpGT(arg, dval(1.0)))
        if smt2_dir:
            with open(f'{smt2_dir}/box_nan_knot{knot}.smt2', 'w') as f:
                f.write('(set-logic QF_FP)\n' + s.to_smt2())
        r = s.check()
        out = {'knot': knot, 'result': str(r)}
        if r == sat:
            m = s.model()
            lb = m.eval(fpToIEEEBV(lat), model_completion=True).as_long()
            rb = m.eval(fpToIEEEBV(radius), model_completion=True).as_long()
            out['lat_bits'] = f'{lb:016x}'
            out['radius_bits'] = f'{rb:016x}'
            out['lat'] = repr(struct.unpack('>d', lb.to_bytes(8, 'big'))[0])
            out['radius'] = repr(struct.unpack('>d', rb.to_bytes(8, 'big'))[0])
        print(json.dumps(out), flush=True)


if __name__ == '__main__':
    main()
