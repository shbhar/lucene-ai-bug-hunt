# overflow-at-scale-2 (overflow-at-scale-2): Z3 bit-vector obligations for the expressions selected from the
# bytecode inventory (tools/Scan.java -> model/inventory-*.tsv.gz -> tools/triage.py).
#
# Java int = 32-bit BV, long = 64-bit BV, with two's-complement wraparound (bvadd/bvmul wrap exactly
# like Java). A property is PROVED when "preconditions AND NOT property" is UNSAT; a SAT answer is a
# counterexample (cex) and its model is printed. Every cex below was replayed on the stock main jars by
# the JUnit named in the row (candidates/*/repro).
#
# Mapping (model element -> Java, Lucene main ea57360912ed):
#   DS*  DrillSidewaysScorer.doDrillDownAdvanceScoring / doUnionScoring chunk loop
#        lucene/facet/src/java/org/apache/lucene/facet/DrillSidewaysScorer.java
#          :60-61  CHUNK = 2048, MASK = CHUNK - 1
#          :353, :566  int nextChunkStart = CHUNK
#          :369, :395, :491, :579, :617, :641  while (docID < nextChunkStart)
#          :476-479, :535-539, :599-602, :689-693  if (nextChunkStart >= maxDoc) break; nextChunkStart += CHUNK;
#        maxDoc = context.reader().maxDoc() (:338, :551), legal up to IndexWriter.MAX_DOCS.
#   TG*  TopGroups.merge per-group hit accumulator
#        lucene/grouping/src/java/org/apache/lucene/search/grouping/TopGroups.java
#          :196 int totalHits = 0;  :235 totalHits += shardGroupDocs.totalHits().value();  (int += long)
#          :252-253 Avg: groupScore = (float) (scoreSum / totalHits);  :282 new TotalHits(totalHits, EQUAL_TO)
#          :166-167 totalHitCount += shard.totalHitCount; totalGroupedHitCount += ... (int += int)
#        per-shard value: TotalHits of one index, <= IndexWriter.MAX_DOCS
#   LM*  LogMergePolicy.findMerges level window
#        lucene/core/src/java/org/apache/lucene/index/LogMergePolicy.java
#          :109-111 setMergeFactor accepts any mergeFactor >= 2
#          :596 int end = start + mergeFactor;  :597 while (end <= 1 + upto)
#          :628 if (end - start >= mergeFactor && minMergeSize < maxMergeSize && mergeSize < minMergeSize ...)
#          :635-636 while (end < 1 + upto) { levels.get(end) ... }   :661 new ArrayList<>(end - start)
#          :679-680 start = end; end = start + mergeFactor;
#   FV*  BaseFragListBuilder.createFieldFragList fragment end (FastVectorHighlighter)
#        lucene/highlighter/src/java/org/apache/lucene/search/vectorhighlight/BaseFragListBuilder.java
#          :39, :48-54 only a lower bound: fragCharSize >= minFragCharSize = max(1, 3 * margin), margin 6 (:27)
#          :71-72 spanStart = max(phraseStart - margin, startOffset); spanEnd = max(phraseEnd, spanStart + fragCharSize)
#          :77-78 later phrases are taken while phrase.getEndOffset() <= spanEnd
#          :95-102 spanStart = phraseStart - newMargin (clamped to startOffset); spanEnd = spanStart + max(matchLen, fragCharSize)
#   WS*  WeightedSpanTermExtractor.extract phrase -> SpanNearQuery slop (Highlighter / QueryScorer)
#        lucene/highlighter/src/java/org/apache/lucene/search/highlight/WeightedSpanTermExtractor.java
#          :149-155 positionGaps = max(0, lastPos - firstPos - numTerms + 1);  :161-162 new SpanNearQuery(clauses, getSlop() + positionGaps, inorder)
#          :215-223 MultiPhraseQuery positionGaps = number of empty positions;  :229-232 new SpanNearQuery(clauses, slop + positionGaps, inorder)
#   PS*  PassageSelector.pickBest window shift (MatchHighlighter)
#        lucene/highlighter/src/java/org/apache/lucene/search/matchhighlight/PassageSelector.java
#          :125 m.length() <= maxPassageWindow;  :128-129 from/to = toIntExact(((long) m.from + m.to -/+ maxPassageWindow) / 2)
#          :130-133 if (from < range.from) { to += range.from - from; from = range.from; }   (int)
#          :142 if (from < to && to <= value.length()) -> passage (:158); otherwise the marker gets none
#   N*   negative results (proved safe under the documented limits)
#
# run: python3 -I model/obligations.py [--smt2-dir DIR] [--json OUT]
import json
import sys
import time

