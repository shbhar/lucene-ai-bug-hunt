"""SMT-5: integer-accumulator bounds of the quantized scoring path (QF_LIA / QF_NIA, Z3).

Max dimension: Lucene104ScalarQuantizedVectorsFormat.getMaxDimensions == 1024 (:171-173) and
Lucene104HnswScalarQuantizedVectorsFormat.getMaxDimensions (:199). For each accumulator we prove
"no overflow for every dim <= 1024" and compute the first overflowing dimension.
Panama int4 kernels accumulate in 16-bit lanes per CHUNK (PanamaVectorUtilSupport:584-603):
CHUNK / BYTE_SPECIES.length() = 2048/16 = 4096/32 = 8192/64 = 128 iterations per lane per chunk, and
the lanes are read back as unsigned (and(0xFFFF)), so the bound is 65535.
"""
from z3 import Int, Solver, And, Or, unsat, sat
import time

def prove(name, dimmax, per_dim, limit):
    d = Int("dim")
    s = Solver()
    s.add(d >= 1, d <= dimmax, d * per_dim > limit)
    r = s.check()
    first = limit // per_dim + 1
    print(f"[{'PROVED' if r == unsat else 'CEX'}] {name}: dim <= {dimmax} -> {per_dim}*dim <= {limit}"
          f"   (first overflowing dim = {first})")

INT = 2 ** 31 - 1
prove("quantizedComponentSum (int), 8-bit codes", 1024, 255, INT)
prove("uint8DotProduct (int), UNSIGNED_BYTE", 1024, 255 * 255, INT)
prove("dotProduct(byte[]) (int), SEVEN_BIT codes 0..127", 1024, 127 * 127, INT)
prove("int4DotProductSinglePacked (int), PACKED_NIBBLE", 1024, 15 * 15, INT)
prove("int4BitDotProduct (long) <= 15*dim", 1024, 15, 2 ** 63 - 1)
prove("qcDist converted to float exactly (< 2^24)? UNSIGNED_BYTE", 1024, 255 * 255, 2 ** 24)
prove("qcDist converted to float exactly (< 2^24)? SEVEN_BIT", 1024, 127 * 127, 2 ** 24)
prove("x1/y1 (quantizedComponentSum) converted to float exactly (< 2^24)", 1024, 255, 2 ** 24)
# Panama 16-bit lane accumulators (unsigned read-back)
prove("Panama int4DotProductBody: 128 iterations x 225 per lane", 128, 225, 65535)
prove("Panama int4DotProductSinglePackedBody: 128 iterations x (225+225) per lane", 128, 450, 65535)
prove("Panama int4DotProductBothPackedBody: 128 iterations x (225+225) per lane", 128, 450, 65535)
prove("Panama uint8 128-bit body: one 16-bit product 255*255 (ZERO_EXTEND_S2I read-back)", 1, 255 * 255, 65535)
prove("Panama signed dotProductBody128: one 16-bit product |-128*-128| (signed short)", 1, 128 * 128, 32767)
