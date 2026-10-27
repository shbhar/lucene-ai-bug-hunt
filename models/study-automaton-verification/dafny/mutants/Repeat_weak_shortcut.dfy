// Unbounded Dafny proof that the Kleene-star construction of Operations.repeat(Automaton)
// (lucene/core/src/java/org/apache/lucene/util/automaton/Operations.java:194-254, main 7cecf79523,
// introduced by #13714 "Make Operations#repeat create simpler automata") accepts exactly L(a)*
// for EVERY automaton with at least one state, and a machine-checked counterexample for the
// zero-state automaton (the already-known repeat-of-empty bug: Lucene returns the empty language).
//
// Abstraction: an NFA is a finite set of labelled transitions (src, symbol, dst). Lucene's range
// labels [min,max] are copied verbatim by the construction, so a range transition is modelled as
// the set of its single-symbol transitions. Builder.finish() (sorting/merging) and the final
// removeDeadStates() are language-preserving (checked exhaustively by the Java harness) and are not
// modelled here.

datatype NFA = NFA(n: nat, acc: set<nat>, T: set<(nat, int, nat)>)

ghost predicate IsPath(T: set<(nat, int, nat)>, p: seq<nat>, w: seq<int>) {
  |p| == |w| + 1 && forall i :: 0 <= i < |w| ==> (p[i], w[i], p[i + 1]) in T
}

ghost predicate AcceptsFrom(a: NFA, q: nat, w: seq<int>) {
  exists p :: IsPath(a.T, p, w) && p[0] == q && p[|w|] in a.acc
}

ghost predicate Accepts(a: NFA, w: seq<int>) { a.n > 0 && AcceptsFrom(a, 0, w) }

// L(a)*: the empty word, or a non-empty word of L(a) followed by a word of L(a)*
ghost predicate InStar(a: NFA, w: seq<int>) decreases |w| {
  |w| == 0 || exists k :: 1 <= k <= |w| && Accepts(a, w[..k]) && InStar(a, w[k..])
}

// ---------------------------------------------------------------------------------------------
// The construction (Java: stateMap, then three groups of transitions, new state 0 accepting)
// ---------------------------------------------------------------------------------------------
ghost predicate NoOut(a: NFA, s: nat) { forall t :: t in a.T ==> t.0 != s }

// stateMap: accept states without transitions are merged into the new initial state 0
ghost function M(a: NFA, s: nat): nat { if s in a.acc && NoOut(a, s) then 0 else s + 1 }

ghost function RT(a: NFA): set<(nat, int, nat)> {
  // (1) copy every transition, renumbered
  (set t | t in a.T :: (M(a, t.0), t.1, M(a, t.2)))
  // (2) transitions of the old initial state, from the new initial state
  + (set t | t in a.T && t.0 == 0 :: (0, t.1, M(a, t.2)))
  // (3) transitions of the old initial state, from every (non-merged) accept state
  + (set s, t | s in a.acc && M(a, s) != 0 && t in a.T && t.0 == 0 :: (M(a, s), t.1, M(a, t.2)))
}

ghost function RAcc(a: NFA): set<nat> { {0} + (set s | s in a.acc :: M(a, s)) }

// Java: if (a.getNumStates() == 0) return a;
//       if (a.isAccept(0) && a.getAcceptStates().cardinality() == 1) return a;
ghost function Repeat(a: NFA): NFA {
  if a.n == 0 then a
  else if 0 in a.acc then a
  else NFA(a.n + 1, RAcc(a), RT(a))
}

// ---------------------------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------------------------
lemma MInj(a: NFA, s: nat, s2: nat)
  requires M(a, s) == M(a, s2) && M(a, s) != 0
  ensures s == s2
{}

lemma PathPrefix(T: set<(nat, int, nat)>, p: seq<nat>, w: seq<int>, k: nat)
  requires IsPath(T, p, w) && k <= |w|
  ensures IsPath(T, p[..k + 1], w[..k])
{}

lemma PathSuffix(T: set<(nat, int, nat)>, p: seq<nat>, w: seq<int>, k: nat)
  requires IsPath(T, p, w) && k <= |w|
  ensures IsPath(T, p[k..], w[k..])
{
  forall i | 0 <= i < |w[k..]| ensures (p[k..][i], w[k..][i], p[k..][i + 1]) in T {
    assert p[k..][i] == p[k + i] && p[k..][i + 1] == p[k + i + 1] && w[k..][i] == w[k + i];
  }
}

