// Soundness of the floating-point score bounds used by Lucene's dynamic pruning,
// on the abstract rounding model of FpModel.dfy (no associativity assumed).
//
// Code references: lucene/core/src/java/org/apache/lucene/ at apache/lucene main 7cecf79523.
//   util/MathUtil.java:162-194            sumRelativeErrorBound / sumUpperBound
//   search/MaxScoreBulkScorer.java:494-511 partition (non-essential prefix)
//   search/MaxScoreBulkScorer.java:533-546 single-essential "required clause" loop
//   search/ScorerUtil.java:131-164        minRequiredScore / filterCompetitiveHits
//   search/BlockMaxConjunctionBulkScorer.java:69-84,163-196
include "FpModel.dfy"

module Pruning {
  import opened FpModel

  // MathUtil.sumUpperBound(sum, n): n <= 2 -> sum, else (1 + 2*(n-1)*2^-52) * sum  ==  (1 + 4(n-1)U) * sum
  function SumUB(s: real, n: nat): real
  {
    if n <= 2 then s else Rd(C(n) * s)
  }

  // A bound on the number of clauses that keeps the first-order error analysis valid
  // ((n-1)*2^-53 <= 1/16, i.e. n up to ~5.6e14 clauses: no practical restriction).
  predicate SmallN(n: nat) { (n as real) * U <= 1.0 / 16.0 }

  lemma PowMonoExp(b: real, k: nat, K: nat)
    requires 0.0 < b <= 1.0 && k <= K
    ensures Pow(b, K) <= Pow(b, k)
    decreases K - k
  {
    if k < K {
      PowMonoExp(b, k, K - 1);
      PowLe1(b, K - 1);
      MulMonoR(b, 1.0, Pow(b, K - 1));
    }
  }

  lemma PowMonoExpUp(b: real, k: nat, K: nat)
    requires b >= 1.0 && k <= K
    ensures Pow(b, k) <= Pow(b, K)
    decreases K - k
  {
    if k < K {
      PowMonoExpUp(b, k, K - 1);
      PowGe1(b, K - 1);
      MulMonoR(1.0, b, Pow(b, K - 1));
    }
  }

  // The key numeric inequality behind sumUpperBound:  (1+U)^(n-1) <= (1+4(n-1)U) * (1-U)^n * ... for n >= 3
  lemma Margin(n: nat)
    requires n >= 3 && SmallN(n)
    ensures Pow(1.0 + U, n - 1) <= (1.0 + 4.0 * ((n - 1) as real) * U) * Pow(1.0 - U, n + 1)
  {
    var a := ((n - 1) as real) * U;
    PowUpperBound(n - 1);           // (1+U)^(n-1) * (1 - a) <= 1
    PowLowerBound(n + 1);           // (1-U)^(n+1) >= 1 - (n+1)U = 1 - a - 2U
    var p := Pow(1.0 + U, n - 1);
    var q := Pow(1.0 - U, n + 1);
    assert U <= a / 2.0;
    assert a <= 1.0 / 16.0;
    // (1+4a)(1 - a - 2U) >= (1+4a)(1-2a) = 1 + 2a - 8a^2 ;  (1-a)(1 + 2a - 8a^2) >= 1  for a <= 1/16
    var c := 1.0 + 4.0 * a;
    assert c * q >= c * (1.0 - 2.0 * a) by {
      assert q >= 1.0 - a - 2.0 * U;
      assert 1.0 - a - 2.0 * U >= 1.0 - 2.0 * a;
      MulMonoR(1.0 - 2.0 * a, q, c);
    }
    assert (1.0 - a) * (c * (1.0 - 2.0 * a)) >= 1.0 by {
      assert (1.0 - a) * (c * (1.0 - 2.0 * a)) == 1.0 + a - 10.0 * a * a + 8.0 * a * a * a;
      assert a * a <= a / 16.0 by { MulMonoR(a, 1.0 / 16.0, a); }
    }
    PowGe1(1.0 + U, n - 1);
    // p <= 1/(1-a) <= c * q
    assert p * (1.0 - a) <= 1.0;
    assert p * (1.0 - a) <= (1.0 - a) * (c * q) by {
      MulMonoR(c * (1.0 - 2.0 * a), c * q, 1.0 - a);
    }
    assert p <= c * q by {
      var d := 1.0 - a;
      assert d > 0.0;
      assert p * d <= (c * q) * d;
      if p > c * q { MulStrict(c * q, p, d); }
    }
  }


