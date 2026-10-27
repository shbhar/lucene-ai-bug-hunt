# Z3 bit-vector obligations for "overflow at scale" in Lucene main 7cecf79523.
# Every Java int is a 32-bit BV, every long a 64-bit BV, with Java's two's-complement wraparound
# (z3 bvadd/bvmul wrap exactly like Java). Shift distances are masked explicitly (& 31 / & 63),
# exactly like JLS 15.19. A property is PROVED when "preconditions AND NOT property" is UNSAT;
# otherwise the model is a counterexample (cex). Limits are cited in LIMITS below.
#
# run: python3 -I z3/overflow_obligations.py [--smt2-dir DIR]
import json
import sys
import time

from z3 import (And, BitVec, BitVecVal, If, Implies, LShR, Not, Or, Solver, ULT, ZeroExt,
                SignExt, sat, unsat, BV2Int, Int, IntVal, Sum, Extract)

INT_MAX = 2**31 - 1
INT_MIN = -2**31

# ---- documented limits (source: lucene main 7cecf79523) ----
LIMITS = {
    "MAX_DOCS": (INT_MAX - 128, "core/.../index/IndexWriter.java:208 MAX_DOCS = Integer.MAX_VALUE - 128"),
    # ArrayUtil.java:33 MAX_ARRAY_LENGTH = Integer.MAX_VALUE - NUM_BYTES_ARRAY_HEADER (16 with
    # compressed oops, 24 without); we use the larger one (16) for counterexamples and quantify
    # over both for proofs where it matters.
    "MAX_ARRAY_LENGTH": (INT_MAX - 16, "core/.../util/ArrayUtil.java:33"),
    "MAX_ARRAY_LENGTH_NOCOOPS": (INT_MAX - 24, "core/.../util/ArrayUtil.java:33 (24-byte header)"),
    "BINARY_DV_MAX_LENGTH": (INT_MAX - 16, "core/.../index/BinaryDocValuesWriter.java:40 MAX_LENGTH = ArrayUtil.MAX_ARRAY_LENGTH"),
    "BKD_MAX_DIMS": (16, "core/.../util/bkd/BKDConfig.java MAX_DIMS = 16"),
    "POINT_MAX_NUM_BYTES": (16, "core/.../index/PointValues.java:107 MAX_NUM_BYTES = 16"),
    "MAX_VECTOR_DIMS": (1024, "core/.../codecs/KnnVectorsFormat.java:41 DEFAULT_MAX_DIMENSIONS = 1024"),
    "HNSW_MAXIMUM_MAX_CONN": (512, "core/.../codecs/lucene99/Lucene99HnswVectorsFormat.java:102"),
    "MAX_RUN_END_SCAN": (1 << 16, "core/.../util/BitSetIterator.java:59"),
    "NO_MORE_DOCS": (INT_MAX, "DocIdSetIterator.NO_MORE_DOCS = Integer.MAX_VALUE"),
}
MAX_DOCS = LIMITS["MAX_DOCS"][0]
MAL = LIMITS["MAX_ARRAY_LENGTH"][0]

RESULTS = []
SMT2_DIR = None


def i32(name):
    return BitVec(name, 32)


def i64(name):
    return BitVec(name, 64)


def c32(v):
    return BitVecVal(v, 32)


def c64(v):
    return BitVecVal(v, 64)


def jmin(a, b):  # Math.min(int,int)
    return If(a < b, a, b)


def jmax(a, b):
    return If(a > b, a, b)


def wide(x, n=64):  # exact mathematical value of a 32-bit signed BV, in a wider BV
    return SignExt(n - x.size(), x)


