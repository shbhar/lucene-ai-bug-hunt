// Formal semantics of a Lucene DFA (Automaton with sorted, non-overlapping transitions) and an
// unbounded proof that a corrected Operations.isTotal(a, lo, hi) is SOUND and COMPLETE, together
// with machine-checked counterexamples showing that the algorithm on main 7cecf79523
// (Operations.java:856-879) is neither sound (dead states) nor complete (out-of-range labels).

datatype Tr = Tr(min: int, max: int, dest: nat)

datatype DFA = DFA(n: nat, acc: set<nat>, tr: seq<seq<Tr>>)

// Lucene's Automaton invariant after finishState(): per state, transitions sorted by min, and (for a
// deterministic automaton) pairwise disjoint; every dest is a valid state.
predicate SortedDisjoint(ts: seq<Tr>) {
  (forall i :: 0 <= i < |ts| ==> ts[i].min <= ts[i].max) &&
  (forall i, j :: 0 <= i < j < |ts| ==> ts[i].max < ts[j].min)
}

predicate WF(a: DFA) {
  a.n > 0 && |a.tr| == a.n &&
  (forall s :: 0 <= s < a.n ==> SortedDisjoint(a.tr[s])) &&
  (forall s, i :: 0 <= s < a.n && 0 <= i < |a.tr[s]| ==> a.tr[s][i].dest < a.n)
}

ghost predicate Covered(ts: seq<Tr>, c: int) { exists i :: 0 <= i < |ts| && ts[i].min <= c <= ts[i].max }

// ---------------------------------------------------------------------------------------------
// Semantics: run of a word; None = rejected by a missing transition.
// ---------------------------------------------------------------------------------------------
function StepIdx(ts: seq<Tr>, c: int): int
  ensures -1 <= StepIdx(ts, c) < |ts|
  ensures StepIdx(ts, c) >= 0 ==> ts[StepIdx(ts, c)].min <= c <= ts[StepIdx(ts, c)].max
  ensures StepIdx(ts, c) == -1 ==> !Covered(ts, c)
{
  if |ts| == 0 then -1
  else if ts[0].min <= c <= ts[0].max then 0
  else
    var k := StepIdx(ts[1..], c);
    if k == -1 then (assert forall i :: 1 <= i < |ts| ==> ts[i] == ts[1..][i-1]; -1) else k + 1
}

function Run(a: DFA, s: nat, w: seq<int>): (r: int)
  requires WF(a) && s < a.n
  ensures -1 <= r < a.n
  decreases |w|
{
  if |w| == 0 then s
  else
    var k := StepIdx(a.tr[s], w[0]);
    if k == -1 then -1 else Run(a, a.tr[s][k].dest, w[1..])
}

predicate Accepts(a: DFA, w: seq<int>) requires WF(a) { var r := Run(a, 0, w); r >= 0 && r in a.acc }

predicate Over(w: seq<int>, lo: int, hi: int) { forall i :: 0 <= i < |w| ==> lo <= w[i] <= hi }

// SPEC (javadoc): "Returns true if the given automaton accepts all strings for the specified
// min/max range of the alphabet."
ghost predicate Total(a: DFA, lo: int, hi: int) requires WF(a) {
  forall w :: Over(w, lo, hi) ==> Accepts(a, w)
}

// States reachable from 0 by words over the alphabet [lo, hi]
ghost predicate ReachBy(a: DFA, s: nat, lo: int, hi: int) requires WF(a) {
  exists u :: Over(u, lo, hi) && Run(a, 0, u) == s
}

// ---------------------------------------------------------------------------------------------
// The interval-gap coverage scan.  FixedScan is the corrected loop; LuceneScan is main's loop:
//   previousLabel = minAlphabet - 1;
//   for each transition: if (min > previousLabel + 1) return false; previousLabel = max;
//   return previousLabel >= maxAlphabet
// ---------------------------------------------------------------------------------------------
function FixedScan(ts: seq<Tr>, lo: int, hi: int, prev: int): bool decreases |ts| {
  if prev >= hi then true
  else if |ts| == 0 then false
  else if ts[0].max < lo then FixedScan(ts[1..], lo, hi, prev)
  else if ts[0].min > prev + 1 then false
  else FixedScan(ts[1..], lo, hi, if ts[0].max > prev then ts[0].max else prev)
}

function LuceneScan(ts: seq<Tr>, hi: int, prev: int): bool decreases |ts| {
  if |ts| == 0 then prev >= hi
  else if ts[0].min > prev + 1 then false
  else LuceneScan(ts[1..], hi, ts[0].max)
}

lemma SubseqSorted(ts: seq<Tr>)
  requires |ts| > 0 && SortedDisjoint(ts)
  ensures SortedDisjoint(ts[1..])
{
  forall i, j | 0 <= i < j < |ts[1..]| ensures ts[1..][i].max < ts[1..][j].min {
    assert ts[1..][i] == ts[i+1] && ts[1..][j] == ts[j+1];
  }
}

