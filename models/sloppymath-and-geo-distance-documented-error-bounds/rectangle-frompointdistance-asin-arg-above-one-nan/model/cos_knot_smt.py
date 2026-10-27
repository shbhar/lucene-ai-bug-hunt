"""
Reduced SMT model (QF_FP, binary64, RNE): the asin argument of Rectangle.fromPointDistance's non-pole
branch (Rectangle.java:156-157) as a function of the two doubles it is computed from,
radLat = Math.toRadians(centerLat) and rd = (radiusMeters + 7E-2) / EARTH_MEAN_RADIUS_METERS
(Rectangle.java:147, 150). The map from (centerLat, radius) to (radLat, rd) is monotone and dense, so a
model is turned into API inputs by a short search on the real jar (harness/src/BoxNaNReplay.java).

Mode 'nan'  : exists radLat, rd with fl(radLat + rd) < PIO2, fl(radLat - rd) > -PIO2,
              index(radLat) = knot - 1, index(|rd - PIO2|) = knot, and
              SloppyMath.sin(rd) / SloppyMath.cos(radLat) > 1   (=> asin NaN => IAE)
Mode 'mono' : exists 0 <= x < y with index(x) = knot - 1, index(y) = knot and cos(y) > cos(x)
              (SloppyMath.cos increases across the knot; necessary for 'nan')
File:line mapping as in model/box_nan_smt.py. Constants: model/sloppy_cos_constants.json.
Usage: python3 -I cos_knot_smt.py CONSTANTS.json MODE KNOT_FROM KNOT_TO TIMEOUT_MS [SMT2_DIR]
"""
import json
import struct
import sys

from z3 import (FP, FPVal, Float64, RNE, Solver, sat, fpAdd, fpSub, fpMul, fpDiv, fpNeg, fpAbs, fpLT,
                fpLEQ, fpGT, And, BitVecVal, fpBVToFP, fpToIEEEBV)

D = Float64()
rm = RNE()


def fromhex(h):
    return fpBVToFP(BitVecVal(int(h, 16), 64), D)


def dval(x):
    return FPVal(x, D)


def bits_to_double(b):
    return struct.unpack('>d', b.to_bytes(8, 'big'))[0]


def main():
    consts = json.load(open(sys.argv[1]))
    mode = sys.argv[2]
    k_from, k_to, tmo = int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5])
    smt2_dir = sys.argv[6] if len(sys.argv) > 6 else None
    C = {k: fromhex(v) for k, v in consts.items() if isinstance(v, str)}

    def sloppy_cos(x, idx):
        t = fpAdd(rm, fpMul(rm, x, C['SIN_COS_INDEXER']), dval(0.5))
        ok = And(fpLEQ(dval(float(idx)), t), fpLT(t, dval(float(idx + 1))))
        fi = dval(float(idx))
        delta = fpSub(rm, fpSub(rm, x, fpMul(rm, fi, C['SIN_COS_DELTA_HI'])), fpMul(rm, fi, C['SIN_COS_DELTA_LO']))
        ic = fromhex(consts['cosTab'][idx])
        is_ = fromhex(consts['sinTab'][idx])
        inner4 = fpMul(rm, fpMul(rm, delta, ic), C['ONE_DIV_F4'])
        inner3 = fpAdd(rm, fpMul(rm, is_, C['ONE_DIV_F3']), inner4)
        inner2 = fpAdd(rm, fpMul(rm, fpNeg(ic), C['ONE_DIV_F2']), fpMul(rm, delta, inner3))
        inner1 = fpAdd(rm, fpNeg(is_), fpMul(rm, delta, inner2))
        return fpAdd(rm, ic, fpMul(rm, delta, inner1)), ok

    for knot in range(k_from, k_to + 1):
        s = Solver()
        s.set('timeout', tmo)
        if mode == 'nan':
            radLat = FP('radLat', D)
            rd = FP('rd', D)
            s.add(fpLEQ(dval(0.0), radLat), fpLEQ(dval(0.0), rd))
            s.add(fpLT(fpAdd(rm, radLat, rd), C['MAX_LAT_RADIANS']), fpGT(fpSub(rm, radLat, rd), fpNeg(C['MAX_LAT_RADIANS'])))
            a2 = fpAbs(fpSub(rm, rd, C['PIO2']))
            sv, ok2 = sloppy_cos(a2, knot)
            cv, ok1 = sloppy_cos(radLat, knot - 1)
            s.add(ok1, ok2, fpGT(fpDiv(rm, sv, cv), dval(1.0)))
            names = [('radLat', radLat), ('rd', rd)]
        else:
            x = FP('x', D)
            y = FP('y', D)
            s.add(fpLEQ(dval(0.0), x), fpLT(x, y))
            cy, oky = sloppy_cos(y, knot)
            cx, okx = sloppy_cos(x, knot - 1)
            s.add(okx, oky, fpGT(cy, cx))
            names = [('x', x), ('y', y)]
        if smt2_dir:
            with open(f'{smt2_dir}/{mode}_knot{knot}.smt2', 'w') as f:
                f.write('(set-logic QF_FP)\n' + s.to_smt2() + '(get-value (' + ' '.join(n for n, _ in names) + '))\n')
        r = s.check()
        out = {'mode': mode, 'knot': knot, 'result': str(r)}
        if r == sat:
            m = s.model()
            for n, v in names:
                b = m.eval(fpToIEEEBV(v), model_completion=True).as_long()
                out[n + '_bits'] = f'{b:016x}'
                out[n] = repr(bits_to_double(b))
        print(json.dumps(out), flush=True)


if __name__ == '__main__':
    main()
