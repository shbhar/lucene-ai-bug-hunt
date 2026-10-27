// Dafny 4.11 proof (unbounded table length) of org.apache.lucene.util.fst.BitTableUtil
// isBitSet / nextBitSet / previousBitSet, transcribed from
// lucene/core/src/java/org/apache/lucene/util/fst/BitTableUtil.java @ 7cecf79523.
//
// Abstraction: the FST.BytesReader positioned at the bit-table start returns table byte k on the
// (k+1)-th readByte(), skipBytes(n) advances by n bytes (negative n goes back). We model the
// reader by the logical byte sequence t (t[k] = byte k) and an explicit read index, so each
// readByte() of the Java code becomes t[idx] with the index arithmetic of the code.
// The reverse-order physical layout itself is checked on the real bytecode by JBMC
// (jbmc/BitTableHarness.java, ReverseBytesReader) and by the bounded-exhaustive harness.
//
// Java semantics used: int ops on values that provably stay far from overflow are modelled as
// Dafny ints; mask computations are bv32 exactly as Java ints; (byte & 0xFF) zero-extends.
// Trusted JDK specs (axioms): Integer.numberOfTrailingZeros / numberOfLeadingZeros for i != 0.

function BitOf8(b: bv8, j: nat): bool
  requires j < 8
{
  ((b >> j) & 1) == 1
}

function BitOf32(i: bv32, j: nat): bool
  requires j < 32
{
  ((i >> j) & 1) == 1
}

// bit k of the table (FSTCompiler.writePresenceBits: label (first + k) present iff bit k set)
function Bit(t: seq<bv8>, k: int): bool
  requires 0 <= k < 8 * |t|
{
  BitOf8(t[k / 8], k % 8)
}

// ---- trusted JDK contracts --------------------------------------------------------------------
// Integer.numberOfTrailingZeros(i), i != 0: index of the lowest set bit
function {:axiom} Ntz(i: bv32): nat
  requires i != 0
  ensures Ntz(i) < 32 && BitOf32(i, Ntz(i))
  ensures forall j: nat | j < Ntz(i) :: !BitOf32(i, j)

// (Integer.SIZE - 1) - Integer.numberOfLeadingZeros(i), i != 0: index of the highest set bit
function {:axiom} HighBit(i: bv32): nat
  requires i != 0
  ensures HighBit(i) < 32 && BitOf32(i, HighBit(i))
  ensures forall j: nat | HighBit(i) < j < 32 :: !BitOf32(i, j)

// ---- bit-vector lemmas (decided by Z3's bit-vector theory) ------------------------------------
lemma ZeroExtBits(b: bv8)
  ensures forall j: nat | j < 32 :: BitOf32(b as bv32, j) <==> (j < 8 && BitOf8(b, j))
{
  forall j: nat | j < 32
    ensures BitOf32(b as bv32, j) <==> (j < 8 && BitOf8(b, j))
  {
    ZeroExtBit(b, j);
  }
}

lemma ZeroExtBit(b: bv8, j: nat)
  requires j < 32
  ensures BitOf32(b as bv32, j) <==> (j < 8 && BitOf8(b, j))
{
  if j < 8 {
    assert (((b as bv32) >> j) & 1) == (((b >> j) & 1) as bv32);
  } else {
    assert ((b as bv32) >> j) == 0;
  }
}

// nextBitSet: mask = -1 << s (Java int), i = (byte & 0xFF) & mask
lemma HighMaskBit(b: bv8, s: nat, j: nat)
  requires s < 8 && j < 32
  ensures BitOf32((b as bv32) & ((0xFFFF_FFFF as bv32) << s), j) <==> (s <= j < 8 && BitOf8(b, j))
{
  ZeroExtBit(b, j);
  var m: bv32 := (0xFFFF_FFFF as bv32) << s;
  assert ((((b as bv32) & m) >> j) & 1) == ((((b as bv32) >> j) & 1) & ((m >> j) & 1));
  assert ((m >> j) & 1) == (if j >= s then 1 else 0);
}

// previousBitSet: mask = (1 << s) - 1 (Java int), i = (byte & 0xFF) & mask
lemma LowMaskBit(b: bv8, s: nat, j: nat)
  requires s < 8 && j < 32
  ensures BitOf32((b as bv32) & (((1 as bv32) << s) - 1), j) <==> (j < s && BitOf8(b, j))
{
  ZeroExtBit(b, j);
  var m: bv32 := ((1 as bv32) << s) - 1;
  assert ((((b as bv32) & m) >> j) & 1) == ((((b as bv32) >> j) & 1) & ((m >> j) & 1));
  assert ((m >> j) & 1) == (if j < s then 1 else 0);
}