// Main coverage lemma: given [lo, prev] already covered, FixedScan decides coverage of (prev, hi].
lemma FixedScanCorrect(ts: seq<Tr>, lo: int, hi: int, prev: int)
  requires SortedDisjoint(ts)
  requires prev >= lo - 1
  // all remaining transitions start after what earlier (consumed) transitions covered, except
  // those entirely below lo, which are skipped
  requires forall i :: 0 <= i < |ts| ==> ts[i].max > prev || ts[i].max < lo
  ensures FixedScan(ts, lo, hi, prev) <==> (forall c :: prev < c <= hi ==> Covered(ts, c))
  decreases |ts|
{
  if prev >= hi {
  } else if |ts| == 0 {
    assert !Covered(ts, prev + 1);
  } else {
    SubseqSorted(ts);
    var rest := ts[1..];
    assert forall i :: 1 <= i < |ts| ==> ts[i] == rest[i-1];
    if ts[0].max < lo {
      FixedScanCorrect(rest, lo, hi, prev);
      forall c | prev < c <= hi ensures Covered(ts, c) <==> Covered(rest, c) {
        if Covered(ts, c) {
          var i :| 0 <= i < |ts| && ts[i].min <= c <= ts[i].max;
          if i == 0 { assert c <= ts[0].max < lo <= prev + 1; assert false; }
          assert rest[i-1] == ts[i];
        }
        if Covered(rest, c) { var i :| 0 <= i < |rest| && rest[i].min <= c <= rest[i].max; assert ts[i+1] == rest[i]; }
      }
    } else if ts[0].min > prev + 1 {
      // prev+1 is not covered: ts[0] starts above it and all later ones start even higher
      assert !Covered(ts, prev + 1) by {
        if Covered(ts, prev + 1) {
          var i :| 0 <= i < |ts| && ts[i].min <= prev + 1 <= ts[i].max;
          if i > 0 { assert ts[0].max < ts[i].min; }
        }
      }
    } else {
      var np := if ts[0].max > prev then ts[0].max else prev;
      assert forall i :: 0 <= i < |rest| ==> rest[i].max > np || rest[i].max < lo by {
        forall i | 0 <= i < |rest| ensures rest[i].max > np || rest[i].max < lo {
          assert rest[i] == ts[i+1];
          assert ts[0].max < ts[i+1].min;
        }
      }
      FixedScanCorrect(rest, lo, hi, np);
      forall c | prev < c <= hi ensures Covered(ts, c) <==> (c <= np || Covered(rest, c)) {
        if c <= np { assert ts[0].min <= prev + 1 <= c; }
        if Covered(rest, c) { var i :| 0 <= i < |rest| && rest[i].min <= c <= rest[i].max; assert ts[i+1] == rest[i]; }
        if Covered(ts, c) && c > np {
          var i :| 0 <= i < |ts| && ts[i].min <= c <= ts[i].max;
          if i == 0 { assert false; }
          assert rest[i-1] == ts[i];
        }
      }
    }
  }
}

lemma CoversFromScan(ts: seq<Tr>, lo: int, hi: int)
  requires SortedDisjoint(ts)
  ensures FixedScan(ts, lo, hi, lo - 1) <==> (forall c :: lo <= c <= hi ==> Covered(ts, c))
{
  FixedScanCorrect(ts, lo, hi, lo - 1);
}

// ---------------------------------------------------------------------------------------------
// The corrected isTotal: every state reachable over [lo,hi] is accepting and covers [lo,hi].
// (The Java patch computes this set by BFS over in-alphabet transitions: FvFixes.isTotalFixed.)
// ---------------------------------------------------------------------------------------------
ghost predicate FixedIsTotal(a: DFA, lo: int, hi: int) requires WF(a) {
  forall s :: 0 <= s < a.n && ReachBy(a, s, lo, hi) ==> s in a.acc && FixedScan(a.tr[s], lo, hi, lo - 1)
}

lemma RunAppend(a: DFA, s: nat, u: seq<int>, c: int)
  requires WF(a) && s < a.n
  ensures Run(a, s, u + [c]) == (var r := Run(a, s, u); if r == -1 then -1 else
            (var k := StepIdx(a.tr[r], c); if k == -1 then -1 else a.tr[r][k].dest))
  decreases |u|
{
  if |u| == 0 {
    assert u + [c] == [c];
    assert [c][1..] == [];
  } else {
    assert (u + [c])[0] == u[0] && (u + [c])[1..] == u[1..] + [c];
    var k := StepIdx(a.tr[s], u[0]);
    if k != -1 { RunAppend(a, a.tr[s][k].dest, u[1..], c); }
  }
}

// SOUNDNESS (unbounded): FixedIsTotal ==> Total
lemma FixedSound(a: DFA, lo: int, hi: int)
  requires WF(a) && FixedIsTotal(a, lo, hi)
  ensures Total(a, lo, hi)
{
  forall w | Over(w, lo, hi) ensures Accepts(a, w) { SoundPrefix(a, lo, hi, w); }
}

