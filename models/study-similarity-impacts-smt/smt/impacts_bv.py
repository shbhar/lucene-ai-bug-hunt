"""Z3 bit-vector proofs for the Lucene104 impacts encoding and SmallFloat.longToInt4/int4ToLong.

(1) Lucene104PostingsWriter.writeImpacts / Lucene104PostingsReader.readImpacts, one step:
  writer (Lucene104PostingsWriter.java:540-556):
     int freqDelta = impact.freq - previous.freq - 1;
     long normDelta = impact.norm - previous.norm - 1;
     if (normDelta == 0) out.writeVInt(freqDelta << 1);
     else { out.writeVInt((freqDelta << 1) | 1); out.writeZLong(normDelta); }
  reader (Lucene104PostingsReader.java:1447-1470):
     int freqDelta = in.readVInt();
     if ((freqDelta & 0x01) != 0) { freq += 1 + (freqDelta >>> 1); norm += 1 + in.readZLong(); }
     else { freq += 1 + (freqDelta >>> 1); norm++; }
  writeZLong = writeVLong(BitUtil.zigZagEncode(l)); readZLong = zigZagDecode(readVLong()).
  zigZagEncode(l) = (l >> 63) ^ (l << 1); zigZagDecode(l) = (l >>> 1) ^ -(l & 1)  (BitUtil.java)
  VInt/VLong are modelled as identity on the 32/64-bit pattern (their round trip is checked
  exhaustively/differentially in java/ImpactsRoundTrip.java).

  Claim: for all prev=(pf,pn), cur=(cf,cn) with 0 <= pf < cf (int32, signed) and
  Long.compareUnsigned(cn, pn) > 0, decode(encode(cur | prev) | prev) == cur.

(2) SmallFloat.longToInt4 / int4ToLong (SmallFloat.java) for ALL longs i >= 0:
   (a) int4ToLong(longToInt4(i)) <= i       (decoding rounds down)
   (b) longToInt4 is monotone: i <= j  ==>  longToInt4(i) <= longToInt4(j)
   (c) longToInt4(int4ToLong(longToInt4(i))) == longToInt4(i)  (idempotent)
   (d) result fits: 0 <= longToInt4(i) <= longToInt4(Long.MAX_VALUE)
"""
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import time
from z3 import (BitVec, BitVecVal, Solver, If, And, Or, Not, ULT, UGT, LShR, ZeroExt, SignExt,
                Extract, unsat, sat, simplify, ULE, prove)


def check(name, solver):
    t0 = time.time()
    r = solver.check()
    dt = time.time() - t0
    verdict = 'PROVED (negation unsat)' if r == unsat else ('COUNTEREXAMPLE' if r == sat else 'UNKNOWN')
    print('%-60s %-26s %.2fs' % (name, verdict, dt), flush=True)
    if r == sat:
        print('   model:', solver.model())
    return r


def zz_enc(l):
    return (l >> 63) ^ (l << 1)


def zz_dec(l):
    return LShR(l, 1) ^ -(l & 1)


def impacts_roundtrip():
    pf, cf = BitVec('pf', 32), BitVec('cf', 32)
    pn, cn = BitVec('pn', 64), BitVec('cn', 64)
    # writer
    freqDelta = cf - pf - 1
    normDelta = cn - pn - 1
    tok_plain = freqDelta << 1
    tok_norm = (freqDelta << 1) | 1
    z = zz_enc(normDelta)
    tok = If(normDelta == 0, tok_plain, tok_norm)
    # reader
    freq_dec = pf + 1 + LShR(tok, 1)
    norm_dec = If((tok & 1) != 0, pn + 1 + zz_dec(z), pn + 1)
    s = Solver()
    s.add(pf >= 0, cf > pf, UGT(cn, pn))
    s.add(Or(freq_dec != cf, norm_dec != cn))
    check('impacts: decode(encode(cur|prev)|prev) == cur', s)
    # strengthen: the precondition pf >= 0 is not needed if cf - pf - 1 fits in 31 bits
    s = Solver()
    s.add(cf > pf, UGT(cn, pn))
    s.add(Or(freq_dec != cf, norm_dec != cn))
    check('impacts: same, without pf >= 0 (expect cex: delta >= 2^31)', s)
    # zig-zag alone
    l = BitVec('l', 64)
    s = Solver()
    s.add(zz_dec(zz_enc(l)) != l)
    check('BitUtil zigZag decode(encode(l)) == l, all longs', s)


def numberOfLeadingZeros64(x):
    # Long.numberOfLeadingZeros as a BV term (0..64)
    r = BitVecVal(64, 64)
    for k in range(64):
        # if bit k is the highest set bit -> 63 - k
        r = If(Extract(k, k, x) == 1, BitVecVal(63 - k, 64), r)
    return r


def longToInt4(i):
    numBits = 64 - numberOfLeadingZeros64(i)
    shift = numBits - 4
    enc_norm = (LShR(i, shift) & 0x07) | ((shift + 1) << 3)
    # Math.toIntExact(i) for numBits < 4 never throws (i < 8); for normal case i >>> shift < 16.
    return If(numBits < 4, i, enc_norm)


def int4ToLong(e):
    bits = e & 0x07
    shift = LShR(e, 3) - 1
    return If(shift == BitVecVal(2 ** 64 - 1, 64), bits, (bits | 0x08) << shift)


def smallfloat_int4():
    i, j = BitVec('i', 64), BitVec('j', 64)
    e = longToInt4(i)
    s = Solver(); s.add(i >= 0, Not(ULE(int4ToLong(e), i)))
    check('SmallFloat: int4ToLong(longToInt4(i)) <= i, all i >= 0', s)
    s = Solver(); s.add(i >= 0, j >= i, Not(longToInt4(i) <= longToInt4(j)))
    check('SmallFloat: longToInt4 monotone on [0, Long.MAX]', s)
    s = Solver(); s.add(i >= 0, longToInt4(int4ToLong(e)) != e)
    check('SmallFloat: longToInt4(int4ToLong(longToInt4(i))) == longToInt4(i)', s)
    maxe = longToInt4(BitVecVal(2 ** 63 - 1, 64))
    s = Solver(); s.add(i >= 0, Or(e < 0, e > maxe, e > 0x7FFFFFFF))
    check('SmallFloat: 0 <= longToInt4(i) <= longToInt4(MAX) and fits int', s)
    print('   longToInt4(Long.MAX_VALUE) =', simplify(maxe))
    # decoded value is the greatest representable <= i: int4ToLong(e+1) > i (if e < max)
    s = Solver(); s.add(i >= 0, e < maxe, Not(UGT(int4ToLong(e + 1), i)))
    check('SmallFloat: int4ToLong(longToInt4(i)+1) > i (floor encoding)', s)


if __name__ == '__main__':
    impacts_roundtrip()
    smallfloat_int4()
