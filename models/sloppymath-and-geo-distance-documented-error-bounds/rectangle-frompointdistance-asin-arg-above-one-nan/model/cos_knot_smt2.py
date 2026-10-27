"""
Semi-concrete SMT model (QF_FP, binary64, RNE) of the NaN in Rectangle.fromPointDistance's non-pole
branch, with ONE symbolic SloppyMath.cos evaluation per query (the fully symbolic queries of
box_nan_smt.py / cos_knot_smt.py returned 'unknown' after 240-300 s with Z3 4.12 and cvc5 1.4.2).

For SloppyMath.cos table knot i (1..512) let x_i be the largest double whose table index is i-1
(model/knot_points.json, computed on the real jar by harness/src/KnotPoints.java; cos(x_i) is the real
SloppyMath.cos value, a constant here). Query 'nan' with radLat := x_i:
    exists rd >= 0:  fl(x_i + rd) < PIO2  and  fl(x_i - rd) > -PIO2              (Rectangle.java:151-152,156)
                     and index(|fl(rd - PIO2)|) = i                               (SloppyMath.java:100, 203-205)
                     and fl(SloppyMath.cos(|fl(rd - PIO2)|) / cos(x_i)) > 1       (Rectangle.java:157)
A model gives an asin argument > 1 (asin -> NaN, SloppyMath.java:156-177), hence NaN longitudes and the
IllegalArgumentException of Rectangle's constructor (Rectangle.java:87-90). 'unsat' at knot i means: no rd
works with radLat = x_i for a right-hand argument in table segment i (other radLat values left of the knot
are not covered by the query). Every model is replayed on the real jar (harness/src/BoxNaNReplay.java).
Usage: python3 -I cos_knot_smt2.py CONSTANTS.json KNOT_POINTS.json KNOT_FROM KNOT_TO TIMEOUT_MS [SMT2_DIR|-] [WIN]
  WIN > 0 restricts rd to the WIN doubles just below PIO2 - x_i (a hint that keeps the bit-blasted problem small)
"""
import json
import struct
import sys

from z3 import (BitVec, ULE, FP, FPVal, Float64, RNE, Solver, sat, fpAdd, fpSub, fpMul, fpDiv, fpNeg, fpAbs, fpLT,
                fpLEQ, fpGT, And, BitVecVal, fpBVToFP, fpToIEEEBV)

D = Float64()
rm = RNE()


def fromhex(h):
    return fpBVToFP(BitVecVal(int(h, 16), 64), D)


def dval(x):
    return FPVal(x, D)


def main():
    consts = json.load(open(sys.argv[1]))
    knots = {k['knot']: k for k in json.load(open(sys.argv[2]))}
    k_from, k_to, tmo = int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5])
    smt2_dir = sys.argv[6] if len(sys.argv) > 6 and sys.argv[6] != '-' else None
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
        kp = knots[knot]
        x = fromhex(kp['x_bits'])
        cx = fromhex(kp['cos_x_bits'])
        WIN = int(sys.argv[7]) if len(sys.argv) > 7 else 0
        if WIN > 0:
            # window of WIN doubles just below PIO2 - x (the right-hand argument just above the knot);
            # rd's bit pattern is a free bit-vector constrained to that window
            import struct as _st
            xd = _st.unpack('>d', int(kp['x_bits'], 16).to_bytes(8, 'big'))[0]
            import math
            hi_rd = math.nextafter(1.5707963267948966 - xd, 0.0)
            hi_bits = int.from_bytes(_st.pack('>d', hi_rd), 'big')
            rdbv = BitVec('rdbv', 64)
            rd = fpBVToFP(rdbv, D)
        else:
            rd = FP('rd', D)
        s = Solver()
        if WIN > 0:
            s.add(ULE(BitVecVal(hi_bits - WIN, 64), rdbv), ULE(rdbv, BitVecVal(hi_bits, 64)))
        s.set('timeout', tmo)
        s.add(fpLEQ(dval(0.0), rd))
        s.add(fpLT(fpAdd(rm, x, rd), C['MAX_LAT_RADIANS']), fpGT(fpSub(rm, x, rd), fpNeg(C['MAX_LAT_RADIANS'])))
        a2 = fpAbs(fpSub(rm, rd, C['PIO2']))
        sv, ok = sloppy_cos(a2, knot)
        s.add(ok, fpGT(fpDiv(rm, sv, cx), dval(1.0)))
        if smt2_dir:
            with open(f'{smt2_dir}/nan2_knot{knot}.smt2', 'w') as f:
                f.write('(set-logic QF_FP)\n' + s.to_smt2() + '(get-value (rd))\n')
        r = s.check()
        out = {'knot': knot, 'window': WIN, 'radLat_bits': kp['x_bits'], 'radLat': kp['x'], 'result': str(r)}
        if r == sat:
            b = s.model().eval(fpToIEEEBV(rd), model_completion=True).as_long()
            out['rd_bits'] = f'{b:016x}'
            out['rd'] = repr(struct.unpack('>d', b.to_bytes(8, 'big'))[0])
        print(json.dumps(out), flush=True)


if __name__ == '__main__':
    main()
