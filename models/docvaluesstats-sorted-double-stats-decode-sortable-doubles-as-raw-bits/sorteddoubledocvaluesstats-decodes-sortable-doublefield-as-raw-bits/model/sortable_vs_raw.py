# Z3 bit-vector / FP64 model: SortedDoubleDocValuesStats raw decode of DoubleField's sortable encoding.
#
# Model element -> Java line (Lucene main 5adff78bb168):
#   enc(v)  = sortable(doubleToLongBits(v))   core/.../document/DoubleField.java:73
#                                             core/.../util/NumericUtils.java:47-49 (doubleToSortableLong)
#   sortable(b) = b ^ ((b >> 63) & 0x7fff..ff) core/.../util/NumericUtils.java:84-86 (sortableDoubleBits, arithmetic >>)
#   dec_bug(x) = longBitsToDouble(x)          misc/.../misc/search/DocValuesStats.java:436 (SortedDoubleDocValuesStats)
#   dec_fix(x) = longBitsToDouble(sortable(x)) core/.../util/NumericUtils.java:56-58 (sortableLongToDouble)
#   min/max update via Double.compare         misc/.../misc/search/DocValuesStats.java:437-442
#
# Obligations (each checked as UNSAT of its negation over all 2^64 bit patterns):
#   P1  sortable(b) == b  <=>  sign bit of b is 0         (raw and sortable bits agree exactly on non-negative values)
#   P2  for every non-NaN v with the sign bit set (negative finite, -0.0, -Inf): dec_bug(enc(v)) != v
#   P3  for normal negatives x < y < 0: dec_bug(enc(x)) > dec_bug(enc(y))   (order among negatives is reversed,
#       so Double.compare-based min/max pick the wrong element)
#   P4  negative subnormals and -0.0 decode to NaN, except the largest-magnitude subnormal, which decodes to -Inf;
#       -Inf decodes to the negative subnormal 0x800fffffffffffff
#   P5  fix: sortable is an involution, so dec_fix(enc(v)) has the same bits as v for every non-NaN v
#   W   witness for the JUnit: dec_bug(enc(-5.0)) == -0.8749999999999999 and enc(3.0) == raw bits of 3.0
# Vacuity: each premise is shown satisfiable (SAT) before its implication is checked.
import struct, sys
from z3 import (BitVec, BitVecVal, Extract, Solver, Not, And, Or, Implies, sat, unsat, fpBVToFP, Float64,
                fpIsNaN, fpEQ, fpLT, fpGT, fpIsNormal, fpIsSubnormal, fpIsZero, fpIsInf, fpIsNegative)

MASK = BitVecVal(0x7FFFFFFFFFFFFFFF, 64)
def sortable(b):  # NumericUtils.sortableDoubleBits; z3 >> on BitVec is arithmetic, like Java's >>
    return b ^ ((b >> 63) & MASK)
def fp(b):
    return fpBVToFP(b, Float64())

results = []
def prove(name, premise, claim):
    s = Solver(); s.add(premise)
    vac = s.check()
    s = Solver(); s.add(premise, Not(claim))
    r = s.check()
    ok = (vac == sat and r == unsat)
    results.append((name, ok))
    print(f"{name}: premise {vac}, negation {r} -> {'PROVED' if ok else 'FAILED'}")
    if r == sat:
        print("   counterexample:", s.model())

b = BitVec('b', 64)
sign = Extract(63, 63, b)
v = fp(b)

prove("P1 sortable(b)==b <=> sign(b)==0", sign == sign,  # trivial premise, all 2^64 patterns
      (sortable(b) == b) == (sign == 0))

# enc(v) for non-NaN v is sortable(rawbits(v)); doubleToLongBits only canonicalizes NaN, so for non-NaN v
# the encoded long is sortable(b).
prove("P2 negative non-NaN v: dec_bug(enc(v)) != v",
      And(Not(fpIsNaN(v)), sign == 1),
      Or(fpIsNaN(fp(sortable(b))), Not(fpEQ(fp(sortable(b)), v)), sortable(b) != b))
# stronger: the decoded double is a different value (not merely different bits), excluding the +0/-0 pair
prove("P2' negative non-NaN v: dec_bug(enc(v)) is not numerically equal to v",
      And(Not(fpIsNaN(v)), sign == 1),
      Not(And(Not(fpIsNaN(fp(sortable(b)))), fpEQ(fp(sortable(b)), v))))

c = BitVec('c', 64)
x, y = fp(b), fp(c)
prove("P3 normal negatives x<y<0 => dec_bug(enc(x)) > dec_bug(enc(y))",
      And(fpIsNormal(x), fpIsNormal(y), fpIsNegative(x), fpIsNegative(y), fpLT(x, y)),
      fpGT(fp(sortable(b)), fp(sortable(c))))

prove("P4a negative subnormal or -0.0 (except 0x800fffffffffffff) decodes to NaN",
      And(Or(fpIsSubnormal(v), fpIsZero(v)), sign == 1, b != BitVecVal(0x800FFFFFFFFFFFFF, 64)),
      fpIsNaN(fp(sortable(b))))
prove("P4b largest-magnitude negative subnormal decodes to -Inf",
      b == BitVecVal(0x800FFFFFFFFFFFFF, 64),
      And(fpIsInf(fp(sortable(b))), fpIsNegative(fp(sortable(b)))))
prove("P4c -Inf decodes to 0x800fffffffffffff (a negative subnormal)",
      And(fpIsInf(v), sign == 1),
      And(sortable(b) == BitVecVal(0x800FFFFFFFFFFFFF, 64), fpIsSubnormal(fp(sortable(b)))))

prove("P5 fix: sortable(sortable(b)) == b for all 2^64 patterns", sign == sign, sortable(sortable(b)) == b)

# Witness for the JUnit values (plain Python, mirrors the Java arithmetic exactly).
def raw(d): return struct.unpack('<q', struct.pack('<d', d))[0]
def frombits(q): return struct.unpack('<d', struct.pack('<q', q))[0]
def py_sortable(q): return q ^ ((q >> 63) & 0x7FFFFFFFFFFFFFFF)
w1 = frombits(py_sortable(raw(-5.0)))
w2 = frombits(py_sortable(raw(3.0)))
print(f"W  dec_bug(enc(-5.0)) = {w1!r}, dec_bug(enc(3.0)) = {w2!r}, enc(3.0)==raw(3.0): {py_sortable(raw(3.0)) == raw(3.0)}")
results.append(("W", repr(w1) == "-0.8749999999999999" and w2 == 3.0))
# min over {dec(-5.0), dec(3.0)} under Double.compare, the stats class's rule
print(f"W  min under the stats class = {min(w1, w2)!r} (true min -5.0); sum = {w1 + w2!r} (true -2.0)")

ok = all(r for _, r in results)
print("ALL OBLIGATIONS:", "PROVED" if ok else "SOME FAILED")
sys.exit(0 if ok else 1)
