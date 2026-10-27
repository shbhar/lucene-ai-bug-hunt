// Dafny model of TopDocs.merge(start, size, shardHits[, tieBreaker]) (Lucene main @ ea57360912ed).
//
// Model element -> Java (lucene/core/src/java/org/apache/lucene/search/TopDocs.java)
//   Hit.score            -> ScoreDoc.score (NaN-free floats; -0.0f excluded: both are outside the
//                           remaining floats are a total order, and == is its equivalence.
//   Hit.tb               -> rank of the hit under the tie breaker (DEFAULT_TIE_BREAKER :36-45 is
//                           (shardIndex, doc) lexicographic; a custom Comparator is any total preorder,
//                           so it is a rank too)
//   le                   -> the documented result order: score descending, then tie breaker
//   RefLess              -> ScoreLessThan.lessThan :110-121 + tieBreakLessThan :75-93
//                           (hitIndex fallback when the tie breaker returns 0)
//   FindTop              -> queue.top() (:319). The heap is abstracted by its contract, proved in
//                           PQ.dfy (TopIsMin): top() is SOME element minimal under the LessThan.
//                           RefLess is a strict weak order (lexicographic), so PQ.dfy applies.
//   MergeAux             -> mergeAux :278-354 for sort == null (the Sort variant only swaps the
//                           primary key: ShardRefLessThan :170-193 has the same shape)
//   Wrap(start + size)   -> `int requestedResultWindow = start + size;` (:314), Java int arithmetic
//   out (Option slots)   -> `hits = new ScoreDoc[...]` (:313): unfilled slots stay null
//
// Entry points:
//   MergeAux (requires start + size <= Integer.MAX_VALUE)        must verify
//   MergeAuxNoWindowBound (only start, size >= 0)                EXPECTED TO FAIL: the
//        "every returned slot is filled" postcondition fails when start + size wraps.
//   TwoShardsTie                                                 EXPECTED TO FAIL: the Java
//        `assert first.hitIndex != second.hitIndex` (:88) fails for a legal tie breaker that
//        returns 0 for hits of different shards.
//   TwoShardsTieInjective (tie breaker distinguishes shards)     must verify
// No `assume` statements.

const MAXINT: int := 0x7fff_ffff
const TWO32: int := 0x1_0000_0000

function Wrap(x: int): int
{
  var m := x % TWO32;
  if m > MAXINT then m - TWO32 else m
}

function Min(a: int, b: int): int { if a <= b then a else b }

datatype Hit = Hit(score: int, tb: int, id: int)
datatype Option<T> = None | Some(v: T)

predicate le(a: Hit, b: Hit) { a.score > b.score || (a.score == b.score && a.tb <= b.tb) }

predicate RefLess(a: Hit, ha: int, b: Hit, hb: int)
{
  if a.score < b.score then false          // :114-115
  else if a.score > b.score then true      // :116-117
  else if a.tb != b.tb then a.tb < b.tb    // tieBreaker.compare != 0 :82, :92
  else ha < hb                             // :84-89
}

lemma LeTrans(a: Hit, b: Hit, c: Hit)
  requires le(a, b) && le(b, c)
  ensures le(a, c)
{}

lemma NotRefLessImpliesLe(a: Hit, ha: int, b: Hit, hb: int)
  requires !RefLess(a, ha, b, hb)
  ensures le(b, a)
{}

lemma RefLessTrans(a: Hit, ha: int, b: Hit, hb: int, c: Hit, hc: int)
  requires RefLess(a, ha, b, hb) && RefLess(b, hb, c, hc)
  ensures RefLess(a, ha, c, hc)
{}

ghost predicate Sorted(s: seq<Hit>) { forall p, q :: 0 <= p < q < |s| ==> le(s[p], s[q]) }

ghost predicate ValidIdx(sh: seq<seq<Hit>>, idx: seq<int>)
{
  |idx| == |sh| && forall s :: 0 <= s < |sh| ==> 0 <= idx[s] <= |sh[s]|
}

function Avail(sh: seq<seq<Hit>>): int
{
  if |sh| == 0 then 0 else Avail(sh[..|sh| - 1]) + |sh[|sh| - 1]|
}