def check(oid, title, src, pre, prop, expect, note="", show=()):
    """expect: 'proved' or 'cex' (what we expect; recorded, never used to force the result)."""
    s = Solver()
    s.set("timeout", 600_000)
    s.add(pre)
    s.add(Not(prop))
    t = time.time()
    r = s.check()
    dt = time.time() - t
    entry = {"id": oid, "title": title, "src": src, "time_s": round(dt, 3), "expected": expect,
             "note": note}
    if r == unsat:
        entry["result"] = "PROVED"
    elif r == sat:
        m = s.model()
        entry["result"] = "CEX"
        entry["cex"] = {str(v): (m.eval(v, model_completion=True).as_signed_long()
                                 if hasattr(m.eval(v, model_completion=True), "as_signed_long")
                                 else str(m.eval(v, model_completion=True))) for v in show}
    else:
        entry["result"] = "UNKNOWN(" + str(s.reason_unknown()) + ")"
    if SMT2_DIR:
        with open(f"{SMT2_DIR}/{oid}.smt2", "w") as f:
            f.write("; " + title + "\n; " + src + "\n")
            f.write(s.to_smt2())
    RESULTS.append(entry)
    flag = "" if entry["result"].lower().startswith(expect[:3].lower()) else "   <-- UNEXPECTED"
    print(f"[{oid}] {entry['result']:<8} {dt:7.3f}s  {title}{flag}")
    if "cex" in entry:
        print("        cex:", entry["cex"])
    return entry


# =====================================================================================
# Family A: doc-ID block / window arithmetic near 2^31 (reachable only with huge maxDoc)
# =====================================================================================

def roaring_range_add():
    src = "core/.../util/RoaringDocIdSet.java:161-171 (Builder.add(int,int), #16081, 2026-05-18)"
    doc, mx, maxDoc = i32("doc"), i32("max"), i32("maxDoc")
    block = LShR(doc, 16)
    blockEnd = jmin(mx, (block + 1) << 16)
    # true value of (block+1)<<16 as a mathematical integer
    trueEnd = (ZeroExt(32, block) + 1) * 65536
    pre = And(maxDoc >= 0, maxDoc <= MAX_DOCS, doc >= 0, doc < mx, mx <= maxDoc)
    # property: blockEnd computed in int equals min(max, true block end) and makes progress
    prop = And(blockEnd > doc, wide(blockEnd) == If(wide(mx) < trueEnd, wide(mx), trueEnd))
    check("A1", "RoaringDocIdSet.Builder.add(min,max): blockEnd=min(max,(block+1)<<16) > doc", src,
          pre, prop, "cex", show=(doc, mx, maxDoc))
    # smallest doc for which it fails
    s = Solver(); s.add(pre, Not(prop))
    lo, hi = 0, MAX_DOCS
    while lo < hi:
        mid = (lo + hi) // 2
        s.push(); s.add(doc <= mid)
        if s.check() == sat:
            hi = mid
        else:
            lo = mid + 1
        s.pop()
    print(f"        smallest failing doc = {lo} (= 2^31 - {2**31 - lo}); needs maxDoc > {lo}")
    RESULTS[-1]["min_failing_doc"] = lo
    # proof of the safe region
    check("A1s", "  ... safe iff doc < 2^31-2^16 (i.e. always safe when maxDoc <= 2^31-2^16)", src,
          And(pre, doc < 2**31 - 2**16), prop, "proved")
    # the constructor's unsigned trick is correct for every int maxDoc >= 0
    m = i32("m")
    numBlocks = LShR(m + 65535, 16)
    check("A2", "RoaringDocIdSet.Builder(maxDoc): (maxDoc+65535)>>>16 == ceil(maxDoc/65536) for all maxDoc>=0",
          "core/.../util/RoaringDocIdSet.java:63", m >= 0,
          ZeroExt(32, numBlocks) == (ZeroExt(32, m) + 65535) / 65536, "proved")


