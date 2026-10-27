# Z3 model of the chunk arithmetic of Lucene90CompressingStoredFields{Writer,Reader},
# FieldsIndex{Writer,Reader} and StoredFieldsInts (Lucene main @ ea57360912ed).
#
# Every Java int is a 32-bit bit-vector with Java wrap-around semantics; longs are 64-bit.
# Documented limits are encoded as preconditions (see PRE_* below), so a SAT answer is a
# counterexample reachable within the documented limits, and UNSAT is a proof for all inputs
# that satisfy them.
#
# Model element -> Java line (paths relative to lucene/core/src/java/org/apache/lucene/)
# | element                     | Java                                                                      |
# |-----------------------------|---------------------------------------------------------------------------|
# | C (chunkSize)               | codecs/lucene90/Lucene90StoredFieldsFormat.java:161,166 (81920 / 491520)    |
# | P (buffered before the doc) | compressing/Lucene90CompressingStoredFieldsWriter.java:183-196 (flush once |
# |                             |   bufferedDocs.size() >= chunkSize, so P < C before the next doc)          |
# | END = toIntExact(P + S)     | Lucene90CompressingStoredFieldsWriter.java:191                             |
# | sliced = T >= 2C            | Lucene90CompressingStoredFieldsWriter.java:248                             |
# | slice loop (int compressed) | Lucene90CompressingStoredFieldsWriter.java:255-260                         |
# | ByteBuffersDataInput.slice  | store/ByteBuffersDataInput.java:406-415 (IAE on negative offset/length)    |
# | token = n << 2 | bits       | Lucene90CompressingStoredFieldsWriter.java:218                             |
# | lengths from endOffsets     | Lucene90CompressingStoredFieldsWriter.java:243-247                         |
# | offsets prefix sums         | compressing/Lucene90CompressingStoredFieldsReader.java:515-518             |
# | merge eager decompress grow | Lucene90CompressingStoredFieldsReader.java:534-548, util/ArrayUtil.java:33 |
# | non-merge sliced first read | Lucene90CompressingStoredFieldsReader.java:586-589                         |
# | getBlockID                  | compressing/FieldsIndexReader.java:119-126                                 |
# | getBlockLength              | compressing/FieldsIndexReader.java:134-142, FieldsIndexWriter.java:103-177 |
# | StoredFieldsInts pack/unpack| compressing/StoredFieldsInts.java:31-195                                   |
# | doc size limit 2^31 - 2^14  | codecs/lucene90/Lucene90StoredFieldsFormat.java:96-99 ("Known limitations")|
# | MAX_STORED_STRING_LENGTH    | index/IndexWriter.java:261-262, index/IndexingChain.java:1440-1449          |
import json
import sys
from z3 import (BitVec, BitVecVal, Solver, And, Or, Not, ULE, ULT, If, sat, unsat, ZeroExt,
                SignExt, Extract, Concat, Implies, BV2Int, Int, IntVal, ForAll)

RESULTS = []
INT_MAX = 2**31 - 1
MAX_ARRAY_LENGTH = INT_MAX - 16  # ArrayUtil.MAX_ARRAY_LENGTH with a 16-byte array header (64-bit JVM)
DOC_LIMIT = 2**31 - 2**14        # documented in Lucene90StoredFieldsFormat "Known limitations"


def bv32(name):
    return BitVec(name, 32)


def record(name, solver, expect, describe, model_vars):
    r = solver.check()
    entry = {"obligation": name, "result": str(r), "expected_if_correct": "unsat", "note": describe}
    if r == sat:
        m = solver.model()
        entry["witness"] = {k: m.eval(v, model_completion=True).as_signed_long()
                            if hasattr(m.eval(v, model_completion=True), "as_signed_long")
                            else str(m.eval(v, model_completion=True)) for k, v in model_vars.items()}
    entry["matches_expectation"] = (str(r) == expect)
    RESULTS.append(entry)
    print(json.dumps(entry))