function Count(idx: seq<int>): int
{
  if |idx| == 0 then 0 else Count(idx[..|idx| - 1]) + idx[|idx| - 1]
}

ghost function Taken(sh: seq<seq<Hit>>, idx: seq<int>): multiset<Hit>
  requires ValidIdx(sh, idx)
{
  if |sh| == 0 then multiset{}
  else Taken(sh[..|sh| - 1], idx[..|idx| - 1]) + multiset(sh[|sh| - 1][..idx[|idx| - 1]])
}

ghost function All(sh: seq<seq<Hit>>): multiset<Hit>
{
  if |sh| == 0 then multiset{} else All(sh[..|sh| - 1]) + multiset(sh[|sh| - 1])
}

lemma AvailNonNeg(sh: seq<seq<Hit>>)
  ensures Avail(sh) >= 0
{
  if |sh| > 0 { AvailNonNeg(sh[..|sh| - 1]); }
}

lemma CountZero(sh: seq<seq<Hit>>, idx: seq<int>)
  requires ValidIdx(sh, idx) && forall s :: 0 <= s < |idx| ==> idx[s] == 0
  ensures Count(idx) == 0 && Taken(sh, idx) == multiset{}
{
  if |sh| > 0 {
    CountZero(sh[..|sh| - 1], idx[..|idx| - 1]);
    assert sh[|sh| - 1][..0] == [];
  }
}

lemma CountLeAvail(sh: seq<seq<Hit>>, idx: seq<int>)
  requires ValidIdx(sh, idx)
  ensures Count(idx) <= Avail(sh)
  ensures Count(idx) < Avail(sh) ==> exists s :: 0 <= s < |sh| && idx[s] < |sh[s]|
{
  if |sh| > 0 {
    CountLeAvail(sh[..|sh| - 1], idx[..|idx| - 1]);
    if Count(idx) < Avail(sh) && idx[|idx| - 1] == |sh[|sh| - 1]| {
      var s :| 0 <= s < |sh| - 1 && idx[..|idx| - 1][s] < |sh[..|sh| - 1][s]|;
      assert idx[s] < |sh[s]|;
    }
  }
}

lemma TakenStep(sh: seq<seq<Hit>>, idx: seq<int>, t: int)
  requires ValidIdx(sh, idx) && 0 <= t < |sh| && idx[t] < |sh[t]|
  ensures ValidIdx(sh, idx[t := idx[t] + 1])
  ensures Count(idx[t := idx[t] + 1]) == Count(idx) + 1
  ensures Taken(sh, idx[t := idx[t] + 1]) == Taken(sh, idx) + multiset{sh[t][idx[t]]}
{
  var idx2 := idx[t := idx[t] + 1];
  var n := |sh|;
  if t == n - 1 {
    assert idx2[..n - 1] == idx[..n - 1];
    assert sh[n - 1][..idx[n - 1] + 1] == sh[n - 1][..idx[n - 1]] + [sh[n - 1][idx[n - 1]]];
  } else {
    assert idx2[..n - 1] == idx[..n - 1][t := idx[t] + 1];
    TakenStep(sh[..n - 1], idx[..n - 1], t);
  }
}

lemma TakenFull(sh: seq<seq<Hit>>, idx: seq<int>)
  requires ValidIdx(sh, idx) && forall s :: 0 <= s < |sh| ==> idx[s] == |sh[s]|
  ensures Taken(sh, idx) == All(sh) && Count(idx) == Avail(sh)
{
  if |sh| > 0 {
    TakenFull(sh[..|sh| - 1], idx[..|idx| - 1]);
    assert sh[|sh| - 1][..idx[|idx| - 1]] == sh[|sh| - 1];
  }
}

