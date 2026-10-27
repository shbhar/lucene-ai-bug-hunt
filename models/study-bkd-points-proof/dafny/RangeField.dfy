// Deductive proof of RangeFieldQuery.QueryType.compare soundness for INTERSECTS, WITHIN, CONTAINS,
// CROSSES (lucene/core/src/java/org/apache/lucene/document/RangeFieldQuery.java:73-334, main
// 7cecf79523). A range with n dims is indexed as a 2n-dim point: dims [0, n) hold the mins,
// dims [n, 2n) the maxes (minOffset = dim * bpd, maxOffset = minOffset + bpd * numDims).
// Soundness is proved for ALL points in the cell, including "ranges" with min > max.

include "Bytes.dfy"

module RangeField {
  import opened Bytes

  datatype Relation = CELL_INSIDE_QUERY | CELL_OUTSIDE_QUERY | CELL_CROSSES_QUERY
  datatype QueryType = INTERSECTS | WITHIN | CONTAINS | CROSSES

  // all byte strings have length bpd; 2n entries
  predicate Ok(x: seq<seq<int>>, n: nat, bpd: nat) { IsPoint(x, 2 * n, bpd) }

  // per-dim matches (RangeFieldQuery.java:101-112, 147-158, 193-204)
  predicate DimMatches(t: QueryType, q: seq<seq<int>>, v: seq<seq<int>>, n: nat, bpd: nat, d: nat)
    requires t != CROSSES && Ok(q, n, bpd) && Ok(v, n, bpd) && d < n
  {
    var mn := d; var mx := d + n;
    match t
      case INTERSECTS => Cmp(q[mx], v[mn]) >= 0 && Cmp(q[mn], v[mx]) <= 0
      case WITHIN => Cmp(q[mn], v[mn]) <= 0 && Cmp(q[mx], v[mx]) >= 0
      case CONTAINS => Cmp(q[mn], v[mn]) >= 0 && Cmp(q[mx], v[mx]) <= 0
  }

  // matches over all dims (RangeFieldQuery.java:321-334); CROSSES (lines 268-276)
  predicate Matches(t: QueryType, q: seq<seq<int>>, v: seq<seq<int>>, n: nat, bpd: nat)
    requires Ok(q, n, bpd) && Ok(v, n, bpd)
    decreases if t == CROSSES then 1 else 0
  {
    if t == CROSSES then Matches(INTERSECTS, q, v, n, bpd) && !Matches(WITHIN, q, v, n, bpd)
    else forall d :: 0 <= d < n ==> DimMatches(t, q, v, n, bpd, d)
  }

  // per-dim compare (RangeFieldQuery.java:76-99, 122-145, 168-191)
  function DimCompare(t: QueryType, q: seq<seq<int>>, lo: seq<seq<int>>, hi: seq<seq<int>>, n: nat, bpd: nat, d: nat): Relation
    requires t != CROSSES && Ok(q, n, bpd) && Ok(lo, n, bpd) && Ok(hi, n, bpd) && d < n
  {
    var mn := d; var mx := d + n;
    match t
      case INTERSECTS =>
        if Cmp(q[mx], lo[mn]) < 0 || Cmp(q[mn], hi[mx]) > 0 then CELL_OUTSIDE_QUERY
        else if Cmp(q[mx], hi[mn]) >= 0 && Cmp(q[mn], lo[mx]) <= 0 then CELL_INSIDE_QUERY
        else CELL_CROSSES_QUERY
      case WITHIN =>
        if Cmp(q[mx], lo[mx]) < 0 || Cmp(q[mn], hi[mn]) > 0 then CELL_OUTSIDE_QUERY
        else if Cmp(q[mx], hi[mx]) >= 0 && Cmp(q[mn], lo[mn]) <= 0 then CELL_INSIDE_QUERY
        else CELL_CROSSES_QUERY
      case CONTAINS =>
        if Cmp(q[mx], hi[mx]) > 0 || Cmp(q[mn], lo[mn]) < 0 then CELL_OUTSIDE_QUERY
        else if Cmp(q[mx], lo[mx]) <= 0 && Cmp(q[mn], hi[mn]) >= 0 then CELL_INSIDE_QUERY
        else CELL_CROSSES_QUERY
  }

