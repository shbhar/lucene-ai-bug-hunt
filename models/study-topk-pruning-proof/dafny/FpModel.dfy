// Abstract IEEE-754 model used for the pruning proofs (Dafny 4.11).
//
// Doubles are modelled as reals. `Rd` is round-to-nearest double (Java double +,*), `Nf` is the
// (float) narrowing cast. Only the following facts about rounding are assumed (all true for IEEE-754
// binary64/binary32 round-to-nearest-even, absent overflow/underflow; scores are finite and >= 0):
//   RdMono   : x <= y  ==>  Rd(x) <= Rd(y)                       (rounding is monotone)
//   RdErr    : x >= 0  ==>  x*(1-U) <= Rd(x) <= x*(1+U)          (standard model, U = 2^-53)
//   RdExact  : IsDouble(x) ==> Rd(x) == x ;  IsDouble(Rd(x))     (representable values are fixed points)
//   FloatIsDouble : every float score is a double
//   NfMono   : x <= y  ==>  Nf(x) <= Nf(y)
// Nothing else (in particular NO associativity / order independence) is assumed.
module FpModel {
  const U: real := 1.0 / 9007199254740992.0  // 2^-53, unit roundoff of binary64

  function Rd(x: real): real
  function Nf(x: real): real
  predicate IsDouble(x: real)
  predicate IsFloat(x: real)

  lemma {:axiom} RdMono(x: real, y: real)
    requires x <= y
    ensures Rd(x) <= Rd(y)

  lemma {:axiom} RdErr(x: real)
    requires x >= 0.0
    ensures x * (1.0 - U) <= Rd(x) <= x * (1.0 + U)

  lemma {:axiom} RdExact(x: real)
    ensures IsDouble(x) ==> Rd(x) == x
    ensures IsDouble(Rd(x))

  lemma {:axiom} FloatIsDouble(x: real)
    ensures IsFloat(x) ==> IsDouble(x)

  lemma {:axiom} NfMono(x: real, y: real)
    requires x <= y
    ensures Nf(x) <= Nf(y)

  // ---------- sequences of scores ----------
  predicate NonNeg(xs: seq<real>) { forall i :: 0 <= i < |xs| ==> xs[i] >= 0.0 }
  predicate Floats(xs: seq<real>) { forall i :: 0 <= i < |xs| ==> IsFloat(xs[i]) }

  function Sum(xs: seq<real>): real
  {
    if |xs| == 0 then 0.0 else Sum(xs[..|xs|-1]) + xs[|xs|-1]
  }

  // Java:  double s = acc; for (float x : xs) s += x;
  function FSum(acc: real, xs: seq<real>): real
    decreases |xs|
  {
    if |xs| == 0 then acc else FSum(Rd(acc + xs[0]), xs[1..])
  }

  function Pow(b: real, k: nat): real
  {
    if k == 0 then 1.0 else b * Pow(b, k - 1)
  }

  lemma SumCons(x: real, xs: seq<real>)
    ensures Sum([x] + xs) == x + Sum(xs)
    decreases |xs|
  {
    if |xs| == 0 {
      assert [x] + xs == [x];
    } else {
      var ys := [x] + xs;
      assert ys[..|ys|-1] == [x] + xs[..|xs|-1];
      SumCons(x, xs[..|xs|-1]);
    }
  }

  lemma SumNonNeg(xs: seq<real>)
    requires NonNeg(xs)
    ensures Sum(xs) >= 0.0
  {
    if |xs| > 0 { SumNonNeg(xs[..|xs|-1]); }
  }

  lemma PowGe1(b: real, k: nat)
    requires b >= 1.0
    ensures Pow(b, k) >= 1.0
  {
    if k > 0 { PowGe1(b, k - 1); }
  }

  lemma PowPos(b: real, k: nat)
    requires b > 0.0
    ensures Pow(b, k) > 0.0
  {
    if k > 0 { PowPos(b, k - 1); }
  }

  lemma PowLe1(b: real, k: nat)
    requires 0.0 < b <= 1.0
    ensures 0.0 < Pow(b, k) <= 1.0
  {
    if k > 0 {
      PowLe1(b, k - 1);
      var p := Pow(b, k - 1);
      MulMonoR(b, 1.0, p);
    }
  }

  lemma ShrinkLe(s: real)
    requires s >= 0.0
    ensures s * (1.0 - U) <= s
  {
    assert s * (1.0 - U) == s - s * U;
    assert s * U >= 0.0;
  }

  lemma MulMonoR(a: real, b: real, c: real)
    requires a <= b && c >= 0.0
    ensures a * c <= b * c
  {}

  // ---------- monotonicity of same-order accumulation (needs only RdMono) ----------
  lemma FSumMono(a: real, b: real, xs: seq<real>, ms: seq<real>)
    requires a <= b && |xs| == |ms|
    requires forall i :: 0 <= i < |xs| ==> xs[i] <= ms[i]
    ensures FSum(a, xs) <= FSum(b, ms)
    decreases |xs|
  {
    if |xs| > 0 {
      RdMono(a + xs[0], b + ms[0]);
      FSumMono(Rd(a + xs[0]), Rd(b + ms[0]), xs[1..], ms[1..]);
    }
  }

