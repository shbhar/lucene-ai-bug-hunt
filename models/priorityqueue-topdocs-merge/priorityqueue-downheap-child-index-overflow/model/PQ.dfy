// Dafny model of org.apache.lucene.util.PriorityQueue (Lucene main @ ea57360912ed).
//
// Model element -> Java (lucene/core/src/java/org/apache/lucene/util/PriorityQueue.java)
//   E, lt                -> element type T, LessThan.lessThan (:44-47). Documented assumption: lessThan
//                           is a strict weak order. Every strict weak order on a finite set is induced by
//                           a ranking function, so it is modelled as key(a) < key(b); equal keys = ties.
//                           (`id` keeps element identity, so ties are distinct elements.)
//   PQ.heap, size        -> heap :75 (1-based, heap[0] unused), size :73
//   PQ.Valid             -> heap length :115-130 (maxSize 0 -> length 2, else maxSize + 1)
//   UpHeap               -> upHeap :332-343
//   DownHeap             -> downHeap :345-362
//   Heapify              -> the finally-block loop of addAll :170-173 / :202-205
//   PQ.Add               -> add :215-222 (ok == false models the ArrayIndexOutOfBoundsException,
//                           thrown by heap[index] = element before size changes)
//   PQ.InsertWithOverflow-> insertWithOverflow :231-243
//   PQ.Top               -> top :246-251
//   PQ.Pop               -> pop :254-265 (the null store into heap[size] is modelled as storing a
//                           dummy element; it is outside 1..size afterwards)
//   PQ.UpdateTop         -> updateTop(T newTop) :292-295; updateTop() :286-289 after the caller
//                           mutated the top in place is the same transition (top value replaced)
//   PQ.Clear             -> clear :303-306
//   PQ.AddAll            -> addAll(Collection) :161-175 (overflow == true models the AIOOBE)
//   PQ.InitSentinel      -> constructor with sentinel supplier :113-148
//   PQ.DrainLowestFirst  -> drainToArrayLowestFirst :312-318
//
// Index arithmetic is unbounded here (Dafny int); the int32 view of `j = i << 1` is in
// PQInt32.dfy. No `assume` statements; the only axioms are the datatype definitions.

datatype E = E(key: int, id: int)

predicate lt(a: E, b: E) { a.key < b.key }

const DUMMY: E := E(0, -1)

// every edge (c/2, c) whose parent is >= lo is ordered
ghost predicate HeapFrom(h: seq<E>, lo: int, size: int)
  requires 0 <= size < |h|
{
  forall c :: 2 <= c <= size && c / 2 >= lo ==> h[c / 2].key <= h[c].key
}

ghost function Contents(h: seq<E>, size: int): multiset<E>
  requires 0 <= size < |h|
{
  multiset(h[1..size + 1])
}

lemma HeapMin(h: seq<E>, size: int, p: int)
  requires 0 <= size < |h| && HeapFrom(h, 1, size) && 1 <= p <= size
  ensures h[1].key <= h[p].key
  decreases p
{
  if p > 1 { HeapMin(h, size, p / 2); }
}

// h[1] has the minimal key among the contents of a valid heap
lemma TopIsMin(h: seq<E>, size: int)
  requires 1 <= size < |h| && HeapFrom(h, 1, size)
  ensures h[1] in Contents(h, size)
  ensures forall y :: y in Contents(h, size) ==> h[1].key <= y.key
{
  assert h[1] == h[1..size + 1][0];
  forall y | y in Contents(h, size) ensures h[1].key <= y.key {
    var t :| 0 <= t < size && h[1..size + 1][t] == y;
    HeapMin(h, size, t + 1);
  }
}

lemma UpdSlice(s: seq<E>, i: int, v: E, n: int)
  requires 0 <= n < |s| && 0 <= i < |s|
  ensures 1 <= i <= n ==> multiset(s[i := v][1..n + 1]) == multiset(s[1..n + 1]) - multiset{s[i]} + multiset{v}
  ensures (i < 1 || i > n) ==> s[i := v][1..n + 1] == s[1..n + 1]
{
  if 1 <= i <= n {
    assert s[i := v][1..n + 1] == s[1..n + 1][i - 1 := v];
    assert s[1..n + 1][i - 1] == s[i];
  } else {
    assert s[i := v][1..n + 1] == s[1..n + 1];
  }
}