  function C(n: nat): real { 1.0 + 4.0 * ((n - 1) as real) * U }

  lemma UBLower(J: real, n: nat)
    requires n >= 3 && J >= 0.0
    ensures SumUB(J, n) >= C(n) * J * (1.0 - U)
  {
    assert SumUB(J, n) == Rd(C(n) * J);
    assert C(n) >= 1.0;
    assert C(n) * J >= 0.0 by { MulMonoR(0.0, C(n), J); }
    RdErr(C(n) * J);
  }

  lemma Sum2(xs: seq<real>)
    requires |xs| == 2
    ensures Sum(xs) == xs[0] + xs[1]
  {
    var a := xs[..1];
    assert Sum(xs) == Sum(xs[..|xs|-1]) + xs[1];
    assert xs[..|xs|-1] == a;
    assert |a| == 1 && a[0] == xs[0];
    assert Sum(a) == Sum(a[..0]) + a[0];
    assert a[..0] == [];
  }

  // doc-side upper bound: FSum(acc, ys) <= S * (1+U)^(n-1)  whenever acc + Sum(ys) <= S, |ys| <= n-1
  lemma DocUpper(acc: real, ys: seq<real>, S: real, n: nat)
    requires acc >= 0.0 && NonNeg(ys) && acc + Sum(ys) <= S && |ys| <= n - 1 && n >= 1
    ensures FSum(acc, ys) <= S * Pow(1.0 + U, n - 1)
  {
    FSumUpper(acc, ys);
    SumNonNeg(ys);
    var P := Pow(1.0 + U, |ys|);
    PowGe1(1.0 + U, |ys|);
    PowMonoExpUp(1.0 + U, |ys|, n - 1);
    MulMonoR(acc + Sum(ys), S, P);
    assert S >= 0.0;
    MulMonoR(P, Pow(1.0 + U, n - 1), S);
    assert P * S == S * P;
  }

  // final step shared by all three theorems: S*(1+U)^(n-1) <= C(n) * (S*Q*(1-U)) * (1-U) with Q=(1-U)^(n-1)
  lemma FinalStep(S: real, n: nat)
    requires n >= 3 && SmallN(n) && S >= 0.0
    ensures S * Pow(1.0 + U, n - 1) <= C(n) * (S * Pow(1.0 - U, n - 1) * (1.0 - U)) * (1.0 - U)
  {
    Margin(n);
    var Q := Pow(1.0 - U, n - 1);
    assert Pow(1.0 - U, n) == (1.0 - U) * Q;
    assert Pow(1.0 - U, n + 1) == (1.0 - U) * ((1.0 - U) * Q);
    assert C(n) * (S * Q * (1.0 - U)) * (1.0 - U) == S * (C(n) * Pow(1.0 - U, n + 1));
    MulMonoR(Pow(1.0 + U, n - 1), C(n) * Pow(1.0 - U, n + 1), S);
  }

  lemma Assoc4(c: real, a: real, q: real, v: real)
    ensures c * (a * q * v) * v == (c * (a * q)) * v * v
  {}

  lemma MulStrict(a: real, b: real, d: real)
    requires a < b && d > 0.0
    ensures a * d < b * d
  {}

