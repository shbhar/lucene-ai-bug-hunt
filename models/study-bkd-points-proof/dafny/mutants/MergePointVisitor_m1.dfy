// Deductive proof of PointInSetQuery's 1-D MergePointVisitor (search/PointInSetQuery.java:300-381,
// main 7cecf79523) driven by PointValues.intersect (index/PointValues.java:342-368).
//
// MergePointVisitor is STATEFUL: compare() and matches() permanently advance a cursor into the
// sorted, de-duplicated query points. Its correctness therefore depends on the 1-D order contract
// of PointValues (IntersectVisitor javadoc, PointValues.java:313-315: "In the 1D case, values are
// visited in increasing order"). This file makes that dependency explicit: under WF (non-empty
// nodes, in-order sorted values, every value inside its node's [lo, hi]) the visitor collects
// exactly the values that are in the query set. WF is what bin/exhaustive.sh checks on the real
// BKD trees (AllVisitor order check + per-node bounds/size check).
//
// Abstraction: 1-D values are ints (the unsigned lexicographic Cmp of dafny/Bytes.dfy is a total
// order on equal-length byte strings, proved there); doc IDs ride along with values and are omitted.

module MergePoint {
  datatype Tree = Leaf(lo: int, hi: int, vals: seq<int>) | Node(lo: int, hi: int, left: Tree, right: Tree)
  datatype Rel = INSIDE | OUTSIDE | CROSSES

  function Flat(t: Tree): seq<int> {
    match t
      case Leaf(_, _, vs) => vs
      case Node(_, _, l, r) => Flat(l) + Flat(r)
  }

  predicate Sorted(s: seq<int>) { forall i, j :: 0 <= i < j < |s| ==> s[i] <= s[j] }
  predicate StrictSorted(s: seq<int>) { forall i, j :: 0 <= i < j < |s| ==> s[i] < s[j] }

  predicate WF(t: Tree) {
    |Flat(t)| > 0 && Sorted(Flat(t))
    && (forall v :: v in Flat(t) ==> t.lo <= v <= t.hi)
    && (match t
          case Leaf(_, _, _) => true
          case Node(_, _, l, r) => WF(l) && WF(r))
  }

  function Filter(s: seq<int>, Q: seq<int>): seq<int> {
    if |s| == 0 then [] else (if s[0] in Q then [s[0]] else []) + Filter(s[1..], Q)
  }

  lemma FilterAppend(a: seq<int>, b: seq<int>, Q: seq<int>)
    ensures Filter(a + b, Q) == Filter(a, Q) + Filter(b, Q)
  {
    if |a| > 0 {
      assert (a + b)[0] == a[0];
      assert (a + b)[1..] == a[1..] + b;
      FilterAppend(a[1..], b, Q);
    } else {
      assert a + b == b;
    }
  }

  lemma FilterNone(s: seq<int>, Q: seq<int>)
    requires forall v :: v in s ==> v !in Q
    ensures Filter(s, Q) == []
  {
    if |s| > 0 { FilterNone(s[1..], Q); }
  }

  lemma FilterAll(s: seq<int>, Q: seq<int>)
    requires forall v :: v in s ==> v in Q
    ensures Filter(s, Q) == s
  {
    if |s| > 0 { FilterAll(s[1..], Q); }
  }

  lemma FilterSnoc(s: seq<int>, j: nat, Q: seq<int>)
    requires j < |s|
    ensures Filter(s[..j + 1], Q) == Filter(s[..j], Q) + (if s[j] in Q then [s[j]] else [])
  {
    assert s[..j + 1] == s[..j] + [s[j]];
    FilterAppend(s[..j], [s[j]], Q);
    assert [s[j]][1..] == [];
  }

  // MergePointVisitor.compare (PointInSetQuery.java:351-380)
  method Compare(Q: seq<int>, k0: nat, lo: int, hi: int) returns (r: Rel, k: nat)
    requires k0 <= |Q|
    ensures k0 <= k <= |Q|
    ensures forall i :: k0 <= i < k ==> Q[i] < lo
    ensures r == OUTSIDE ==> k == |Q| || Q[k] > hi
    ensures r == INSIDE ==> k < |Q| && Q[k] == lo && Q[k] == hi
    ensures r == CROSSES ==> k < |Q| && lo <= Q[k] <= hi
  {
    k := k0;
    while k < |Q|
      invariant k0 <= k <= |Q|
      invariant forall i :: k0 <= i < k ==> Q[i] < lo
    {
      if Q[k] <= lo {            // MUTANT
        k := k + 1;
        continue;
      }
      if Q[k] > hi {            // cmpMax > 0
        return OUTSIDE, k;
      }
      if Q[k] == lo && Q[k] == hi {
        return INSIDE, k;
      }
      return CROSSES, k;
    }
    return OUTSIDE, k;          // exhausted all query points
  }