lemma AllOnesShift(s: nat)
  requires s < 8 && ((0xFFFF_FFFF as bv32) << s) == 0xFFFF_FFFF
  ensures s == 0
{
}

lemma NonZeroHasBit(i: bv32)
  requires i != 0
  ensures exists j: nat | j < 32 :: BitOf32(i, j)
{
  assert BitOf32(i, Ntz(i));
}

lemma ZeroHasNoBit(i: bv32)
  requires i == 0
  ensures forall j: nat | j < 32 :: !BitOf32(i, j)
{
}

// ---- isBitSet ---------------------------------------------------------------------------------
// return (readByte(reader) & (1L << (bitIndex & 7))) != 0   after skipBytes(bitIndex >> 3)
method IsBitSet(t: seq<bv8>, bitIndex: int) returns (r: bool)
  requires 0 <= bitIndex < 8 * |t|
  ensures r == Bit(t, bitIndex)
{
  var byteIdx := bitIndex / 8;            // bitIndex >> 3 for bitIndex >= 0
  var s := bitIndex % 8;                  // bitIndex & (Byte.SIZE - 1)
  var b := t[byteIdx];
  var v: bv64 := (b as bv64) & ((1 as bv64) << s);
  r := v != 0;
  assert v != 0 <==> BitOf8(b, s) by {
    assert ((b as bv64) & ((1 as bv64) << s)) == ((((b >> s) & 1) as bv64) << s);
  }
}

// ---- nextBitSet -------------------------------------------------------------------------------
method NextBitSet(t: seq<bv8>, bitIndex: int, bitTableBytes: int) returns (r: int)
  requires bitTableBytes == |t| && |t| < 0x1000_0000
  requires -1 <= bitIndex < bitTableBytes * 8          // the Java assert
  // The Java assert also admits bitTableBytes == 0 with bitIndex == -1, where the code would read
  // one byte past the table; a direct-addressing node always has >= 1 presence byte, so callers
  // never do that. Dafny reported the out-of-range read until this precondition was added.
  requires bitTableBytes >= 1
  ensures r == -1 <==> (forall k | bitIndex < k < 8 * |t| :: !Bit(t, k))
  ensures r != -1 ==> bitIndex < r < 8 * |t| && Bit(t, r) && (forall k | bitIndex < k < r :: !Bit(t, k))
{
  var byteIndex := if bitIndex < 0 then 0 else bitIndex / 8;   // Java: bitIndex / Byte.SIZE (truncating)
  var s := (bitIndex + 1) % 8;                                  // (bitIndex + 1) & (Byte.SIZE - 1)
  var mask: bv32 := (0xFFFF_FFFF as bv32) << s;                // -1 << s
  var i: bv32;
  if mask == 0xFFFF_FFFF && bitIndex != -1 {
    // reader.skipBytes(byteIndex + 1); i = 0
    i := 0;
  } else {
    // reader.skipBytes(byteIndex); i = (reader.readByte() & 0xFF) & mask
    i := (t[byteIndex] as bv32) & mask;
  }
  // establish: bits of i are exactly the table bits of byte byteIndex that are > bitIndex
  forall j: nat | j < 32
    ensures BitOf32(i, j) <==> (j < 8 && 8 * byteIndex + j > bitIndex && Bit(t, 8 * byteIndex + j))
  {
    if j < 8 {
      assert (8 * byteIndex + j) / 8 == byteIndex && (8 * byteIndex + j) % 8 == j;
    }
    if mask == 0xFFFF_FFFF && bitIndex != -1 {
      AllOnesShift(s);
      assert s == 0;
      assert bitIndex == 8 * byteIndex + 7;
      ZeroHasNoBit(i);
    } else {
      HighMaskBit(t[byteIndex], s, j);
      if bitIndex >= 0 {
        if s == 0 { assert mask == 0xFFFF_FFFF; }
        assert s != 0;
        assert bitIndex == 8 * byteIndex + s - 1;
      } else {
        assert byteIndex == 0 && s == 0;
      }
    }
  }
  while i == 0
    invariant 0 <= byteIndex < bitTableBytes
    invariant forall k | bitIndex < k < 8 * byteIndex :: !Bit(t, k)
    invariant forall j: nat | j < 32 :: BitOf32(i, j) <==> (j < 8 && 8 * byteIndex + j > bitIndex && Bit(t, 8 * byteIndex + j))
    decreases bitTableBytes - byteIndex
  {
    // no bit of byte byteIndex above bitIndex is set
    forall k | bitIndex < k < 8 * byteIndex + 8
      ensures !Bit(t, k)
    {
      if k >= 8 * byteIndex {
        var j: nat := k - 8 * byteIndex;
        assert !BitOf32(i, j);
      }
    }
    byteIndex := byteIndex + 1;
    if byteIndex == bitTableBytes {
      return -1;
    }
    i := t[byteIndex] as bv32;            // reader.readByte() & 0xFF
    forall j: nat | j < 32
      ensures BitOf32(i, j) <==> (j < 8 && 8 * byteIndex + j > bitIndex && Bit(t, 8 * byteIndex + j))
    {
      ZeroExtBit(t[byteIndex], j);
      if j < 8 {
        assert (8 * byteIndex + j) / 8 == byteIndex && (8 * byteIndex + j) % 8 == j;
      }
    }
  }
  var z := Ntz(i);
  r := z + byteIndex * 8;
  assert z < 8;
  forall k | bitIndex < k < r
    ensures !Bit(t, k)
  {
    if k >= 8 * byteIndex {
      var j: nat := k - 8 * byteIndex;
      assert j < z;
      assert !BitOf32(i, j);
    }
  }
}