lemma PathConcat(T: set<(nat, int, nat)>, p1: seq<nat>, w1: seq<int>, p2: seq<nat>, w2: seq<int>)
  requires IsPath(T, p1, w1) && IsPath(T, p2, w2) && p1[|w1|] == p2[0]
  ensures IsPath(T, p1 + p2[1..], w1 + w2)
{
  var p := p1 + p2[1..];
  var w := w1 + w2;
  forall i | 0 <= i < |w| ensures (p[i], w[i], p[i + 1]) in T {
    if i < |w1| {
      assert p[i] == p1[i] && w[i] == w1[i];
      if i + 1 < |p1| { assert p[i + 1] == p1[i + 1]; } else { assert p[i + 1] == p2[1]; }
    } else {
      var j := i - |w1|;
      assert w[i] == w2[j];
      assert p[i] == p2[j] by { if j == 0 { assert p[i] == p1[|w1|]; } else { assert p[i] == p2[1..][j - 1]; } }
      assert p[i + 1] == p2[j + 1] by { assert p[i + 1] == p2[1..][j]; }
    }
  }
}

lemma StarAppend(a: NFA, pre: seq<int>, r: seq<int>)
  requires InStar(a, pre) && Accepts(a, r)
  ensures InStar(a, pre + r)
  decreases |pre|
{
  if |pre| == 0 {
    assert pre + r == r;
    if |r| > 0 {
      assert r[..|r|] == r && r[|r|..] == [];
      assert InStar(a, r[|r|..]);
    }
  } else {
    var k :| 1 <= k <= |pre| && Accepts(a, pre[..k]) && InStar(a, pre[k..]);
    StarAppend(a, pre[k..], r);
    assert (pre + r)[..k] == pre[..k];
    assert (pre + r)[k..] == pre[k..] + r;
  }
}

// ---------------------------------------------------------------------------------------------
// Direction 1: every word accepted by Repeat(a) is in L(a)*
// Invariant after reading w along an R-path ending in R-state x:
//   w = w[..j] + w[j..] with w[..j] in L(a)*, and either (j == |w| and x == 0) or some a-path from
//   0 reads w[j..] and ends in an a-state s with M(s) == x.
// ---------------------------------------------------------------------------------------------
ghost predicate Inv(a: NFA, w: seq<int>, x: nat) {
  exists j :: 0 <= j <= |w| && InStar(a, w[..j]) &&
    ((j == |w| && x == 0) ||
     (exists p :: IsPath(a.T, p, w[j..]) && p[0] == 0 && M(a, p[|w| - j]) == x))
}

lemma InvStep(a: NFA, w: seq<int>, x: nat, c: int, y: nat)
  requires a.n > 0 && !(0 in a.acc && a.acc == {0})
  requires Inv(a, w, x) && (x, c, y) in RT(a)
  ensures Inv(a, w + [c], y)
{
  var W := w + [c];
  assert W[..|w|] == w;
  var j :| 0 <= j <= |w| && InStar(a, w[..j]) &&
    ((j == |w| && x == 0) || (exists p :: IsPath(a.T, p, w[j..]) && p[0] == 0 && M(a, p[|w| - j]) == x));
  // Restart helper: from InStar(w[..j]) and an accepted current segment, w is in L(a)*
  if (x, c, y) in (set t | t in a.T :: (M(a, t.0), t.1, M(a, t.2))) {
    var t :| t in a.T && (M(a, t.0), t.1, M(a, t.2)) == (x, c, y);
    assert !NoOut(a, t.0);
    assert x != 0;
    var p :| IsPath(a.T, p, w[j..]) && p[0] == 0 && M(a, p[|w| - j]) == x;
    MInj(a, p[|w| - j], t.0);
    var p2 := p + [t.2];
    assert W[j..] == w[j..] + [c];
    assert IsPath(a.T, p2, W[j..]) by {
      forall i | 0 <= i < |W[j..]| ensures (p2[i], W[j..][i], p2[i + 1]) in a.T {
        if i < |w[j..]| { assert p2[i] == p[i] && p2[i + 1] == p[i + 1] && W[j..][i] == w[j..][i]; }
        else { assert p2[i] == t.0 && W[j..][i] == c && p2[i + 1] == t.2; }
      }
    }
    assert W[..j] == w[..j];
    assert p2[|W| - j] == t.2;
    assert IsPath(a.T, p2, W[j..]) && p2[0] == 0 && M(a, p2[|W| - j]) == y;
    assert 0 <= j <= |W| && InStar(a, W[..j]) &&
      (exists p :: IsPath(a.T, p, W[j..]) && p[0] == 0 && M(a, p[|W| - j]) == y);
    assert Inv(a, W, y);
  } else {
    // groups (2) and (3): a restart with an initial transition (0, c, d) of a
    var d: nat;
    if (x, c, y) in (set t | t in a.T && t.0 == 0 :: (0, t.1, M(a, t.2))) {
      var t :| t in a.T && t.0 == 0 && (0, t.1, M(a, t.2)) == (x, c, y);
      d := t.2;
      assert (0, c, d) in a.T;
    } else {
      var s, t :| s in a.acc && M(a, s) != 0 && t in a.T && t.0 == 0 && (M(a, s), t.1, M(a, t.2)) == (x, c, y);
      d := t.2;
      assert (0, c, d) in a.T;
    }
    assert (0, c, d) in a.T && y == M(a, d);
    // the word read so far is in L(a)*
    assert InStar(a, w) by {
      if j == |w| && x == 0 {
        assert w[..j] == w;
      } else {
        var p :| IsPath(a.T, p, w[j..]) && p[0] == 0 && M(a, p[|w| - j]) == x;
        var e := p[|w| - j];
        // x is either 0 (then e is a merged accept state) or M(s) for an accept s (then e == s)
        if (x, c, y) in (set t | t in a.T && t.0 == 0 :: (0, t.1, M(a, t.2))) {
          assert x == 0;
          assert e in a.acc;
        } else {
          var s, t :| s in a.acc && M(a, s) != 0 && t in a.T && t.0 == 0 && (M(a, s), t.1, M(a, t.2)) == (x, c, y);
          MInj(a, e, s);
        }
        assert Accepts(a, w[j..]);
        StarAppend(a, w[..j], w[j..]);
        assert w[..j] + w[j..] == w;
      }
    }
    var p2: seq<nat> := [0, d];
    assert W[|w|..] == [c];
    assert IsPath(a.T, p2, W[|w|..]);
    assert M(a, p2[|W| - |w|]) == y;
    assert InStar(a, W[..|w|]);
    assert 0 <= |w| <= |W| && InStar(a, W[..|w|]) &&
      (exists p :: IsPath(a.T, p, W[|w|..]) && p[0] == 0 && M(a, p[|W| - |w|]) == y);
  }
}

