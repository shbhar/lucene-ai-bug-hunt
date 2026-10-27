// Proof obligations for the code AS WRITTEN. Each lemma below is the natural correctness
// statement; Dafny cannot prove them, and the counterexamples it extracts
// (--extract-counterexample, logs/dafny-bounds-failing.log) are the JUnit repros:
//   junit/src/TestStandardQueryParserExclusivePointBounds.java
//   junit/src/TestHistogramLongMaxValue.java

include "Bounds.dfy"

module BoundsFailing {
  import opened Bounds

  // PointRangeQueryNodeBuilder.java:61-77 (Integer):  if (minInclusive == false) lower = lower + 1;
  lemma ParserExclusiveLowerInt(L: int, U: int, x: int)
    requires I32(L) && I32(U) && I32(x)
    ensures (L < x && x <= U) <==> (Wrap32(L + 1) <= x && x <= U)
  {}

  // PointRangeQueryNodeBuilder.java:72-75 (Integer):  if (maxInclusive == false) upper = upper - 1;
  lemma ParserExclusiveUpperInt(L: int, U: int, x: int)
    requires I32(L) && I32(U) && I32(x)
    ensures (L <= x && x < U) <==> (L <= x && x <= Wrap32(U - 1))
  {}

  // PointRangeQueryNodeBuilder.java:78-95 (Long)
  lemma ParserExclusiveLowerLong(L: int, U: int, x: int)
    requires I64(L) && I64(U) && I64(x)
    ensures (L < x && x <= U) <==> (Wrap64(L + 1) <= x && x <= U)
  {}

  // PointTreeBulkCollector.java:85-92 + BucketManager.<init> / finalizePreviousBucket
  // (lines 213-220, 233-245): maxValue = leafMax + 1;
  //   endValue = Math.min((Math.floorDiv(startValue, w) + 1) * w, maxValue)
  // Requirement (else visit() throws CollectionTerminatedException and the bucket is lost):
  // every in-range value v lies below the end of the bucket started at v.
  function EndValueJava(start: int, w: int, maxValue: int): int
    requires w > 0
  {
    var e := Wrap64(Wrap64(FloorDiv(start, w) + 1) * w);
    if e < maxValue then e else maxValue
  }

  lemma BucketEndCoversValue(v: int, w: int, leafMax: int)
    requires I64(v) && I64(w) && I64(leafMax)
    requires w >= 2 && v <= leafMax   // HistogramCollectorManager requires bucketWidth >= 2
    ensures v < EndValueJava(v, w, Wrap64(leafMax + 1))
  {}
}