from z3 import (And, BitVec, BitVecVal, If, Not, Or, Solver, ULT, SignExt, ZeroExt, Extract, sat,
                unsat, simplify)

INT_MAX = 2**31 - 1
INT_MIN = -2**31
LIMITS = {
    "MAX_DOCS": (INT_MAX - 128, "core/.../index/IndexWriter.java:207 MAX_DOCS = Integer.MAX_VALUE - 128"),
    "MAX_ARRAY_LENGTH": (INT_MAX - 16, "core/.../util/ArrayUtil.java:33 (16-byte array header)"),
    "NO_MORE_DOCS": (INT_MAX, "DocIdSetIterator.NO_MORE_DOCS"),
    "CHUNK": (2048, "facet/.../DrillSidewaysScorer.java:60"),
    "OFFLINE_SORTER_MAX_RECORD": (32767, "core/.../util/OfflineSorter.java:550 len > Short.MAX_VALUE -> IAE"),
    "HEATMAP_MAX_ROWS_OR_COLUMNS": (46340, "spatial-extras/.../HeatmapFacetCounter.java:45 (int) sqrt(MAX_ARRAY_LENGTH)"),
}
MAX_DOCS = LIMITS["MAX_DOCS"][0]
CHUNK = LIMITS["CHUNK"][0]

RESULTS = []
SMT2_DIR = None


def i32(n):
    return BitVec(n, 32)


def i64(n):
    return BitVec(n, 64)


def c32(v):
    return BitVecVal(v, 32)


def c64(v):
    return BitVecVal(v, 64)


def l2i(x):  # (int) long
    return Extract(31, 0, x)


def i2l(x):  # (long) int
    return SignExt(32, x)


def unsigned_min(a, b):  # MathUtil.unsignedMin
    return If(ULT(a, b), a, b)


def check(oid, title, pre, prop, expect, notes=""):
    """expect = 'proved' (pre AND NOT prop is UNSAT) or 'cex' (SAT, a witness of NOT prop)."""
    s = Solver()
    s.set("timeout", 600_000)
    s.add(pre)
    s.add(Not(prop))
    if SMT2_DIR:
        with open(f"{SMT2_DIR}/{oid}.smt2", "w") as f:
            f.write(s.to_smt2())
    t0 = time.time()
    r = s.check()
    dt = time.time() - t0
    if r == unsat:
        res = "PROVED"
        model = None
    elif r == sat:
        res = "CEX"
        m = s.model()
        model = {}
        for d in m.decls():
            v = m[d].as_long()
            if m[d].size() == 32 and v >= 2**31:
                v -= 2**32
            if m[d].size() == 64 and v >= 2**63:
                v -= 2**64
            model[d.name()] = v
    else:
        res = "UNKNOWN"
        model = None
    ok = (res == "PROVED" and expect == "proved") or (res == "CEX" and expect == "cex")
    RESULTS.append({"id": oid, "title": title, "result": res, "expected": expect, "as_expected": ok,
                    "seconds": round(dt, 3), "model": model, "notes": notes})
    print(f"{oid:6s} {res:7s} {'ok ' if ok else 'BAD'} {dt:7.3f}s  {title}" + (f"\n        cex: {model}" if model else ""))