lemma SwapSlice(s: seq<E>, i: int, j: int, n: int)
  requires 1 <= i <= n && 1 <= j <= n && n < |s|
  ensures multiset(s[i := s[j]][j := s[i]][1..n + 1]) == multiset(s[1..n + 1])
{
  UpdSlice(s, i, s[j], n);
  UpdSlice(s[i := s[j]], j, s[i], n);
}

// ---------------------------------------------------------------- upHeap :332-343
method UpHeap(heap: array<E>, size: int, origPos: int)
  requires 1 <= origPos <= size < heap.Length
  requires 2 * origPos > size        // called from add with origPos == size, a leaf
  requires forall c :: 2 <= c <= size && c != origPos ==> heap[c / 2].key <= heap[c].key
  modifies heap
  ensures HeapFrom(heap[..], 1, size)
  ensures Contents(heap[..], size) == old(Contents(heap[..], size))
  ensures forall q :: 0 <= q < heap.Length && (q == 0 || q > size) ==> heap[q] == old(heap[q])
{
  var i := origPos;
  var node := heap[i];
  var j := i / 2;
  ghost var M := Contents(heap[..], size);
  ghost var a := heap[..];
  while j > 0 && lt(node, heap[j])
    invariant 1 <= i <= origPos && j == i / 2
    invariant |a| == heap.Length && a == heap[..][i := node]
    invariant multiset(a[1..size + 1]) == M
    invariant forall c :: 2 <= c <= size && c != i ==> a[c / 2].key <= a[c].key
    invariant i > 1 ==> forall c :: 2 <= c <= size && c / 2 == i ==> heap[i / 2].key <= heap[c].key
    invariant forall q :: 0 <= q < heap.Length && (q == 0 || q > size) ==> heap[q] == old(heap[q])
    decreases i
  {
    SwapSlice(a, i, j, size);
    a := a[i := a[j]][j := a[i]];
    heap[i] := heap[j];
    i := j;
    j := j / 2;
  }
  heap[i] := node;
  assert heap[..] == a;
}

// ---------------------------------------------------------------- downHeap :345-362
method DownHeap(heap: array<E>, size: int, i0: int)
  requires 1 <= i0 <= size + 1 && size < heap.Length && i0 < heap.Length
  requires HeapFrom(heap[..], i0 + 1, size)
  modifies heap
  ensures HeapFrom(heap[..], i0, size)
  ensures Contents(heap[..], size) == old(Contents(heap[..], size))
  ensures forall q :: 0 <= q < i0 ==> heap[q] == old(heap[q])
  ensures forall q :: size < q < heap.Length ==> heap[q] == old(heap[q])
{
  var i := i0;
  var node := heap[i];
  var j := 2 * i;
  var k := j + 1;
  if k <= size && lt(heap[k], heap[j]) {
    j := k;
  }
  ghost var M := Contents(heap[..], size);
  ghost var a := heap[..];
  while j <= size && lt(heap[j], node)
    invariant i0 <= i && i < heap.Length && (i == i0 || i <= size)
    invariant j == 2 * i || j == 2 * i + 1
    invariant j == 2 * i + 1 ==> j <= size && heap[j].key < heap[2 * i].key
    invariant j == 2 * i && 2 * i + 1 <= size ==> heap[2 * i].key <= heap[2 * i + 1].key
    invariant |a| == heap.Length && a == heap[..][i := node]
    invariant multiset(a[1..size + 1]) == M
    invariant forall c :: 2 <= c <= size && c / 2 >= i0 && c / 2 != i ==> a[c / 2].key <= a[c].key
    invariant i != i0 ==> forall c :: 2 <= c <= size && c / 2 == i ==> heap[i / 2].key <= heap[c].key
    invariant i != i0 ==> heap[i / 2].key <= node.key && i / 2 >= i0
    invariant forall q :: 0 <= q < i0 ==> heap[q] == old(heap[q])
    invariant forall q :: size < q < heap.Length ==> heap[q] == old(heap[q])
    decreases size - i
  {
    SwapSlice(a, i, j, size);
    a := a[i := a[j]][j := a[i]];
    heap[i] := heap[j];
    i := j;
    j := 2 * i;
    k := j + 1;
    if k <= size && lt(heap[k], heap[j]) {
      j := k;
    }
  }
  heap[i] := node;
  assert heap[..] == a;
}

