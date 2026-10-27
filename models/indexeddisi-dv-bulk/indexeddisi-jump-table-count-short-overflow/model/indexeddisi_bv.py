# Z3 bit-vector model of the IndexedDISI layout arithmetic (Lucene main @ ea57360912ed).
#
# Every Java int is a 32-bit two's-complement bit vector, every long a 64-bit one; Java shift
# distances are masked (& 31 for int, & 63 for long), which this model does explicitly (JBMC
# famously does not). Each obligation is checked as "precondition AND NOT property": unsat means
# the property is PROVED for every input allowed by the precondition; sat gives a counterexample.
# Every obligation also gets a vacuity probe (precondition alone must be sat) and, where useful, a
# mutant (a plausible wrong variant of the Java expression, which must give sat).
#
# Documented limits used as preconditions:
#   IndexWriter.MAX_DOCS = Integer.MAX_VALUE - 128 (IndexWriter.java:208), so a real doc id is in
#   [0, 2^31 - 130]. IndexedDISI.writeBitSet itself is public and accepts any DocIdSetIterator, so
#   obligations are also checked with the weaker bound doc in [0, 2^31 - 2] (NO_MORE_DOCS excluded).
#
# File:line map (lucene/core/src/java/org/apache/lucene/codecs/lucene90/IndexedDISI.java):
#   O1  block id written as short, read back as unsigned << 16   :117 (flush), :520 (readBlockHeader)
#   O2  writer per-block upTo = min(MAX_VALUE, (doc|0xFFFF)+1L)   :212-213
#   O3  cardinality header (short)(card-1) -> 1+unsigned          :119, :522, method choice :120/:525-534
#   O4  ALL index: gap = block - index - 1; index = target - gap  :532, :768, :774
#   O5  DENSE in-word rank: index = numberOfOnes - bitCount(word>>>target)   :669-673, :712-713
#       DENSE next doc in word: target + ntz(word>>>target)        :671
#   O6  rank table layout (createRank) vs lookup (rankSkip), p in 7..15        :141-156, :835-855
#       rank skip is forward-only under the skip condition         :658-661, :701-704
#       rank entry byte-pair round trip                             :150-151, :842-844
#   O7  jump table entry count returned as short                    :231, :235, :253-267
#       reader: createJumpTable (<=0 -> null), createBlockSlice (<0 -> 0 bytes)  :372-398
#   O8  jump-table offset (int) cannot overflow                    :215, :239-247
#   O9  DENSE intoBitSetWithinBlock argument arithmetic             :718-754
#       (destTo = unsignedMin(upTo, offset + bitSet.length()), orRange bounds, readLongs bounds,
#        true/false path split, ISE only on caller precondition violation)
#   O10 docIDRunEnd overflow: DENSE (doc|0x3F)+1, ALL (doc|0xFFFF)+1  :758-761, :793
#   O11 advanceBlock jump condition and in-range clamp             :498-505
#   O12 SPARSE doc reconstruction block | (short & 0xFFFF)          :584-588, :630-631

import sys
import z3

INT_MAX = 2**31 - 1
MAX_DOCS = INT_MAX - 128          # IndexWriter.MAX_DOCS
MAX_DOC_ID = MAX_DOCS - 1         # 2^31 - 130
NO_MORE_DOCS = INT_MAX
BLOCK_SIZE = 65536
MAX_ARRAY_LENGTH = (1 << 12) - 1  # 4095

results = []


def I(v):
    return z3.BitVecVal(v & 0xFFFFFFFF, 32)


def L(v):
    return z3.BitVecVal(v & 0xFFFFFFFFFFFFFFFF, 64)


def ishl(a, s):  # Java int <<
    return a << (s & I(31))


def ishr(a, s):  # Java int >>
    return a >> (s & I(31))


def iushr(a, s):  # Java int >>>
    return z3.LShR(a, s & I(31))


def lushr(a, s):  # Java long >>>, s is a 32-bit int distance
    return z3.LShR(a, z3.ZeroExt(32, s & I(63)))


