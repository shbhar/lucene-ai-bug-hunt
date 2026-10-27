"""postings-lucene104 (postings-lucene104): Z3 queries for the Lucene104 skip-data field widths and bit-set block bounds.

Model element -> Java line mapping (lucene/core/src/java/org/apache/lucene/codecs/lucene104/, main @ ea57360912ed):

  Q1 bit-set doc block bound
     docRange              Lucene104PostingsWriter.java:435   int docRange = lastDocID - level0LastDocID
     numBitSetLongs        Lucene104PostingsWriter.java:437   FixedBitSet.bits2words(docRange) = ((docRange-1)>>6)+1
     FOR-vs-bitset choice  Lucene104PostingsWriter.java:438-441  min(32, bpv+1)*256 <= numBitSetLongs*64 (int)
     byte header           Lucene104PostingsWriter.java:457   writeByte((byte) -numBitSetLongs)
     reader                Lucene104PostingsReader.java:592-609 bitsPerValue = readByte(); numLongs = -bitsPerValue
     reader capacity       Lucene104PostingsReader.java:307   docBitSet = new FixedBitSet(256 * 32) -> 128 longs
     limits                IndexWriter.MAX_DOCS = Integer.MAX_VALUE - 128 -> doc ids 0..MAX_DOCS-1
  Q2 VInt15 / VLong15 round trip
     writer                Lucene104PostingsWriter.java:371-387
     reader                Lucene104PostingsReader.java:1412-1431
  Q3 level-1 impact length stored in a signed short
     writer                Lucene104PostingsWriter.java:501-528  numImpactBytes = scratch size; writeShort((short) ...)
     impact encoding       Lucene104PostingsWriter.java:540-556  vint(freqDelta<<1 | flag) [+ zlong(normDelta)]
     reader                Lucene104PostingsReader.java:696-703  int numImpactBytes = docIn.readShort(); skipBytes/readBytes
     level-1 block size    Lucene104PostingsFormat.java:348-351  LEVEL1_NUM_DOCS = 32 * 256 = 8192 (4096 in Lucene912..103)
     competitive set       CompetitiveImpactAccumulator.java:63-71 byte norms -> at most 256 pairs; other norms unbounded
  Q4 docIDRunEnd level-1 density test when level1LastDocID == NO_MORE_DOCS
     Lucene104PostingsReader.java:1117-1121 level1IsDense = level1LastDocID - level0LastDocID == level1DocCountUpto - level0DocCountUpto
     Lucene104PostingsReader.java:683-688  tail: level1LastDocID = NO_MORE_DOCS, level1DocCountUpto += 8192
     Lucene104PostingsReader.java:1121     return level1LastDocID + 1  (int)

Run: python3 -I skip_bounds.py   (prints one line per query; exit 0)
"""
import sys
from z3 import (BitVec, BitVecVal, Solver, Optimize, Int, If, And, Or, Not, ULT, ULE, UGE, LShR, Extract,
                SignExt, ZeroExt, sat, unsat, simplify)

INT_MAX = 2**31 - 1
MAX_DOCS = INT_MAX - 128          # IndexWriter.MAX_DOCS
NO_MORE_DOCS = INT_MAX
results = []


def report(name, verdict, detail=""):
    line = f"{name}: {verdict}" + (f"  {detail}" if detail else "")
    print(line)
    results.append(line)


def bv32(x):
    return BitVecVal(x, 32)