lemma Forward(a: NFA, P: seq<nat>, w: seq<int>)
  requires a.n > 0 && !(0 in a.acc && a.acc == {0})
  requires IsPath(RT(a), P, w) && P[0] == 0
  ensures Inv(a, w, P[|w|])
  decreases |w|
{
  if |w| == 0 {
    assert w[..0] == [];
    assert InStar(a, w[..0]);
  } else {
    var w0 := w[..|w| - 1];
    PathPrefix(RT(a), P, w, |w| - 1);
    Forward(a, P[..|w|], w0);
    assert P[..|w|][|w0|] == P[|w| - 1];
    assert (P[|w| - 1], w[|w| - 1], P[|w|]) in RT(a);
    InvStep(a, w0, P[|w| - 1], w[|w| - 1], P[|w|]);
    assert w0 + [w[|w| - 1]] == w;
  }
}

lemma Soundness(a: NFA, w: seq<int>)
  requires a.n > 0 && Accepts(Repeat(a), w)
  ensures InStar(a, w)
{
  if 0 in a.acc && a.acc == {0} {
    ShortcutSound(a, w);
  } else {
    var R := Repeat(a);
    var P :| IsPath(R.T, P, w) && P[0] == 0 && P[|w|] in R.acc;
    Forward(a, P, w);
    var x := P[|w|];
    var j :| 0 <= j <= |w| && InStar(a, w[..j]) &&
      ((j == |w| && x == 0) || (exists p :: IsPath(a.T, p, w[j..]) && p[0] == 0 && M(a, p[|w| - j]) == x));
    if j == |w| && x == 0 {
      assert w[..j] == w;
    } else {
      var p :| IsPath(a.T, p, w[j..]) && p[0] == 0 && M(a, p[|w| - j]) == x;
      var e := p[|w| - j];
      if x == 0 {
        assert e in a.acc;
      } else {
        var s :| s in a.acc && M(a, s) == x;
        MInj(a, e, s);
      }
      assert Accepts(a, w[j..]);
      StarAppend(a, w[..j], w[j..]);
      assert w[..j] + w[j..] == w;
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Direction 2: every word of L(a)* is accepted by Repeat(a)
// ---------------------------------------------------------------------------------------------
ghost predicate Restart(a: NFA, q: nat) { q == 0 || exists s :: s in a.acc && M(a, s) == q }

lemma RestartPath(a: NFA, q: nat, p: seq<nat>, u: seq<int>) returns (P: seq<nat>)
  requires a.n > 0 && Restart(a, q) && |u| >= 1 && IsPath(a.T, p, u) && p[0] == 0
  ensures IsPath(RT(a), P, u) && P[0] == q && P[|u|] == M(a, p[|u|])
{
  P := [q] + seq(|u|, i requires 0 <= i < |u| => M(a, p[i + 1]));
  forall i | 0 <= i < |u| ensures (P[i], u[i], P[i + 1]) in RT(a) {
    var tr := (p[i], u[i], p[i + 1]);
    assert tr in a.T;
    if i == 0 {
      assert p[0] == 0;
      if q == 0 {
        assert (0, tr.1, M(a, tr.2)) in (set t | t in a.T && t.0 == 0 :: (0, t.1, M(a, t.2)));
      } else {
        var s0 :| s0 in a.acc && M(a, s0) == q;
        assert (M(a, s0), tr.1, M(a, tr.2)) in
          (set s, t | s in a.acc && M(a, s) != 0 && t in a.T && t.0 == 0 :: (M(a, s), t.1, M(a, t.2)));
      }
    } else {
      assert P[i] == M(a, p[i]);
      assert (M(a, tr.0), tr.1, M(a, tr.2)) in (set t | t in a.T :: (M(a, t.0), t.1, M(a, t.2)));
    }
  }
}

lemma Completeness(a: NFA, q: nat, v: seq<int>)
  requires a.n > 0 && !(0 in a.acc && a.acc == {0})
  requires Restart(a, q) && InStar(a, v)
  ensures AcceptsFrom(Repeat(a), q, v)
  decreases |v|
{
  var R := Repeat(a);
  if |v| == 0 {
    assert q in RAcc(a);
    assert IsPath(R.T, [q], v);
  } else {
    var k :| 1 <= k <= |v| && Accepts(a, v[..k]) && InStar(a, v[k..]);
    var p :| IsPath(a.T, p, v[..k]) && p[0] == 0 && p[|v[..k]|] in a.acc;
    var P1 := RestartPath(a, q, p, v[..k]);
    var q2 := M(a, p[k]);
    assert p[k] in a.acc && M(a, p[k]) == q2;
    assert Restart(a, q2);
    Completeness(a, q2, v[k..]);
    var P2 :| IsPath(R.T, P2, v[k..]) && P2[0] == q2 && P2[|v[k..]|] in R.acc;
    PathConcat(R.T, P1, v[..k], P2, v[k..]);
    assert v[..k] + v[k..] == v;
    var P := P1 + P2[1..];
    assert P[|v|] == P2[|v[k..]|] by {
      if |v[k..]| == 0 { assert P[|v|] == P1[k]; } else { assert P[|v|] == P2[1..][|v[k..]| - 1]; }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// The short-cut "state 0 is the only accept state => return a"
// ---------------------------------------------------------------------------------------------
lemma ShortcutSound(a: NFA, w: seq<int>)
  requires a.n > 0 && a.acc == {0} && Accepts(a, w)
  ensures InStar(a, w)
{
  if |w| > 0 {
    assert w[..|w|] == w && w[|w|..] == [];
    assert InStar(a, w[|w|..]);
  }
}

lemma ShortcutComplete(a: NFA, w: seq<int>)
  requires a.n > 0 && a.acc == {0} && InStar(a, w)
  ensures Accepts(a, w)
  decreases |w|
{
  if |w| == 0 {
    assert IsPath(a.T, [0], w);
  } else {
    var k :| 1 <= k <= |w| && Accepts(a, w[..k]) && InStar(a, w[k..]);
    ShortcutComplete(a, w[k..]);
    var p1 :| IsPath(a.T, p1, w[..k]) && p1[0] == 0 && p1[|w[..k]|] in a.acc;
    var p2 :| IsPath(a.T, p2, w[k..]) && p2[0] == 0 && p2[|w[k..]|] in a.acc;
    PathConcat(a.T, p1, w[..k], p2, w[k..]);
    assert w[..k] + w[k..] == w;
    var p := p1 + p2[1..];
    assert p[0] == 0;
    assert p[|w|] in a.acc by {
      if |w[k..]| == 0 { assert p[|w|] == p1[k]; } else { assert p[|w|] == p2[1..][|w[k..]| - 1]; }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// MAIN THEOREM: for every automaton with >= 1 state, L(repeat(a)) = L(a)*
// ---------------------------------------------------------------------------------------------
lemma RepeatCorrect(a: NFA, w: seq<int>)
  requires a.n > 0
  ensures Accepts(Repeat(a), w) <==> InStar(a, w)
{
  if Accepts(Repeat(a), w) { Soundness(a, w); }
  if InStar(a, w) {
    if 0 in a.acc && a.acc == {0} { ShortcutComplete(a, w); }
    else {
      Completeness(a, 0, w);
      assert Repeat(a).n > 0;
    }
  }
}

// ... and the precondition is necessary: the zero-state automaton (empty language) is returned
// unchanged, which rejects "" although "" is in (empty)* (known bug, Operations.java:196-199).
lemma RepeatEmptyCounterexample()
  ensures InStar(NFA(0, {}, {}), []) && !Accepts(Repeat(NFA(0, {}, {})), [])
{}
