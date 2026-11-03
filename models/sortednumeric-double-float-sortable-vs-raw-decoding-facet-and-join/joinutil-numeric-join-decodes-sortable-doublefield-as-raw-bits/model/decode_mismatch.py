# Z3 bit-vector model of the JoinUtil numeric-join decode for a DoubleField / FloatField from field.
# Mapping (Lucene main e1faeba08bd):
#   enc(v)  = NumericUtils.doubleToSortableLong(v) = bits ^ ((bits >> 63) & 0x7fff...ffff)
#             (NumericUtils.sortableDoubleBits; DoubleField stores this in SORTED_NUMERIC doc values,
#              DoubleField.java:90 decodes it with sortableLongToDouble)
#   dec(x)  = Double.longBitsToDouble(x)   (JoinUtil.java:387, the Double stream; :369 Float.intBitsToFloat for Float)
#   to-side = DoublePoint.encodeDimension(v) of the same value (DoubleField indexes the point of v)
# The join matches the to document iff the streamed point equals the to-side point, i.e. iff dec(enc(v)) has
# the same bits as v (the point encoding is injective on bits). Obligation checked: for every 64-bit (and 32-bit)
# pattern b, sortableBits(b) == b  <=>  sign bit of b is 0.  So every from value with the sign bit set
# (negative numbers, -0.0, negative-sign NaNs) is streamed as a different value; positives are unaffected.
import z3

def check(width):
    b = z3.BitVec('b', width)
    mask = z3.BitVecVal((1 << (width - 1)) - 1, width)
    sortable = b ^ ((b >> (width - 1)) & mask)  # z3py >> is arithmetic, as Java >>
    sign_clear = z3.Extract(width - 1, width - 1, b) == 0
    s = z3.Solver()
    s.add(z3.Not((sortable == b) == sign_clear))
    r = s.check()
    print(f"width={width}: counterexample to 'sortableBits(b)==b <=> sign bit clear' -> {r}")
    # witness: -5.0
    return r

r64 = check(64)
r32 = check(32)
# concrete witness for the repro value -5.0
import struct
bits = struct.unpack('>q', struct.pack('>d', -5.0))[0]
srt = bits ^ ((bits >> 63) & 0x7fffffffffffffff)
print("-5.0 raw bits      = 0x%016x" % (bits & 0xffffffffffffffff))
print("-5.0 sortable bits = 0x%016x" % (srt & 0xffffffffffffffff))
print("JoinUtil streams   =", struct.unpack('>d', struct.pack('>q', srt))[0], "instead of -5.0")
assert r64 == z3.unsat and r32 == z3.unsat