  // MergePointVisitor.matches (PointInSetQuery.java:335-349)
  method Matches(Q: seq<int>, k0: nat, v: int) returns (b: bool, k: nat)
    requires k0 <= |Q|
    ensures k0 <= k <= |Q|
    ensures forall i :: k0 <= i < k ==> Q[i] < v
    ensures b <==> (k < |Q| && Q[k] == v)
    ensures !b ==> (k == |Q| || Q[k] > v)
  {
    k := k0;
    while k < |Q|
      invariant k0 <= k <= |Q|
      invariant forall i :: k0 <= i < k ==> Q[i] < v
    {
      if Q[k] == v {
        return true, k;
      } else if Q[k] < v {
        k := k + 1;
      } else {
        break;
      }
    }
    return false, k;
  }

  // PointValues.intersect (recursive form of the loop at PointValues.java:349-368) with the visitor
  method Intersect(Q: seq<int>, t: Tree, k0: nat) returns (out: seq<int>, k: nat)
    requires StrictSorted(Q) && WF(t) && k0 <= |Q|
    requires forall i :: 0 <= i < k0 ==> Q[i] < Flat(t)[0]
    ensures out == Filter(Flat(t), Q)
    ensures k0 <= k <= |Q|
    ensures forall i :: 0 <= i < k ==> Q[i] < Flat(t)[|Flat(t)| - 1]
    decreases t
  {
    var fl := Flat(t);
    assert fl[0] in fl && fl[|fl| - 1] in fl;
    var r, k1 := Compare(Q, k0, t.lo, t.hi);
    assert forall i :: 0 <= i < k1 ==> Q[i] < fl[0];
    if r == OUTSIDE {
      forall v | v in fl ensures v !in Q {
        if v in Q {
          var p :| 0 <= p < |Q| && Q[p] == v;
          if p < k1 { assert Q[p] < fl[0] <= v; } else if k1 < |Q| { assert Q[k1] > t.hi >= v; assert Q[k1] <= Q[p]; }
        }
      }
      FilterNone(fl, Q);
      return [], k1;
    } else if r == INSIDE {
      forall v | v in fl ensures v in Q { assert v == Q[k1]; }
      FilterAll(fl, Q);
      return fl, k1;
    }
    match t {
      case Node(_, _, l, rt) => {
        assert Flat(l)[0] == fl[0];
        var o1, k2 := Intersect(Q, l, k1);
        var last := Flat(l)[|Flat(l)| - 1];
        assert last == fl[|Flat(l)| - 1] && Flat(rt)[0] == fl[|Flat(l)|];
        var o2, k3 := Intersect(Q, rt, k2);
        assert Flat(rt)[|Flat(rt)| - 1] == fl[|fl| - 1];
        FilterAppend(Flat(l), Flat(rt), Q);
        return o1 + o2, k3;
      }
      case Leaf(_, _, vs) => {
        out := [];
        k := k1;
        var j := 0;
        while j < |vs|
          invariant 0 <= j <= |vs|
          invariant k1 <= k <= |Q|
          invariant out == Filter(vs[..j], Q)
          invariant j == 0 ==> forall i :: 0 <= i < k ==> Q[i] < vs[0]
          invariant j > 0 ==> forall i :: 0 <= i < k ==> Q[i] < vs[j - 1]
        {
          var kk := k;
          assert forall i :: 0 <= i < k ==> Q[i] < vs[j] by {
            if j > 0 { assert vs[j - 1] <= vs[j]; }
          }
          var b;
          b, k := Matches(Q, k, vs[j]);
          assert b <==> vs[j] in Q by {
            if vs[j] in Q {
              var p :| 0 <= p < |Q| && Q[p] == vs[j];
              assert p >= kk;
              if !b { if k < |Q| { assert Q[k] > vs[j]; if p >= k { assert Q[p] >= Q[k]; } else { assert Q[p] < vs[j]; } } else { assert p < k; } }
            }
          }
          FilterSnoc(vs, j, Q);
          if b {
            out := out + [vs[j]];
          }
          assert vs[..j + 1] == vs[..j] + [vs[j]];
          j := j + 1;
        }
        assert vs[..|vs|] == vs;
        return out, k;
      }
    }
  }

  // Theorem: PointInSetQuery (1-D) collects exactly the indexed values that are in the query set.
  method PointInSet1D(Q: seq<int>, root: Tree) returns (out: seq<int>)
    requires StrictSorted(Q) && WF(root)
    ensures out == Filter(Flat(root), Q)
  {
    var k;
    out, k := Intersect(Q, root, 0);
  }
}
