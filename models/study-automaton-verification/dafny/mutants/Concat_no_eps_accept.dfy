// Unbounded Dafny proof that Operations.concatenate(List.of(A, B))
// (lucene/core/src/java/org/apache/lucene/util/automaton/Operations.java:73-139, main 7cecf79523)
// accepts exactly L(A)·L(B) for all automata A, B with at least one state.
// The construction places A's states at 0..nA-1 and B's at nA..nA+nB-1, copies all transitions,
// adds a "virtual epsilon" from every accept state s of A by copying B's initial transitions to s,
// and keeps s accepting iff B accepts the empty string (the `followA.isAccept(0)` chaining).
// (The zero-state case returns makeEmpty(), trivially correct; removeDeadStates is not modelled.)

datatype NFA = NFA(n: nat, acc: set<nat>, T: set<(nat, int, nat)>)

ghost predicate WF(a: NFA) {
  (forall t :: t in a.T ==> t.0 < a.n && t.2 < a.n) && (forall s :: s in a.acc ==> s < a.n)
}

ghost predicate IsPath(T: set<(nat, int, nat)>, p: seq<nat>, w: seq<int>) {
  |p| == |w| + 1 && forall i :: 0 <= i < |w| ==> (p[i], w[i], p[i + 1]) in T
}

ghost predicate Accepts(a: NFA, w: seq<int>) {
  a.n > 0 && exists p :: IsPath(a.T, p, w) && p[0] == 0 && p[|w|] in a.acc
}

ghost predicate InConcat(a: NFA, b: NFA, w: seq<int>) {
  exists k :: 0 <= k <= |w| && Accepts(a, w[..k]) && Accepts(b, w[k..])
}

ghost function Shift(t: (nat, int, nat), off: nat): (nat, int, nat) { (t.0 + off, t.1, t.2 + off) }

ghost function CT(a: NFA, b: NFA): set<(nat, int, nat)> {
  a.T
  + (set t | t in b.T :: Shift(t, a.n))
  + (set s, t | s in a.acc && t in b.T && t.0 == 0 :: (s, t.1, t.2 + a.n))
}

ghost function CAcc(a: NFA, b: NFA): set<nat> {
  (set s | s in b.acc :: s + a.n)
}

ghost function Concat(a: NFA, b: NFA): NFA { NFA(a.n + b.n, CAcc(a, b), CT(a, b)) }

// ------------------------------------------------------------------------------- helpers
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

// ------------------------------------------------------------------------------- completeness
lemma Complete(a: NFA, b: NFA, w: seq<int>)
  requires WF(a) && WF(b) && a.n > 0 && b.n > 0 && InConcat(a, b, w)
  ensures Accepts(Concat(a, b), w)
{
  var k :| 0 <= k <= |w| && Accepts(a, w[..k]) && Accepts(b, w[k..]);
  var u, v := w[..k], w[k..];
  var p :| IsPath(a.T, p, u) && p[0] == 0 && p[|u|] in a.acc;
  var q :| IsPath(b.T, q, v) && q[0] == 0 && q[|v|] in b.acc;
  var C := Concat(a, b);
  if |v| == 0 {
    // B accepts "": the accept state p[k] of A stays accepting
    assert q[0] in b.acc;
    assert p[|u|] in CAcc(a, b);
    assert u == w;
    assert IsPath(C.T, p, w) by {
      forall i | 0 <= i < |w| ensures (p[i], w[i], p[i + 1]) in C.T { assert (p[i], u[i], p[i + 1]) in a.T; }
    }
  } else {
    // A-path to p[k], then B's first transition copied onto p[k], then the shifted B-path
    var P := p + seq(|v|, i requires 0 <= i < |v| => q[i + 1] + a.n);
    assert |P| == |w| + 1;
    forall i | 0 <= i < |w| ensures (P[i], w[i], P[i + 1]) in C.T {
      if i < k {
        assert P[i] == p[i] && w[i] == u[i];
        if i + 1 < |p| { assert P[i + 1] == p[i + 1]; }
        assert (p[i], u[i], p[i + 1]) in a.T;
      } else if i == k {
        assert P[i] == p[k] && w[i] == v[0] && P[i + 1] == q[1] + a.n;
        var t := (q[0], v[0], q[1]);
        assert t in b.T && t.0 == 0;
        assert (p[k], t.1, t.2 + a.n) in (set s, t | s in a.acc && t in b.T && t.0 == 0 :: (s, t.1, t.2 + a.n));
      } else {
        var j := i - k;
        assert P[i] == q[j] + a.n && P[i + 1] == q[j + 1] + a.n && w[i] == v[j];
        var t := (q[j], v[j], q[j + 1]);
        assert t in b.T;
        assert Shift(t, a.n) in (set t | t in b.T :: Shift(t, a.n));
      }
    }
    assert P[|w|] == q[|v|] + a.n;
    assert P[|w|] in CAcc(a, b);
    assert IsPath(C.T, P, w) && P[0] == 0 && P[|w|] in C.acc;
  }
}

