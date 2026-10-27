// Unsigned lexicographic comparison of fixed-length byte strings, i.e. the semantics of
// ArrayUtil.getUnsignedComparator(bytesPerDim).compare(a, aOff, b, bOff)
// (lucene/core/src/java/org/apache/lucene/util/ArrayUtil.java:803-826). For bytesPerDim 4 / 8 the
// real code compares big-endian ints/longs with Integer/Long.compareUnsigned; z3/comparators.py
// proves those kernels equal to this lexicographic definition for all inputs.
//
// Points are modelled per dimension: a packed value of numDims * bytesPerDim bytes is a
// seq<seq<int>> with numDims entries, each a byte string of length bytesPerDim (the Java code
// addresses dimension d at offset d * bytesPerDim).

module Bytes {
  predicate IsByte(x: int) { 0 <= x < 256 }

  predicate IsBytes(s: seq<int>, n: nat) {
    |s| == n && forall i :: 0 <= i < |s| ==> IsByte(s[i])
  }

  function Cmp(a: seq<int>, b: seq<int>): int
    requires |a| == |b|
    decreases |a|
  {
    if |a| == 0 then 0
    else if a[0] < b[0] then -1
    else if a[0] > b[0] then 1
    else Cmp(a[1..], b[1..])
  }

  lemma CmpRange(a: seq<int>, b: seq<int>)
    requires |a| == |b|
    ensures -1 <= Cmp(a, b) <= 1
    decreases |a|
  {
    if |a| > 0 && a[0] == b[0] { CmpRange(a[1..], b[1..]); }
  }

  lemma CmpRefl(a: seq<int>)
    ensures Cmp(a, a) == 0
    decreases |a|
  {
    if |a| > 0 { CmpRefl(a[1..]); }
  }

  lemma CmpAntisym(a: seq<int>, b: seq<int>)
    requires |a| == |b|
    ensures Cmp(a, b) == -Cmp(b, a)
    decreases |a|
  {
    if |a| > 0 && a[0] == b[0] { CmpAntisym(a[1..], b[1..]); }
  }

  lemma CmpZeroIsEq(a: seq<int>, b: seq<int>)
    requires |a| == |b|
    ensures Cmp(a, b) == 0 <==> a == b
    decreases |a|
  {
    if |a| == 0 {
    } else if a[0] == b[0] {
      CmpZeroIsEq(a[1..], b[1..]);
      assert a == [a[0]] + a[1..];
      assert b == [b[0]] + b[1..];
    } else {
      assert a[0] != b[0];
    }
  }

  // transitivity of <=, and the strict mixes
  lemma CmpTrans(a: seq<int>, b: seq<int>, c: seq<int>)
    requires |a| == |b| == |c|
    ensures Cmp(a, b) <= 0 && Cmp(b, c) <= 0 ==> Cmp(a, c) <= 0
    ensures Cmp(a, b) < 0 && Cmp(b, c) <= 0 ==> Cmp(a, c) < 0
    ensures Cmp(a, b) <= 0 && Cmp(b, c) < 0 ==> Cmp(a, c) < 0
    decreases |a|
  {
    if |a| > 0 { CmpTrans(a[1..], b[1..], c[1..]); }
  }

  // convenience: a total order
  predicate Le(a: seq<int>, b: seq<int>) requires |a| == |b| { Cmp(a, b) <= 0 }

  lemma LeTotal(a: seq<int>, b: seq<int>)
    requires |a| == |b|
    ensures Le(a, b) || Le(b, a)
  {
    CmpAntisym(a, b); CmpRange(a, b);
  }

  // max / min under Cmp (used to build witnesses)
  function MaxB(a: seq<int>, b: seq<int>): seq<int> requires |a| == |b| { if Cmp(a, b) >= 0 then a else b }
  function MinB(a: seq<int>, b: seq<int>): seq<int> requires |a| == |b| { if Cmp(a, b) <= 0 then a else b }

  // ---------- multi-dimensional points ----------
  predicate IsPoint(p: seq<seq<int>>, nd: nat, bpd: nat) {
    |p| == nd && forall d :: 0 <= d < nd ==> IsBytes(p[d], bpd)
  }

  // p lies in the cell [mn, mx] (inclusive in every dimension): the BKD cell semantics
  predicate InBox(p: seq<seq<int>>, mn: seq<seq<int>>, mx: seq<seq<int>>, nd: nat, bpd: nat)
    requires IsPoint(mn, nd, bpd) && IsPoint(mx, nd, bpd)
  {
    IsPoint(p, nd, bpd) && forall d :: 0 <= d < nd ==> Cmp(mn[d], p[d]) <= 0 && Cmp(p[d], mx[d]) <= 0
  }

  predicate NonEmptyBox(mn: seq<seq<int>>, mx: seq<seq<int>>, nd: nat, bpd: nat)
    requires IsPoint(mn, nd, bpd) && IsPoint(mx, nd, bpd)
  {
    forall d :: 0 <= d < nd ==> Cmp(mn[d], mx[d]) <= 0
  }
}