def sparse_fixed_bitset():
    src = "core/.../util/SparseFixedBitSet.java:434-479 (firstClearInBlock, #16072, 2026-05-18)"
    length, i4096 = i32("length"), i32("i4096")
    blockBase = i4096 << 12
    blockEnd = jmin(blockBase + 4096, length)
    # blockCount(length) from SparseFixedBitSet.java:45-51
    bc = LShR(length, 12)
    bc = If((bc << 12) < length, bc + 1, bc)
    pre = And(length >= 1, length <= MAX_DOCS, i4096 >= 0, i4096 < bc)
    trueEnd = jmin(wide(blockBase) + 4096, wide(length))
    check("A3", "SparseFixedBitSet.firstClearInBlock: blockEnd=min(blockBase+4096,length) is exact, length<=MAX_DOCS",
          src, pre, And(wide(blockEnd) == trueEnd, blockEnd > blockBase), "cex", show=(length, i4096))
    check("A3s", "  ... safe iff length <= 2^31-4096", src, And(pre, length <= 2**31 - 4096),
          And(wide(blockEnd) == trueEnd, blockEnd > blockBase), "proved")
    # wStart + 64 inside the same function: wStart = (pos>>>6)<<6 with pos < blockCap <= length
    pos = i32("pos")
    wStart = LShR(pos, 6) << 6
    check("A4", "SparseFixedBitSet.firstClearInBlock: wStart+64 does not overflow for length<=MAX_DOCS",
          src, And(length >= 1, length <= MAX_DOCS, pos >= 0, pos < length),
          wide(wStart + 64) == wide(wStart) + 64, "proved")
    check("A4b", "  ... but it does for lengths the class itself accepts (length up to Integer.MAX_VALUE)",
          src, And(length >= 1, pos >= 0, pos < length),
          wide(wStart + 64) == wide(wStart) + 64, "cex", show=(length, pos))
    # blockCount itself is exact for every length >= 1
    check("A5", "SparseFixedBitSet.blockCount(length) == ceil(length/4096) for all int length>=1",
          "core/.../util/SparseFixedBitSet.java:45-51", length >= 1,
          ZeroExt(32, bc) == (ZeroExt(32, length) + 4095) / 4096, "proved")


def boolean_scorer_window():
    src = "core/.../search/BooleanScorer.java:270-272 (scoreWindow; line 272 from 03ab02157a2d, 2023-08-06)"
    doc, mx = i32("topDoc"), i32("max")
    windowBase = doc & ~c32(4095)
    windowMax = jmin(mx, windowBase + 4096)
    pre = And(doc >= 0, doc < MAX_DOCS, doc < mx)  # a matching doc of a segment, max up to NO_MORE_DOCS
    check("A6", "BooleanScorer.scoreWindow: windowMax=min(max,windowBase+SIZE) > topDoc (window contains the match)",
          src, pre, windowMax > doc, "cex", show=(doc, mx))
    check("A6s", "  ... safe iff topDoc < 2^31-4096", src, And(pre, doc < 2**31 - 4096), windowMax > doc,
          "proved")
    # the single-scorer path then computes end = max(windowMax, min(max, nextWindowBase));
    # show that when the window overflows and the other clauses are exhausted (NO_MORE_DOCS)
    # no doc of the window is collected (end <= topDoc) => no progress
    nxt = c32(INT_MAX)
    end = jmax(windowMax, jmin(mx, nxt & ~c32(4095)))
    check("A6p", "  ... with the other clauses exhausted, scoreWindowSingleScorer collects nothing (end<=topDoc) => no progress",
          "core/.../search/BooleanScorer.java:247-256",
          And(pre, doc >= 2**31 - 4096), Not(end > doc), "proved",
          note="PROVED here means: for every topDoc in the last window, end <= topDoc, i.e. the loop makes no progress")


def bitset_iterator_run_end():
    src = "core/.../util/BitSetIterator.java:106-119"
    doc, length = i32("doc"), i32("length")
    upTo = doc + jmin(length - doc, c32(1 << 16))
    check("A7", "BitSetIterator.docIDRunEnd: upTo=doc+min(length-doc,2^16) is exact and <= length",
          src, And(length >= 1, doc >= 0, doc < length),
          And(wide(upTo) == wide(doc) + If(wide(length) - wide(doc) < 65536, wide(length) - wide(doc), 65536),
              upTo <= length), "proved")