def slice_loop_overflows(C, T):
    """The writer's loop `for (int compressed = 0; compressed < capacity; compressed += chunkSize)`.
    Let k = number of slices = ceil(T / C). The last executed body has compressed = (k-1)*C; the
    increment after it computes (k-1)*C + C = k*C in 32-bit int. The loop is correct iff that
    increment does not wrap, i.e. k*C <= INT_MAX (as a mathematical integer)."""
    return None  # expressed directly with Ints below


# ---------------------------------------------------------------------------------------------
# O1: endOffsets[i] = Math.toIntExact(bufferedDocs.size()) for a document within the documented
#     limit, written after P < C bytes of earlier documents of the same chunk.
for mode, C in (("BEST_SPEED", 81920), ("BEST_COMPRESSION", 491520)):
    s = Solver()
    P = Int("P")
    S = Int("S")
    s.add(P >= 0, P < C)               # bytes of earlier docs of the chunk (no flush yet)
    s.add(S >= 0, S <= DOC_LIMIT)      # PRE: documented per-document limit
    s.add(P + S > INT_MAX)             # toIntExact throws ArithmeticException
    record(f"O1-endOffsets-toIntExact-{mode}", s, "unsat",
           "a document within the documented limit after earlier docs in the same chunk overflows "
           "the int end offset (ArithmeticException in finishDocument, an aborting exception)",
           {"P": P, "S": S})

    # O1b: the same with P = 0 (document alone in a fresh chunk): must be UNSAT
    s = Solver()
    s.add(P == 0, S >= 0, S <= DOC_LIMIT, P + S > INT_MAX)
    record(f"O1b-endOffsets-alone-{mode}", s, "unsat",
           "a document alone in its chunk within the documented limit never overflows the end offset",
           {"P": P, "S": S})

# ---------------------------------------------------------------------------------------------
# O2: the slice loop in flush() for a chunk of T bytes, T = P + S <= INT_MAX (O1 did not throw).
#     Exact 32-bit semantics: we execute the loop symbolically via its closed form and check
#     that the int `compressed` never wraps. k = ceil(T/C), last increment = k*C must be <= INT_MAX.
for mode, C in (("BEST_SPEED", 81920), ("BEST_COMPRESSION", 491520)):
    for alone in (True, False):
        s = Solver()
        P = Int("P")
        S = Int("S")
        T = Int("T")
        k = Int("k")
        s.add(P >= 0, P < C, S >= 0, S <= DOC_LIMIT)
        if alone:
            s.add(P == 0)
        s.add(T == P + S, T <= INT_MAX, T >= 2 * C)   # sliced, and O1 did not throw
        s.add(k * C >= T, (k - 1) * C < T)            # k = ceil(T / C)
        s.add(k * C > INT_MAX)                        # `compressed += chunkSize` wraps
        record(f"O2-slice-loop-int-overflow-{mode}-{'alone' if alone else 'after-earlier-docs'}", s,
               "unsat",
               "the int loop variable of the slice loop wraps; the next slice() gets a negative "
               "offset and length and throws IllegalArgumentException from flush()",
               {"P": P, "S": S, "T": T, "k": k})

# O2-bv: the same loop executed literally on 32-bit bit-vectors for the smallest T found by O2,
#        to make sure the closed form matches Java semantics (no Int abstraction gap).
def run_loop_bv(C, T):
    """Simulates the Java loop with 32-bit wrap; returns the first (compressed, l) whose
    slice arguments would be negative, or None if the loop ends normally."""
    def wrap(x):
        x &= 0xFFFFFFFF
        return x - 2**32 if x >= 2**31 else x
    compressed = 0
    steps = 0
    while compressed < T:
        l = min(C, wrap(T - compressed))
        if compressed < 0 or l < 0:
            return {"compressed": compressed, "l": l, "steps": steps}
        compressed = wrap(compressed + C)
        steps += 1
    return None

