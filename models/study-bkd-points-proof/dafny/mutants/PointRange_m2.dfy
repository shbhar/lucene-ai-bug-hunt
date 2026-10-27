// Deductive proof of the relate / match logic of PointRangeQuery and PointInSetQuery
// (Lucene main 7cecf79523):
//   PointRangeQuery.relate            search/PointRangeQuery.java:594-619
//   PointRangeQuery matches()         search/PointRangeQuery.java:134-147
//   inverse visitor compare           search/PointRangeQuery.java:233-247
//   scorerSupplier "return null"      search/PointRangeQuery.java:263-275
//   scorerSupplier allDocsMatch       search/PointRangeQuery.java:278-297
//   PointInSetQuery SinglePointVisitor.compare / visit   search/PointInSetQuery.java:446-490
// The methods below transcribe the Java control flow (loops with early return) statement by
// statement; only the byte[]+offset addressing is replaced by per-dimension byte strings.

include "Bytes.dfy"

module PointRange {
  import opened Bytes

  datatype Relation = CELL_INSIDE_QUERY | CELL_OUTSIDE_QUERY | CELL_CROSSES_QUERY

  // matches(packedValue): PointRangeQuery.java:134-147
  predicate Matches(p: seq<seq<int>>, lower: seq<seq<int>>, upper: seq<seq<int>>, nd: nat, bpd: nat)
    requires IsPoint(p, nd, bpd) && IsPoint(lower, nd, bpd) && IsPoint(upper, nd, bpd)
  {
    forall d :: 0 <= d < nd ==> !(Cmp(p[d], lower[d]) < 0) && !(Cmp(p[d], upper[d]) > 0)
  }

  method Relate(nd: nat, bpd: nat, lower: seq<seq<int>>, upper: seq<seq<int>>,
                minP: seq<seq<int>>, maxP: seq<seq<int>>) returns (r: Relation)
    requires IsPoint(lower, nd, bpd) && IsPoint(upper, nd, bpd)
    requires IsPoint(minP, nd, bpd) && IsPoint(maxP, nd, bpd)
    // soundness
    ensures r == CELL_OUTSIDE_QUERY ==>
      forall p :: InBox(p, minP, maxP, nd, bpd) ==> !Matches(p, lower, upper, nd, bpd)
    ensures r == CELL_INSIDE_QUERY ==>
      forall p :: InBox(p, minP, maxP, nd, bpd) ==> Matches(p, lower, upper, nd, bpd)
    // the result is also determined by a closed form (used for the precision theorem below)
    ensures r == RelateSpec(nd, bpd, lower, upper, minP, maxP)
  {
    var crosses := false;
    var dim := 0;
    while dim < nd
      invariant 0 <= dim <= nd
      invariant forall d :: 0 <= d < dim ==> !Disjoint(d, lower, upper, minP, maxP)
      invariant crosses <==> exists d :: 0 <= d < dim && Overhang(d, lower, upper, minP, maxP)
    {
      if Cmp(minP[dim], upper[dim]) >= 0 || Cmp(maxP[dim], lower[dim]) < 0 {
        OutsideSound(nd, bpd, lower, upper, minP, maxP, dim);
        return CELL_OUTSIDE_QUERY;
      }
      ghost var old_crosses := crosses;
      if crosses == false {
        crosses := Cmp(minP[dim], lower[dim]) < 0 || Cmp(maxP[dim], upper[dim]) > 0;
      }
      if old_crosses {
        var w :| 0 <= w < dim && Overhang(w, lower, upper, minP, maxP);
        assert 0 <= w < dim + 1 && Overhang(w, lower, upper, minP, maxP);
      } else if crosses {
        assert Overhang(dim, lower, upper, minP, maxP);
      } else {
        assert !Overhang(dim, lower, upper, minP, maxP);
        assert forall d :: 0 <= d < dim + 1 ==> !Overhang(d, lower, upper, minP, maxP);
      }
      dim := dim + 1;
    }
    if crosses {
      return CELL_CROSSES_QUERY;
    } else {
      InsideSound(nd, bpd, lower, upper, minP, maxP);
      return CELL_INSIDE_QUERY;
    }
  }