def ds():
    maxDoc, k, x, d = i32("maxDoc"), i32("k"), i32("chunkStart"), i32("docID")
    legal = And(maxDoc >= 1, maxDoc <= MAX_DOCS)
    # every value the loop variable takes: CHUNK * k for k >= 1 (Java int wrap), i.e. x = 2048 * k mod 2^32
    reach = x == c32(CHUNK) * k
    # DS1: the addition nextChunkStart + CHUNK, executed while nextChunkStart < maxDoc, can overflow
    check("DS1", "DrillSideways: nextChunkStart + 2048 is exact whenever it is executed (x < maxDoc)",
          And(legal, reach, x >= 1, x < maxDoc),
          i2l(x + c32(CHUNK)) == i2l(x) + c64(CHUNK), "cex",
          "smallest maxDoc with a cex: 2^31 - 2047 (DS1b proves it)")
    # DS1b: exact boundary: overflow needs maxDoc > 2^31 - 2048
    check("DS1b", "DrillSideways: no overflow for every maxDoc <= 2^31 - 2048",
          And(maxDoc >= 1, maxDoc <= 2**31 - CHUNK, reach, x >= 1, x < maxDoc),
          i2l(x + c32(CHUNK)) == i2l(x) + c64(CHUNK), "proved")
    # DS2: non-termination: the exit test nextChunkStart >= maxDoc is false for EVERY reachable value
    check("DS2", "DrillSideways: for maxDoc in (2^31-2048, MAX_DOCS] no reachable chunk start is >= maxDoc",
          And(maxDoc > 2**31 - CHUNK, maxDoc <= MAX_DOCS, reach),
          x < maxDoc, "proved",
          "so 'if (nextChunkStart >= maxDoc) break' never fires: the while(true) loop never exits")
    # DS3: lost hits: docs in [2^31-2048, maxDoc) never satisfy docID < nextChunkStart
    check("DS3", "DrillSideways: a doc in the last chunk is never < any reachable chunk start",
          And(maxDoc > 2**31 - CHUNK, maxDoc <= MAX_DOCS, reach, d >= 2**31 - CHUNK, d < maxDoc),
          Not(d < x), "proved", "so no doc of the last chunk is ever collected")
    # DS4: the fix nextChunkStart = MathUtil.unsignedMin(nextChunkStart + CHUNK, NO_MORE_DOCS):
    # every step either advances by exactly CHUNK or jumps to NO_MORE_DOCS >= maxDoc (loop exits), and
    # every doc < maxDoc is below the chunk start after the step that covers it
    nxt = unsigned_min(x + c32(CHUNK), c32(INT_MAX))
    check("DS4", "fix: next chunk start is x+2048 exactly, or NO_MORE_DOCS (>= maxDoc, loop exits)",
          And(legal, x >= 0, x < maxDoc, Extract(10, 0, x) == 0),
          Or(And(nxt == x + c32(CHUNK), i2l(nxt) == i2l(x) + c64(CHUNK)), And(nxt == c32(INT_MAX), nxt >= maxDoc)),
          "proved")
    check("DS4b", "fix: a doc in [x, maxDoc) with x the last chunk start is < next chunk start",
          And(legal, x >= 0, x < maxDoc, Extract(10, 0, x) == 0, d >= x, d < maxDoc, d - x < CHUNK),
          d < nxt, "proved")