def lshl(a, s):
    return a << z3.ZeroExt(32, s & I(63))


def i2l(a):
    return z3.SignExt(32, a)


def l2i(a):
    return z3.Extract(31, 0, a)


def i2s(a):  # (short) int -> sign-extended back to int when read via readShort()
    return z3.SignExt(16, z3.Extract(15, 0, a))


def s_unsigned(a):  # Short.toUnsignedInt((short) a)
    return z3.ZeroExt(16, z3.Extract(15, 0, a))


def popcount64(w):
    return z3.Sum([z3.ZeroExt(31, z3.Extract(i, i, w)) for i in range(64)])


def ntz64(w):
    # Long.numberOfTrailingZeros: 64 for 0
    r = I(64)
    for i in range(63, -1, -1):
        r = z3.If(z3.Extract(i, i, w) == 1, I(i), r)
    return r


def imin(a, b):
    return z3.If(a < b, a, b)


def unsigned_min(a, b):  # MathUtil.unsignedMin
    return z3.If(z3.ULT(a, b), a, b)


def check(name, pre, prop, expect="unsat", note="", quiet=False):
    s = z3.Solver()
    s.set("timeout", 600000)
    s.add(pre)
    vac = s.check()
    s.push()
    s.add(z3.Not(prop))
    r = s.check()
    model = None
    if r == z3.sat:
        m = s.model()
        model = {str(d): m[d].as_signed_long() if hasattr(m[d], "as_signed_long") else str(m[d])
                 for d in m.decls()}
    ok = (str(r) == expect) and vac == z3.sat
    if quiet:
        return r, model
    results.append((name, str(vac), str(r), expect, ok, model, note))
    print(f"{'OK ' if ok else 'BAD'} {name}: vacuity={vac} result={r} expected={expect} {note}")
    if model is not None:
        print("    model:", model)
    sys.stdout.flush()
    return r, model


def doc_range(doc, hi=MAX_DOC_ID):
    return z3.And(doc >= 0, doc <= I(hi))


# ---------------------------------------------------------------------------------------------
# O1: block id round trip. flush writes (short) block where block = doc >>> 16; readBlockHeader
# reads Short.toUnsignedInt(readShort()) << 16. Property: equals doc & 0xFFFF0000 and is >= 0.
doc = z3.BitVec("doc", 32)
blk = iushr(doc, I(16))
read_block = ishl(s_unsigned(blk), I(16))
for hi, tag in ((MAX_DOC_ID, "MAX_DOCS"), (INT_MAX - 1, "any doc < NO_MORE_DOCS")):
    check(f"O1 block id round trip [{tag}]", doc_range(doc, hi),
          z3.And(read_block == (doc & I(0xFFFF0000)), read_block >= 0))
# mutant: signed readShort instead of toUnsignedInt
check("O1-mutant shift by 15 instead of 16 must fail", doc_range(doc, INT_MAX - 1),
      ishl(s_unsigned(blk), I(15)) == (doc & I(0xFFFF0000)), expect="sat")

# ---------------------------------------------------------------------------------------------
# O2: writer upTo = (int) Math.min(Integer.MAX_VALUE, (doc | 0xFFFF) + 1L).
# Property: every doc d in the same 65536-block as `doc` with d <= limit satisfies d < upTo, and
# no doc of the next block is < upTo; and offset = doc & 0xFFFF0000 <= doc (intoBitSet precondition).
d2 = z3.BitVec("d2", 32)
upto_l = i2l(doc | I(0xFFFF)) + L(1)
upto = l2i(z3.If(upto_l < L(INT_MAX), upto_l, L(INT_MAX)))
same_block = iushr(d2, I(16)) == iushr(doc, I(16))
for hi, tag in ((MAX_DOC_ID, "MAX_DOCS"), (INT_MAX - 1, "any doc < NO_MORE_DOCS")):
    pre = z3.And(doc_range(doc, hi), doc_range(d2, hi))
    check(f"O2 writer upTo covers exactly the block [{tag}]", pre,
          z3.And(z3.Implies(same_block, d2 < upto),
                 z3.Implies(iushr(d2, I(16)) > iushr(doc, I(16)), d2 >= upto),
                 (doc & I(0xFFFF0000)) <= doc, upto > doc))