  predicate Disjoint(d: nat, lower: seq<seq<int>>, upper: seq<seq<int>>, minP: seq<seq<int>>, maxP: seq<seq<int>>)
    requires d < |lower| == |upper| == |minP| == |maxP|
    requires |minP[d]| == |upper[d]| == |maxP[d]| == |lower[d]|
  {
    Cmp(minP[d], upper[d]) > 0 || Cmp(maxP[d], lower[d]) < 0
  }

  predicate Overhang(d: nat, lower: seq<seq<int>>, upper: seq<seq<int>>, minP: seq<seq<int>>, maxP: seq<seq<int>>)
    requires d < |lower| == |upper| == |minP| == |maxP|
    requires |minP[d]| == |upper[d]| == |maxP[d]| == |lower[d]|
  {
    Cmp(minP[d], lower[d]) < 0 || Cmp(maxP[d], upper[d]) > 0
  }

  function RelateSpec(nd: nat, bpd: nat, lower: seq<seq<int>>, upper: seq<seq<int>>,
                      minP: seq<seq<int>>, maxP: seq<seq<int>>): Relation
    requires IsPoint(lower, nd, bpd) && IsPoint(upper, nd, bpd)
    requires IsPoint(minP, nd, bpd) && IsPoint(maxP, nd, bpd)
  {
    if exists d :: 0 <= d < nd && Disjoint(d, lower, upper, minP, maxP) then CELL_OUTSIDE_QUERY
    else if exists d :: 0 <= d < nd && Overhang(d, lower, upper, minP, maxP) then CELL_CROSSES_QUERY
    else CELL_INSIDE_QUERY
  }

  lemma OutsideSound(nd: nat, bpd: nat, lower: seq<seq<int>>, upper: seq<seq<int>>,
                     minP: seq<seq<int>>, maxP: seq<seq<int>>, dim: nat)
    requires IsPoint(lower, nd, bpd) && IsPoint(upper, nd, bpd)
    requires IsPoint(minP, nd, bpd) && IsPoint(maxP, nd, bpd)
    requires dim < nd && Disjoint(dim, lower, upper, minP, maxP)
    ensures forall p :: InBox(p, minP, maxP, nd, bpd) ==> !Matches(p, lower, upper, nd, bpd)
  {
    forall p | InBox(p, minP, maxP, nd, bpd)
      ensures !Matches(p, lower, upper, nd, bpd)
    {
      // upper < minP <= p   or   p <= maxP < lower
      CmpAntisym(minP[dim], upper[dim]);
      CmpTrans(upper[dim], minP[dim], p[dim]);
      CmpAntisym(upper[dim], p[dim]);
      CmpTrans(p[dim], maxP[dim], lower[dim]);
    }
  }

  lemma InsideSound(nd: nat, bpd: nat, lower: seq<seq<int>>, upper: seq<seq<int>>,
                    minP: seq<seq<int>>, maxP: seq<seq<int>>)
    requires IsPoint(lower, nd, bpd) && IsPoint(upper, nd, bpd)
    requires IsPoint(minP, nd, bpd) && IsPoint(maxP, nd, bpd)
    requires forall d :: 0 <= d < nd ==> !Overhang(d, lower, upper, minP, maxP)
    ensures forall p :: InBox(p, minP, maxP, nd, bpd) ==> Matches(p, lower, upper, nd, bpd)
  {
    forall p, d | InBox(p, minP, maxP, nd, bpd) && 0 <= d < nd
      ensures !(Cmp(p[d], lower[d]) < 0) && !(Cmp(p[d], upper[d]) > 0)
    {
      // lower <= minP <= p  and  p <= maxP <= upper
      assert !Overhang(d, lower, upper, minP, maxP);
      assert Cmp(minP[d], p[d]) <= 0 && Cmp(p[d], maxP[d]) <= 0;
      CmpAntisym(minP[d], lower[d]);
      CmpTrans(lower[d], minP[d], p[d]);
      CmpAntisym(lower[d], p[d]);
      CmpTrans(p[d], maxP[d], upper[d]);
    }
  }