  // ===================================================================================
  // T1. sumUpperBound soundness (used by MaxScoreBulkScorer.partitionScorers 499-502,
  //     WANDScorer.getMaxScore 584-592 and constructor 186-192, DisjunctionSumScorer.getMaxScore):
  //     for ANY doc whose k <= n clause scores (in ANY order) sum (exactly) to at most the exact sum of the
  //     n clause maxima, Java's left-to-right double sum of the doc scores is <= sumUpperBound(JSum(maxima), n).
  // ===================================================================================
  lemma {:isolate_assertions} SumUpperBoundSound(xs: seq<real>, ms: seq<real>)
    requires |ms| >= 1 && 1 <= |xs| <= |ms| && SmallN(|ms|)
    requires NonNeg(xs) && NonNeg(ms) && Floats(xs) && Floats(ms)
    requires |ms| <= 2 ==> |xs| == |ms|        // for n<=2 the code needs no factor; see SumUB2
    requires |ms| <= 2 ==> (|ms| == 1 ==> xs[0] <= ms[0])
    requires Sum(xs) <= Sum(ms)
    ensures JSum(xs) <= SumUB(JSum(ms), |ms|)
  {
    var n := |ms|;
    if n == 1 {
      JSumFirstExact(xs); JSumFirstExact(ms);
    } else if n == 2 {
      JSumFirstExact(xs); JSumFirstExact(ms);
      assert xs == [xs[0], xs[1]] && ms == [ms[0], ms[1]];
      assert JSum(xs) == Rd(xs[0] + xs[1]);
      assert JSum(ms) == Rd(ms[0] + ms[1]);
      Sum2(xs); Sum2(ms);
      RdMono(xs[0] + xs[1], ms[0] + ms[1]);
    } else {
      SumNonNeg(xs); SumNonNeg(ms);
      var S := Sum(ms);
      JSumFirstExact(xs);
      SumCons(xs[0], xs[1..]); assert xs == [xs[0]] + xs[1..];
      DocUpper(xs[0], xs[1..], S, n);
      JSumLowerN(ms, n - 1);
      var J := JSum(ms);
      var Q := Pow(1.0 - U, n - 1);
      PowLe1(1.0 - U, n - 1);
      assert S * Q >= 0.0 by { MulMonoR(0.0, S, Q); }
      assert J >= S * Q * (1.0 - U) by { ShrinkLe(S * Q); }
      FSumNonNeg(0.0, ms);
      UBLower(J, n);
      calc {
        JSum(xs);
        <= S * Pow(1.0 + U, n - 1);
        <= { FinalStep(S, n); }
           C(n) * (S * Pow(1.0 - U, n - 1) * (1.0 - U)) * (1.0 - U);
        <= { MulMonoR(S * Pow(1.0 - U, n - 1) * (1.0 - U), J, C(n) * (1.0 - U)); }
           C(n) * J * (1.0 - U);
        <= { UBLower(J, n); }
           SumUB(J, n);
      }
    }
  }

  // ===================================================================================
  // T2. Same-order monotonicity (BlockMaxConjunctionScorer 202-231, BlockMaxConjunctionBulkScorer
  //     69-84 vs 175-196, ConjunctionScorer 57-74): needs ONLY RdMono, no error factor.
  // ===================================================================================
  lemma SameOrderSound(xs: seq<real>, ms: seq<real>)
    requires |xs| == |ms| && forall i :: 0 <= i < |xs| ==> xs[i] <= ms[i]
    ensures JSum(xs) <= JSum(ms)
  {
    FSumMono(0.0, 0.0, xs, ms);
  }