def tg():
    v = [i64(f"v{i}") for i in range(4)]
    for n in (2, 3, 4):
        pre = And(*[And(v[i] >= 0, v[i] <= MAX_DOCS) for i in range(n)])
        acc = c32(0)
        exact = c64(0)
        for i in range(n):
            acc = l2i(i2l(acc) + v[i])  # totalHits += value  (int += long: (int) (totalHits + value))
            exact = exact + v[i]
        check(f"TG{n}", f"TopGroups.merge: int accumulator equals the exact sum of {n} per-shard TotalHits",
              pre, i2l(acc) == exact, "cex")
        check(f"TG{n}s", f"TopGroups.merge: exact whenever the exact sum of {n} shards fits in an int",
              And(pre, exact <= INT_MAX), i2l(acc) == exact, "proved")
    # TGneg: with 2 shards the wrapped value is always negative once the sum passes INT_MAX
    pre2 = And(v[0] >= 0, v[0] <= MAX_DOCS, v[1] >= 0, v[1] <= MAX_DOCS, v[0] + v[1] > INT_MAX)
    acc2 = l2i(i2l(l2i(v[0])) + v[1])
    check("TG2neg", "TopGroups.merge, 2 shards, sum > INT_MAX: wrapped total is negative (TotalHits throws)",
          pre2, acc2 < 0, "proved", "TotalHits(value < 0) throws IllegalArgumentException (TotalHits.java:46-47)")
    # TG3pos: with 3 shards the wrapped total can be positive and wrong (silent)
    pre3 = And(*[And(v[i] >= 0, v[i] <= MAX_DOCS) for i in range(3)])
    acc3 = l2i(i2l(l2i(i2l(l2i(v[0])) + v[1])) + v[2])
    check("TG3pos", "TopGroups.merge, 3 shards: a non-negative wrapped total always equals the exact sum",
          pre3, Or(acc3 < 0, i2l(acc3) == v[0] + v[1] + v[2]), "cex",
          "cex = silent wrong count (and wrong Avg score), e.g. 3 x 1.5e9 -> 205,032,704")


def lm():
    start, mf, upto, end = i32("start"), i32("mergeFactor"), i32("upto"), i32("end")
    nseg = i32("numSegments")
    pre = And(mf >= 2, nseg >= 1, nseg <= 1_000_000, start >= 0, start < nseg, upto >= start, upto < nseg)
    e = start + mf
    check("LM1", "LogMergePolicy: end = start + mergeFactor never wraps (setMergeFactor only checks >= 2)",
          pre, i2l(e) == i2l(start) + i2l(mf), "cex")
    check("LM1s", "LogMergePolicy: exact iff mergeFactor <= Integer.MAX_VALUE - start",
          And(pre, mf <= c32(INT_MAX) - start), i2l(e) == i2l(start) + i2l(mf), "proved")
    # LM2: in the wrap case the loop is entered and the packing branch reads levels.get(end) with end < 0
    wrap = And(pre, i2l(start) + i2l(mf) > INT_MAX)
    check("LM2", "LogMergePolicy wrap: 'end <= 1+upto' and 'end - start >= mergeFactor' both hold, end < 0",
          wrap, And(e <= upto + 1, e - start >= mf, e < 0, e < upto + 1), "proved",
          "so the min-merge-size packing loop calls levels.get(end) with a negative index (IOOBE); "
          "with minMergeSize >= maxMergeSize it reaches new ArrayList<>(end - start) = MAX_VALUE elements")