lemma SoundPrefix(a: DFA, lo: int, hi: int, w: seq<int>)
  requires WF(a) && FixedIsTotal(a, lo, hi) && Over(w, lo, hi)
  ensures Run(a, 0, w) >= 0 && ReachBy(a, Run(a, 0, w), lo, hi) && Run(a, 0, w) in a.acc
  decreases |w|
{
  if |w| == 0 {
    assert Over([], lo, hi) && Run(a, 0, []) == 0;
  } else {
    var u := w[..|w|-1];
    var c := w[|w|-1];
    assert w == u + [c];
    assert Over(u, lo, hi);
    SoundPrefix(a, lo, hi, u);
    var s := Run(a, 0, u);
    CoversFromScan(a.tr[s], lo, hi);
    assert Covered(a.tr[s], c);
    RunAppend(a, 0, u, c);
    var k := StepIdx(a.tr[s], c);
    assert k != -1;
    assert Run(a, 0, w) == a.tr[s][k].dest;
    assert ReachBy(a, Run(a, 0, w), lo, hi);
  }
}

// COMPLETENESS (unbounded): Total ==> FixedIsTotal
lemma FixedComplete(a: DFA, lo: int, hi: int)
  requires WF(a) && Total(a, lo, hi)
  ensures FixedIsTotal(a, lo, hi)
{
  forall s | 0 <= s < a.n && ReachBy(a, s, lo, hi) ensures s in a.acc && FixedScan(a.tr[s], lo, hi, lo - 1) {
    var u :| Over(u, lo, hi) && Run(a, 0, u) == s;
    assert Accepts(a, u);
    CoversFromScan(a.tr[s], lo, hi);
    forall c | lo <= c <= hi ensures Covered(a.tr[s], c) {
      assert Over(u + [c], lo, hi);
      assert Accepts(a, u + [c]);
      RunAppend(a, 0, u, c);
      var k := StepIdx(a.tr[s], c);
      assert k != -1;
      assert a.tr[s][k].min <= c <= a.tr[s][k].max;
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Lucene main's algorithm, Operations.java:856-879: iterates getLiveStates(a) = (reachable by ANY
// label) AND (can reach an accept state), and uses LuceneScan.  Machine-checked counterexamples:
// ---------------------------------------------------------------------------------------------
// main's predicate, given the live set it computes
predicate LuceneIsTotal(a: DFA, lo: int, hi: int, live: set<nat>) requires WF(a) {
  live != {} && forall s :: s in live && s < a.n ==> s in a.acc && LuceneScan(a.tr[s], hi, lo - 1)
}
// a state is "live" for getLiveStatesToAccept iff some word leads from it to an accept state
ghost predicate CoReach(a: DFA, s: nat) requires WF(a) && s < a.n { exists w :: Run(a, s, w) >= 0 && Run(a, s, w) in a.acc }

// CE1 (false positive, dead state):  0 --[0..96]--> 1 (dead), 0 --['a'..MAX]--> 0, accept={0}
const MAXCP := 0x10FFFF
const CE1 := DFA(2, {0}, [[Tr(0, 96, 1), Tr(97, MAXCP, 0)], []])

lemma DeadState1(w: seq<int>) ensures WF(CE1) && Run(CE1, 1, w) in {-1, 1} {
  if |w| > 0 { assert StepIdx(CE1.tr[1], w[0]) == -1; }
}

lemma CE1_Lucene_says_total()
  ensures WF(CE1)
  ensures !CoReach(CE1, 1)                      // state 1 is dead, so getLiveStates = {0}
  ensures LuceneIsTotal(CE1, 0, MAXCP, {0})     // main's isTotal returns true
  ensures !Accepts(CE1, [48])                   // but the string "0" is rejected
  ensures !Total(CE1, 0, MAXCP)
{
  forall w ensures !(Run(CE1, 1, w) >= 0 && Run(CE1, 1, w) in CE1.acc) { DeadState1(w); }
  var t0 := CE1.tr[0];
  assert t0[1..] == [Tr(97, MAXCP, 0)] && t0[1..][1..] == [];
  assert LuceneScan([], MAXCP, MAXCP);
  assert LuceneScan([Tr(97, MAXCP, 0)], MAXCP, 96);
  assert LuceneScan(t0, MAXCP, -1);
  assert Run(CE1, 0, [48]) == Run(CE1, 1, []);
  assert Over([48], 0, MAXCP);
}

// CE2 (false negative, label outside [lo,hi]): 0 --a--> 0, 0 --c--> 0, accept={0}; lo=hi='c'
const CE2 := DFA(1, {0}, [[Tr(97, 97, 0), Tr(99, 99, 0)]])

lemma CE2_Lucene_says_not_total()
  ensures WF(CE2)
  ensures !LuceneScan(CE2.tr[0], 99, 98)       // main returns false
  ensures FixedIsTotal(CE2, 99, 99)             // corrected check returns true
  ensures Total(CE2, 99, 99)                    // and the language is total over ['c','c']
{
  assert FixedScan(CE2.tr[0], 99, 99, 98);
  forall s | 0 <= s < CE2.n && ReachBy(CE2, s, 99, 99) ensures s in CE2.acc && FixedScan(CE2.tr[s], 99, 99, 98) {
    assert s == 0;
  }
  FixedSound(CE2, 99, 99);
}
