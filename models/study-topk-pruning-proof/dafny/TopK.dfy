// Collector-level theorem: TopScoreDocCollector + any scorer that honours the documented
// Scorable.setMinCompetitiveScore contract returns exactly the exhaustive top-k.
//
// Code: search/TopScoreDocCollector.java:93-172 (collect, updateMinCompetitiveScore, updateGlobalMinCompetitiveScore),
//       search/DocScoreEncoder.java:31-41, search/MaxScoreAccumulator.java:23-44, util/TernaryLongHeap (heap of codes).
// Scores are the order-isomorphic sortable-int images of the float scores
// (NumericUtils.floatToSortableInt).  For finite non-negative scores other than Float.MAX_VALUE,
// Math.nextUp(f) corresponds to +1 on that image.  Assumption (documented in TopScoreDocCollector's
// javadoc only for NaN/-Inf; AssertingScorer additionally rejects -0.0f): no NaN, no -0.0f.
module TopK {
  // doc d (an index into sc) beats doc e: higher score, ties broken by smaller doc id (collector javadoc 25-27)
  predicate Better(sc: seq<int>, d: int, e: int)
    requires 0 <= d < |sc| && 0 <= e < |sc|
  {
    sc[d] > sc[e] || (sc[d] == sc[e] && d < e)
  }

  predicate InRange(sc: seq<int>, H: set<int>) { forall d :: d in H ==> 0 <= d < |sc| }

  // H is the top-k of the docs in P
  predicate IsTopK(sc: seq<int>, k: nat, P: set<int>, H: set<int>)
    requires InRange(sc, P)
  {
    H <= P && |H| == (if |P| < k then |P| else k) &&
    forall d, e :: d in H && e in P - H ==> Better(sc, d, e)
  }

  predicate IsWorst(sc: seq<int>, H: set<int>, w: int)
    requires InRange(sc, H)
  {
    w in H && forall d :: d in H && d != w ==> Better(sc, d, w)
  }

  lemma BetterTotal(sc: seq<int>, d: int, e: int)
    requires 0 <= d < |sc| && 0 <= e < |sc| && d != e
    ensures Better(sc, d, e) || Better(sc, e, d)
  {}

  lemma BetterTrans(sc: seq<int>, a: int, b: int, c: int)
    requires 0 <= a < |sc| && 0 <= b < |sc| && 0 <= c < |sc|
    requires Better(sc, a, b) && Better(sc, b, c)
    ensures Better(sc, a, c)
  {}

