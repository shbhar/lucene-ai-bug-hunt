// Exclusive -> inclusive bound adjustment for integer points, and the bucket arithmetic of the
// histogram point-tree collector, with Java's two's-complement wrap-around made explicit.
//
// Transcribed code (Lucene main 7cecf79523):
//   StandardQueryParser PointRangeQueryNodeBuilder.build (Integer / Long branches)
//     lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/PointRangeQueryNodeBuilder.java:61-95
//   IntPoint/LongPoint.newRangeQuery javadoc: "For exclusive ranges, pass Math.addExact(lowerValue, 1)
//     or Math.addExact(upperValue, -1)"  (document/IntPoint.java, document/LongPoint.java)
//   PointTreeBulkCollector.collect / BucketManager
//     lucene/sandbox/src/java/org/apache/lucene/sandbox/facet/plain/histograms/PointTreeBulkCollector.java:71-95, 199-266
//
// This file holds the PROVED lemmas (the correct variants); BoundsFailing.dfy holds the
// obligations for the code as written, which Dafny rejects (counterexamples in the README).

module Bounds {
  const MIN32: int := -0x8000_0000
  const MAX32: int := 0x7fff_ffff
  const MIN64: int := -0x8000_0000_0000_0000
  const MAX64: int := 0x7fff_ffff_ffff_ffff

  predicate I32(x: int) { MIN32 <= x <= MAX32 }
  predicate I64(x: int) { MIN64 <= x <= MAX64 }

  // Java int / long + and * (JLS 15.18.2, 15.17.1): wrap modulo 2^32 / 2^64
  function Wrap32(x: int): int { (x - MIN32) % 0x1_0000_0000 + MIN32 }
  function Wrap64(x: int): int { (x - MIN64) % 0x1_0000_0000_0000_0000 + MIN64 }

  lemma Wrap32Id(x: int) requires I32(x) ensures Wrap32(x) == x {}
  lemma Wrap64Id(x: int) requires I64(x) ensures Wrap64(x) == x {}

  // Java Math.floorDiv(long, long) for w > 0 equals Euclidean division (Dafny's / on int, w > 0)
  function FloorDiv(x: int, w: int): int requires w > 0 { x / w }

  // --- correct exclusive-lower adjustment (the javadoc recipe Math.addExact(lower, 1), with
  //     the overflow turned into an empty query) ---
  datatype IntQuery = Range(lo: int, hi: int) | NoDocs

  predicate QMatches(q: IntQuery, x: int) {
    match q
      case Range(lo, hi) => lo <= x <= hi
      case NoDocs => false
  }

  function BuildExclusiveLowerChecked(L: int, U: int, maxV: int): IntQuery {
    if L == maxV then NoDocs else Range(L + 1, U)
  }

  function BuildExclusiveUpperChecked(L: int, U: int, minV: int): IntQuery {
    if U == minV then NoDocs else Range(L, U - 1)
  }

  lemma CheckedLowerIsExact32(L: int, U: int, x: int)
    requires I32(L) && I32(U) && I32(x)
    ensures (L < x && x <= U) <==> QMatches(BuildExclusiveLowerChecked(L, U, MAX32), x)
  {}

  lemma CheckedUpperIsExact32(L: int, U: int, x: int)
    requires I32(L) && I32(U) && I32(x)
    ensures (L <= x && x < U) <==> QMatches(BuildExclusiveUpperChecked(L, U, MIN32), x)
  {}

  lemma CheckedLowerIsExact64(L: int, U: int, x: int)
    requires I64(L) && I64(U) && I64(x)
    ensures (L < x && x <= U) <==> QMatches(BuildExclusiveLowerChecked(L, U, MAX64), x)
  {}

  // The code as written is exact whenever the bound is not the extreme value:
  lemma ParserLowerExactBelowMax32(L: int, U: int, x: int)
    requires I32(L) && I32(U) && I32(x) && L != MAX32
    ensures (L < x && x <= U) <==> (Wrap32(L + 1) <= x && x <= U)
  { Wrap32Id(L + 1); }

  // --- histogram BucketManager, correct (unbounded / saturating) arithmetic ---
  // After finalizePreviousBucket(v) for an in-range value v (v <= leafMax) the collector needs
  // v < endValue, otherwise it throws CollectionTerminatedException and drops v's bucket.
  function EndValueExact(start: int, w: int, maxValueExclusive: int): int
    requires w > 0
  {
    var e := (FloorDiv(start, w) + 1) * w;
    if e < maxValueExclusive then e else maxValueExclusive
  }

  lemma ExactEndValueCoversValue(v: int, w: int, leafMax: int)
    requires w >= 2 && v <= leafMax
    ensures v < EndValueExact(v, w, leafMax + 1)
  {
    // v < (v / w + 1) * w by Euclidean division
    assert v == (v / w) * w + v % w;
  }
}