// ---- previousBitSet ---------------------------------------------------------------------------
method PreviousBitSet(t: seq<bv8>, bitIndex: int) returns (r: int)
  requires 0 <= bitIndex < 8 * |t| && |t| < 0x1000_0000   // the Java assert (bitIndex >= 0) + caller domain
  ensures r == -1 <==> (forall k | 0 <= k < bitIndex :: !Bit(t, k))
  ensures r != -1 ==> 0 <= r < bitIndex && Bit(t, r) && (forall k | r < k < bitIndex :: !Bit(t, k))
{
  var byteIndex := bitIndex / 8;                 // bitIndex >> 3
  var s := bitIndex % 8;                         // bitIndex & (Byte.SIZE - 1)
  var mask: bv32 := ((1 as bv32) << ((s + 1) % 8)) - 1;  // MUTANT
  var i: bv32 := (t[byteIndex] as bv32) & mask;  // (reader.readByte() & 0xFF) & mask
  forall j: nat | j < 32
    ensures BitOf32(i, j) <==> (j < 8 && 8 * byteIndex + j < bitIndex && Bit(t, 8 * byteIndex + j))
  {
    LowMaskBit(t[byteIndex], s, j);
    if j < 8 {
      assert (8 * byteIndex + j) / 8 == byteIndex && (8 * byteIndex + j) % 8 == j;
    }
  }
  while i == 0
    invariant 0 <= byteIndex <= bitIndex / 8
    invariant forall k | 8 * byteIndex + 8 <= k < bitIndex :: !Bit(t, k)
    invariant forall j: nat | j < 32 :: BitOf32(i, j) <==> (j < 8 && 8 * byteIndex + j < bitIndex && Bit(t, 8 * byteIndex + j))
    decreases byteIndex
  {
    forall k | 8 * byteIndex <= k < bitIndex
      ensures !Bit(t, k)
    {
      if k < 8 * byteIndex + 8 {
        var j: nat := k - 8 * byteIndex;
        assert !BitOf32(i, j);
      }
    }
    // if (byteIndex-- == 0) return -1;
    if byteIndex == 0 {
      return -1;
    }
    byteIndex := byteIndex - 1;
    // reader.skipBytes(-2); i = reader.readByte() & 0xFF
    i := t[byteIndex] as bv32;
    forall j: nat | j < 32
      ensures BitOf32(i, j) <==> (j < 8 && 8 * byteIndex + j < bitIndex && Bit(t, 8 * byteIndex + j))
    {
      ZeroExtBit(t[byteIndex], j);
      if j < 8 {
        assert (8 * byteIndex + j) / 8 == byteIndex && (8 * byteIndex + j) % 8 == j;
      }
    }
  }
  var h := HighBit(i);
  r := h + byteIndex * 8;   // (Integer.SIZE - 1) - Integer.numberOfLeadingZeros(i) + (byteIndex << 3)
  assert h < 8;
  forall k | r < k < bitIndex
    ensures !Bit(t, k)
  {
    if k < 8 * byteIndex + 8 {
      var j: nat := k - 8 * byteIndex;
      assert h < j;
      assert !BitOf32(i, j);
    }
  }
}