# ---------------------------------------------------------------- Q1: bit-set doc block bound
def q1():
    r = BitVec("docRange", 32)
    words = (r - 1) >> 6  # arithmetic shift, r >= 256 > 0 so same as Java
    nlongs = words + 1
    bits_times = nlongs * 64  # int multiply, wraps
    # Q1a: within the legal range, numBitSetLongs * Long.SIZE never wraps.
    s = Solver()
    s.add(r >= 256, r <= bv32(MAX_DOCS))      # docRange <= (MAX_DOCS-1) - (-1)
    s.add(bits_times < 0)
    report("Q1a numBitSetLongs*64 overflow for legal docRange in [256, MAX_DOCS]", str(s.check()),
           "(unsat = proved safe)")
    # Q1b: the smallest docRange where it wraps (margin above MAX_DOCS).
    s = Solver()
    s.add(r >= 256, bits_times < 0)
    lo, hi = 256, INT_MAX
    # binary search with the solver as oracle
    while lo < hi:
        mid = (lo + hi) // 2
        s.push(); s.add(r <= bv32(mid))
        if s.check() == sat:
            hi = mid
        else:
            lo = mid + 1
        s.pop()
    report("Q1b first wrapping docRange", str(lo), f"margin above MAX_DOCS = {lo - MAX_DOCS} doc ids")
    # Q1c: if the bit-set branch is taken, 1 <= numBitSetLongs <= 127, the byte header is negative and
    # the reader recovers numLongs, and numLongs <= 128 (reader capacity).
    bpv = BitVec("bpv", 32)
    s = Solver()
    s.add(r >= 256, r <= bv32(MAX_DOCS), bpv >= 1, bpv <= 31)
    s.add(r != 256)                                          # 256 -> dense byte 0
    nbits_next = If(bpv + 1 < 32, bpv + 1, bv32(32)) * 256
    s.add(Not(nbits_next <= bits_times))                     # bit-set branch
    hdr = Extract(7, 0, -nlongs)                             # (byte) -numBitSetLongs
    read_bpv = SignExt(24, hdr)                              # docIn.readByte()
    s.add(Or(nlongs < 1, nlongs > 127, read_bpv >= 0, -read_bpv != nlongs))
    report("Q1c bit-set branch -> header byte round-trips and numLongs in [1,127]", str(s.check()),
           "(unsat = proved; free bpv/docRange, no bpv/docRange relation assumed)")


# ---------------------------------------------------------------- Q2: VInt15 / VLong15 round trip
def q2():
    v = BitVec("v", 32)
    s = Solver()
    s.add(v >= 0)
    lowshort = Extract(15, 0, v)                             # (short) v when v < 2^15
    small = (v & ~bv32(0x7FFF)) == 0
    hi15 = v >> 15                                           # written as vlong, read back as vint (fits)
    tagged = Extract(15, 0, bv32(0x8000) | (v & 0x7FFF))     # (short) (0x8000 | (v & 0x7FFF))
    s_read = If(small, SignExt(16, lowshort), SignExt(16, tagged))
    dec = If(s_read >= 0, s_read, (s_read & 0x7FFF) | (hi15 << 15))
    s.add(dec != v)
    report("Q2a readVInt15(writeVInt15(v)) == v for all v >= 0 (int)", str(s.check()), "(unsat = proved)")
    w = BitVec("w", 64)
    s = Solver()
    s.add(w >= 0)
    smallw = (w & ~BitVecVal(0x7FFF, 64)) == 0
    sw = If(smallw, SignExt(48, Extract(15, 0, w)), SignExt(48, Extract(15, 0, BitVecVal(0x8000, 64) | (w & 0x7FFF))))
    decw = If(sw >= 0, sw, (sw & 0x7FFF) | ((w >> 15) << 15))
    s.add(decw != w)
    report("Q2b readVLong15(writeVLong15(v)) == v for all v >= 0 (long)", str(s.check()), "(unsat = proved)")