  // aggregate compare over dims (RangeFieldQuery.java:291-314), transcribed as a loop
  method Compare(t: QueryType, q: seq<seq<int>>, lo: seq<seq<int>>, hi: seq<seq<int>>, n: nat, bpd: nat)
    returns (r: Relation)
    requires t != CROSSES && Ok(q, n, bpd) && Ok(lo, n, bpd) && Ok(hi, n, bpd)
    ensures r == CELL_OUTSIDE_QUERY ==> forall v :: InBox(v, lo, hi, 2 * n, bpd) ==> !Matches(t, q, v, n, bpd)
    ensures r == CELL_INSIDE_QUERY ==> forall v :: InBox(v, lo, hi, 2 * n, bpd) ==> Matches(t, q, v, n, bpd)
  {
    var inside := true;
    var dim := 0;
    while dim < n
      invariant 0 <= dim <= n
      invariant inside ==> forall d :: 0 <= d < dim ==> DimCompare(t, q, lo, hi, n, bpd, d) == CELL_INSIDE_QUERY
    {
      var relation := DimCompare(t, q, lo, hi, n, bpd, dim);
      if relation == CELL_OUTSIDE_QUERY {
        forall v | InBox(v, lo, hi, 2 * n, bpd) ensures !Matches(t, q, v, n, bpd) {
          DimSound(t, q, lo, hi, n, bpd, dim, v);
        }
        return CELL_OUTSIDE_QUERY;
      } else if relation != CELL_INSIDE_QUERY {
        inside := false;
      }
      dim := dim + 1;
    }
    if inside {
      forall v | InBox(v, lo, hi, 2 * n, bpd) ensures Matches(t, q, v, n, bpd) {
        forall d | 0 <= d < n ensures DimMatches(t, q, v, n, bpd, d) { DimSound(t, q, lo, hi, n, bpd, d, v); }
      }
      return CELL_INSIDE_QUERY;
    }
    return CELL_CROSSES_QUERY;
  }

  lemma DimSound(t: QueryType, q: seq<seq<int>>, lo: seq<seq<int>>, hi: seq<seq<int>>, n: nat, bpd: nat, d: nat, v: seq<seq<int>>)
    requires t != CROSSES && Ok(q, n, bpd) && Ok(lo, n, bpd) && Ok(hi, n, bpd) && d < n
    requires InBox(v, lo, hi, 2 * n, bpd)
    ensures DimCompare(t, q, lo, hi, n, bpd, d) == CELL_OUTSIDE_QUERY ==> !DimMatches(t, q, v, n, bpd, d)
    ensures DimCompare(t, q, lo, hi, n, bpd, d) == CELL_INSIDE_QUERY ==> DimMatches(t, q, v, n, bpd, d)
  {
    var mn := d; var mx := d + n;
    assert Cmp(lo[mn], v[mn]) <= 0 && Cmp(v[mn], hi[mn]) <= 0;
    assert Cmp(lo[mx], v[mx]) <= 0 && Cmp(v[mx], hi[mx]) <= 0;
    // every needed fact is an instance of transitivity / antisymmetry between q, lo, hi and v
    var a := [q[mn], q[mx]];
    var b := [lo[mn], lo[mx], hi[mn], hi[mx], v[mn], v[mx]];
    forall x, y | x in a && y in b ensures Cmp(x, y) == -Cmp(y, x) { CmpAntisym(x, y); }
    forall x, y | x in b && y in b ensures Cmp(x, y) == -Cmp(y, x) { CmpAntisym(x, y); }
    forall x, y, z | x in a + b && y in b && z in a + b ensures
      (Cmp(x, y) <= 0 && Cmp(y, z) <= 0 ==> Cmp(x, z) <= 0) &&
      (Cmp(x, y) < 0 && Cmp(y, z) <= 0 ==> Cmp(x, z) < 0) &&
      (Cmp(x, y) <= 0 && Cmp(y, z) < 0 ==> Cmp(x, z) < 0)
    { CmpTrans(x, y, z); }
  }

  // CROSSES compare (RangeFieldQuery.java:240-266)
  method CrossesCompare(q: seq<seq<int>>, lo: seq<seq<int>>, hi: seq<seq<int>>, n: nat, bpd: nat)
    returns (r: Relation)
    requires Ok(q, n, bpd) && Ok(lo, n, bpd) && Ok(hi, n, bpd)
    ensures r == CELL_OUTSIDE_QUERY ==> forall v :: InBox(v, lo, hi, 2 * n, bpd) ==> !Matches(CROSSES, q, v, n, bpd)
    ensures r == CELL_INSIDE_QUERY ==> forall v :: InBox(v, lo, hi, 2 * n, bpd) ==> Matches(CROSSES, q, v, n, bpd)
  {
    var intersectRelation := Compare(INTERSECTS, q, lo, hi, n, bpd);
    if intersectRelation == CELL_OUTSIDE_QUERY {
      return CELL_OUTSIDE_QUERY;
    }
    var withinRelation := Compare(WITHIN, q, lo, hi, n, bpd);
    if withinRelation == CELL_INSIDE_QUERY {
      return CELL_OUTSIDE_QUERY;
    }
    if intersectRelation == CELL_INSIDE_QUERY && withinRelation == CELL_OUTSIDE_QUERY {
      return CELL_INSIDE_QUERY;
    }
    return CELL_CROSSES_QUERY;
  }
}
