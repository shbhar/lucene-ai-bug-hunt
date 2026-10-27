# Z3 obligations for the int-overflow findings of unit bitsets-livedocs-acceptdocs (Lucene main ea57360912ed).
# Same conventions as bulk_bitset_proofs.py: Java int = BV32 with wraparound, `>>` arithmetic.
# PROVED = UNSAT(pre AND NOT prop); CEX = a model, then minimised by binary search with the solver.
#
# Model element -> Java (lucene/core/src/java/org/apache/lucene/...):
#   oversize8(m)            util/ArrayUtil.java:133-190 (bytesPerElement = 8, 64-bit JRE)
#   ensureCapacityInternal  util/FixedBitSet.java:62-83  (numBits = arr.length << 6, line 81)
#   batch loop              codecs/lucene90/Lucene90LiveDocsFormat.java:197-215 (writeBits)
#                           index/CheckIndex.java:1369-1383 (bitsCardinality); same loop shape
#   WindowBits.applyMask    search/ReqExclBulkScorer.java:123-145 (bitSetEnd, line 125)
#   approximateCardinality  util/FixedBitSet.java:258-282 (line 280)
# Limits: IndexWriter.MAX_DOCS = 2^31-129 (index/IndexWriter.java), ArrayUtil.MAX_ARRAY_LENGTH =
# 2^31-1-16 (compressed oops) -- the 24-byte-header value gives the same results here.
import json
import sys
import time

from z3 import And, BitVec, BitVecVal, If, Implies, Not, Or, SignExt, Solver, ULT, sat, unsat

MAX_DOCS = 2**31 - 129
MAL = 2**31 - 1 - 16
R = []


def i32(n):
    return BitVec(n, 32)


def c32(v):
    return BitVecVal(v, 32)


def wide(x):
    return SignExt(32, x)


def jmin(a, b):
    return If(a < b, a, b)


def solve(oid, title, pre, prop, show, expect):
    s = Solver()
    s.set("timeout", 300_000)
    s.add(pre, Not(prop))
    t = time.time()
    r = s.check()
    e = {"id": oid, "title": title, "expected": expect, "time_s": round(time.time() - t, 3)}
    if r == unsat:
        e["result"] = "PROVED"
    elif r == sat:
        m = s.model()
        e["result"] = "CEX"
        e["cex"] = {str(v): m.eval(v, model_completion=True).as_signed_long() for v in show}
    else:
        e["result"] = "UNKNOWN"
    R.append(e)
    print(f"[{e['result']:>7}] {oid:<5} {e['time_s']:6.2f}s {title} {e.get('cex', '')}", flush=True)
    return e


def minimise(var, pre, prop, lo, hi):
    """smallest value of var in [lo, hi] for which pre AND NOT prop is SAT (binary search)."""
    best = None
    while lo <= hi:
        mid = (lo + hi) // 2
        s = Solver()
        s.add(pre, Not(prop), var >= lo, var <= mid)
        if s.check() == sat:
            best = mid
            hi = mid - 1
            m = s.model()
            best = m.eval(var).as_signed_long()
            hi = best - 1
        else:
            lo = mid + 1
    return best


# ---------------------------------------------------------------- O1 FixedBitSet.ensureCapacity
def oversize8(m):
    extra = jmax3(m >> 3)
    newSize = m + extra
    # newSize + 7 < 0 || newSize + 7 > MAX_ARRAY_LENGTH -> MAX_ARRAY_LENGTH; bytesPerElement 8 -> newSize
    over = Or(newSize + 7 < 0, newSize + 7 > c32(MAL))
    return If(m == 0, c32(0), If(over, c32(MAL), newSize))


def jmax3(x):
    return If(x < 3, c32(3), x)


def bits2words(n):
    return ((n - 1) >> 6) + 1


def ensure_capacity():
    d, n0 = i32("desiredBit"), i32("numBits0")
    arr0 = bits2words(n0)  # new FixedBitSet(numBits0): exact array
    numWords = bits2words(d)
    grow = numWords >= arr0
    arr = If(grow, oversize8(numWords + 1), arr0)
    numBits = arr << 6  # FixedBitSet.java:81 int shift
    pre = And(n0 >= 1, n0 <= 64, d >= n0, d <= c32(2**31 - 2))
    prop = numBits > d
    solve("O1", "ensureCapacity: returned length() > desiredBit for every int desiredBit", pre, prop,
          (d, n0), "cex")
    pre64 = And(pre, n0 == 64)
    m = minimise(d, pre64, prop, 0, 2**31 - 2)
    print(f"        O1 smallest failing desiredBit from a 64-bit set: {m}", flush=True)
    R.append({"id": "O1min", "smallest_failing_desiredBit_from_len64": m})
    # sub-case: desiredBit up to MAX_DOCS-1 (doc IDs) is enough to fail
    solve("O1d", "ensureCapacity: some doc ID < MAX_DOCS already fails", And(pre, d <= c32(MAX_DOCS - 1)), prop,
          (d,), "cex")
    # control: below the threshold it is correct
    if m is not None:
        solve("O1s", f"ensureCapacity: correct for every desiredBit < {m} (from any small set)",
              And(pre, d < c32(m)), prop, (d, n0), "proved")
    # fix: numBits = (int) min((long) arr << 6, Integer.MAX_VALUE); also numWords(fixed) <= arr
    fixed = If(wide(arr) * 64 > 2**31 - 1, c32(2**31 - 1), arr << 6)
    solve("O1f", "ensureCapacity fix: (int) min((long) arr.length << 6, MAX_VALUE) > desiredBit and fits arr",
          pre, And(fixed > d, bits2words(fixed) <= arr), (d, n0), "proved")
    # growth from any earlier size (incremental growth, as DocsWithFieldSet does)
    a0 = i32("arrLen0")
    numBits0 = a0 << 6
    pre2 = And(a0 >= 1, a0 <= c32(2**25 - 1), d >= numBits0, d <= c32(MAX_DOCS - 1))
    arr2 = If(bits2words(d) >= a0, oversize8(bits2words(d) + 1), a0)
    solve("O1g", "ensureCapacity from an arbitrary (non-overflowed) earlier array: still fails for some doc ID",
          pre2, (arr2 << 6) > d, (d, a0), "cex")


