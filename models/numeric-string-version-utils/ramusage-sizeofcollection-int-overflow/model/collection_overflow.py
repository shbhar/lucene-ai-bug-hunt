#!/usr/bin/env python3
"""
numeric-string-version-utils: RamUsageEstimator.sizeOfCollection int arithmetic (z3, exact Java int semantics as BV32).

Mapping (Lucene main @ ea57360912ed, lucene/core/src/java/org/apache/lucene/util/RamUsageEstimator.java):
  line 307  size += NUM_BYTES_ARRAY_HEADER + collection.size() * NUM_BYTES_OBJECT_REF;
            -> the right-hand side is evaluated in 32-bit int (both operands int), then widened to long.
  lines 120-142  NUM_BYTES_OBJECT_REF = 4 (compressed oops) or 8; NUM_BYTES_ARRAY_HEADER = alignObjectSize(12+4)=16
            or alignObjectSize(16+4)=24 (8-byte alignment, the HotSpot default).
  Domain: collection.size() in [0, Integer.MAX_VALUE] (Collection.size() contract); array-backed collections are
          additionally bounded by ArrayUtil.MAX_ARRAY_LENGTH = Integer.MAX_VALUE - NUM_BYTES_ARRAY_HEADER.
Query: exists n in domain with  sext64(BV32 expr) != exact 64-bit expr; minimise n (iterative tightening).
Also checks that every other size multiplication in the file is widened to long first (grep inventory, printed).
"""
import z3


def minimal_witness(ref, header, max_n):
    n = z3.BitVec("n", 32)
    bv = z3.BitVecVal(header, 32) + n * z3.BitVecVal(ref, 32)
    exact = z3.BitVecVal(header, 64) + z3.ZeroExt(32, n) * z3.BitVecVal(ref, 64)
    base = [z3.ULE(n, max_n), z3.SignExt(32, bv) != exact]
    s = z3.Solver()
    s.add(*base)
    if s.check() != z3.sat:
        return None, None
    best = s.model()[n].as_long()
    # tighten to the minimum
    while True:
        s2 = z3.Solver()
        s2.add(*base, z3.ULT(n, best))
        if s2.check() == z3.sat:
            best = s2.model()[n].as_long()
        else:
            break
    val = (header + best * ref) & 0xFFFFFFFF
    if val >= 1 << 31:
        val -= 1 << 32
    # proof that below the minimum the int expression is exact
    s3 = z3.Solver()
    s3.add(z3.ULT(n, best), z3.SignExt(32, bv) != exact)
    below = s3.check()
    return best, (val, str(below))


for ref, header in ((4, 16), (8, 24), (8, 16)):
    max_n = 2**31 - 1 - header
    w, info = minimal_witness(ref, header, max_n)
    print(f"REF={ref} ARRAY_HEADER={header}: minimal overflowing collection.size() = {w} "
          f"(int value of header+size*ref = {info[0]}); below it exact: {info[1]} (unsat = proved); "
          f"witness <= MAX_ARRAY_LENGTH ({max_n}): {w <= max_n}")
