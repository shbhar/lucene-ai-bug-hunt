// Java-int view of the index arithmetic in PriorityQueue.downHeap (Lucene main @ ea57360912ed).
//
// Model element -> Java (lucene/core/src/java/org/apache/lucene/util/PriorityQueue.java)
//   Wrap / Shl1     -> Java int arithmetic of `j = i << 1` (:347, :355) and `k = j + 1` (:348, :356)
//   heapLen         -> heap.length = maxSize + 1 (:129), maxSize < ArrayUtil.MAX_ARRAY_LENGTH (:121)
//   MAX_ARRAY_LENGTH-> ArrayUtil.MAX_ARRAY_LENGTH = Integer.MAX_VALUE - NUM_BYTES_ARRAY_HEADER
//                      (16 on 64-bit HotSpot; any header >= 1 gives the same result)
//   DownHeapIndices -> the control flow of downHeap :345-362 with every key comparison replaced by
//                      a nondeterministic boolean (any LessThan, any contents)
//   each `assert 0 <= x < heapLen` sits where Java reads heap[x]
//
// Two entry points:
//   DownHeapIndicesSafe     requires size < 2^30; must verify (proof that the indices stay in range)
//   DownHeapIndicesFull     only the constructor's documented limit; EXPECTED TO FAIL: the
//                           assertion before heap[k] fails once i >= 2^30, because i << 1 wraps to a
//                           negative int, which passes the `k <= size` / `j <= size` guards.

const MAXINT: int := 0x7fff_ffff
const TWO32: int := 0x1_0000_0000
const MAX_ARRAY_LENGTH: int := MAXINT - 16

function Wrap(x: int): int
{
  var m := x % TWO32;
  if m > MAXINT then m - TWO32 else m
}

function Shl1(i: int): int { Wrap(2 * i) }

method DownHeapIndicesSafe(heapLen: int, size: int, i0: int)
  requires 2 <= heapLen <= MAX_ARRAY_LENGTH && 0 <= size < heapLen   // heap.length >= 2 (:117-130)
  requires i0 == 1 || 1 <= i0 <= size / 2   // Java calls downHeap(1) (pop, updateTop) and downHeap(i <= size/2) (addAll)
  requires size < 0x4000_0000
{
  var i := i0;
  var j := Shl1(i);
  var k := Wrap(j + 1);
  if k <= size {
    assert 0 <= k < heapLen && 0 <= j < heapLen;
    var smaller: bool := *;
    if smaller { j := k; }
  }
  while j <= size
    invariant i0 <= i < heapLen && (i == i0 || i <= size)
    invariant j == 2 * i || (j == 2 * i + 1 && j <= size)
    decreases heapLen - i
  {
    assert 0 <= j < heapLen;
    var less: bool := *;     // lessThan(heap[j], node)
    if !less { break; }
    i := j;
    j := Shl1(i);
    k := Wrap(j + 1);
    if k <= size {
      assert 0 <= k < heapLen && 0 <= j < heapLen;
      var smaller: bool := *;
      if smaller { j := k; }
    }
  }
}

method DownHeapIndicesFull(heapLen: int, size: int, i0: int)
  requires 2 <= heapLen <= MAX_ARRAY_LENGTH && 0 <= size < heapLen   // heap.length >= 2 (:117-130)
  requires i0 == 1 || 1 <= i0 <= size / 2   // Java calls downHeap(1) (pop, updateTop) and downHeap(i <= size/2) (addAll)
{
  var i := i0;
  var j := Shl1(i);
  var k := Wrap(j + 1);
  if k <= size {
    assert 0 <= k < heapLen && 0 <= j < heapLen;   // holds: i0 <= size / 2 < 2^30 or i0 == 1
    var smaller: bool := *;
    if smaller { j := k; }
  }
  while j <= size
    invariant i0 <= i < heapLen && (i == i0 || i <= size)
    invariant j == Shl1(i) || j == Wrap(Shl1(i) + 1)
    decreases heapLen - i
  {
    assert 0 <= j < heapLen;                        // EXPECTED FAILURE (i >= 2^30)
    var less: bool := *;
    if !less { break; }
    i := j;
    j := Shl1(i);
    k := Wrap(j + 1);
    if k <= size {
      assert 0 <= k < heapLen && 0 <= j < heapLen;  // EXPECTED FAILURE (i >= 2^30)
      var smaller: bool := *;
      if smaller { j := k; }
    }
  }
}

// Concrete witness: a queue of 2^30 + 1 elements, downHeap reaching node 2^30 (a leaf) reads
// heap[Integer.MIN_VALUE + 1].
lemma Witness()
  ensures Shl1(0x4000_0000) == -0x8000_0000
  ensures Wrap(Shl1(0x4000_0000) + 1) == -0x7fff_ffff
  ensures Wrap(Shl1(0x4000_0000) + 1) <= 0x4000_0001
{
}