// ---------------------------------------------------------------- addAll's heapify loop :170-173
method Heapify(heap: array<E>, size: int)
  requires 0 <= size < heap.Length
  modifies heap
  ensures HeapFrom(heap[..], 1, size)
  ensures Contents(heap[..], size) == old(Contents(heap[..], size))
  ensures forall q :: size < q < heap.Length ==> heap[q] == old(heap[q])
{
  var i := size / 2;
  while i >= 1
    invariant 0 <= i <= size / 2
    invariant HeapFrom(heap[..], i + 1, size)
    invariant Contents(heap[..], size) == old(Contents(heap[..], size))
    invariant forall q :: size < q < heap.Length ==> heap[q] == old(heap[q])
    decreases i
  {
    DownHeap(heap, size, i);
    i := i - 1;
  }
}

class PQ {
  var heap: array<E>
  var size: int
  const maxSize: int

  ghost predicate Valid()
    reads this, heap
  {
    0 <= maxSize && heap.Length == (if maxSize == 0 then 2 else maxSize + 1)
    && 0 <= size < heap.Length && HeapFrom(heap[..], 1, size)
  }

  ghost function Elems(): multiset<E>
    reads this, heap
    requires 0 <= size < heap.Length
  {
    Contents(heap[..], size)
  }

  // constructor without sentinels :79-81, :113-148 with a null supplier
  constructor (m: int)
    requires 0 <= m
    ensures Valid() && maxSize == m && size == 0 && Elems() == multiset{}
    ensures fresh(heap)
  {
    maxSize := m;
    heap := new E[if m == 0 then 2 else m + 1](_ => DUMMY);
    size := 0;
  }

  // constructor with a sentinel supplier :139-147 (every sentinel compares equal: same key)
  constructor InitSentinel(m: int, sentinelKey: int)
    requires 1 <= m
    ensures Valid() && maxSize == m && size == m
    ensures forall y :: y in Elems() ==> y.key == sentinelKey
    ensures fresh(heap)
  {
    maxSize := m;
    heap := new E[m + 1](q => E(sentinelKey, q));
    size := m;
    new;
    forall y | y in Contents(heap[..], size) ensures y.key == sentinelKey {
      var t :| 0 <= t < size && heap[..][1..size + 1][t] == y;
    }
  }

  method Top() returns (t: E)
    requires Valid() && size > 0
    ensures t in Elems() && forall y :: y in Elems() ==> t.key <= y.key
  {
    TopIsMin(heap[..], size);
    t := heap[1];
  }

  // add :215-222
  method Add(e: E) returns (ok: bool)
    requires Valid()
    modifies this, heap
    ensures Valid() && heap == old(heap)
    ensures ok <==> old(size) + 1 < heap.Length
    ensures ok ==> size == old(size) + 1 && Elems() == old(Elems()) + multiset{e}
    ensures !ok ==> size == old(size) && heap[..] == old(heap[..])
  {
    var index := size + 1;
    if index >= heap.Length {
      ok := false;     // heap[index] = element throws AIOOBE; size untouched
      return;
    }
    heap[index] := e;
    assert heap[..][1..index + 1] == old(heap[..])[1..size + 1] + [e];
    size := index;
    forall c | 2 <= c <= size && c != index ensures heap[c / 2].key <= heap[c].key {
      assert heap[c] == old(heap[c]) && heap[c / 2] == old(heap[c / 2]);
    }
    UpHeap(heap, size, index);
    ok := true;
  }