def fv():
    ss, fcs, pe, ps, nxt = i32("spanStart"), i32("fragCharSize"), i32("phraseEnd"), i32("phraseStart"), i32("nextPhraseEnd")
    margin = 6  # BaseFragListBuilder.MARGIN_DEFAULT
    pre = And(ss >= 0, fcs >= 3 * margin, ps >= 0, ps < pe, ss <= ps)
    spanEnd = If(pe > ss + fcs, pe, ss + fcs)
    exact = i2l(ss) + i2l(fcs)
    check("FV1", "FVH: spanStart + fragCharSize never wraps (fragCharSize only has a lower bound)",
          pre, i2l(ss + fcs) == exact, "cex")
    check("FV1s", "FVH: exact iff fragCharSize <= Integer.MAX_VALUE - spanStart",
          And(pre, fcs <= c32(INT_MAX) - ss), i2l(ss + fcs) == exact, "proved")
    # FV2: on the wrap, spanEnd collapses to the first phrase's end, so a later phrase that the exact
    # window contains (nextPhraseEnd <= spanStart + fragCharSize, exactly) is not taken
    check("FV2", "FVH wrap: a later phrase inside the exact window is excluded (end > spanEnd)",
          And(pre, exact > INT_MAX, nxt > pe, i2l(nxt) <= exact),
          nxt <= spanEnd, "cex", "cex = a phrase the fragment should contain but does not")
    check("FV2b", "FVH wrap: spanEnd == phraseEnd for every wrapping input",
          And(pre, exact > INT_MAX), spanEnd == pe, "proved")
    # FV3: the fix spanEnd = max(phraseEnd, unsignedMin(spanStart + fragCharSize, MAX_VALUE))
    fixed = If(pe > unsigned_min(ss + fcs, c32(INT_MAX)), pe, unsigned_min(ss + fcs, c32(INT_MAX)))
    check("FV3", "fix: spanEnd >= min(exact sum, MAX_VALUE) and >= phraseEnd, so no in-window phrase is dropped",
          pre, And(i2l(fixed) >= If(exact > INT_MAX, c64(INT_MAX), exact), fixed >= pe), "proved")


def ws():
    slop, gaps = i32("slop"), i32("positionGaps")
    pre = And(slop >= 0, gaps >= 0)  # PhraseQuery/MultiPhraseQuery reject slop < 0; gaps is a count
    check("WS1", "Highlighter: getSlop() + positionGaps is a non-negative span slop",
          pre, slop + gaps >= 0, "cex", "a negative SpanNearQuery slop matches nothing")
    check("WS1s", "Highlighter: non-negative and exact iff slop <= Integer.MAX_VALUE - positionGaps",
          And(pre, slop <= c32(INT_MAX) - gaps), And(slop + gaps >= 0, i2l(slop + gaps) == i2l(slop) + i2l(gaps)), "proved")
    fixed = unsigned_min(slop + gaps, c32(INT_MAX))
    check("WS2", "fix: unsignedMin(slop + gaps, MAX_VALUE) is >= slop and >= 0 (never stricter than the phrase)",
          pre, And(fixed >= slop, fixed >= 0), "proved")


def ps():
    a, b, w, rf = i32("markerFrom"), i32("markerTo"), i32("maxPassageWindow"), i32("rangeFrom")
    lfrm = (i2l(a) + i2l(b) - i2l(w)) / c64(2)  # Java long division truncates toward zero, like bvsdiv
    lto = (i2l(a) + i2l(b) + i2l(w)) / c64(2)
    # Math.toIntExact at :128-129 throws unless both fit in an int (needs offsets near 2^30 to fail; excluded)
    pre = And(rf >= 0, rf <= a, a < b, w >= 1, b - a <= w, lto <= INT_MAX, lfrm >= INT_MIN)
    frm = l2i(lfrm)
    to = l2i(lto)
    shifted = to + (rf - frm)
    exact = i2l(to) + (i2l(rf) - i2l(frm))
    check("PS1", "PassageSelector: to += range.from - from is exact when from < range.from",
          And(pre, frm < rf), i2l(shifted) == exact, "cex", "cex: the shifted end wraps negative, no passage")
    check("PS1s", "PassageSelector: exact whenever maxPassageWindow <= Integer.MAX_VALUE - range.from - 1",
          And(pre, frm < rf, w <= c32(INT_MAX) - rf - 1), i2l(shifted) == exact, "proved")
    fixed = If(exact > INT_MAX, c32(INT_MAX), l2i(exact))
    check("PS2", "fix: (int) min((long) to + range.from - from, MAX_VALUE) >= range.from + 1 > from, so the passage is kept",
          And(pre, frm < rf), And(fixed > rf, fixed >= b), "proved")


