// FAILED PROOF OBLIGATION (expected: Dafny reports 1 error).
// MaxScoreBulkScorer.java:533-546 (main 7cecf79523) decides that clause fRS-1 is REQUIRED when
//     (float) (maxRequiredScore + maxScoreSums[fRS - 2]) < minCompetitiveScore          (line 540)
// i.e. WITHOUT MathUtil.sumUpperBound, although the bound and the doc score are summed in different
// orders: bound = Rd( FSum(mE, ups) + JSum(lows) )   (two separately accumulated groups),
//         doc   = FSum(xE, ys)                       (essential first, then non-essential, decreasing index).
// With only the IEEE facts of FpModel (monotone rounding, standard error model) this does not follow.
// Z3 (smt/o3_required_clause.py, o3_e2e.py) produced concrete binary32/binary64 counterexamples, e.g.
//   mE = 0.75f, ups = [] , lows = [0x1p-53f + 0x1p-76f, 0.25f + 0x1p-24f], xE = 0.75f, ys = [0.25f + 0x1p-24f, 0x1p-53f + 0x1p-76f]
//   bound = (float)(0.75 + (2^-53+2^-76 + 0.25+2^-24)) = 1.0f ;  doc = (float)((0.75 + 0.25+2^-24) + 2^-53+2^-76) = 0x1.000002p0f
// and junit/src/TestMaxScoreRequiredClauseRounding.java reproduces the wrong top hit on main.
include "Pruning.dfy"

module RequiredClauseUnsound {
  import opened FpModel
  import opened Pruning

  lemma RequiredClauseAsCoded(mE: real, ups: seq<real>, lows: seq<real>, xE: real, ys: seq<real>, mcs: real)
    requires mE >= 0.0 && NonNeg(ups) && NonNeg(lows) && Floats(lows) && NonNeg(ys) && xE >= 0.0
    requires |lows| >= 1 && |ys| <= |ups| + |lows|
    requires xE <= mE && Sum(ys) <= Sum(ups) + Sum(lows)
    requires Nf(Rd(FSum(mE, ups) + JSum(lows))) < mcs       // line 540 as written (no sumUpperBound)
    ensures Nf(FSum(xE, ys)) < mcs                            // what pruning needs
  {
    // No proof exists from the FpModel axioms; Dafny reports this postcondition as unproved.
  }
}