// ------------------------------------------------------------------------------- soundness
// A path that starts in the B part (state >= nA) stays there and is a shifted B-path.
lemma StaysInB(a: NFA, b: NFA, P: seq<nat>, w: seq<int>)
  requires WF(a) && WF(b) && IsPath(CT(a, b), P, w) && P[0] >= a.n
  ensures forall i :: 0 <= i <= |w| ==> P[i] >= a.n
  ensures IsPath(b.T, seq(|P|, i requires 0 <= i < |P| => P[i] - a.n), w)
  decreases |w|
{
  var Q := seq(|P|, i requires 0 <= i < |P| => P[i] - a.n);
  if |w| > 0 {
    var t := (P[0], w[0], P[1]);
    assert t in CT(a, b);
    if t in a.T { assert false; }  // A transitions have source < nA
    if t in (set s, t | s in a.acc && t in b.T && t.0 == 0 :: (s, t.1, t.2 + a.n)) {
      var s, tb :| s in a.acc && tb in b.T && tb.0 == 0 && (s, tb.1, tb.2 + a.n) == t;
      assert false; // source s < nA
    }
    var tb :| tb in b.T && Shift(tb, a.n) == t;
    PathSuffix(CT(a, b), P, w, 1);
    StaysInB(a, b, P[1..], w[1..]);
    var Q1 := seq(|P[1..]|, i requires 0 <= i < |P[1..]| => P[1..][i] - a.n);
    forall i | 0 <= i <= |w| ensures P[i] >= a.n {
      if i > 0 { assert P[i] == P[1..][i - 1]; }
    }
    forall i | 0 <= i < |w| ensures (Q[i], w[i], Q[i + 1]) in b.T {
      if i == 0 { assert (Q[0], w[0], Q[1]) == tb; }
      else { assert Q[i] == Q1[i - 1] && Q[i + 1] == Q1[i] && w[i] == w[1..][i - 1]; }
    }
  }
}

// classification of the transitions of the concatenation by the parts of their endpoints
lemma InA(a: NFA, b: NFA, t: (nat, int, nat))
  requires WF(a) && WF(b) && t in CT(a, b) && t.0 < a.n && t.2 < a.n
  ensures t in a.T
{
  if t in (set t | t in b.T :: Shift(t, a.n)) { var x :| x in b.T && Shift(x, a.n) == t; assert false; }
  if t in (set s, t | s in a.acc && t in b.T && t.0 == 0 :: (s, t.1, t.2 + a.n)) {
    var s2, x :| s2 in a.acc && x in b.T && x.0 == 0 && (s2, x.1, x.2 + a.n) == t; assert false;
  }
}

lemma Crossing(a: NFA, b: NFA, t: (nat, int, nat)) returns (s: nat, tb: (nat, int, nat))
  requires WF(a) && WF(b) && t in CT(a, b) && t.0 < a.n && t.2 >= a.n
  ensures s in a.acc && tb in b.T && tb.0 == 0 && t == (s, tb.1, tb.2 + a.n)
{
  if t in a.T { assert false; }
  if t in (set t | t in b.T :: Shift(t, a.n)) { var x :| x in b.T && Shift(x, a.n) == t; assert false; }
  s, tb :| s in a.acc && tb in b.T && tb.0 == 0 && (s, tb.1, tb.2 + a.n) == t;
}

lemma SoundStayA(a: NFA, b: NFA, w: seq<int>, P: seq<nat>)
  requires WF(a) && WF(b) && a.n > 0 && b.n > 0
  requires IsPath(CT(a, b), P, w) && P[0] == 0 && P[|w|] in CAcc(a, b)
  requires forall i :: 0 <= i <= |w| ==> P[i] < a.n
  ensures InConcat(a, b, w)
{
  forall i | 0 <= i < |w| ensures (P[i], w[i], P[i + 1]) in a.T { InA(a, b, (P[i], w[i], P[i + 1])); }
  var e := P[|w|];
  if e in (set s | s in b.acc :: s + a.n) { var s :| s in b.acc && s + a.n == e; assert false; }
  assert 0 in b.acc && e in a.acc;
  assert w[..|w|] == w && w[|w|..] == [];
  assert IsPath(b.T, [0], []);
  assert Accepts(b, w[|w|..]);
  assert Accepts(a, w[..|w|]);
}