def live_docs_sparse():
    src = "core/.../codecs/lucene90/Lucene90LiveDocsFormat.java:149-151 (#15413, 2025-11-25)"
    length, w = i32("length"), i32("wordIndex")
    nwords = LShR(length - 1, 6) + 1  # FixedBitSet.bits2words
    base = w << 6
    pre = And(length >= 1, length <= MAX_DOCS, w >= 0, w < nwords)
    check("A8", "readSparseFixedBitSet: baseDocId=wordIndex<<6 and baseDocId+64 exact for maxDoc<=MAX_DOCS",
          src, pre, And(wide(base) == wide(w) * 64, wide(base + 64) == wide(base) + 64), "proved")


def postings_bitset_encoding():
    src = "core/.../codecs/lucene104/Lucene104PostingsWriter.java:436-441 (#15174, 2025-09-17)"
    docRange = i32("docRange")
    nl = LShR(docRange - 1, 6) + 1  # FixedBitSet.bits2words(docRange) = ((numBits-1)>>6)+1
    # docRange = lastDocID - level0LastDocID is a sum of 256 positive deltas between doc ids < maxDoc
    pre = And(docRange >= 256, docRange <= MAX_DOCS - 1)
    check("A9", "Lucene104PostingsWriter.flushDocBlock: numBitSetLongs*Long.SIZE does not overflow (docRange<=MAX_DOCS-1)",
          src, pre, wide(nl * 64) == wide(nl) * 64, "proved",
          note="margin: overflows only for docRange > 2^31-64, MAX_DOCS leaves 64 doc ids of headroom")
    check("A9b", "  ... would overflow if docRange could exceed 2^31-64", src,
          And(docRange >= 256), wide(nl * 64) == wide(nl) * 64, "cex", show=(docRange,))


# =====================================================================================
# Family B: sizes / offsets (bytes, values, leaves)
# =====================================================================================

def bulk_binary_values():
    src = "core/.../codecs/lucene90/Lucene90DocValuesProducer.java:1347 & :1389-1391 (#16286, 2026-06-30)"
    size, length = i32("size"), i32("length")
    pre = And(size >= 1, size <= MAL, length >= 0, length <= LIMITS["BINARY_DV_MAX_LENGTH"][0])
    # property: the int product is the exact byte count (otherwise the bulk array is wrong-sized)
    check("B1", "DenseBinaryDocValues(fixed length).binaryValues: new byte[size*length] is exact",
          src, pre, wide(size * length) == wide(size) * wide(length), "cex", show=(size, length))
    # smallest realistic witness: size = 2 values of 2^30 bytes
    check("B1w", "  ... witness size=2,length=2^30 gives a negative array size", src,
          And(pre, size == 2, length == 2**30), (size * length) >= 0, "cex", show=(size, length))
    # the variable-length path: (int)(lastEnd - firstStart) truncation
    first, last = i64("firstStart"), i64("lastEnd")
    tot = BitVec("t", 32)
    pre2 = And(first >= 0, last >= first, last - first <= c64(2**40))
    check("B2", "DenseBinaryDocValues(variable).binaryValues: (int)(lastEnd-firstStart) is exact",
          src, pre2, SignExt(32, Extract(31, 0, last - first)) == last - first, "cex", show=(first, last))