# ---------------------------------------------------------------- O2 live-docs batch loops
def batch_loop():
    L, off = i32("length"), i32("offset")
    # a loop state reached legitimately: offset = k * 1024, 0 <= offset < L
    pre = And(L >= 1, off >= 0, off < L, (off & 1023) == 0)
    nxt = off + 1024
    # property: the increment never wraps while the loop keeps running
    prop = Not(And(nxt < 0, nxt < L))
    solve("O2", "liveDocs batch loop: offset += 1024 never wraps into a running iteration", pre, prop, (L, off),
          "cex")
    solve("O2s", "liveDocs batch loop: safe for every length <= 2^31 - 1024", And(pre, L <= c32(2**31 - 1024)),
          prop, (L, off), "proved")
    m = minimise(L, pre, prop, 1, 2**31 - 1)
    print(f"        O2 smallest failing length: {m} (MAX_DOCS = {MAX_DOCS}, failing legal maxDocs = {MAX_DOCS - m + 1 if m else 0})",
          flush=True)
    R.append({"id": "O2min", "smallest_failing_length": m, "legal_failing_count": (MAX_DOCS - m + 1) if m else 0})
    # what the wrapped iteration computes: numBitsToCopy = min(L - offset, 1024) < 0 -> clear(neg, 1024)
    wrapped = c32(-2**31)
    ntc = jmin(L - wrapped, c32(1024))
    solve("O2n", "liveDocs batch loop: the wrapped iteration has numBitsToCopy < 0 (clear(negative, 1024))",
          And(L > c32(2**31 - 1024), L <= c32(MAX_DOCS)), ntc < 0, (L,), "proved")


# ---------------------------------------------------------------- O3 ReqExclBulkScorer.WindowBits
def window_bits():
    off, n, wb, we = i32("offset"), i32("n"), i32("windowBase"), i32("windowEnd")
    pre = And(off >= 0, n >= 1, n <= 4096, wb >= 0, wb <= off, off < we, we <= c32(MAX_DOCS))
    bitSetEnd = jmin(c32(2**31 - 1), off + n)  # ReqExclBulkScorer.java:125 int addition
    exact = If(wide(off) + wide(n) > 2**31 - 1, c32(2**31 - 1), off + n)
    to = jmin(we, bitSetEnd)
    to_exact = jmin(we, exact)
    solve("O3", "WindowBits.applyMask: 'to' equals the exact min(windowEnd, offset + n)", pre, to == to_exact,
          (off, n, wb, we), "cex")
    solve("O3s", "WindowBits.applyMask: correct whenever offset <= 2^31 - 1 - n", And(pre, wide(off) + wide(n) <= 2**31 - 1),
          to == to_exact, (off, n), "proved")
    pre4096 = And(pre, n == 4096)
    m = minimise(off, pre4096, to == to_exact, 0, 2**31 - 1)
    print(f"        O3 smallest failing offset for a 4096-bit window: {m}", flush=True)
    R.append({"id": "O3min", "smallest_failing_offset_n4096": m})
    # in the failing case the method clears the whole bit set (from >= to)
    frm = If(wb > off, wb, off)
    solve("O3c", "WindowBits.applyMask overflow case takes the bitSet.clear() branch although [offset, windowEnd) is non-empty",
          And(pre, wide(off) + wide(n) > 2**31 - 1), frm >= to, (off, n, wb, we), "proved")


# ---------------------------------------------------------------- O4 approximateCardinality
def approx_card():
    nb = i32("numBits")
    numWords = bits2words(nb)
    # maxWord: largest multiple of 1024 with maxWord + 1024 >= numWords (numWords > 1024)
    # the scale factor (interval / rangeLength) * numWords is int: 64 * numWords
    factor = c32(64) * numWords
    pre = And(nb >= 1024 * 64 + 1, nb <= c32(2**31 - 1))
    solve("O4", "approximateCardinality: 64 * numWords does not overflow (legal FixedBitSet lengths)", pre,
          wide(factor) == 64 * wide(numWords), (nb,), "cex")
    solve("O4d", "approximateCardinality: no overflow for lengths <= MAX_DOCS (doc-ID bit sets)",
          And(pre, nb <= c32(MAX_DOCS)), wide(factor) == 64 * wide(numWords), (nb,), "proved")


if __name__ == "__main__":
    ensure_capacity()
    batch_loop()
    window_bits()
    approx_card()
    if "--json" in sys.argv:
        with open(sys.argv[sys.argv.index("--json") + 1], "w") as f:
            json.dump(R, f, indent=1)
