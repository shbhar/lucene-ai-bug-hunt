"""
Z3 model: are the column-pass writers failure-atomic?  After a non-aborting exception thrown while a
writer consumes a caller cursor, the number of VALUES the writer has committed must equal the number
of DOCS it has registered for them (otherwise every later value of that field in the same in-memory
segment is attributed to the wrong document).

Model element -> Java (Lucene main @ ea57360912ed)
  W1 NumericDocValuesWriter.addDenseValues:81-94 / SortedNumericDocValuesWriter.addDenseValues:73-102
       -> PackedLongValues.Builder.add(LongValuesCursor):265-279 (page loop; size += toFill per page)
          docsWithField.addRange only after add() returns
  W2 PointValuesWriter.addDense1DIntValues / addDense1DLongValues / addDenseNDValues:101-160
       chunk = BYTES_SCRATCH_SIZE(4096) / width; bytesOut.writeBytes(chunk) per chunk;
       numPoints / numDocs / lastDocID only in commitDenseRange() after the loop
       (addDenseNDValues: default BytesRefValuesCursor.fillPackedPoints:884-893 throws IAE on a bad length)
  W3 SortedDocValuesWriter.addDenseOrdinalValues (try/finally with `processed`)   <- contrast
  W4 SortedSetDocValuesWriter.addOrdinalTuples: currentDoc is advanced (finishCurrentDoc + currentDoc =
       docID) BEFORE the ordinal range check throws; the doc is later finished with 0 values.

For W1/W2 the cursor throws when it is asked for value index k (0 <= k < n); before the call the
builder already holds p0 pending values of earlier docs (0 <= p0 < PAGE).

Run: python3 -I model/dense_commit.py
"""
import sys
from z3 import Int, If, And, Or, Not, Solver, sat, unsat, IntVal, Bool

PAGE = 256       # PackedLongValues DEFAULT_PAGE_SIZE (pending = new long[pageSize])
SCRATCH = 4096   # SharedIndexingScratch.BYTES_SCRATCH_SIZE


def paged_loop(p0, n, k, cap_fn, iters):
    """Unrolled `while (remaining > 0) { packIfFull(); toFill = min(remaining, cap - off); fill(toFill) ... }`.
    Returns (committed_values, threw)."""
    off, remaining, committed, threw, consumed = p0, n, IntVal(0), Bool("never"), IntVal(0)
    threw = False
    threw_e = IntVal(0)  # 0/1
    for _ in range(iters):
        active = And(remaining > 0, threw_e == 0)
        off = If(And(active, off == cap_fn), 0, off)                    # packIfFull
        to_fill = If(remaining < cap_fn - off, remaining, cap_fn - off)
        hits = And(active, k >= consumed, k < consumed + to_fill)       # fill() reaches value k -> throws
        threw_e = If(hits, 1, threw_e)
        ok = And(active, Not(hits))
        committed = If(ok, committed + to_fill, committed)
        consumed = If(ok, consumed + to_fill, consumed)
        off = If(ok, off + to_fill, off)
        remaining = If(ok, remaining - to_fill, remaining)
    return committed, threw_e


def w1():
    s = Solver()
    p0, n, k = Int("p0"), Int("n"), Int("k")
    s.add(p0 >= 0, p0 < PAGE, n >= 1, n <= 4 * PAGE, k >= 0, k < n)
    committed, threw = paged_loop(p0, n, k, IntVal(PAGE), 8)
    docs_registered = IntVal(0)  # addRange happens only after add() returns
    s.add(threw == 1, committed != docs_registered)
    r = s.check()
    print(f"W1 dense long DV page loop failure-atomic: {'VIOLATED (sat)' if r == sat else 'proved (unsat)' if r == unsat else r}")
    if r == sat:
        # minimise n
        best = None
        for nn in range(1, 4 * PAGE + 1):
            s.push(); s.add(n == nn)
            if s.check() == sat:
                m = s.model(); best = (m[p0], m[n], m[k], m.eval(committed)); s.pop(); break
            s.pop()
        print(f"   smallest batch: p0={best[0]} numDocs={best[1]} throwAt={best[2]} -> {best[3]} values committed, 0 docs")
        s.push(); s.add(p0 == 0)
        for nn in range(1, 4 * PAGE + 1):
            s.push(); s.add(n == nn)
            if s.check() == sat:
                m = s.model(); print(f"   with an empty page (p0=0): numDocs={m[n]} throwAt={m[k]} -> {m.eval(committed)} values committed"); s.pop(); break
            s.pop()
        s.pop()
    # vacuity: an exception is reachable at all, and with n <= PAGE - p0 nothing is committed
    s2 = Solver(); s2.add(p0 >= 0, p0 < PAGE, n >= 1, k >= 0, k < n, n <= PAGE - p0)
    committed2, threw2 = paged_loop(p0, n, k, IntVal(PAGE), 8)
    s2.add(threw2 == 1, committed2 != 0)
    print(f"   single-page batches are atomic (unsat expected): {s2.check()}")
    return r == sat