def bkd_tree_ids():
    src = ("core/.../util/bkd/BKDWriter.java:904-911 checkMaxLeafNodeCount vs "
           "BKDReader.java:430 (nodeID *= 2), :463 (2*nodeID+1), :525-533 size()")
    bpd, nl = i32("bytesPerDim"), i32("numLeaves")
    allowed = And(bpd >= 1, bpd <= 16, nl >= 1,
                  wide(bpd) * wide(nl) <= MAL)  # exactly what checkMaxLeafNodeCount lets through
    node = i32("nodeID")
    # BKDReader uses heap-style ids: root 1, children 2n and 2n+1, leaves in [numLeaves, 2*numLeaves)
    pre = And(allowed, node >= 1, node < nl)  # any inner node
    prop = And(wide(node * 2) == wide(node) * 2, wide(node * 2 + 1) == wide(node) * 2 + 1)
    check("B3", "BKDReader inner node ids: 2*nodeID+1 fits in int for every tree checkMaxLeafNodeCount accepts",
          src, pre, prop, "cex", show=(bpd, nl, node))
    check("B3s", "  ... safe for bytesPerDim >= 2 (all built-in numeric points)", src,
          And(pre, bpd >= 2), prop, "proved")
    check("B3t", "  ... safe for bytesPerDim = 1 iff numLeaves <= 2^30", src,
          And(pre, nl <= 2**30), prop, "proved")
    # BKDReader.size(): while (leftMost < leafNodeOffset) leftMost *= 2;  starting from an inner node
    # one doubling step from x < numLeaves gives 2x < 2*numLeaves; show the step can wrap negative,
    # after which the loop never terminates (0*2 == 0 < numLeaves)
    x = i32("x")
    check("B4", "BKDReader.size(): leftMostLeafNode*2 stays positive while < leafNodeOffset",
          src, And(allowed, x >= 1, x < nl), x * 2 > 0, "cex", show=(bpd, nl, x))


def bkd_leaf_buffer():
    src = "core/.../util/bkd/BKDWriter.java:687 (OneDimensionBKDWriter leafValues; only used when numDims == 1, BKDWriter.java:448)"
    mp, nd, bpd = i32("maxPointsInLeafNode"), i32("numDims"), i32("bytesPerDim")
    # BKDConfig accepts maxPointsInLeafNode up to MAX_ARRAY_LENGTH; PointValues.MAX_NUM_BYTES = 16
    pre = And(mp >= 1, mp <= MAL, nd == 1, bpd >= 1, bpd <= 16)
    pbl = nd * bpd
    check("B5", "OneDimensionBKDWriter: new byte[maxPointsInLeafNode*packedBytesLength] exact under BKDConfig limits",
          src, pre, wide(mp * pbl) == wide(mp) * wide(pbl), "cex", show=(mp, nd, bpd))
    # B5s: nonlinear 32x32 multiplication times out (600 s) as one query; split into the 256
    # (numDims, bytesPerDim) cases, each a multiplication by a constant (exact case split).
    t0 = time.time(); allp = True
    for ndv in range(1, 2):
        for bpdv in range(1, 17):
            s2 = Solver()
            s2.add(mp >= 1, mp < 2**27)
            s2.add(Not(wide(mp * c32(ndv * bpdv)) == wide(mp) * (ndv * bpdv)))
            if s2.check() != unsat:
                allp = False
    dt = time.time() - t0
    RESULTS.append({"id": "B5s", "title": "  ... safe for maxPointsInLeafNode < 2^27 (default 512), 16-way case split over bytesPerDim",
                    "src": src, "result": "PROVED" if allp else "CEX", "time_s": round(dt, 3), "expected": "proved"})
    print(f"[B5s] {'PROVED' if allp else 'CEX':<8} {dt:7.3f}s    ... safe for maxPointsInLeafNode < 2^27 (16-way case split over bytesPerDim)")


def bkd_left_leaf_nodes():
    src = "core/.../util/bkd/BKDWriter.java:864-881 getNumLeftLeafNodes"
    n = i32("numLeaves")
    # 31 - nlz(n): floor(log2 n) for n>=1; encode nlz via a bit-scan definition
    lg = i32("lg")
    lastFull = lg
    full = c32(1) << (lastFull & 31)
    left = LShR(full, 1)  # full / 2 (full > 0)
    unbal = n - full
    left2 = left + jmin(unbal, left)
    pre = And(n >= 2, lg >= 0, lg <= 30, (c32(1) << lg) <= n, Or(lg == 30, n < (c32(1) << (lg + 1))))
    prop = And(left2 >= n - left2, wide(left2) <= 2 * (wide(n) - wide(left2)), left2 >= 1, left2 < n)
    check("B6", "BKDWriter.getNumLeftLeafNodes: 1 <= left < n, left >= right, left <= 2*right, all n in [2, 2^31-1]",
          src, pre, prop, "proved")