  // insertWithOverflow :231-243
  method InsertWithOverflow(e: E) returns (dropped: E, droppedIsNull: bool)
    requires Valid()
    modifies this, heap
    ensures Valid() && heap == old(heap)
    ensures size <= old(size) + 1 && (old(size) < maxSize ==> size == old(size) + 1)
    // not full: added, returns null
    ensures old(size) < maxSize ==> droppedIsNull && Elems() == old(Elems()) + multiset{e}
    // full: returns a minimal element of old contents + {e}, contents = old + {e} - {dropped}
    ensures old(size) >= maxSize ==> !droppedIsNull
    ensures old(size) >= maxSize ==> dropped in old(Elems()) + multiset{e}
    ensures old(size) >= maxSize ==> forall y :: y in old(Elems()) + multiset{e} ==> dropped.key <= y.key
    ensures old(size) >= maxSize ==> Elems() == old(Elems()) + multiset{e} - multiset{dropped}
    // non-competitive argument is returned unchanged
    ensures old(size) >= maxSize && dropped == e ==> Elems() == old(Elems())
  {
    if size < maxSize {
      var ok := Add(e);
      dropped, droppedIsNull := DUMMY, true;
      return;
    }
    droppedIsNull := false;
    if size > 0 && lt(heap[1], e) {
      TopIsMin(heap[..], size);
      var ret := heap[1];
      UpdSlice(heap[..], 1, e, size);
      heap[1] := e;
      assert HeapFrom(heap[..], 2, size) by {
        forall c | 2 <= c <= size && c / 2 >= 2 ensures heap[c / 2].key <= heap[c].key {
          assert heap[c] == old(heap[c]) && heap[c / 2] == old(heap[c / 2]);
        }
      }
      DownHeap(heap, size, 1);
      dropped := ret;
      forall y | y in old(Elems()) + multiset{e} ensures dropped.key <= y.key {
        if y in old(Elems()) {} else { assert y == e; }
      }
    } else {
      dropped := e;
      if size > 0 {
        TopIsMin(heap[..], size);
        forall y | y in old(Elems()) + multiset{e} ensures dropped.key <= y.key {
          if y in old(Elems()) { assert heap[1].key <= y.key; } else { assert y == e; }
        }
      }
    }
  }

  // pop :254-265
  method Pop() returns (result: E, isNull: bool)
    requires Valid()
    modifies this, heap
    ensures Valid() && heap == old(heap)
    ensures old(size) == 0 ==> isNull && size == 0 && Elems() == old(Elems())
    ensures old(size) > 0 ==> !isNull && size == old(size) - 1
    ensures old(size) > 0 ==> result in old(Elems()) && forall y :: y in old(Elems()) ==> result.key <= y.key
    ensures old(size) > 0 ==> Elems() == old(Elems()) - multiset{result}
  {
    if size > 0 {
      TopIsMin(heap[..], size);
      result := heap[1];
      isNull := false;
      ghost var h0 := heap[..];
      heap[1] := heap[size];
      heap[size] := DUMMY;
      size := size - 1;
      // contents now: old contents minus the old top
      assert heap[..][1..size + 1] == (if size >= 1 then [h0[size + 1]] + h0[2..size + 1] else []);
      if size >= 1 {
        assert h0[1..size + 2] == h0[1..size + 1] + [h0[size + 1]];
        assert h0[1..size + 1] == [h0[1]] + h0[2..size + 1];
        assert h0[1..size + 2] == [h0[1]] + h0[2..size + 1] + [h0[size + 1]];
        assert multiset(h0[1..size + 2]) == multiset{h0[1]} + multiset(h0[2..size + 1]) + multiset{h0[size + 1]};
      } else {
        assert h0[1..2] == [h0[1]];
      }
      assert HeapFrom(heap[..], 2, size) by {
        forall c | 2 <= c <= size && c / 2 >= 2 ensures heap[c / 2].key <= heap[c].key {
          assert heap[c] == h0[c] && heap[c / 2] == h0[c / 2];
        }
      }
      DownHeap(heap, size, 1);
    } else {
      result, isNull := DUMMY, true;
    }
  }