# ---------------------------------------------------------------------------------------------
# O3: cardinality header. Writer: writeShort((short)(cardinality - 1)), DENSE/ALL iff
# cardinality > MAX_ARRAY_LENGTH, ALL iff cardinality == 65536. Reader: numValues = 1 +
# toUnsignedInt(readShort()); SPARSE iff numValues <= MAX_ARRAY_LENGTH, ALL iff == 65536.
card = z3.BitVec("card", 32)
num_values = I(1) + s_unsigned(card - I(1))
check("O3 cardinality header round trip and method agreement",
      z3.And(card >= 1, card <= BLOCK_SIZE),
      z3.And(num_values == card,
             (num_values <= MAX_ARRAY_LENGTH) == z3.Not(card > MAX_ARRAY_LENGTH),
             (num_values == BLOCK_SIZE) == (card == BLOCK_SIZE)))
# the writer's flush() is also called with cardinality 0 if an iterator's intoBitSet leaves the
# iterator inside an already-flushed block (assert at :118 only); with -da the header reads back
# as 65536, i.e. an ALL block. Documented here as a consequence check (sat expected).
check("O3b cardinality 0 (only via a broken source iterator) decodes as ALL",
      card == 0, num_values == BLOCK_SIZE, expect="unsat",
      note="(card=0 -> (short)-1 -> 65536: an ALL block; reachable only if the source iterator's intoBitSet does not advance)")

# ---------------------------------------------------------------------------------------------
# O4: ALL block index arithmetic. At readBlockHeader, index = nextBlockIndex = (#docs before block)
# - 1 =: C - 1. gap = block - index - 1. advanceWithinBlock/advanceExactWithinBlock: index = target
# - gap. Property: index == C + (target - block), the ordinal of target.
C = z3.BitVec("C", 32)
blockv = z3.BitVec("block", 32)
target = z3.BitVec("target", 32)
idx0 = C - I(1)
gap = blockv - idx0 - I(1)
pre = z3.And((blockv & I(0xFFFF)) == 0, blockv >= 0, C >= 0, C <= blockv,
             target >= blockv, target - blockv <= I(0xFFFF), doc_range(target, INT_MAX - 1),
             # at most MAX_DOCS docs overall
             z3.ULE(z3.ZeroExt(1, C) + z3.ZeroExt(1, target - blockv) + 1, z3.BitVecVal(MAX_DOCS, 33)))
check("O4 ALL index = target - gap is the ordinal", pre,
      z3.And(target - gap == C + (target - blockv), target - gap >= 0))
check("O4-mutant gap off by one must fail", pre, target - (blockv - idx0) == C + (target - blockv),
      expect="sat")

# ---------------------------------------------------------------------------------------------
# O5: DENSE in-word rank. With numberOfOnes = base + popcount(words before) + popcount(word), the
# code sets index = numberOfOnes - bitCount(word >>> target). Property (for the word part):
# popcount(word) - popcount(word >>> (target & 63)) == popcount(word & ((1L << (target&63)) - 1)),
# i.e. the number of set bits strictly below target in its word. Also: when leftBits != 0 the
# next doc target + ntz(leftBits) is the smallest set bit >= target in the word and stays in it.
w = z3.BitVec("word", 64)
t = z3.BitVec("t", 32)
left = lushr(w, t)
below = w & (lshl(L(1), t) - L(1))
# O5a with a symbolic shift times out (popcount over a symbolic barrel shift); split on the 64
# values of target & 63 (Java masks the long shift distance), each case is then quantifier-free
# and concrete-shift. Plus a target-level check that the split is exhaustive.
o5a_all = True
for sh in range(64):
    r5, _ = check(f"O5a[{sh}] DENSE rank: bitCount(word) - bitCount(word>>>t) == bits below t",
                  z3.And(t >= 0, (t & I(63)) == sh),
                  popcount64(w) - popcount64(left) == popcount64(below), quiet=True)
    o5a_all = o5a_all and r5 == z3.unsat