def long_heap_growth():
    src = "core/.../util/LongHeap.java:76-80 push(): ArrayUtil.grow(heap, (size * 3 + 1) / 2)"
    size = i32("size")
    req = (size * 3 + 1) / 2  # Java int division truncates toward zero == z3 bvsdiv
    pre = And(size >= 2, size <= MAL)
    check("C1", "LongHeap.push growth target (size*3+1)/2 >= size+1 for every reachable size",
          src, pre, And(req > size), "cex", show=(size,))
    s = Solver(); s.add(pre, Not(req > size))
    lo, hi = 2, MAL
    while lo < hi:
        mid = (lo + hi) // 2
        s.push(); s.add(size <= mid)
        if s.check() == sat:
            hi = mid
        else:
            lo = mid + 1
        s.pop()
    print(f"        smallest failing size = {lo} (heap of {lo} longs = {lo*8/2**30:.2f} GiB)")
    RESULTS[-1]["min_failing_size"] = lo


def hnsw_and_vectors():
    m, dims = i32("M"), i32("dims")
    check("D1", "Lucene99HnswVectorsReader.OffHeapHnswGraph: new int[M*2] exact (M<=512)",
          "core/.../codecs/lucene99/Lucene99HnswVectorsReader.java:578",
          And(m >= 1, m <= 512), wide(m * 2) == wide(m) * 2, "proved")
    check("D2", "OffHeap{Float,Float16}VectorValues.load: byteSize=dimension*{4,2} exact for dims<=1024",
          "core/.../codecs/lucene95/OffHeapFloatVectorValues.java:112, OffHeapFloat16VectorValues.java:118",
          And(dims >= 1, dims <= 1024), And(wide(dims * 4) == wide(dims) * 4, dims * 4 > 0), "proved")
    # vector offsets are computed as (long) ord * byteSize: for ord < 2^31 and byteSize < 2^31 they fit
    ordv, bs = i32("ord"), i32("byteSize")
    prod = SignExt(32, ordv) * SignExt(32, bs)
    check("D3", "OffHeap*VectorValues: (long)ord*byteSize exact for any int ord>=0, byteSize>=0",
          "core/.../codecs/lucene95/OffHeapFloatVectorValues.java:82,95; lucene104/OffHeapScalarQuantized*:112-161",
          And(ordv >= 0, bs >= 0), SignExt(64, prod) == SignExt(96, ordv) * SignExt(96, bs), "proved")
    # quantized byteSize = docPackedLength + 3*4 + 4 with docPackedLength <= dims (8-bit) for dims<=1024
    dpl = i32("docPackedLength")
    check("D4", "OffHeapScalarQuantized*VectorValues: byteSize=docPackedLength+16 exact (docPackedLength<=dims<=1024)",
          "core/.../codecs/lucene104/OffHeapScalarQuantizedVectorValues.java:108",
          And(dpl >= 0, dpl <= 1024), wide(dpl + 16) == wide(dpl) + 16, "proved")
    # FilteredHnswGraphSearcher: IntArrayQueue(maxConn * 2 * maxConn) style sizes, M <= 512
    check("D5", "FilteredHnswGraphSearcher: maxConn*maxConn*2 exact for maxConn<=2*512",
          "core/.../util/hnsw/FilteredHnswGraphSearcher.java:129-130",
          And(m >= 1, m <= 1024), wide(m * m * 2) == wide(m) * wide(m) * 2, "proved")