  // updateTop(T) :292-295, and updateTop() :286-289 after the caller changed the top in place
  method UpdateTop(newTop: E) returns (t: E)
    requires Valid()
    modifies this, heap
    ensures Valid() && heap == old(heap) && size == old(size)
    ensures size > 0 ==> Elems() == old(Elems()) - multiset{old(heap[1])} + multiset{newTop}
    ensures size > 0 ==> t in Elems() && forall y :: y in Elems() ==> t.key <= y.key
  {
    UpdSlice(heap[..], 1, newTop, size);
    heap[1] := newTop;
    assert HeapFrom(heap[..], 2, size) by {
      forall c | 2 <= c <= size && c / 2 >= 2 ensures heap[c / 2].key <= heap[c].key {
        assert heap[c] == old(heap[c]) && heap[c / 2] == old(heap[c / 2]);
      }
    }
    DownHeap(heap, size, 1);
    if size > 0 { TopIsMin(heap[..], size); }
    t := heap[1];
  }

  // clear :303-306
  method Clear()
    requires Valid()
    modifies this, heap
    ensures Valid() && heap == old(heap) && size == 0 && Elems() == multiset{}
  {
    var q := 0;
    while q < size + 1
      invariant 0 <= q <= size + 1
      invariant heap == old(heap) && size == old(size) && size < heap.Length
    {
      heap[q] := DUMMY;
      q := q + 1;
    }
    size := 0;
  }

  // addAll(Collection) :161-175
  method AddAll(elems: seq<E>) returns (overflow: bool, n: int)
    requires Valid()
    modifies this, heap
    ensures Valid() && heap == old(heap)
    ensures 0 <= n <= |elems| && size == old(size) + n
    ensures n == (if |elems| <= heap.Length - 1 - old(size) then |elems| else heap.Length - 1 - old(size))
    ensures overflow <==> n < |elems|
    ensures Elems() == old(Elems()) + multiset(elems[..n])
  {
    n := 0;
    overflow := false;
    ghost var M := Elems();
    while n < |elems|
      invariant 0 <= n <= |elems| && size == old(size) + n && size < heap.Length
      invariant Contents(heap[..], size) == M + multiset(elems[..n])
      invariant n <= heap.Length - 1 - old(size)
      invariant heap == old(heap)
    {
      if size + 1 >= heap.Length {
        overflow := true;     // heap[size + 1] = element throws; size not incremented
        break;
      }
      ghost var h0 := heap[..];
      heap[size + 1] := elems[n];
      assert heap[..][1..size + 2] == h0[1..size + 1] + [elems[n]];
      assert elems[..n + 1] == elems[..n] + [elems[n]];
      size := size + 1;
      n := n + 1;
    }
    Heapify(heap, size);   // the finally block runs on both paths
  }

  // drainToArrayLowestFirst :312-318: the drained keys are non-decreasing
  method DrainLowestFirst() returns (out: seq<E>)
    requires Valid()
    modifies this, heap
    ensures Valid() && size == 0
    ensures |out| == old(size) && multiset(out) == old(Elems())
    ensures forall p, q :: 0 <= p < q < |out| ==> out[p].key <= out[q].key
  {
    out := [];
    var total := size;
    ghost var M := Elems();
    while |out| < total
      invariant Valid() && heap == old(heap) && size + |out| == total
      invariant multiset(out) + Elems() == M
      invariant forall p, q :: 0 <= p < q < |out| ==> out[p].key <= out[q].key
      invariant |out| > 0 ==> forall y :: y in Elems() ==> out[|out| - 1].key <= y.key
      decreases total - |out|
    {
      var r, isNull := Pop();
      assert !isNull;
      out := out + [r];
    }
  }
}

// Witness of the maxSize == 0 corner: the javadoc of add (:210-211) says adding more than maxSize
// elements throws, but a maxSize-0 queue (heap length 2, :117-119) accepts one element.
method MaxSizeZeroAcceptsOne()
{
  var pq := new PQ(0);
  var ok := pq.Add(E(5, 1));
  assert ok && pq.size == 1 && pq.size > pq.maxSize;
  var ok2 := pq.Add(E(6, 2));
  assert !ok2;
}
