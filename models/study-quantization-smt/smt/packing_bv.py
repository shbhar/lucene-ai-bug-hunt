"""SMT-4: QF_BV proofs of the per-byte-group lemmas behind the asymmetric OSQ encodings.

OptimizedScalarQuantizer.transposeHalfByte (:412-432): for each group of 8 dims (MSB first), bit p of
  the 8 nibbles goes to byte j of plane p (planes are len/4 bytes apart).
OptimizedScalarQuantizer.packAsBinary (:440-452): 8 bits, MSB first, into byte j.
OptimizedScalarQuantizer.transposeDibit (:480-518): bit 0 / bit 1 of 8 dibits, MSB first, into byte j of
  the lower / upper stripe.
DefaultVectorUtilSupport.int4BitDotProductImpl (:368-385): sum_p 2^p * popcount(plane_p[j] & d[j]);
  int4DibitDotProductImpl (:350-357): bitdot(q, lower) + 2 * bitdot(q, upper).
packNibbles (OffHeapScalarQuantizedVectorValues:182-188) / int4DotProductSinglePacked (Default:209-219).
Lemma (per group of 8 dims, all 2^40 / 2^48 / 2^64 contents): the kernel equals sum_i q_i * d_i.
The whole-vector statement follows because the kernels sum these groups over byte positions r of the
stripes and the layout arithmetic is checked exhaustively for every dimension 1..4096 against the real
enum by ../junit/src/LayoutCheck.java.
"""
import time
from z3 import BitVec, BitVecVal, ZeroExt, Extract, Concat, Solver, ULE, unsat, sat, Sum, If

def popcount8(b):  # b: BV8 -> BV16
    return Sum([ZeroExt(15, Extract(i, i, b)) for i in range(8)])

def check(name, solver_constraints, goal):
    s = Solver(); s.set("timeout", 300000)
    s.add(*solver_constraints); s.add(goal)
    t = time.time(); r = s.check()
    print(f"[{'PROVED' if r == unsat else r}] {name} ({time.time() - t:.2f}s)", flush=True)

q = [BitVec(f"q{i}", 8) for i in range(8)]
d = [BitVec(f"d{i}", 8) for i in range(8)]
rng_q = [ULE(v, 15) for v in q]

def plane(vals, bit):  # byte with bit `bit` of vals[0] at MSB ... vals[7] at LSB (transpose order)
    return Concat(*[Extract(bit, bit, v) for v in vals])

def expected(qs, ds):  # sum q_i * d_i as BV16
    return Sum([ZeroExt(8, qs[i]) * ZeroExt(8, ds[i]) for i in range(8)])

# 1-bit docs
rng_d1 = [ULE(v, 1) for v in d]
dbyte = plane(d, 0)  # packAsBinary: (vector[i] & 1) << j, j = 7..0
kernel = Sum([popcount8(plane(q, p) & dbyte) << p for p in range(4)])
check("SINGLE_BIT_QUERY_NIBBLE: sum_p 2^p popcount(transposeHalfByte_p & packAsBinary) == sum q_i d_i",
      rng_q + rng_d1, kernel != expected(q, d))
# 2-bit docs (dibit stripes)
rng_d2 = [ULE(v, 3) for v in d]
lower, upper = plane(d, 0), plane(d, 1)
bitdot = lambda db: Sum([popcount8(plane(q, p) & db) << p for p in range(4)])
# q_i * d_i for d_i in [0,3] written without a multiplier: bit0 ? q_i : 0  +  bit1 ? 2 q_i : 0
exp2 = Sum([If(Extract(0, 0, d[i]) == 1, ZeroExt(8, q[i]), BitVecVal(0, 16)) +
            If(Extract(1, 1, d[i]) == 1, ZeroExt(8, q[i]) << 1, BitVecVal(0, 16)) for i in range(8)])
# Lemma L (any byte D): bitdot(q, D) == sum_i (bit (7-i) of D) ? q_i : 0.  DIBIT then follows from
# L(lower) + 2 L(upper) == exp2 (definition of plane) and the side lemma exp2 == sum q_i d_i.
D = BitVec("D", 8)
check("Lemma L: sum_p 2^p popcount(plane_p(q) & D) == sum_i [bit(7-i) of D] q_i   (all q nibbles, all D)",
      rng_q, bitdot(D) != Sum([If(Extract(7 - i, 7 - i, D) == 1, ZeroExt(8, q[i]), BitVecVal(0, 16)) for i in range(8)]))
check("DIBIT step: L(lower) + 2 L(upper) == exp2 (pure rewriting of the plane definition)",
      rng_q + rng_d2,
      Sum([If(Extract(7 - i, 7 - i, lower) == 1, ZeroExt(8, q[i]), BitVecVal(0, 16)) for i in range(8)]) +
      (Sum([If(Extract(7 - i, 7 - i, upper) == 1, ZeroExt(8, q[i]), BitVecVal(0, 16)) for i in range(8)]) << 1) != exp2)
check("  (side lemma) multiplier-free form == sum q_i * d_i for d_i in [0,3]", rng_q + rng_d2, exp2 != expected(q, d))
# untransposeDibit is the inverse of transposeDibit (per group)
rec = [Concat(BitVecVal(0, 6), Extract(7 - j, 7 - j, upper), Extract(7 - j, 7 - j, lower)) for j in range(8)]
from z3 import Or
check("untransposeDibit(transposeDibit(d)) == d for d_i in [0,3]",
      rng_d2, Or(*[rec[j] != d[j] for j in range(8)]))
# unpackBinary(packAsBinary(d)) == d
rec1 = [Concat(BitVecVal(0, 7), Extract(7 - j, 7 - j, dbyte)) for j in range(8)]
check("unpackBinary(packAsBinary(d)) == d for d_i in {0,1}", rng_d1, Or(*[rec1[j] != d[j] for j in range(8)]))
# packed nibbles: packed[i] = hi << 4 | lo ; kernel = (packed & 0x0F)*qLo + (packed >>> 4)*qHi
hi, lo, qh, ql = BitVec("hi", 8), BitVec("lo", 8), BitVec("qh", 8), BitVec("ql", 8)
packed = (hi << 4) | lo
k2 = ZeroExt(8, packed & 0x0F) * ZeroExt(8, ql) + ZeroExt(8, Extract(7, 4, packed) if False else (packed >> 4) & 0x0F) * ZeroExt(8, qh)
check("PACKED_NIBBLE: int4DotProductSinglePacked lane == hi*qHi + lo*qLo, and int4Unpack restores (hi, lo)",
      [ULE(hi, 15), ULE(lo, 15), ULE(qh, 15), ULE(ql, 15)],
      Or(k2 != ZeroExt(8, hi) * ZeroExt(8, qh) + ZeroExt(8, lo) * ZeroExt(8, ql),
         ((packed >> 4) & 0x0F) != hi, (packed & 0x0F) != lo))
# negative control: the same nibble packing with an out-of-range code (-1 = 0xFF, finding F6) corrupts the neighbour
# Java: int x = unpacked[i] << 4 | unpacked[len + i]; bytes are SIGN-extended to int before | .
from z3 import SignExt
xj = (SignExt(24, hi) << 4) | SignExt(24, lo)
pj = Extract(7, 0, xj)
check("Java packNibbles == BV model for codes in [0,15]", [ULE(hi, 15), ULE(lo, 15)], pj != packed)
check("negative control: low-slot code -1 (0xFF, finding F6) leaves the HIGH nibble intact? (sat = neighbour corrupted)",
      [ULE(hi, 14), lo == 0xFF], ((pj >> 4) & 0x0F) != hi)
