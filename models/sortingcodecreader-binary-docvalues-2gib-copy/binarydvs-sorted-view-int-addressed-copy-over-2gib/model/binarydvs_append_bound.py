#!/usr/bin/env python3
"""Exact Java-int model (Z3 bit-vectors) of the append path of BinaryDocValuesWriter.BinaryDVs.

Question: for which column sizes does the sorted copy of a BINARY doc-values field fail, is the failure always
loud, and is the failing size exactly 2^31 bytes?

Mapping (upstream main 781025d6fe32; paths under lucene/core/src/java/org/apache/lucene):
  model element                      Java
  ---------------------------------  ---------------------------------------------------------------------------
  one loop iteration `step`          util/ByteBlockPool.java:287-306  append(byte[], int offset, int length)
  nextBuffer in `step`               util/ByteBlockPool.java:187-200  (bufferUpto++, byteUpto = 0,
                                     byteOffset = Math.addExact(byteOffset, BYTE_BLOCK_SIZE) at :199)
  BLOCK = 2^15                       util/ByteBlockPool.java  BYTE_BLOCK_SHIFT = 15, BYTE_BLOCK_SIZE = 1 << 15
  initial state (0, 0, 0)            util/BytesRefArray.java:40-45   the constructor's pool.nextBuffer() from
                                     (bufferUpto, byteUpto, byteOffset) = (-1, BLOCK, -BLOCK) (ByteBlockPool.java:113-126)
  currentOffset (int)                util/BytesRefArray.java:36, :71-72  offsets[lastElement++] = currentOffset;
                                     currentOffset += bytes.length
  one append per document            index/BinaryDocValuesWriter.java:285-289 (BinaryDVs: values.append(oldValues.binaryValue()))
  callers                            index/SortingCodecReader.java:719-726 (getBinary), index/BinaryDocValuesWriter.java:134-141 (sorted flush)
  `written` (64-bit ghost)           the number of bytes appended so far; not a Java variable

Java ints are 32-bit bit-vectors with wrapping arithmetic; Math.addExact is modelled as "throws iff the 33-bit sum
leaves the signed 32-bit range". Lengths are 0..Integer.MAX_VALUE (a BytesRef length).

Obligations (each is checked by asking Z3 for a counterexample; "proved" = unsat):
  Q0 base     the state after the BytesRefArray constructor satisfies the invariant INV
  Q1 step     INV and a loop iteration that does not throw  =>  INV afterwards, and written grows by the bytes copied
  Q2 throw    INV: an iteration throws  <=>  it fills the block and written reaches exactly 2^31
  Q3 loud     INV  =>  written <= 2^31 - 1, so BytesRefArray.currentOffset (int) never wraps before the throw
  Q4 summary  with the loop invariant written + bytesLeft == W + len: an append of len bytes at W bytes
              returns  =>  W + len <= 2^31 - 1;   throws  =>  W + len >= 2^31
              (the loop ends: every iteration that continues consumes bufferLeft >= 1 bytes)
  => BinaryDVs' constructor throws ArithmeticException in the append that takes the column to 2^31 bytes or more,
     and never before; a column of at most 2^31 - 1 bytes is copied without any wrapped offset.
Self-checks: V1 INV is satisfiable at the last legal state; V2 a mutant without addExact (plain wrapping +=, the code
before #12392) violates "byteOffset >= 0" (Z3 returns the witness); V3 a mutant invariant bound is refuted.
"""
import sys
from z3 import (BitVec, BitVecVal, BV2Int, Solver, And, Or, Not, Implies, If, ULT, SignExt, ZeroExt, sat, unsat, Bool)

BLOCK = 1 << 15
I32 = lambda name: BitVec(name, 32)
c32 = lambda v: BitVecVal(v, 32)
c64 = lambda v: BitVecVal(v, 64)
sx = lambda x: SignExt(32, x)          # int -> long


def inv(bufferUpto, byteUpto, byteOffset, written):
    return And(byteUpto >= 0, byteUpto < BLOCK,
               bufferUpto >= 0, bufferUpto <= 65535,
               sx(byteOffset) == sx(bufferUpto) * c64(BLOCK),
               written == sx(byteOffset) + sx(byteUpto))


def step(s, bytesLeft, exact=True):
    """One iteration of the while loop of ByteBlockPool.append. Returns (throws, s', bytesLeft', copied)."""
    bufferUpto, byteUpto, byteOffset, written = s
    bufferLeft = c32(BLOCK) - byteUpto
    fits = bytesLeft < bufferLeft                       # signed compare, as in Java
    # fits: copy bytesLeft bytes, byteUpto += bytesLeft, break
    s_fit = (bufferUpto, byteUpto + bytesLeft, byteOffset, written + sx(bytesLeft))
    # else: copy bufferLeft bytes, nextBuffer(), bytesLeft -= bufferLeft
    wide = SignExt(1, byteOffset) + BitVecVal(BLOCK, 33)
    overflow = Or(wide > BitVecVal(2**31 - 1, 33), wide < BitVecVal(-2**31, 33))
    throws = And(Not(fits), overflow) if exact else False
    s_fill = (bufferUpto + 1, c32(0), byteOffset + c32(BLOCK), written + sx(bufferLeft))
    s2 = tuple(If(fits, a, b) for a, b in zip(s_fit, s_fill))
    left2 = If(fits, c32(0), bytesLeft - bufferLeft)
    copied = If(fits, bytesLeft, bufferLeft)
    return throws, s2, left2, copied, fits