def negatives():
    # N1 CheckIndex.checkFields skipDocID = (int) (((idx + 1) * (long) maxDoc) / 8), idx in [0, 7)
    idx, maxDoc = i32("idx"), i32("maxDoc")
    sk = l2i((i2l(idx + 1) * i2l(maxDoc)) / c64(8))
    check("N1", "CheckIndex skipDocID: 0 <= skipDocID < maxDoc and no truncation (CheckIndex.java:1943, :2057)",
          And(idx >= 0, idx < 7, maxDoc >= 1, maxDoc <= MAX_DOCS),
          And(sk >= 0, sk < maxDoc, i2l(sk) == (i2l(idx + 1) * i2l(maxDoc)) / c64(8)), "proved")
    # N2 DocValuesFieldUpdates comparator: (int) (ords.get(i) - ords.get(j)) has the sign of Long.compare
    oi, oj, size = i64("oi"), i64("oj"), i32("size")
    dif = l2i(oi - oj)
    check("N2", "DocValuesFieldUpdates tie-break (int)(ord_i - ord_j) has the sign of the exact difference (:315)",
          And(size >= 1, oi >= 0, oj >= 0, oi < i2l(size), oj < i2l(size)),
          And(Or(dif > 0, oi <= oj), Or(dif < 0, oi >= oj), Or(dif == 0, oi != oj)), "proved")
    # N3 HeatmapFacetCounter: columns * rows, both <= MAX_ROWS_OR_COLUMNS, never wraps (:140, Heatmap :62)
    cols, rows = i32("columns"), i32("rows")
    M = LIMITS["HEATMAP_MAX_ROWS_OR_COLUMNS"][0]
    check("N3", "HeatmapFacetCounter columns*rows exact for columns, rows in [1, 46340]",
          And(cols >= 1, rows >= 1, cols <= M, rows <= M),
          i2l(cols * rows) == i2l(cols) * i2l(rows), "proved")
    # N4 SortedInputIterator.encode (short) payload.length / ctx.length / contexts.size(): the whole entry
    # goes through OfflineSorter.ByteSequencesWriter.write, which rejects len > 32767 before anything is
    # read back, so every length that reaches decode fits in a short.
    spare, pay, ctxlen, nctx = i32("spareLength"), i32("payloadLength"), i32("ctxLength"), i32("numContexts")
    req = spare + 8 + 2 + pay + 2 + ctxlen + 2  # one context, with payload (SortedInputIterator.java:216-221)
    exact_req = i2l(spare) + 8 + 2 + i2l(pay) + 2 + i2l(ctxlen) + 2
    short_ok = lambda x: SignExt(16, Extract(15, 0, x)) == x
    check("N4", "SortedInputIterator: entry length <= 32767 (OfflineSorter) implies (short) casts are exact",
          And(spare >= 0, pay >= 0, ctxlen >= 0, spare <= LIMITS["MAX_ARRAY_LENGTH"][0],
              pay <= LIMITS["MAX_ARRAY_LENGTH"][0], ctxlen <= LIMITS["MAX_ARRAY_LENGTH"][0],
              exact_req <= 32767, i2l(req) == exact_req),
          And(short_ok(pay), short_ok(ctxlen)), "proved",
          "the writer throws IllegalArgumentException('len must be <= 32767') first: not an overflow bug")


def main():
    global SMT2_DIR
    args = sys.argv[1:]
    out = None
    if "--smt2-dir" in args:
        SMT2_DIR = args[args.index("--smt2-dir") + 1]
    if "--json" in args:
        out = args[args.index("--json") + 1]
    ds()
    tg()
    lm()
    fv()
    ws()
    ps()
    negatives()
    bad = [r for r in RESULTS if not r["as_expected"]]
    print(f"{len(RESULTS)} obligations, {len(bad)} not as expected")
    if out:
        with open(out, "w") as f:
            json.dump({"limits": LIMITS, "results": RESULTS}, f, indent=1)
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