print(f"{'OK ' if o5a_all else 'BAD'} O5a DENSE rank identity: all 64 shift cases unsat = {o5a_all}")
results.append(("O5a (64-way split)", "sat", "unsat" if o5a_all else "?", "unsat", o5a_all, None, ""))
nxt = t + ntz64(left)
k = z3.BitVec("k", 32)
bit_at = lambda pos: z3.Extract(0, 0, lushr(w, pos)) == 1
# quantifier-free: k is a free variable ranging over [t, nxt)
check("O5b DENSE next doc in word is the least set bit >= t, inside the word",
      z3.And(t >= 0, t <= I(INT_MAX - 1), left != 0, k >= t, k < nxt),
      z3.And(bit_at(nxt), (nxt & I(~63)) == (t & I(~63)), nxt >= t, z3.Not(bit_at(k))))
check("O5c advanceExact membership (leftBits & 1) == bit t", t >= 0,
      (z3.Extract(0, 0, left) == 1) == bit_at(t))
check("O5-mutant rank with signed shift must fail", t >= 0,
      popcount64(w) - popcount64(w >> z3.ZeroExt(32, t & I(63))) == popcount64(below), expect="sat")

# ---------------------------------------------------------------------------------------------
# O6: rank table. createRank(p): longsPerRank = 1 << (p-6), rankMark = longsPerRank-1,
# rankIndexShift = p-7, rank = byte[1024 >> rankIndexShift]; for each word with (word & rankMark)
# == 0: rank[word >> shift] = hi(bitCount), rank[(word >> shift)+1] = lo(bitCount), where bitCount =
# popcount of words [0, word). rankSkip(t): rankIndex = t >> p; reads bytes 2*rankIndex, +1;
# rankAlignedWordIndex = rankIndex << p >> 6. Properties for all p in 7..15, t in [0, 65535]:
#  (a) the byte-pair index read is inside rank[]; (b) it is exactly the pair that createRank wrote
#  for word rankAlignedWordIndex (so the rank is popcount of words [0, rankAlignedWordIndex));
#  (c) rankAlignedWordIndex <= t>>>6 < rankAlignedWordIndex + longsPerRank; (d) under the skip
#  condition (t>>>6) - wordIndex >= longsPerRank with wordIndex in [-1, 1023], rankAlignedWordIndex
#  > wordIndex (the skip only moves forward, so the counting invariant is preserved).
p = z3.BitVec("p", 32)
wi = z3.BitVec("wordIndex", 32)
tib = z3.BitVec("targetInBlock", 32)
longs_per_rank = ishl(I(1), p - I(6))
rank_index_shift = p - I(7)
rank_len = ishr(I(1024), rank_index_shift)
rank_index = ishr(tib, p)
byte_idx = ishl(rank_index, I(1))
raw = ishr(ishl(rank_index, p), I(6))
twi = iushr(tib, I(6))
written_word = raw  # createRank writes the pair for word `raw` at index raw >> shift
pre6 = z3.And(p >= 7, p <= 15, tib >= 0, tib <= 0xFFFF, wi >= -1, wi <= 1023)
check("O6 rank table lookup matches createRank layout and is forward-only", pre6,
      z3.And(byte_idx >= 0, byte_idx + 1 < rank_len,
             (written_word & (longs_per_rank - 1)) == 0,
             ishr(written_word, rank_index_shift) == byte_idx,
             raw <= twi, twi < raw + longs_per_rank,
             z3.Implies(twi - wi >= longs_per_rank, raw > wi)))
check("O6-mutant rankIndexShift = p - 6 must fail", pre6,
      z3.And(ishr(written_word, p - I(6)) == byte_idx), expect="sat")