lemma SoundCross(a: NFA, b: NFA, w: seq<int>, P: seq<nat>, i: nat)
  requires WF(a) && WF(b) && a.n > 0 && b.n > 0
  requires IsPath(CT(a, b), P, w) && P[0] == 0 && P[|w|] in CAcc(a, b)
  requires 1 <= i <= |w| && P[i] >= a.n && forall j :: 0 <= j < i ==> P[j] < a.n
  ensures InConcat(a, b, w)
{
  var k := i - 1;
  var s, tb := Crossing(a, b, (P[k], w[k], P[i]));
  // u = w[..k] is accepted by A along P[..k+1]
  var U := P[..k + 1];
  forall j | 0 <= j < k ensures (U[j], w[..k][j], U[j + 1]) in a.T {
    assert U[j] == P[j] && U[j + 1] == P[j + 1] && w[..k][j] == w[j];
    InA(a, b, (P[j], w[j], P[j + 1]));
  }
  assert IsPath(a.T, U, w[..k]) && U[0] == 0 && U[k] == s;
  assert Accepts(a, w[..k]);
  // v = w[k..] is accepted by B along [0] + (P[i..] shifted back)
  PathSuffix(CT(a, b), P, w, i);
  StaysInB(a, b, P[i..], w[i..]);
  var Q := seq(|P[i..]|, j requires 0 <= j < |P[i..]| => P[i..][j] - a.n);
  assert IsPath(b.T, Q, w[i..]);
  var V := [0] + Q;
  var v := w[k..];
  assert |V| == |v| + 1;
  forall j | 0 <= j < |v| ensures (V[j], v[j], V[j + 1]) in b.T {
    if j == 0 { assert V[0] == 0 && v[0] == w[k] == tb.1 && V[1] == Q[0] == P[i] - a.n == tb.2; }
    else { assert V[j] == Q[j - 1] && V[j + 1] == Q[j] && v[j] == w[i..][j - 1]; }
  }
  var e := P[|w|];
  assert e == P[i..][|w| - i] && e >= a.n;
  if e in (if 0 in b.acc then a.acc else {}) { assert e < a.n; }
  var sb :| sb in b.acc && sb + a.n == e;
  assert V[|v|] == Q[|w| - i] == sb;
  assert Accepts(b, v);
}

lemma Sound(a: NFA, b: NFA, w: seq<int>)
  requires WF(a) && WF(b) && a.n > 0 && b.n > 0 && Accepts(Concat(a, b), w)
  ensures InConcat(a, b, w)
{
  var P :| IsPath(CT(a, b), P, w) && P[0] == 0 && P[|w|] in CAcc(a, b);
  if forall i :: 0 <= i <= |w| ==> P[i] < a.n {
    SoundStayA(a, b, w, P);
  } else {
    var m :| 0 <= m <= |w| && P[m] >= a.n;
    FirstIndex(P, a.n, m);
    var i :| 0 <= i <= m && P[i] >= a.n && forall j :: 0 <= j < i ==> P[j] < a.n;
    SoundCross(a, b, w, P, i);
  }
}

lemma FirstIndex(P: seq<nat>, bound: nat, m: nat)
  requires 0 <= m < |P| && P[m] >= bound
  ensures exists i :: 0 <= i <= m && P[i] >= bound && forall j :: 0 <= j < i ==> P[j] < bound
  decreases m
{
  if m > 0 && exists j :: 0 <= j < m && P[j] >= bound {
    var j :| 0 <= j < m && P[j] >= bound;
    FirstIndex(P, bound, j);
  }
}

// ------------------------------------------------------------------------------- theorem
lemma ConcatCorrect(a: NFA, b: NFA, w: seq<int>)
  requires WF(a) && WF(b) && a.n > 0 && b.n > 0
  ensures Accepts(Concat(a, b), w) <==> InConcat(a, b, w)
{
  if Accepts(Concat(a, b), w) { Sound(a, b, w); }
  if InConcat(a, b, w) { Complete(a, b, w); }
}