// queue.top(): some active shard whose head is minimal under RefLess
method FindTop(sh: seq<seq<Hit>>, idx: seq<int>) returns (m: int)
  requires ValidIdx(sh, idx) && exists s :: 0 <= s < |sh| && idx[s] < |sh[s]|
  ensures 0 <= m < |sh| && idx[m] < |sh[m]|
  ensures forall t :: 0 <= t < |sh| && idx[t] < |sh[t]| ==> !RefLess(sh[t][idx[t]], idx[t], sh[m][idx[m]], idx[m])
{
  var first :| 0 <= first < |sh| && idx[first] < |sh[first]|;
  m := first;
  var s := 0;
  while s < |sh|
    invariant 0 <= s <= |sh| && 0 <= m < |sh| && idx[m] < |sh[m]|
    invariant forall t :: 0 <= t < s && idx[t] < |sh[t]| ==> !RefLess(sh[t][idx[t]], idx[t], sh[m][idx[m]], idx[m])
    invariant !RefLess(sh[first][idx[first]], idx[first], sh[m][idx[m]], idx[m])
  {
    if idx[s] < |sh[s]| && RefLess(sh[s][idx[s]], idx[s], sh[m][idx[m]], idx[m]) {
      var old_m := m;
      forall t | 0 <= t < s && idx[t] < |sh[t]|
        ensures !RefLess(sh[t][idx[t]], idx[t], sh[s][idx[s]], idx[s])
      {
        if RefLess(sh[t][idx[t]], idx[t], sh[s][idx[s]], idx[s]) {
          RefLessTrans(sh[t][idx[t]], idx[t], sh[s][idx[s]], idx[s], sh[old_m][idx[old_m]], idx[old_m]);
        }
      }
      if RefLess(sh[first][idx[first]], idx[first], sh[s][idx[s]], idx[s]) {
        RefLessTrans(sh[first][idx[first]], idx[first], sh[s][idx[s]], idx[s], sh[old_m][idx[old_m]], idx[old_m]);
      }
      m := s;
    }
    s := s + 1;
  }
}

ghost predicate Prefix(sh: seq<seq<Hit>>, idx: seq<int>, taken: seq<Hit>)
  requires ValidIdx(sh, idx)
{
  Sorted(taken)
  && multiset(taken) == Taken(sh, idx)
  && |taken| == Count(idx)
  && forall p, s, i :: 0 <= p < |taken| && 0 <= s < |sh| && idx[s] <= i < |sh[s]| ==> le(taken[p], sh[s][i])
}

ghost predicate AllFilled(out: seq<Option<Hit>>) { forall p :: 0 <= p < |out| ==> out[p].Some? }

method MergeAux(sh: seq<seq<Hit>>, start: int, size: int)
  returns (out: seq<Option<Hit>>, ghost taken: seq<Hit>, idx: seq<int>)
  requires forall s :: 0 <= s < |sh| ==> Sorted(sh[s])
  requires 0 <= start <= MAXINT && 0 <= size <= MAXINT && Avail(sh) <= MAXINT
  requires start + size <= MAXINT
  ensures ValidIdx(sh, idx) && Prefix(sh, idx, taken)
  ensures Avail(sh) <= start ==> out == []
  ensures Avail(sh) > start ==> |out| == Min(size, Avail(sh) - start)
  ensures Avail(sh) > start ==> |taken| == Min(Avail(sh), start + size)
  ensures Avail(sh) > start ==> forall p :: 0 <= p < |out| ==> start + p < |taken| && out[p] == Some(taken[start + p])
  ensures AllFilled(out)
{
  out, taken, idx := MergeCore(sh, start, size);
}

method MergeAuxNoWindowBound(sh: seq<seq<Hit>>, start: int, size: int)
  returns (out: seq<Option<Hit>>, ghost taken: seq<Hit>, idx: seq<int>)
  requires forall s :: 0 <= s < |sh| ==> Sorted(sh[s])
  requires 0 <= start <= MAXINT && 0 <= size <= MAXINT && Avail(sh) <= MAXINT
  ensures AllFilled(out)          // EXPECTED FAILURE when start + size > Integer.MAX_VALUE
{
  out, taken, idx := MergeCore(sh, start, size);
}