def check(name, claim, expect_proved=True):
    s = Solver()
    s.add(Not(claim))
    r = s.check()
    ok = (r == unsat) if expect_proved else (r == sat)
    print("%-10s %s%s" % (name, "proved (unsat)" if r == unsat else "counterexample (sat)" if r == sat else str(r),
                          "" if ok else "   <-- UNEXPECTED"))
    if r == sat and not expect_proved:
        m = s.model()
        print("           witness: " + ", ".join("%s=%s" % (d.name(), m[d].as_signed_long() if hasattr(m[d], 'as_signed_long') else m[d])
                                                  for d in sorted(m.decls(), key=lambda d: d.name())))
    return ok


def main():
    ok = True
    bu, up, off, left, length, W0 = I32('bufferUpto'), I32('byteUpto'), I32('byteOffset'), I32('bytesLeft'), I32('len'), BitVec('W', 64)
    written = BitVec('written', 64)
    s0 = (bu, up, off, written)

    # Q0: constructor: nextBuffer() from (-1, BLOCK, -BLOCK): no overflow, state (0, 0, 0), written 0
    ok &= check("Q0 base", inv(c32(-1) + 1, c32(0), c32(-BLOCK) + c32(BLOCK), c64(0)))

    throws, s1, left1, copied, fits = step(s0, left)
    pre = And(inv(*s0), left > 0)
    # Q1: invariant preserved by a non-throwing iteration
    ok &= check("Q1 step", Implies(And(pre, Not(throws)), And(inv(*s1), s1[3] == written + sx(copied), left1 >= 0)))
    # Q2: an iteration throws exactly when it fills the block and the byte count reaches 2^31
    ok &= check("Q2 throw", Implies(pre, throws == And(Not(fits), written + sx(copied) == c64(2**31))))
    # Q3: under INV no more than 2^31 - 1 bytes are in the pool, so the int currentOffset (== written) has not wrapped
    ok &= check("Q3 loud", Implies(inv(*s0), And(written >= 0, written <= c64(2**31 - 1))))
    # Q4: loop summary for one append of `len` bytes that starts with W bytes in the pool
    J = And(inv(*s0), left >= 0, length >= 0, W0 >= 0, written + sx(left) == W0 + sx(length))
    #   a) J is kept by a non-throwing iteration
    ok &= check("Q4a keep", Implies(And(J, left > 0, Not(throws)), And(inv(*s1), left1 >= 0, s1[3] + sx(left1) == W0 + sx(length))))
    #   b) exit without a throw (bytesLeft == 0): everything is in, and it is below 2^31
    ok &= check("Q4b return", Implies(And(J, left == 0), And(written == W0 + sx(length), W0 + sx(length) <= c64(2**31 - 1))))
    #   c) a throw means the append would have taken the pool to 2^31 bytes or more
    ok &= check("Q4c throw", Implies(And(J, left > 0, throws), W0 + sx(length) >= c64(2**31)))
    #   d) progress: an iteration that continues consumes at least one byte (termination)
    ok &= check("Q4d ends", Implies(And(J, left > 0, Not(throws), Not(fits)), And(left1 < left, left1 >= 0)))

    # V1: vacuity: INV holds in the last legal state (2^31 - 1 bytes) and an append of 1 byte throws from there
    s = Solver()
    s.add(inv(*s0), written == c64(2**31 - 1), left == 1, throws)
    r = s.check()
    print("%-10s %s" % ("V1 reach", "sat (the last legal state exists and its next byte throws)" if r == sat else "UNEXPECTED " + str(r)))
    ok &= (r == sat)
    # V2: mutant without addExact (the code before #12392): byteOffset goes negative silently
    _, m1, _, _, _ = step(s0, left, exact=False)
    ok &= check("V2 mutant", Implies(pre, m1[2] >= 0), expect_proved=False)
    # V3: mutant bound: "written <= 2^31 - 2" is not an invariant consequence
    ok &= check("V3 mutant", Implies(inv(*s0), written <= c64(2**31 - 2)), expect_proved=False)

    # The boundary in documents of 4,096 bytes (the executed pair):
    for docs in (524287, 524288):
        total = docs * 4096
        print("docs=%d x 4096 bytes = %d: %s" % (docs, total, "throws (>= 2^31)" if total >= 2**31 else "copied (<= 2^31 - 1)"))
    print("RESULT: " + ("all obligations as expected" if ok else "FAILED"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