  // ===================================================================================
  // T3. ScorerUtil.minRequiredScore/filterCompetitiveHits (131-164) as used by MaxScoreBulkScorer
  //     (457-458) and BlockMaxConjunctionBulkScorer (182-188).  A doc with partial (double) score p is
  //     dropped iff p < r; r > 0 satisfies the loop exit condition Nf(SumUB(Rd(r + M), n)) < mcs.
  //     M is the code's (left- or right-fold) double sum of the remaining maxima; all we need is the
  //     standard-model lower bound M >= Sum(ms) (1-U)^(n-1) (lemmas JSumLowerN / RightFoldLower below).
  //     Remaining clauses add ys (Sum(ys) <= Sum(ms), |ys| <= n-1) left to right.
  // ===================================================================================
  lemma FilterCompetitiveHitsSound(p: real, r: real, M: real, ms: seq<real>, ys: seq<real>,
                                     n: nat, mcs: real)
    requires n >= 3 && SmallN(n) && 1 <= |ms| <= n - 1 && |ys| <= |ms|
    requires NonNeg(ms) && NonNeg(ys) && p >= 0.0 && Sum(ys) <= Sum(ms)
    requires M >= Sum(ms) * Pow(1.0 - U, n - 1)
    requires p < r
    requires Nf(SumUB(Rd(r + M), n)) < mcs          // loop exit condition, ScorerUtil.java:136-140
    ensures Nf(FSum(p, ys)) < mcs
  {
    SumNonNeg(ms); SumNonNeg(ys);
    var S := r + Sum(ms);
    DocUpper(p, ys, S, n);
    var Q := Pow(1.0 - U, n - 1);
    PowLe1(1.0 - U, n - 1);
    assert r >= r * Q by { MulMonoR(Q, 1.0, r); }
    assert r + M >= S * Q;
    assert S * Q >= 0.0 by { MulMonoR(0.0, S, Q); }
    RdErr(r + M);
    var V := Rd(r + M);
    assert V >= S * Q * (1.0 - U) by { MulMonoR(S * Q, r + M, 1.0 - U); }
    assert V >= 0.0 by { MulMonoR(0.0, S * Q, 1.0 - U); }
    UBLower(V, n);
    assert C(n) * V * (1.0 - U) >= C(n) * (S * Q * (1.0 - U)) * (1.0 - U) by {
      MulMonoR(S * Q * (1.0 - U), V, C(n) * (1.0 - U));
    }
    FinalStep(S, n);
    calc {
      FSum(p, ys);
      <= S * Pow(1.0 + U, n - 1);
      <= C(n) * (S * Q * (1.0 - U)) * (1.0 - U);
      <= C(n) * V * (1.0 - U);
      <= SumUB(V, n);
    }
    NfMono(FSum(p, ys), SumUB(V, n));
  }

  lemma JSumLowerN(ms: seq<real>, K: nat)
    requires 1 <= |ms| <= K + 1 && NonNeg(ms) && Floats(ms)
    ensures JSum(ms) >= Sum(ms) * Pow(1.0 - U, K)
  {
    JSumFirstExact(ms);
    SumCons(ms[0], ms[1..]); assert ms == [ms[0]] + ms[1..];
    FSumLower(ms[0], ms[1..]);
    SumNonNeg(ms);
    PowMonoExp(1.0 - U, |ms| - 1, K);
    PowLe1(1.0 - U, K);
    MulMonoR(Pow(1.0 - U, K), Pow(1.0 - U, |ms| - 1), Sum(ms));
  }


  // BlockMaxConjunctionBulkScorer.computeMaxScore (80-82): sumOfOtherClauses[i] = Rd(m_i + sumOfOtherClauses[i+1])
  function RightFold(ms: seq<real>): real
    requires |ms| >= 1
    decreases |ms|
  {
    if |ms| == 1 then ms[0] else Rd(ms[0] + RightFold(ms[1..]))
  }

  lemma RightFoldLower(ms: seq<real>, K: nat)
    requires 1 <= |ms| <= K + 1 && NonNeg(ms)
    ensures RightFold(ms) >= Sum(ms) * Pow(1.0 - U, K)
    ensures RightFold(ms) >= 0.0
    decreases |ms|
  {
    SumNonNeg(ms);
    PowLe1(1.0 - U, K);
    if |ms| == 1 {
      assert Sum(ms) == ms[0] by { assert ms[..0] == []; }
      MulMonoR(Pow(1.0 - U, K), 1.0, ms[0]);
    } else {
      var rest := ms[1..];
      assert ms == [ms[0]] + rest;
      SumCons(ms[0], rest);
      RightFoldLower(rest, K - 1);
      SumNonNeg(rest);
      var Q := Pow(1.0 - U, K - 1);
      PowLe1(1.0 - U, K - 1);
      var a := ms[0] + RightFold(rest);
      assert a >= ms[0] * Q + Sum(rest) * Q by { MulMonoR(Q, 1.0, ms[0]); }
      RdErr(a);
      assert Rd(a) >= a * (1.0 - U);
      assert a * (1.0 - U) >= (ms[0] * Q + Sum(rest) * Q) * (1.0 - U) by {
        MulMonoR(ms[0] * Q + Sum(rest) * Q, a, 1.0 - U);
      }
      assert (ms[0] * Q + Sum(rest) * Q) * (1.0 - U) == Sum(ms) * Pow(1.0 - U, K);
      assert Rd(a) >= 0.0 by { MulMonoR(0.0, a, 1.0 - U); }
    }
  }