# ---------------------------------------------------------------- Q3: level-1 impact byte length vs signed short
def vlen_unsigned(bits_needed):
    """VInt/VLong byte count for a value needing `bits_needed` bits (>=1)."""
    return max(1, -(-bits_needed // 7))


def impact_bytes_equal_deltas(x):
    """Bytes of one impact when freqDelta == normDelta == x (norm = field length, single-token docs whose
    custom term frequency is the field length): vint(x<<1 | (x!=0)) + (x!=0 ? zlong(x) : 0). Exact Java
    semantics: x<<1 is an int shift; for x < 2^30 it does not wrap (holds below because freq <= 2^31-1 is
    split over >= 4095 deltas)."""
    if x == 0:
        return 1
    a = (x << 1) | 1
    z = x << 1  # zigzag of non-negative x
    return vlen_unsigned(a.bit_length()) + vlen_unsigned(z.bit_length())


def q3():
    # Byte-class table for x = delta - 1 (exact, derived from the encoder above):
    classes = []
    prev = None
    x = 0
    # thresholds where the encoded size changes: 0, 1, 64, 8192, 2^20, 2^27
    for x in [0, 1, 63, 64, 8191, 8192, 2**20 - 1, 2**20, 2**27 - 1, 2**27]:
        classes.append((x, impact_bytes_equal_deltas(x)))
    # min x for each byte count
    by_bytes = {}
    for x, b in sorted(classes):
        by_bytes.setdefault(b, x)
    table = sorted(by_bytes.items())  # [(bytes, min_x)]
    for n_docs, label in [(8192, "Lucene104 (LEVEL1_NUM_DOCS = 8192)"), (4096, "Lucene912..103 (LEVEL1_NUM_DOCS = 4096)")]:
        opt = Optimize()
        counts = [Int(f"c_{b}") for b, _ in table]
        for c in counts:
            opt.add(c >= 0)
        # first impact is relative to (0, 0): with freq_1 = 1 + x0 the same table applies (x0 = freq_1 - 1)
        opt.add(sum(counts) == n_docs)
        # sum of freq deltas = largest freq <= Integer.MAX_VALUE (custom term frequency == field length,
        # IndexingChain adds term frequencies into FieldInvertState.length with Math.addExact)
        opt.add(sum(c * (mx + 1) for c, (_, mx) in zip(counts, table)) <= INT_MAX)
        total = sum(c * b for c, (b, _) in zip(counts, table))
        h = opt.maximize(total)
        assert opt.check() == sat
        best = opt.model().eval(total).as_long()
        report(f"Q3a max level-1 impact bytes, norm = length, {label}", str(best),
               "> 32767 overflows the short" if best > 32767 else "<= 32767 fits the short")
        s = Solver()
        cs = [Int(f"d_{b}") for b, _ in table]
        for c in cs:
            s.add(c >= 0)
        s.add(sum(cs) == n_docs)
        s.add(sum(c * (mx + 1) for c, (_, mx) in zip(cs, table)) <= INT_MAX)
        t = sum(c * b for c, (b, _) in zip(cs, table))
        s.add(t >= 65536)
        report(f"Q3b positive (silent) short wrap reachable, norm = length, {label}", str(s.check()),
               "(unsat = only negative wraps -> IllegalArgumentException on read)")
    # Q3c: default (byte) norms: at most 256 competitive pairs; per-pair bytes <= vint(5) + zlong of a delta
    # between two byte norms seen as longs in unsigned order (|delta| <= 256 -> <= 2 bytes) = 7.
    report("Q3c byte norms: max level-1 impact bytes", str(256 * 7), "<= 32767 (safe by limit)")
    # Q3d: wide norms (norm = length << 24), 8192 docs, freq step 8193 (the repro): exact size
    total = 0
    pf, pn = 0, 0
    for i in range(8192):
        f = 1 + i * 8193
        n = f << 24
        fd = f - pf - 1
        nd = n - pn - 1
        if nd == 0:
            total += vlen_unsigned(max(1, (fd << 1).bit_length()))
        else:
            zz = (nd << 1) if nd >= 0 else ((-nd) << 1) - 1
            total += vlen_unsigned(((fd << 1) | 1).bit_length()) + vlen_unsigned(max(1, zz.bit_length()))
        pf, pn = f, n
    wrapped = ((total + 2**15) % 2**16) - 2**15
    report("Q3d repro testWideNorms impact bytes", str(total), f"(short) = {wrapped}")
    total = 0
    pf, pn = 0, 0
    for i in range(8192):
        f = 1 + i * 8193
        n = f
        fd = f - pf - 1
        nd = n - pn - 1
        if nd == 0:
            total += vlen_unsigned(max(1, (fd << 1).bit_length()))
        else:
            zz = nd << 1
            total += vlen_unsigned(((fd << 1) | 1).bit_length()) + vlen_unsigned(max(1, zz.bit_length()))
        pf, pn = f, n
    wrapped = ((total + 2**15) % 2**16) - 2**15
    report("Q3e repro testExactLengthNorms impact bytes", str(total), f"(short) = {wrapped}")


# ---------------------------------------------------------------- Q4: docIDRunEnd in the level-1 tail
def q4():
    k = BitVec("k", 32)          # number of complete level-1 groups (>= 1 so that the term has level-1 data)
    j = BitVec("j", 32)          # 1-based index of the current full level-0 block inside the tail
    df = BitVec("docFreq", 32)
    l0 = BitVec("level0LastDocID", 32)
    s = Solver()
    s.add(k >= 1, k <= 2**18, j >= 1, j <= 31)
    l1_count_upto = (k + 1) * 8192           # level1DocCountUpto after the tail skipLevel1To
    l0_count_upto = k * 8192 + j * 256        # docFreq - docCountLeft after refilling block j of the tail
    s.add(df >= l0_count_upto, df < (k + 1) * 8192, df <= bv32(MAX_DOCS))
    remaining = df - l0_count_upto
    s.add(l0 >= l0_count_upto - 1, l0 + remaining <= bv32(MAX_DOCS - 1))
    lhs = bv32(NO_MORE_DOCS) - l0             # level1LastDocID - level0LastDocID
    rhs = l1_count_upto - l0_count_upto
    s.add(lhs == rhs)
    res = s.check()
    detail = ""
    if res == sat:
        m = s.model()
        detail = (f"witness docFreq={m[df].as_long()} k={m[k].as_long()} j={m[j].as_long()} "
                  f"level0LastDocID={m[l0].as_long()} -> returns NO_MORE_DOCS + 1 = {-(2**31)}")
    report("Q4a docIDRunEnd level1IsDense true with level1LastDocID == NO_MORE_DOCS", str(res), detail)
    s.push()
    s.add(l0 < bv32(MAX_DOCS - 1 - 8192 * 2))
    report("Q4b same, with level0LastDocID < MAX_DOCS - 16386 (any index below ~2^31 - 2^14 docs)",
           str(s.check()), "(unsat = unreachable unless the segment has ~2^31 docs)")
    s.pop()


def q4c():
    """Term present in every document of a segment (doc i is the i-th posting): find the segment sizes
    for which docIDRunEnd overflows on a tail block."""
    k = BitVec("k", 32)
    j = BitVec("j", 32)
    maxdoc = BitVec("maxDoc", 32)
    s = Solver()
    s.add(k >= 1, k <= 2**18, j >= 1, j <= 31)
    # domain constraints in 64-bit (no wrap); the reader's own arithmetic below stays 32-bit (Java int)
    K, J, M = ZeroExt(32, k), ZeroExt(32, j), ZeroExt(32, maxdoc)
    s.add(maxdoc >= 0, maxdoc <= bv32(MAX_DOCS), M >= K * 8192 + J * 256, M < (K + 1) * 8192)
    l0 = k * 8192 + j * 256 - 1                 # all docs: the j-th tail block ends at this doc id
    s.add(bv32(NO_MORE_DOCS) - l0 == ((k + 1) * 8192) - (k * 8192 + j * 256))
    res = s.check()
    lo = None
    if res == sat:
        m = s.model()
        kk = m[k].as_long()
        # smallest maxDoc: minimise over j (j = 1)
        s2 = Solver(); s2.add(s.assertions()); s2.add(j == 1)
        assert s2.check() == sat
        lo = s2.model()[k].as_long() * 8192 + 256
        s3 = Solver(); s3.add(s.assertions()); s3.add(k != kk)
        uniq = s3.check()
        report("Q4c all-docs term: docIDRunEnd overflows in a tail block", "sat",
               f"k={kk} (unique: {uniq == unsat}); any segment with maxDoc in [{lo}, MAX_DOCS={MAX_DOCS}]")
    else:
        report("Q4c all-docs term: docIDRunEnd overflows in a tail block", str(res))


if __name__ == "__main__":
    q1()
    q2()
    q3()
    q4()
    q4c()