bc = z3.BitVec("bitCount", 32)
hi_b = z3.Extract(7, 0, ishr(bc, I(8)))
lo_b = z3.Extract(7, 0, bc & I(0xFF))
rebuilt = ishl(z3.ZeroExt(24, hi_b), I(8)) | z3.ZeroExt(24, lo_b)
check("O6b rank byte-pair round trip for every rank value of a DENSE block",
      z3.And(bc >= 0, bc <= BLOCK_SIZE - 1), rebuilt == bc)

# ---------------------------------------------------------------------------------------------
# O7: jump-table entry count. writeBitSet: lastBlock = (last real block id) + 1 (0 if no docs);
# flushBlockJumps(jumps, lastBlock + 1) writes blockCount = lastBlock + 1 entries (0 if
# blockCount == 2) and returns (short) blockCount. Callers store it with writeShort and read it back
# with readShort; IndexedDISI.createJumpTable returns null for <= 0 and createBlockSlice treats < 0
# as "no jump table bytes". Property: the value read back equals the number of entries written.
last_doc = z3.BitVec("lastDoc", 32)
last_block = iushr(last_doc, I(16)) + I(1)
block_count = last_block + I(1)
entries = z3.If(block_count == 2, I(0), block_count)
returned = i2s(entries)
r, m = check("O7 jump-table entry count survives the short return value [MAX_DOCS]",
             doc_range(last_doc, MAX_DOC_ID), returned == entries, expect="sat",
             note="(sat = counterexample: count does not fit a short)")
# smallest last doc id that loses the jump table
opt = z3.Optimize()
opt.add(doc_range(last_doc, MAX_DOC_ID), returned != entries)
h = opt.minimize(last_doc)
assert opt.check() == z3.sat
min_bad = opt.model()[last_doc].as_long()
print(f"    O7 smallest last doc id with a lost jump table: {min_bad} (= {min_bad >> 16} << 16), "
      f"entries={(min_bad >> 16) + 2}, returned={(((min_bad >> 16) + 2) ^ 0x8000) - 0x8000}")
results.append(("O7 min last doc", "sat", "sat", "sat", True, {"min_last_doc": min_bad}, ""))
check("O7b below that doc id the count always fits",
      z3.And(doc_range(last_doc, MAX_DOC_ID), last_doc < I(min_bad)), returned == entries)
# what the reader does with the negative value: jump table disabled (null), block slice = full length
neg = returned < 0
check("O7c a lost count always reads back negative (jump table silently disabled, not misread)",
      z3.And(doc_range(last_doc, MAX_DOC_ID), returned != entries), neg)

# ---------------------------------------------------------------------------------------------
# O8: jump-table offset is an int (addJumps casts offset to int; assert offset < MAX_VALUE).
# Largest block: DENSE = 4 header + rank (1024 >> (p-7) bytes, 0 if p == -1) + 8192 bitmap;
# SPARSE <= 4 + 2*4095; ALL = 4. At most 32768 real blocks precede the NO_MORE_DOCS block.
nb = z3.BitVec("numBlocks", 64)
pp = z3.BitVec("pp", 64)
rank_bytes = z3.If(pp == -1, L(0), z3.LShR(L(1024), pp - L(7)))
max_block = 4 + rank_bytes + L(8192)
check("O8 jump-table offsets fit an int for every denseRankPower",
      z3.And(z3.Or(pp == -1, z3.And(pp >= 7, pp <= 15)), nb >= 0, nb <= 32768),
      nb * max_block < L(INT_MAX))