method MergeCore(sh: seq<seq<Hit>>, start: int, size: int)
  returns (out: seq<Option<Hit>>, ghost taken: seq<Hit>, idx: seq<int>)
  requires forall s :: 0 <= s < |sh| ==> Sorted(sh[s])
  requires 0 <= start <= MAXINT && 0 <= size <= MAXINT && Avail(sh) <= MAXINT
  ensures ValidIdx(sh, idx) && Prefix(sh, idx, taken)
  ensures Avail(sh) <= start ==> out == []
  ensures Avail(sh) > start ==> |out| == Min(size, Avail(sh) - start)
  ensures Avail(sh) > start && start + size <= MAXINT ==> |taken| == Min(Avail(sh), start + size)
  ensures forall p :: 0 <= p < |out| && out[p].Some? ==> start + p < |taken| && out[p] == Some(taken[start + p])
  ensures start + size <= MAXINT ==> AllFilled(out)
{
  idx := seq(|sh|, _ => 0);
  CountZero(sh, idx);
  taken := [];
  var avail := Avail(sh);
  AvailNonNeg(sh);
  if avail <= start {
    out := [];
    return;
  }
  var len := Min(size, avail - start);
  out := seq(len, _ => None);
  var requestedResultWindow := Wrap(start + size);           // :314
  var numIterOnHits := Min(avail, requestedResultWindow);     // :315
  var hitUpto := 0;
  while hitUpto < numIterOnHits
    invariant ValidIdx(sh, idx) && Prefix(sh, idx, taken)
    invariant 0 <= hitUpto == |taken| && (hitUpto == 0 || hitUpto <= numIterOnHits) && |out| == len
    invariant forall p :: 0 <= p < len && start + p < hitUpto ==> out[p] == Some(taken[start + p])
    invariant forall p :: 0 <= p < len && start + p >= hitUpto ==> out[p] == None
    decreases numIterOnHits - hitUpto
  {
    CountLeAvail(sh, idx);
    var t := FindTop(sh, idx);
    var hit := sh[t][idx[t]];
    // hit ranks before or equal every not-yet-taken hit
    forall s, i | 0 <= s < |sh| && idx[s] <= i < |sh[s]| ensures le(hit, sh[s][i]) {
      NotRefLessImpliesLe(sh[s][idx[s]], idx[s], hit, idx[t]);
      if i > idx[s] { LeTrans(hit, sh[s][idx[s]], sh[s][i]); }
    }
    if hitUpto >= start {
      out := out[hitUpto - start := Some(hit)];               // :333-335
    }
    TakenStep(sh, idx, t);
    var idx2 := idx[t := idx[t] + 1];
    forall p, s, i | 0 <= p < |taken| + 1 && 0 <= s < |sh| && idx2[s] <= i < |sh[s]|
      ensures le((taken + [hit])[p], sh[s][i])
    {
      if p < |taken| {} else { assert (taken + [hit])[p] == hit; }
    }
    assert Sorted(taken + [hit]) by {
      forall p, q | 0 <= p < q < |taken| + 1 ensures le((taken + [hit])[p], (taken + [hit])[q]) {
        if q == |taken| { assert le(taken[p], sh[t][idx[t]]); }
      }
    }
    taken := taken + [hit];
    idx := idx2;
    hitUpto := hitUpto + 1;
  }
  CountLeAvail(sh, idx);
}

// ---------------------------------------------------------------- tieBreakLessThan's assertion
method TieBreakLessThan(a: Hit, ha: int, b: Hit, hb: int) returns (r: bool)
  requires a.tb == b.tb ==> ha != hb   // the Java assertion, as the obligation callers must meet
{
  if a.tb == b.tb {
    assert ha != hb;            // :88 `assert first.hitIndex != second.hitIndex`
    r := ha < hb;
  } else {
    r := a.tb < b.tb;
  }
}

// mergeAux :302-305 adds ShardRef(0) then ShardRef(1); add -> upHeap compares the new ref (hitIndex
// 0) with the root (hitIndex 0) through ScoreLessThan.
method TwoShardsTie(h0: Hit, h1: Hit)                       // EXPECTED FAILURE
  requires h0.score == h1.score
{
  var r := TieBreakLessThan(h1, 0, h0, 0);
}

method TwoShardsTieInjective(h0: Hit, h1: Hit)              // must verify
  requires h0.score == h1.score
  requires h0.tb != h1.tb     // the tie breaker never returns 0 for hits of different shards
{
  var r := TieBreakLessThan(h1, 0, h0, 0);
}