loop_checks = {}
for mode, C in (("BEST_SPEED", 81920), ("BEST_COMPRESSION", 491520)):
    # smallest T for which k*C > INT_MAX: T = floor(INT_MAX / C) * C + 1
    t_min = (INT_MAX // C) * C + 1
    loop_checks[mode] = {
        "C": C,
        "threshold_T_min_failing": t_min,
        "T_min_minus_1_result": run_loop_bv(C, t_min - 1),
        "T_min_result": run_loop_bv(C, t_min),
        "T_doc_limit_alone": run_loop_bv(C, DOC_LIMIT),
        "T_INT_MAX": run_loop_bv(C, INT_MAX),
    }
print(json.dumps({"O2-literal-loop-simulation": loop_checks}))
RESULTS.append({"obligation": "O2-literal-loop-simulation", "data": loop_checks})

# ---------------------------------------------------------------------------------------------
# O3: MAX_STORED_STRING_LENGTH (IndexWriter's own guard against too-massive stored strings)
#     vs the codec. A String of n chars, each encoded on 3 UTF-8 bytes, alone in a chunk:
#     serialized = 1 (field header vlong, field number < 16) + vint(len) (5 bytes for len >= 2^28) + 3n
for mode, C in (("BEST_SPEED", 81920), ("BEST_COMPRESSION", 491520)):
    s = Solver()
    n = Int("n")
    T = Int("T")
    k = Int("k")
    MSSL = MAX_ARRAY_LENGTH // 3
    s.add(n >= 0, n <= MSSL)                       # PRE: accepted by IndexingChain's check
    s.add(T == 1 + 5 + 3 * n, T <= INT_MAX)
    s.add(k * C >= T, (k - 1) * C < T, k * C > INT_MAX)
    record(f"O3-max-stored-string-vs-slice-loop-{mode}", s, "unsat",
           "a String accepted by IndexWriter.MAX_STORED_STRING_LENGTH still wraps the slice loop",
           {"n": n, "T": T, "k": k})

# ---------------------------------------------------------------------------------------------
# O4: merge-instance eager decompression: bytes.bytes grows to the chunk total length T via
#     ArrayUtil.grow, which throws IllegalArgumentException above MAX_ARRAY_LENGTH.
for alone in (True, False):
    s = Solver()
    P = Int("P")
    S = Int("S")
    C = 81920
    s.add(P >= 0, P < C, S >= 0, S <= DOC_LIMIT)
    if alone:
        s.add(P == 0)
    s.add(P + S <= INT_MAX, P + S > MAX_ARRAY_LENGTH)
    record(f"O4-merge-eager-grow-{'alone' if alone else 'after-earlier-docs'}", s, "unsat",
           "a chunk that the writer accepted cannot be decompressed by a merge instance",
           {"P": P, "S": S})

# ---------------------------------------------------------------------------------------------
# O5: header token `(numBufferedDocs << 2) | dirtyBit | slicedBit` in 32-bit int and the reader's
#     `chunkDocs = token >>> 2`: round trip for 1 <= n <= maxDocsPerChunk.
for mdpc in (1024, 4096, 2**29 - 1, 2**29, 2**31 - 1):
    s = Solver()
    n = bv32("n")
    d = bv32("dirty")
    sl = bv32("sliced")
    s.add(ULE(BitVecVal(1, 32), n), ULE(n, BitVecVal(mdpc, 32)))
    s.add(Or(d == 0, d == 2), Or(sl == 0, sl == 1))
    token = (n << 2) | d | sl
    from z3 import LShR
    s.add(Or(LShR(token, 2) != n, (token & 1) != sl, (token & 2) != d))
    record(f"O5-token-roundtrip-maxDocsPerChunk={mdpc}", s, "unsat",
           "header token loses chunkDocs bits",
           {"n": n})

# ---------------------------------------------------------------------------------------------
# O6: lengths from endOffsets (writer, in place, i from n-1 down to 1) and offsets as prefix
#     sums (reader) give back endOffsets exactly and the last offset equals the chunk length.
#     Bounded n <= 12 with symbolic 32-bit values (enough to cover the in-place aliasing).
for n in range(1, 13):
    s = Solver()
    E = [bv32(f"e{i}") for i in range(n)]
    # endOffsets are non-decreasing non-negative ints (Math.toIntExact of a growing size)
    for i in range(n):
        s.add(E[i] >= 0)
        if i > 0:
            s.add(E[i - 1] <= E[i])
    # the writer transforms the array in place (lengths aliases endOffsets), from the end:
    #   for (i = n - 1; i > 0; --i) lengths[i] = endOffsets[i] - endOffsets[i - 1];
    L = list(E)
    for i in range(n - 1, 0, -1):
        L[i] = L[i] - L[i - 1]
    # reader: offsets[0] = 0; offsets[i+1] = offsets[i] + lengths[i] as longs
    off = [BitVecVal(0, 64)]
    for i in range(n):
        off.append(off[-1] + ZeroExt(32, L[i]))
    bad = Or([off[i + 1] != ZeroExt(32, E[i]) for i in range(n)])
    s.add(bad)
    r = s.check()
    RESULTS.append({"obligation": f"O6-lengths-offsets-roundtrip-n={n}", "result": str(r),
                    "expected_if_correct": "unsat", "matches_expectation": str(r) == "unsat"})
print(json.dumps({"O6": [e["result"] for e in RESULTS if e["obligation"].startswith("O6")]}))

# ---------------------------------------------------------------------------------------------
# O7: StoredFieldsInts lane packing. For bpv in {8,16,32} the writer builds one long from
#     64/bpv ints with `((long) v0 << (64-bpv)) | ... | (long) vlast` (sign-extending casts),
#     and the reader extracts lane j with `(l >>> shift_j) & mask` (32: `l >>> 32` and
#     `l & 0xFFFFFFFFL`). The choice of bpv is `max |= toUnsignedLong(v)`, then <= 0xff / 0xffff.
#     Property: for values with 0 <= v and OR(v) <= mask(bpv), unpack(pack(v)) == v.
def pack(vals, bpv):
    lanes = 64 // bpv
    acc = BitVecVal(0, 64)
    for j in range(lanes):
        shift = 64 - bpv * (j + 1)
        acc = acc | (SignExt(32, vals[j]) << shift)
    return acc


def unpack(l, bpv, j):
    from z3 import LShR
    shift = 64 - bpv * (j + 1)
    if bpv == 32:
        return LShR(l, 32) if j == 0 else (l & BitVecVal(0xFFFFFFFF, 64))
    return LShR(l, shift) & BitVecVal((1 << bpv) - 1, 64)


for bpv in (8, 16, 32):
    for domain in ("nonnegative-int", "any-int"):
        s = Solver()
        lanes = 64 // bpv
        vals = [bv32(f"v{j}") for j in range(lanes)]
        orv = BitVecVal(0, 64)
        for v in vals:
            orv = orv | ZeroExt(32, v)
            if domain == "nonnegative-int":
                s.add(v >= 0)
        if bpv < 32:
            s.add(ULE(orv, BitVecVal((1 << bpv) - 1, 64)))
        else:
            s.add(Not(ULE(orv, BitVecVal(0xFFFF, 64))))
        l = pack(vals, bpv)
        # the reader stores into long[] and the callers use the long value; compare as long
        # against the int value zero-extended (the writer's values are lengths / field counts)
        s.add(Or([unpack(l, bpv, j) != ZeroExt(32, vals[j]) for j in range(lanes)]))
        r = s.check()
        e = {"obligation": f"O7-storedfieldsints-pack-bpv={bpv}-{domain}", "result": str(r),
             "expected_if_correct": "unsat" if domain == "nonnegative-int" else "sat (outside domain)"}
        if r == sat:
            m = s.model()
            e["witness"] = [m.eval(v, model_completion=True).as_signed_long() for v in vals]
        RESULTS.append(e)
        print(json.dumps(e))

# ---------------------------------------------------------------------------------------------
# O8: FieldsIndexReader.getBlockID: docs[] = [0, d1, ..., dK] strictly increasing (each chunk
#     has >= 1 doc), numChunks = K + 1 (FieldsIndexWriter.finish writes totalChunks + 1), and
#     binarySearch(0, numChunks, docID) returns i if docs[i] == docID, else -(ip) - 1 with ip the
#     insertion point (DirectMonotonicReader.binarySearch contract; packed-ints-monotonic proves the method).
#     Property for 0 <= docID < maxDoc = dK: block b satisfies docs[b] <= docID < docs[b+1],
#     0 <= b <= K - 1, and getBlockLength(b) = sp[b+1] - sp[b] with sp[K] = maxPointer.
for K in range(1, 9):
    s = Solver()
    d = [IntVal(0)] + [Int(f"d{i}") for i in range(1, K + 1)]
    for i in range(K):
        s.add(d[i] < d[i + 1])
    doc = Int("doc")
    s.add(doc >= 0, doc < d[K])
    # found/idx: an entry equal to doc; ip: insertion point (first index with d[i] > doc,
    # only meaningful when not found)
    found = Or([d[i] == doc for i in range(K + 1)])
    ip = Int("ip")
    s.add(ip >= 0, ip <= K + 1)
    idx = Int("idx")
    s.add(Or([And(idx == i, d[i] == doc) for i in range(K + 1)]) == found)
    for i in range(K + 1):
        s.add(Implies(Not(found), Implies(IntVal(i) < ip, d[i] < doc)))
        s.add(Implies(Not(found), Implies(IntVal(i) >= ip, d[i] > doc)))
    bsr = If(found, idx, -ip - 1)
    block = If(bsr < 0, -2 - bsr, bsr)

    def _sel(arr, b):
        e = arr[-1]
        for i in range(len(arr) - 2, -1, -1):
            e = If(b == i, arr[i], e)
        return e
    lo = _sel(d, block)
    hi = _sel(d, block + 1)
    s.add(Or(block < 0, block > K - 1, Not(And(lo <= doc, doc < hi))))
    r = s.check()
    RESULTS.append({"obligation": f"O8-getBlockID-K={K}", "result": str(r),
                    "expected_if_correct": "unsat", "matches_expectation": str(r) == "unsat"})
print(json.dumps({"O8": [e["result"] for e in RESULTS if e["obligation"].startswith("O8")]}))

# ---------------------------------------------------------------------------------------------
# O9: non-merge read of a document of a sliced chunk: `decompress(fieldsStream, chunkSize, offset,
#     Math.min(length, chunkSize - offset), bytes)` requires 0 <= offset < C (every document starts
#     in the first slice) and 0 < min(length, C - offset) and offset + that <= C. The writer flushes
#     as soon as size >= C, so every document of a chunk starts at offset P_i < C.
s = Solver()
C = Int("C")
o = Int("o")
ln = Int("len")
s.add(C >= 1, C <= 2**30)
s.add(o >= 0, o < C, ln >= 1, o + ln <= INT_MAX)
m = If(ln < C - o, ln, C - o)
s.add(Or(m <= 0, o + m > C))
record("O9-sliced-first-window", s, "unsat", "first-slice window of a document is out of range",
       {"C": C, "o": o, "len": ln})

json.dump(RESULTS, open(sys.argv[1] if len(sys.argv) > 1 else "chunk_arith.results.json", "w"),
          indent=1, default=str)

# ---------------------------------------------------------------------------------------------
# Vacuity: every UNSAT obligation above must turn SAT on a plausible mutant of the modelled code.
MUTANTS = []


def mutant(name, s):
    r = s.check()
    MUTANTS.append({"mutant": name, "result": str(r), "killed": str(r) == "sat"})
    print(json.dumps(MUTANTS[-1]))


# O1b mutant: the documented limit loosened to INT_MAX - 1 (a doc alone then can overflow when the
# header bytes are counted as part of the chunk; here simply P + S with S <= INT_MAX and P = 1)
s = Solver(); P = Int("P"); S = Int("S")
s.add(P == 1, S >= 0, S <= INT_MAX, P + S > INT_MAX)
mutant("O1b-limit-INT_MAX-with-1-byte-earlier", s)
# O6 mutant: the writer transforms endOffsets into lengths front to back (aliasing bug)
for n in (2, 3):
    s = Solver(); E = [bv32(f"e{i}") for i in range(n)]
    for i in range(n):
        s.add(E[i] >= 0)
        if i > 0:
            s.add(E[i - 1] <= E[i])
    Lm = list(E)
    for i in range(1, n):
        Lm[i] = Lm[i] - Lm[i - 1]
    off = [BitVecVal(0, 64)]
    for i in range(n):
        off.append(off[-1] + ZeroExt(32, Lm[i]))
    s.add(Or([off[i + 1] != ZeroExt(32, E[i]) for i in range(n)]))
    mutant(f"O6-front-to-back-n={n}", s)
# O8 mutant: getBlockID uses -1 - result instead of -2 - result
K = 3
s = Solver()
d = [IntVal(0)] + [Int(f"d{i}") for i in range(1, K + 1)]
for i in range(K):
    s.add(d[i] < d[i + 1])
doc = Int("doc"); s.add(doc >= 0, doc < d[K])
found = Or([d[i] == doc for i in range(K + 1)])
ip = Int("ip"); idx = Int("idx")
s.add(ip >= 0, ip <= K + 1)
s.add(Or([And(idx == i, d[i] == doc) for i in range(K + 1)]) == found)
for i in range(K + 1):
    s.add(Implies(Not(found), Implies(IntVal(i) < ip, d[i] < doc)))
    s.add(Implies(Not(found), Implies(IntVal(i) >= ip, d[i] > doc)))
bsr = If(found, idx, -ip - 1)
block = If(bsr < 0, -1 - bsr, bsr)


def _sel2(arr, b):
    e = arr[-1]
    for i in range(len(arr) - 2, -1, -1):
        e = If(b == i, arr[i], e)
    return e


s.add(Or(block < 0, block > K - 1, Not(And(_sel2(d, block) <= doc, doc < _sel2(d, block + 1)))))
mutant("O8-minus-one-instead-of-minus-two", s)
# O9 mutant: first window uses chunkSize - offset + 1
s = Solver(); C = Int("C"); o = Int("o"); ln = Int("len")
s.add(C >= 1, C <= 2**30, o >= 0, o < C, ln >= 1, o + ln <= INT_MAX)
m = If(ln < C - o + 1, ln, C - o + 1)
s.add(Or(m <= 0, o + m > C))
mutant("O9-window-plus-one", s)
# O2 sanity: the closed form must agree with the literal loop at both sides of the threshold
agree = all(
    (run_loop_bv(C_, T_) is not None) == (-(-T_ // C_) * C_ > INT_MAX)
    for C_ in (81920, 491520, 65536, 1 << 20)
    for T_ in [2 * C_, INT_MAX // C_ * C_, INT_MAX // C_ * C_ + 1, INT_MAX - 1, INT_MAX, DOC_LIMIT]
    if T_ >= 2 * C_)
MUTANTS.append({"check": "O2 closed form == literal 32-bit loop simulation", "agree": agree})
print(json.dumps(MUTANTS[-1]))
json.dump({"results": RESULTS, "mutants": MUTANTS},
          open(sys.argv[1] if len(sys.argv) > 1 else "chunk_arith.results.json", "w"),
          indent=1, default=str)