# ---------------------------------------------------------------------------------------------
# O9: DENSE intoBitSetWithinBlock argument arithmetic (IndexedDISI.java:718-754).
# State: block (multiple of 65536), doc in [block, block+65535] (the current doc, a match),
# wordIndex = (doc & 0xFFFF) >>> 6. Call: intoBitSet(upTo, bitSet, offset) with offset <= doc
# (DocIdSetIterator#intoBitSet), doc < upTo (loop guard :478), len = bitSet.length() >= 0.
# Caller obligation implied by "behave exactly as the default implementation" (bitSet.set(doc -
# offset) for every match < upTo): every match d in [doc, upTo) has d - offset < len. We encode
# the strongest form that does not depend on the bit pattern: doc - offset < len (current doc).
blk9 = z3.BitVec("block", 32)
doc9 = z3.BitVec("doc", 32)
off9 = z3.BitVec("offset", 32)
len9 = z3.BitVec("len", 32)
up9 = z3.BitVec("upTo", 32)
dest_from = doc9 - off9
dest_to = unsigned_min(up9, off9 + len9)
source_from = doc9 & I(0xFFFF)
source_to = imin(dest_to - blk9, I(BLOCK_SIZE))
wordIndex9 = iushr(source_from, I(6))
bits2words = lambda n: ishr(n - I(1), I(6)) + I(1)
num_words = bits2words(source_to) - wordIndex9
length9 = source_to - source_from
block_end9 = blk9 | I(0xFFFF)
# exact (mathematical) bound of the bits the default implementation would set from this block
X = lambda v: z3.SignExt(2, v)
end_math = z3.If(X(up9) < X(off9) + X(len9), X(up9), X(off9) + X(len9))
end_math = z3.If(end_math < X(blk9) + BLOCK_SIZE, end_math, X(blk9) + BLOCK_SIZE)
for hi, tag in ((MAX_DOC_ID, "MAX_DOCS"), (INT_MAX - 1, "any doc < NO_MORE_DOCS")):
    pre9 = z3.And((blk9 & I(0xFFFF)) == 0, blk9 >= 0, doc9 >= blk9, doc9 - blk9 <= I(0xFFFF),
                  doc_range(doc9, hi), off9 <= doc9, doc9 < up9, len9 >= 0,
                  # the current doc must fit (default implementation sets doc - offset)
                  X(doc9) - X(off9) < X(len9))
    check(f"O9a DENSE intoBitSet: orRange and readLongs arguments in bounds [{tag}]", pre9,
          z3.And(length9 > 0, dest_from >= 0, dest_from + length9 <= len9,
                 source_from >= 0, source_to <= BLOCK_SIZE,
                 num_words >= 1, wordIndex9 + num_words <= 1024,
                 dest_from + length9 >= dest_from))
    check(f"O9b DENSE intoBitSet copies exactly [doc, min(upTo, offset+len, blockEnd+1)) [{tag}]",
          pre9, X(blk9) + X(source_to) == end_math)
    # false path (:735) is taken iff the copy reached the end of the block
    check(f"O9c DENSE intoBitSet: false path iff the whole rest of the block was copied [{tag}]",
          pre9, (dest_to > block_end9) == (source_to == BLOCK_SIZE))
    # true path calls advanceWithinBlock(destTo): destTo must be inside this block and > doc
    check(f"O9d DENSE intoBitSet: true-path target in (doc, blockEnd] [{tag}]",
          z3.And(pre9, z3.Not(dest_to > block_end9)),
          z3.And(dest_to > doc9, dest_to <= block_end9))
    # the IllegalStateException (:742-751) fires for a found doc f in [destTo, upTo); show that such
    # an f is never a doc the caller allowed (f - offset >= len), i.e. the ISE only reports caller misuse
    f9 = z3.BitVec("found", 32)
    check(f"O9e DENSE intoBitSet: ISE only for docs outside the caller's bitSet [{tag}]",
          z3.And(pre9, z3.Not(dest_to > block_end9), f9 >= dest_to, f9 < up9, f9 <= block_end9),
          X(f9) - X(off9) >= X(len9))
# mutant: the pre-#14922 code (sourceTo from upTo, ignoring bitSet.length())
pre9m = z3.And((blk9 & I(0xFFFF)) == 0, blk9 >= 0, doc9 >= blk9, doc9 - blk9 <= I(0xFFFF),
               doc_range(doc9), off9 <= doc9, doc9 < up9, len9 >= 0, X(doc9) - X(off9) < X(len9))