  // ---------- precision: CROSSES is only returned when the cell really crosses ----------
  // For a non-empty cell and a non-empty query box, CELL_CROSSES_QUERY implies that the cell
  // contains both a matching and a non-matching point: relate is exact for boxes.
  lemma CrossesIsExact(nd: nat, bpd: nat, lower: seq<seq<int>>, upper: seq<seq<int>>,
                       minP: seq<seq<int>>, maxP: seq<seq<int>>)
    requires IsPoint(lower, nd, bpd) && IsPoint(upper, nd, bpd)
    requires IsPoint(minP, nd, bpd) && IsPoint(maxP, nd, bpd)
    requires NonEmptyBox(minP, maxP, nd, bpd) && NonEmptyBox(lower, upper, nd, bpd)
    requires RelateSpec(nd, bpd, lower, upper, minP, maxP) == CELL_CROSSES_QUERY
    ensures exists p :: InBox(p, minP, maxP, nd, bpd) && Matches(p, lower, upper, nd, bpd)
    ensures exists p :: InBox(p, minP, maxP, nd, bpd) && !Matches(p, lower, upper, nd, bpd)
  {
    // matching witness: per dim, max(minP, lower)
    var m := seq(nd, d requires 0 <= d < nd => MaxB(minP[d], lower[d]));
    forall d | 0 <= d < nd
      ensures IsBytes(m[d], bpd) && Cmp(minP[d], m[d]) <= 0 && Cmp(m[d], maxP[d]) <= 0
      ensures !(Cmp(m[d], lower[d]) < 0) && !(Cmp(m[d], upper[d]) > 0)
    {
      assert !Disjoint(d, lower, upper, minP, maxP);
      CmpRefl(minP[d]); CmpRefl(lower[d]);
      CmpAntisym(minP[d], lower[d]); CmpRange(minP[d], lower[d]);
      CmpAntisym(maxP[d], lower[d]); CmpAntisym(minP[d], upper[d]);
    }
    assert InBox(m, minP, maxP, nd, bpd) && Matches(m, lower, upper, nd, bpd);
    // non-matching witness: in an overhanging dim take minP (if minP < lower) or maxP; elsewhere minP
    var k :| 0 <= k < nd && Overhang(k, lower, upper, minP, maxP);
    var useMin := Cmp(minP[k], lower[k]) < 0;
    var q := seq(nd, d requires 0 <= d < nd => if d == k && !useMin then maxP[d] else minP[d]);
    forall d | 0 <= d < nd
      ensures Cmp(minP[d], q[d]) <= 0 && Cmp(q[d], maxP[d]) <= 0
    {
      CmpRefl(minP[d]); CmpRefl(maxP[d]);
    }
    assert InBox(q, minP, maxP, nd, bpd);
    assert !Matches(q, lower, upper, nd, bpd);
  }

  // ---------- inverse visitor (PointRangeQuery.java:233-247) ----------
  method InverseCompare(nd: nat, bpd: nat, lower: seq<seq<int>>, upper: seq<seq<int>>,
                        minP: seq<seq<int>>, maxP: seq<seq<int>>) returns (r: Relation)
    requires IsPoint(lower, nd, bpd) && IsPoint(upper, nd, bpd)
    requires IsPoint(minP, nd, bpd) && IsPoint(maxP, nd, bpd)
    // the inverse visitor collects NON-matching docs; soundness w.r.t. !Matches
    ensures r == CELL_OUTSIDE_QUERY ==>
      forall p :: InBox(p, minP, maxP, nd, bpd) ==> !!Matches(p, lower, upper, nd, bpd)
    ensures r == CELL_INSIDE_QUERY ==>
      forall p :: InBox(p, minP, maxP, nd, bpd) ==> !Matches(p, lower, upper, nd, bpd)
  {
    var relation := Relate(nd, bpd, lower, upper, minP, maxP);
    match relation {
      case CELL_INSIDE_QUERY => return CELL_OUTSIDE_QUERY;
      case CELL_OUTSIDE_QUERY => return CELL_INSIDE_QUERY;
      case CELL_CROSSES_QUERY => return relation;
    }
  }

