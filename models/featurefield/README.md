# Formal models and scripts: featurefield

Tools: SMT-LIB (Z3/cvc5).

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- FeatureField.newSigmoidQuery (and the IntervalQuery sigmoid) return NaN scores when pivot^exp overflows or underflows a double, for pivot and exp values the factories accept (bug LFV-258; [repro](../../repro/LFV-258/))

## Files

- [sigmoid-pivot-power-overflow-nan/model/fix_L1_add.smt2](sigmoid-pivot-power-overflow-nan/model/fix_L1_add.smt2) (used for LFV-258)
- [sigmoid-pivot-power-overflow-nan/model/fix_L2_div.smt2](sigmoid-pivot-power-overflow-nan/model/fix_L2_div.smt2) (used for LFV-258)
- [sigmoid-pivot-power-overflow-nan/model/fix_L3_narrow.smt2](sigmoid-pivot-power-overflow-nan/model/fix_L3_narrow.smt2) (used for LFV-258)
- [sigmoid-pivot-power-overflow-nan/model/sigm_core_nan_only_if.smt2](sigmoid-pivot-power-overflow-nan/model/sigm_core_nan_only_if.smt2) (used for LFV-258)
- [sigmoid-pivot-power-overflow-nan/model/sigm_core_nan_witness.smt2](sigmoid-pivot-power-overflow-nan/model/sigm_core_nan_witness.smt2) (used for LFV-258)
- [sigmoid-pivot-power-overflow-nan/model/sigm_fixed_formula.smt2](sigmoid-pivot-power-overflow-nan/model/sigm_fixed_formula.smt2) (used for LFV-258)
- [sigmoid-pivot-power-overflow-nan/model/vac_sigm_only_if_noInf.smt2](sigmoid-pivot-power-overflow-nan/model/vac_sigm_only_if_noInf.smt2) (used for LFV-258)
- [sigmoid-pivot-power-overflow-nan/model/vac_sigm_only_if_noZero.smt2](sigmoid-pivot-power-overflow-nan/model/vac_sigm_only_if_noZero.smt2) (used for LFV-258)

## How to run

- SMT-LIB: `z3 <file>.smt2` or `cvc5 <file>.smt2` (`sat` with a model is a counterexample where the query asks for one).