st_old = imin(up9 - blk9, I(BLOCK_SIZE))
check("O9-mutant pre-#14922 sourceTo overruns the bitSet (must fail)", pre9m,
      dest_from + (st_old - source_from) <= len9, expect="sat")
# mutant: signed min instead of unsignedMin (offset + len can wrap)
dest_to_signed = imin(up9, off9 + len9)
st_s = imin(dest_to_signed - blk9, I(BLOCK_SIZE))
check("O9-mutant signed min instead of unsignedMin (must fail)", pre9m,
      X(blk9) + X(st_s) == end_math, expect="sat")

# ---------------------------------------------------------------------------------------------
# O10: docIDRunEnd overflow. DENSE returns (doc | 0x3F) + 1 when word == -1L, i.e. all 64 docs of
# the word are present; ALL returns (doc | 0xFFFF) + 1, i.e. all 65536 docs of the block present.
# Property: whenever every doc of the word/block is a legal doc (< NO_MORE_DOCS; writeBitSet never
# encodes NO_MORE_DOCS) the result is > doc (no int overflow).
doc10 = z3.BitVec("doc", 32)
check("O10a DENSE docIDRunEnd (doc|0x3F)+1 > doc when the full word is legal docs",
      z3.And(doc_range(doc10, INT_MAX - 1), (doc10 | I(0x3F)) <= I(INT_MAX - 1)),
      (doc10 | I(0x3F)) + I(1) > doc10)
check("O10b ALL docIDRunEnd (doc|0xFFFF)+1 > doc when the full block is legal docs",
      z3.And(doc_range(doc10, INT_MAX - 1), (doc10 | I(0xFFFF)) <= I(INT_MAX - 1)),
      (doc10 | I(0xFFFF)) + I(1) > doc10)
check("O10c the only overflow cases need doc NO_MORE_DOCS in the set",
      z3.And(doc_range(doc10, INT_MAX - 1), z3.Not((doc10 | I(0x3F)) + I(1) > doc10)),
      (doc10 | I(0x3F)) == I(INT_MAX))

# ---------------------------------------------------------------------------------------------
# O11: advanceBlock. Jump used iff blockIndex >= (block >> 16) + 2 (block = -1 initially, so the
# first jump is to blockIndex >= 1). inRangeBlockIndex = min(blockIndex, entryCount - 1); the read
# offsets inRange * 8L and + 4 are inside the table [0, entryCount*8).
bi = z3.BitVec("blockIndex", 32)
cur = z3.BitVec("curBlock", 32)
cnt = z3.BitVec("entryCount", 32)
in_range = z3.If(bi < cnt, bi, cnt - I(1))
pre11 = z3.And(bi >= 0, bi <= 32767, cnt >= 1, cnt <= 32767,
               z3.Or(cur == -1, z3.And((cur & I(0xFFFF)) == 0, cur >= 0)),
               bi >= ishr(cur, I(16)) + I(2))
off11 = i2l(in_range) * L(8)
check("O11 jump-table reads stay inside the table", pre11,
      z3.And(in_range >= 0, off11 + L(8) <= i2l(cnt) * L(8)))

# ---------------------------------------------------------------------------------------------
# O12: SPARSE doc reconstruction block | (readShort() & 0xFFFF) equals the written doc.
doc12 = z3.BitVec("doc", 32)
written = z3.Extract(15, 0, doc12)  # writeShort((short) doc) of the in-block doc (:133)
rebuilt12 = (doc12 & I(0xFFFF0000)) | (z3.SignExt(16, written) & I(0xFFFF))
check("O12 SPARSE doc reconstruction", doc_range(doc12, INT_MAX - 1), rebuilt12 == doc12)

print()
bad = [r for r in results if not r[4]]
print(f"{len(results)} obligations, {len(bad)} unexpected")
for r in bad:
    print("UNEXPECTED:", r)