def w2():
    found = []
    for width in (4, 8, 16, 32):
        chunk = SCRATCH // width
        s = Solver()
        n, k = Int("n"), Int("k")
        s.add(n >= 1, n <= 4 * chunk, k >= 0, k < n)
        committed, threw = paged_loop(IntVal(0), n, k, IntVal(chunk), 8)  # buffer offset restarts at 0 per call
        s.add(threw == 1, committed != 0)  # numPoints is advanced only in commitDenseRange
        r = s.check()
        mn = None
        if r == sat:
            for nn in range(1, 4 * chunk + 1):
                s.push(); s.add(n == nn)
                if s.check() == sat:
                    m = s.model(); mn = (m[n], m[k], m.eval(committed)); s.pop(); break
                s.pop()
        print(f"W2 dense points width={width} (chunk={chunk}) failure-atomic: {'VIOLATED' if r == sat else 'proved'}"
              + (f"; smallest: numDocs={mn[0]} badValueAt={mn[1]} -> {mn[2]} orphan packed values in bytesOut" if mn else ""))
        found.append(r == sat)
    return all(found)


def w3():
    # SortedDocValuesWriter.addDenseOrdinalValues: values added one by one, processed++ after each,
    # finally { addRange(first, first + processed) } -> committed == registered by construction.
    s = Solver()
    n, k = Int("n"), Int("k")
    s.add(n >= 1, n <= 4096, k >= 0, k < n)
    processed = k               # values 0..k-1 succeed, value k throws
    committed = k
    registered = processed
    s.add(committed != registered)
    r = s.check()
    print(f"W3 dictionary SORTED dense (try/finally processed) failure-atomic: {'proved (unsat)' if r == unsat else r}")
    return r == unsat


def w4():
    # SortedSetDocValuesWriter.addOrdinalTuples state machine, one bad tuple (doc d, ord out of range)
    # state: currentDoc, currentUpto. Invariant I: currentDoc == -1 or currentUpto >= 1 or the doc is
    # already registered.  A later finishCurrentDoc() registers currentDoc with `currentUpto` values.
    s = Solver()
    cur0, upto0, d = Int("cur0"), Int("upto0"), Int("d")
    s.add(cur0 >= -1, upto0 >= 0, Or(cur0 == -1, upto0 >= 1), d > cur0)  # legal state before the batch
    # tuple (d, badOrd): docID != currentDoc -> finishCurrentDoc(); currentDoc = d; then the ord check throws
    cur1 = d
    upto1 = IntVal(0)
    # the batch fails; the writer is reused for the next valid doc d2 > d: finishCurrentDoc() registers d with 0 values
    registered_values_for_d = upto1
    s.add(registered_values_for_d == 0)
    r = s.check()
    print(f"W4 SORTED_SET dictionary tuples: doc registered with 0 ordinals after a rejected ordinal: {'REACHABLE (sat)' if r == sat else r}")
    # contrast: SortedSetDocValuesWriter.addValue checks null / length BEFORE touching currentDoc
    return r == sat


def main():
    a = w1(); b = w2(); c = w3(); d = w4()
    print("summary: W1 violated=%s W2 violated=%s W3 proved=%s W4 reachable=%s" % (a, b, c, d))
    return 0


if __name__ == "__main__":
    sys.exit(main())