  // ---------- scorerSupplier shortcuts ----------
  // (PointRangeQuery.java:263-275) return null (no match in this segment) if, in some dim,
  // lower > fieldMax or upper < fieldMin
  lemma NullShortcutSound(nd: nat, bpd: nat, lower: seq<seq<int>>, upper: seq<seq<int>>,
                          fmin: seq<seq<int>>, fmax: seq<seq<int>>, i: nat)
    requires IsPoint(lower, nd, bpd) && IsPoint(upper, nd, bpd)
    requires IsPoint(fmin, nd, bpd) && IsPoint(fmax, nd, bpd)
    requires i < nd && (Cmp(lower[i], fmax[i]) > 0 || Cmp(upper[i], fmin[i]) < 0)
    ensures forall p :: InBox(p, fmin, fmax, nd, bpd) ==> !Matches(p, lower, upper, nd, bpd)
  {
    CmpAntisym(lower[i], fmax[i]); CmpAntisym(upper[i], fmin[i]);
    OutsideSound(nd, bpd, lower, upper, fmin, fmax, i);
  }

  // (PointRangeQuery.java:278-297) allDocsMatch: docCount == maxDoc and in every dim
  // lower <= fieldMin and upper >= fieldMax  ==> every indexed point matches
  lemma AllDocsMatchSound(nd: nat, bpd: nat, lower: seq<seq<int>>, upper: seq<seq<int>>,
                          fmin: seq<seq<int>>, fmax: seq<seq<int>>)
    requires IsPoint(lower, nd, bpd) && IsPoint(upper, nd, bpd)
    requires IsPoint(fmin, nd, bpd) && IsPoint(fmax, nd, bpd)
    requires forall i :: 0 <= i < nd ==> !(Cmp(lower[i], fmin[i]) > 0 || Cmp(upper[i], fmax[i]) < 0)
    ensures forall p :: InBox(p, fmin, fmax, nd, bpd) ==> Matches(p, lower, upper, nd, bpd)
  {
    forall d | 0 <= d < nd ensures !Overhang(d, lower, upper, fmin, fmax) {
      CmpAntisym(lower[d], fmin[d]); CmpAntisym(upper[d], fmax[d]);
    }
    InsideSound(nd, bpd, lower, upper, fmin, fmax);
  }

  // ---------- PointInSetQuery.SinglePointVisitor (PointInSetQuery.java:446-490) ----------
  method SinglePointCompare(nd: nat, bpd: nat, point: seq<seq<int>>, minP: seq<seq<int>>, maxP: seq<seq<int>>)
    returns (r: Relation)
    requires IsPoint(point, nd, bpd) && IsPoint(minP, nd, bpd) && IsPoint(maxP, nd, bpd)
    ensures r == CELL_OUTSIDE_QUERY ==> forall p :: InBox(p, minP, maxP, nd, bpd) ==> p != point
    ensures r == CELL_INSIDE_QUERY ==> forall p :: InBox(p, minP, maxP, nd, bpd) ==> p == point
  {
    var crosses := false;
    var dim := 0;
    while dim < nd
      invariant 0 <= dim <= nd
      invariant forall d :: 0 <= d < dim ==> Cmp(minP[d], point[d]) <= 0 && Cmp(maxP[d], point[d]) >= 0
      invariant !crosses ==> forall d :: 0 <= d < dim ==> Cmp(minP[d], point[d]) == 0 && Cmp(maxP[d], point[d]) == 0
    {
      var cmpMin := Cmp(minP[dim], point[dim]);
      if cmpMin > 0 {
        forall p | InBox(p, minP, maxP, nd, bpd) ensures p != point {
          if p == point { CmpAntisym(minP[dim], point[dim]); }
        }
        return CELL_OUTSIDE_QUERY;
      }
      var cmpMax := Cmp(maxP[dim], point[dim]);
      if cmpMax < 0 {
        forall p | InBox(p, minP, maxP, nd, bpd) ensures p != point {
          if p == point { CmpAntisym(maxP[dim], point[dim]); }
        }
        return CELL_OUTSIDE_QUERY;
      }
      if cmpMin != 0 || cmpMax != 0 {
        crosses := true;
      }
      dim := dim + 1;
    }
    if crosses {
      return CELL_CROSSES_QUERY;
    } else {
      forall p | InBox(p, minP, maxP, nd, bpd) ensures p == point {
        forall d | 0 <= d < nd ensures p[d] == point[d] {
          CmpZeroIsEq(minP[d], point[d]); CmpZeroIsEq(maxP[d], point[d]);
          CmpAntisym(p[d], point[d]); CmpZeroIsEq(p[d], point[d]);
        }
      }
      return CELL_INSIDE_QUERY;
    }
  }
}