  lemma WorstExists(sc: seq<int>, H: set<int>) returns (w: int)
    requires InRange(sc, H) && |H| > 0
    ensures IsWorst(sc, H, w)
    decreases |H|
  {
    var x :| x in H;
    if |H| == 1 {
      assert |H - {x}| == 0;
      assert H - {x} == {};
      assert H == {x} by {
        forall y | y in H ensures y == x { if y != x { assert y in H - {x}; } }
      }
      w := x;
    } else {
      var w' := WorstExists(sc, H - {x});
      if Better(sc, w', x) {
        w := x;
        forall d | d in H && d != w ensures Better(sc, d, w) {
          if d != w' { BetterTrans(sc, d, w', x); }
        }
      } else {
        BetterTotal(sc, w', x);
        w := w';
      }
    }
  }

  // ---- one step of the exhaustive collector on doc i (docs arrive in increasing id order) ----
  // TopScoreDocCollector.collect: heap not full -> insert; else reject iff score <= topScore (line 113),
  // otherwise replace the top (least competitive) entry (line 130-135).
  function Step(sc: seq<int>, k: nat, H: set<int>, i: int): set<int>
    requires InRange(sc, H) && 0 <= i < |sc| && k >= 1
  {
    if |H| < k then H + {i}
    else
      var w := WorstOf(sc, H);
      if sc[i] > sc[w] then (H - {w}) + {i} else H
  }

  function WorstOf(sc: seq<int>, H: set<int>): int
    requires InRange(sc, H) && |H| > 0
    ensures IsWorst(sc, H, WorstOf(sc, H))
  {
    var w := WorstExistsF(sc, H); w
  }

  function WorstExistsF(sc: seq<int>, H: set<int>): int
    requires InRange(sc, H) && |H| > 0
    ensures IsWorst(sc, H, WorstExistsF(sc, H))
  {
    WorstLemma(sc, H);
    var w :| IsWorst(sc, H, w); w
  }

  lemma WorstLemma(sc: seq<int>, H: set<int>)
    requires InRange(sc, H) && |H| > 0
    ensures exists w :: IsWorst(sc, H, w)
  {
    var w := WorstExists(sc, H);
  }

  function Prefix(i: int): set<int>
    requires i >= 0
  {
    set d | 0 <= d < i
  }

  lemma PrefixCard(i: nat)
    ensures |Prefix(i)| == i
  {
    if i > 0 {
      assert Prefix(i) == Prefix(i - 1) + {i - 1};
      PrefixCard(i - 1);
    }
  }

  // Exhaustive collection maintains the top-k of the prefix.
  lemma StepCorrect(sc: seq<int>, k: nat, H: set<int>, i: nat)
    requires k >= 1 && i < |sc| && InRange(sc, Prefix(i))
    requires IsTopK(sc, k, Prefix(i), H)
    ensures InRange(sc, Prefix(i + 1)) && InRange(sc, H)
    ensures IsTopK(sc, k, Prefix(i + 1), Step(sc, k, H, i))
  {
    PrefixCard(i); PrefixCard(i + 1);
    assert Prefix(i + 1) == Prefix(i) + {i};
    assert i !in Prefix(i);
    var H' := Step(sc, k, H, i);
    if |H| < k {
      assert |Prefix(i)| < k;
      assert H == Prefix(i) by {
        assert H <= Prefix(i) && |H| == |Prefix(i)|;
        SubsetCard(H, Prefix(i));
      }
      assert H' == Prefix(i + 1);
    } else {
      var w := WorstOf(sc, H);
      assert |H| == k;
      if sc[i] > sc[w] {
        assert H' == (H - {w}) + {i};
        forall d, e | d in H' && e in Prefix(i + 1) - H' ensures Better(sc, d, e) {
          if d == i {
            if e == w { } else {
              assert e in Prefix(i) - H;
              assert Better(sc, w, e);
            }
          } else {
            if e == w { assert Better(sc, d, w); } else { assert e in Prefix(i) - H; }
          }
        }
      } else {
        // i loses against every element of H: i has the largest doc id and score <= worst score
        forall d, e | d in H' && e in Prefix(i + 1) - H' ensures Better(sc, d, e) {
          if e == i {
            if d == w { assert sc[w] >= sc[i] && w < i; }
            else { assert Better(sc, d, w); assert Better(sc, w, i) by { assert sc[w] >= sc[i] && w < i; } BetterTrans(sc, d, w, i); }
          }
        }
      }
    }
  }

  lemma SubsetCard(A: set<int>, B: set<int>)
    requires A <= B && |A| == |B|
    ensures A == B
  {
    if A != B {
      var x :| x in B && x !in A;
      assert A <= B - {x};
      assert |B - {x}| == |B| - 1;
      SubsetLeCard(A, B - {x});
    }
  }

  lemma SubsetLeCard(A: set<int>, B: set<int>)
    requires A <= B
    ensures |A| <= |B|
  {
    if A != {} {
      var x :| x in A;
      SubsetLeCard(A - {x}, B - {x});
    }
  }

  // ---- pruned run ----
  // The scorer may skip doc i only if sc[i] < mcs (Scorable.setMinCompetitiveScore contract, Scorable.java:178-187).
  // The collector only ever sets mcs = nextUp(topScore) = sc[worst(H)] + 1 when the heap is full
  // (line 154-171), and topScore never decreases; hence the invariant  mcs <= sc[worst(H)] + 1  (or no mcs).
  datatype Decision = Collect | Skip

  // mcsSeq[i] is the min competitive score in force when doc i is reached (or a value <= any score if unset).
  predicate ValidRun(sc: seq<int>, k: nat, dec: seq<Decision>, mcs: seq<int>, i: nat, H: set<int>)
    requires k >= 1 && i <= |sc| && |dec| == |sc| && |mcs| == |sc| && InRange(sc, H)
  {
    true
  }

  function RunPruned(sc: seq<int>, k: nat, dec: seq<Decision>, i: nat): set<int>
    requires k >= 1 && i <= |sc| && |dec| == |sc|
    ensures InRange(sc, RunPruned(sc, k, dec, i))
    ensures RunPruned(sc, k, dec, i) <= Prefix(i)
    decreases i
  {
    if i == 0 then {}
    else
      var H := RunPruned(sc, k, dec, i - 1);
      if dec[i - 1] == Skip then H else
        assert InRange(sc, H);
        var H' := Step(sc, k, H, i - 1);
        assert H' <= Prefix(i) by { assert Prefix(i) == Prefix(i - 1) + {i - 1}; }
        H'
  }

  function RunExhaustive(sc: seq<int>, k: nat, i: nat): set<int>
    requires k >= 1 && i <= |sc|
    ensures InRange(sc, RunExhaustive(sc, k, i))
    ensures RunExhaustive(sc, k, i) <= Prefix(i)
    decreases i
  {
    if i == 0 then {}
    else
      var H := RunExhaustive(sc, k, i - 1);
      var H' := Step(sc, k, H, i - 1);
      assert H' <= Prefix(i) by { assert Prefix(i) == Prefix(i - 1) + {i - 1}; }
      H'
  }

  // A skip of doc i is legal iff the heap is full and sc[i] < mcs <= sc[worst] + 1.
  predicate LegalSkips(sc: seq<int>, k: nat, dec: seq<Decision>)
    requires k >= 1 && |dec| == |sc|
  {
    forall i :: 0 <= i < |sc| && dec[i] == Skip ==>
      var H := RunPruned(sc, k, dec, i);
      |H| == k && sc[i] <= sc[WorstOf(sc, H)]     // sc[i] < mcs <= sc[worst]+1
  }

  lemma PrunedEqualsExhaustive(sc: seq<int>, k: nat, dec: seq<Decision>, i: nat)
    requires k >= 1 && i <= |sc| && |dec| == |sc| && LegalSkips(sc, k, dec)
    ensures RunPruned(sc, k, dec, i) == RunExhaustive(sc, k, i)
  {
    if i > 0 {
      PrunedEqualsExhaustive(sc, k, dec, i - 1);
      var H := RunPruned(sc, k, dec, i - 1);
      if dec[i - 1] == Skip {
        var w := WorstOf(sc, H);
        assert |H| == k && sc[i - 1] <= sc[w];
        assert Step(sc, k, H, i - 1) == H;
      }
    }
  }

  lemma ExhaustiveIsTopK(sc: seq<int>, k: nat, i: nat)
    requires k >= 1 && i <= |sc|
    ensures InRange(sc, Prefix(i))
    ensures IsTopK(sc, k, Prefix(i), RunExhaustive(sc, k, i))
  {
    if i == 0 {
      PrefixCard(0);
    } else {
      ExhaustiveIsTopK(sc, k, i - 1);
      StepCorrect(sc, k, RunExhaustive(sc, k, i - 1), i - 1);
    }
  }

  // MAIN THEOREM (collector level): every legal pruning decision sequence yields the exhaustive top-k.
  lemma {:isolate_assertions} TopKWithPruning(sc: seq<int>, k: nat, dec: seq<Decision>)
    requires k >= 1 && |dec| == |sc| && LegalSkips(sc, k, dec)
    ensures InRange(sc, Prefix(|sc|))
    ensures IsTopK(sc, k, Prefix(|sc|), RunPruned(sc, k, dec, |sc|))
  {
    PrunedEqualsExhaustive(sc, k, dec, |sc|);
    ExhaustiveIsTopK(sc, k, |sc|);
  }

  // ---- MaxScoreAccumulator (shared minimum across slices / intra-segment partitions) ----
  // Another collector (slice) published code (s, dA): its heap is full (k docs) with worst entry (s, dA)
  // (updateMinCompetitiveScore line 165-169 accumulates topCode).  A leaf with docBase B (all its docs
  // have ids >= B, and doc sets of slices are disjoint) derives
  //     mcs' = B >= dA ? nextUp(s) : s          (updateGlobalMinCompetitiveScore line 139-150)
  // Claim: every doc d of the leaf with sc[d] < mcs' is beaten by k distinct docs, so it is not in the
  // global top-k: skipping it is safe.
  lemma {:isolate_assertions} GlobalMinScoreSound(sc: seq<int>, k: nat, H1: set<int>, dA: int, B: int, d: int)
    requires k >= 1 && InRange(sc, H1) && |H1| == k && IsWorst(sc, H1, dA)
    requires 0 <= d < |sc| && d >= B && d !in H1
    requires sc[d] < (if B >= dA then sc[dA] + 1 else sc[dA])
    ensures forall h :: h in H1 ==> Better(sc, h, d)
  {
    assert Better(sc, dA, d);
    forall h | h in H1 ensures Better(sc, h, d) {
      if h != dA { BetterTrans(sc, h, dA, d); }
    }
  }

  // NOTE (precondition d != dA, i.e. "d !in H1"): needs disjoint doc sets per slice.  The case
  // B >= dA with d == dA is impossible because dA belongs to another slice.
}
