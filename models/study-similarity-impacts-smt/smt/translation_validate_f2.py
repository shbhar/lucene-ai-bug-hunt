"""Translation validation of the QF_FP encoding of AxiomaticF2LOG against the real JVM on the
repro point: the SMT model's (float) scores must be bit-identical to SimScorer.score's output
printed by TestAxiomaticF2PruningRepro (0.12735014f for freq 2^24-1, 0.12735012f for 2^24).
Usage: python -I translation_validate_f2.py <idf_hex>"""
import os, sys, struct
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from z3 import simplify, fpToIEEEBV, FPVal, BitVecVal, fpBVToFP
from fpjava import F32, F64, f2d, d2f, add, sub, mul, div, dconst, jmax
from axiomatic_f2_narrow import score
idf = float.fromhex(sys.argv[1])
s = fpBVToFP(BitVecVal(0x3a831330, 32), F32)
boost = FPVal(0.87969565, F32)
for f in (2 ** 24 - 1, 2 ** 24):
    out = d2f(score(FPVal(float(f), F32), f2d(s), dconst(18874392.0), dconst(2e7), dconst(idf), f2d(boost)))
    bits = simplify(fpToIEEEBV(out)).as_long()
    print('freq', f, 'SMT model (float) score =', repr(struct.unpack('>f', struct.pack('>I', bits))[0]), hex(bits))