def bytes_ref_hash_rehash():
    # BytesRefHash.add: if (count == hashHalfSize) rehash(2 * hashSize, true): overflows when
    # hashSize == 2^30, i.e. at count == 2^29. Each entry is a DISTINCT byte string stored in a
    # ByteBlockPool with a 1-byte (len < 128) or 2-byte length prefix; bytesStart[] holds int
    # offsets, so the pool can never exceed 2^31-1 bytes. Minimal pool bytes for N distinct strings:
    # take all strings of length 0, 1, 2, 3 (cost 1+len each) and the rest of length 4 (cost 5).
    src = "core/.../util/BytesRefHash.java:338-340, :412-414 rehash(2 * hashSize)"
    N = 2**29
    counts = [256**k for k in range(4)]
    used = sum(counts)
    cost = sum((1 + k) * c for k, c in enumerate(counts)) + (N - used) * 5
    n = Int("n")
    # tiny Int-theory check of the counting arithmetic itself (sum of the cheapest N strings)
    s = Solver(); s.add(n == cost, n <= INT_MAX)
    t = time.time(); r = s.check(); dt = time.time() - t
    RESULTS.append({"id": "E1", "title": "BytesRefHash rehash(2*hashSize) overflow needs count=2^29 distinct terms; "
                    "minimal pool bytes > 2^31-1, so it is unreachable through add(BytesRef)",
                    "src": src, "result": "PROVED" if r == unsat else "CEX", "time_s": round(dt, 3),
                    "expected": "proved", "note": f"min bytes for 2^29 distinct strings = {cost} > {INT_MAX}"})
    print(f"[E1] {'PROVED' if r == unsat else 'CEX':<8} {dt:7.3f}s  BytesRefHash rehash overflow unreachable "
          f"(min pool bytes {cost:,} > 2^31-1)")
    # and that 2*hashSize does overflow at hashSize = 2^30
    hs = i32("hashSize")
    check("E2", "  ... (the expression itself: 2*hashSize overflows at hashSize=2^30)", src,
          And(hs >= 16, hs <= 2**30, (hs & (hs - 1)) == 0), wide(2 * hs) == 2 * wide(hs), "cex", show=(hs,))


def direct_packed():
    # DirectReader / DirectWriter offsets are long: offset = baseOffset + (index * bpv) / 8
    idx, bpv, base = i64("index"), i64("bpv"), i64("baseOffset")
    pre = And(idx >= 0, idx < c64(2**56), bpv >= 1, bpv <= 64, base >= 0, base < c64(2**62))
    off = base + (idx * bpv) / 8
    check("F1", "DirectReader merge buffers: baseOffset+(index*bpv)/8 exact for index<2^56 (long math)",
          "core/.../util/packed/DirectReader.java:141,158,172",
          pre, SignExt(64, off) == SignExt(64, base) + (SignExt(64, idx) * SignExt(64, bpv)) / 8, "proved")
    # PackedInts.Format.PACKED_SINGLE_BLOCK/PACKED byteCount -> longCount cast to int
    vc, b = i32("valueCount"), i32("bitsPerValue")
    bc = (ZeroExt(32, vc) * ZeroExt(32, b) + 7) / 8           # exact byteCount (int math in 64 bits)
    lc = LShR(bc + 7, 3)
    check("F2", "PackedInts.Format.PACKED.longCount: (int)((byteCount+7)>>>3) exact for valueCount<=2^31-1, bpv<=64",
          "core/.../util/packed/PackedInts.java:148-153",
          And(vc >= 0, b >= 0, b <= 64), SignExt(32, Extract(31, 0, lc)) == lc, "proved")


def main():
    global SMT2_DIR
    if "--smt2-dir" in sys.argv:
        SMT2_DIR = sys.argv[sys.argv.index("--smt2-dir") + 1]
    t = time.time()
    roaring_range_add()
    sparse_fixed_bitset()
    boolean_scorer_window()
    bitset_iterator_run_end()
    live_docs_sparse()
    postings_bitset_encoding()
    bulk_binary_values()
    bkd_tree_ids()
    bkd_leaf_buffer()
    bkd_left_leaf_nodes()
    long_heap_growth()
    hnsw_and_vectors()
    bytes_ref_hash_rehash()
    direct_packed()
    print(f"total {time.time() - t:.1f}s, {len(RESULTS)} obligations")
    out = sys.argv[sys.argv.index("--json") + 1] if "--json" in sys.argv else None
    if out:
        with open(out, "w") as f:
            json.dump({"limits": LIMITS, "results": RESULTS}, f, indent=1)


if __name__ == "__main__":
    main()