  // ===================================================================================
  // T4 (FAILED OBLIGATION, see RequiredClauseUnsound.dfy): MaxScoreBulkScorer.java:540 compares
  //     (float)(maxRequiredScore + maxScoreSums[fRS-2]) with NO sumUpperBound.  The fixed obligation
  //     (with sumUpperBound(., n)) is proved here.
  //     R  = FSum(mE, ups)     maxRequiredScore: essential max, then += max of clauses n-2 .. fRS   (533,546)
  //     L  = JSum(lows)        maxScoreSums[fRS-2] = left fold of maxima of clauses 0 .. fRS-2       (505)
  //     doc (does not match clause fRS-1) = FSum(xE, ys): essential score first, then matching
  //     non-essential scores in decreasing index order (scoreNonEssentialClauses 452-466).
  // ===================================================================================
  lemma {:isolate_assertions} RequiredClauseFixedSound(mE: real, ups: seq<real>, lows: seq<real>,
                                   xE: real, ys: seq<real>, n: nat, mcs: real)
    requires n >= 3 && SmallN(n) && 1 + |ups| + |lows| <= n - 1 && |lows| >= 1 && |ys| <= |ups| + |lows|
    requires mE >= 0.0 && NonNeg(ups) && NonNeg(lows) && Floats(lows) && NonNeg(ys) && xE >= 0.0
    requires xE + Sum(ys) <= mE + Sum(ups) + Sum(lows)
    requires Nf(SumUB(Rd(FSum(mE, ups) + JSum(lows)), n)) < mcs     // the FIXED test
    ensures Nf(FSum(xE, ys)) < mcs
  {
    var S := mE + Sum(ups) + Sum(lows);
    SumNonNeg(ups); SumNonNeg(lows); SumNonNeg(ys);
    DocUpper(xE, ys, S, n);
    var Q := Pow(1.0 - U, n - 1);
    PowLe1(1.0 - U, n - 1);
    FSumLower(mE, ups);
    PowMonoExp(1.0 - U, |ups|, n - 1);
    PowLe1(1.0 - U, |ups|);
    assert FSum(mE, ups) >= (mE + Sum(ups)) * Q by {
      MulMonoR(Q, Pow(1.0 - U, |ups|), mE + Sum(ups));
    }
    JSumLowerN(lows, n - 1);
    FSumNonNeg(mE, ups); FSumNonNeg(0.0, lows);
    var A := FSum(mE, ups) + JSum(lows);
    assert A >= S * Q;
    assert S * Q >= 0.0 by { MulMonoR(0.0, S, Q); }
    RdErr(A);
    var V := Rd(A);
    assert V >= S * Q * (1.0 - U) by { MulMonoR(S * Q, A, 1.0 - U); }
    assert V >= 0.0 by { MulMonoR(0.0, S * Q, 1.0 - U); }
    UBLower(V, n);
    assert C(n) * V * (1.0 - U) >= C(n) * (S * Q * (1.0 - U)) * (1.0 - U) by {
      MulMonoR(S * Q * (1.0 - U), V, C(n) * (1.0 - U));
    }
    FinalStep(S, n);
    calc {
      FSum(xE, ys);
      <= S * Pow(1.0 + U, n - 1);
      <= C(n) * (S * Q * (1.0 - U)) * (1.0 - U);
      <= C(n) * V * (1.0 - U);
      <= SumUB(V, n);
    }
    NfMono(FSum(xE, ys), SumUB(V, n));
  }
}