  lemma FSumNonNeg(acc: real, xs: seq<real>)
    requires acc >= 0.0 && NonNeg(xs)
    ensures FSum(acc, xs) >= 0.0
    decreases |xs|
  {
    if |xs| > 0 {
      RdErr(acc + xs[0]);
      FSumNonNeg(Rd(acc + xs[0]), xs[1..]);
    }
  }

  // ---------- forward error of a left-fold sum (standard model) ----------
  lemma FSumUpper(acc: real, xs: seq<real>)
    requires acc >= 0.0 && NonNeg(xs)
    ensures FSum(acc, xs) <= (acc + Sum(xs)) * Pow(1.0 + U, |xs|)
    decreases |xs|
  {
    if |xs| > 0 {
      var r := Rd(acc + xs[0]);
      RdErr(acc + xs[0]);
      var rest := xs[1..];
      assert xs == [xs[0]] + rest;
      SumCons(xs[0], rest);
      SumNonNeg(rest);
      FSumUpper(r, rest);
      var P := Pow(1.0 + U, |rest|);
      PowGe1(1.0 + U, |rest|);
      // FSum <= (r + S) P <= ((acc+x)(1+U) + S) P <= (acc+x+S)(1+U) P
      calc {
        FSum(acc, xs);
        == FSum(r, rest);
        <= (r + Sum(rest)) * P;
        <= { assert r <= (acc + xs[0]) * (1.0 + U); }
           ((acc + xs[0]) * (1.0 + U) + Sum(rest)) * P;
        <= { assert Sum(rest) <= Sum(rest) * (1.0 + U); }
           ((acc + xs[0]) * (1.0 + U) + Sum(rest) * (1.0 + U)) * P;
        == (acc + xs[0] + Sum(rest)) * ((1.0 + U) * P);
        == (acc + Sum(xs)) * Pow(1.0 + U, |xs|);
      }
    }
  }

  lemma FSumLower(acc: real, xs: seq<real>)
    requires acc >= 0.0 && NonNeg(xs)
    ensures FSum(acc, xs) >= (acc + Sum(xs)) * Pow(1.0 - U, |xs|)
    decreases |xs|
  {
    if |xs| > 0 {
      var r := Rd(acc + xs[0]);
      RdErr(acc + xs[0]);
      var rest := xs[1..];
      assert xs == [xs[0]] + rest;
      SumCons(xs[0], rest);
      SumNonNeg(rest);
      FSumLower(r, rest);
      var P := Pow(1.0 - U, |rest|);
      PowLe1(1.0 - U, |rest|);
      calc {
        FSum(acc, xs);
        == FSum(r, rest);
        >= (r + Sum(rest)) * P;
        >= { assert r >= (acc + xs[0]) * (1.0 - U); }
           ((acc + xs[0]) * (1.0 - U) + Sum(rest)) * P;
        >= { ShrinkLe(Sum(rest));
             MulMonoR((acc + xs[0]) * (1.0 - U) + Sum(rest) * (1.0 - U), (acc + xs[0]) * (1.0 - U) + Sum(rest), P); }
           ((acc + xs[0]) * (1.0 - U) + Sum(rest) * (1.0 - U)) * P;
        == (acc + xs[0] + Sum(rest)) * ((1.0 - U) * P);
        == (acc + Sum(xs)) * Pow(1.0 - U, |xs|);
      }
    }
  }

  // Java's  double s = 0; for (x : xs) s += x;  where the first addition is exact (0 + float).
  function JSum(xs: seq<real>): real { FSum(0.0, xs) }

  lemma JSumFirstExact(xs: seq<real>)
    requires |xs| > 0 && Floats(xs)
    ensures JSum(xs) == FSum(xs[0], xs[1..])
  {
    FloatIsDouble(xs[0]);
    RdExact(0.0 + xs[0]);
  }

  // ---------- Bernoulli-type bounds on the powers ----------
  lemma PowUpperBound(k: nat)
    requires (k as real) * U <= 0.5
    ensures Pow(1.0 + U, k) * (1.0 - (k as real) * U) <= 1.0
  {
    if k > 0 {
      PowUpperBound(k - 1);
      var p := Pow(1.0 + U, k - 1);
      var a := ((k - 1) as real) * U;
      PowGe1(1.0 + U, k - 1);
      // p (1 - a) <= 1  ==>  (1+U) p (1 - a - U) <= p (1 - a)(1 + U) - p U (1+U) ... use: (1+U)(1-a-U) <= 1 - a
      assert (1.0 + U) * (1.0 - a - U) <= 1.0 - a by {
        assert (1.0 + U) * (1.0 - a - U) == 1.0 - a - U * a - U * U;
      }
      calc {
        Pow(1.0 + U, k) * (1.0 - (k as real) * U);
        == p * ((1.0 + U) * (1.0 - a - U));
        <= { assert p >= 0.0; } p * (1.0 - a);
        <= 1.0;
      }
    }
  }

  lemma PowLowerBound(k: nat)
    requires (k as real) * U <= 1.0
    ensures Pow(1.0 - U, k) >= 1.0 - (k as real) * U
  {
    if k > 0 {
      PowLowerBound(k - 1);
      var p := Pow(1.0 - U, k - 1);
      var a := ((k - 1) as real) * U;
      calc {
        Pow(1.0 - U, k);
        == (1.0 - U) * p;
        >= { assert 1.0 - U >= 0.0; } (1.0 - U) * (1.0 - a);
        == 1.0 - a - U + U * a;
        >= 1.0 - (k as real) * U;
      }
    }
  }
}
