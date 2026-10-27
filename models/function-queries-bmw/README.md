# Formal models and scripts: function-queries-bmw

Tools: SMT-LIB (Z3/cvc5), Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- FunctionScoreQuery's block-max bound multiplies by the wrapped query's no-score max score (0 for a TermQuery), so a BooleanQuery that contains it can drop the true top hits (bug LFV-211; [repro](../../repro/LFV-211/), [bug page](../../bugs/LFV-211.html))
- FunctionScoreQuery scores negative values as 0, but its block-max bound stays negative, so top-k search skips competitive documents (bug LFV-203; [repro](../../repro/LFV-203/))
- DoubleValuesSource.fromDoubleField/fromFloatField declare their raw IEEE-bit decoders INCREASING, so on a skip-indexed raw-bits field one NaN makes a block's score bound NaN (FunctionScoreQuery under TOP_SCORES then silently drops the true top hit) and all-negative blocks get inverted bounds (bug LFV-189; [repro](../../repro/LFV-189/))
- DoubleValues.withDefault's getMaxScore returns the wrapped source's bound, which does not cover missingValue, so a FunctionScoreQuery over such a source can silently drop the true top hit under TOP_SCORES (bug LFV-214; [repro](../../repro/LFV-214/))
- DoubleValuesSource rounds the skip-index block bound to the nearest float, so with a boost FunctionScoreQuery's max score can be one ulp below a real score and top-k search returns the wrong top hit (bug LFV-221; [repro](../../repro/LFV-221/))

## Files

- [doublevalues-withdefault-maxscore-ignores-missing-value/model/fsq_bound_smt.py](doublevalues-withdefault-maxscore-ignores-missing-value/model/fsq_bound_smt.py) (used for LFV-214)
- [doublevaluessource-field-bound-rounds-to-nearest-float/model/fsq_bound_smt.py](doublevaluessource-field-bound-rounds-to-nearest-float/model/fsq_bound_smt.py) (used for LFV-221)
- [doublevaluessource-field-bound-rounds-to-nearest-float/model/smt2/Q1b2_long_rounding.smt2](doublevaluessource-field-bound-rounds-to-nearest-float/model/smt2/Q1b2_long_rounding.smt2) (used for LFV-221)
- [doublevaluessource-field-bound-rounds-to-nearest-float/model/smt2/Q1b_inner1_rounding.smt2](doublevaluessource-field-bound-rounds-to-nearest-float/model/smt2/Q1b_inner1_rounding.smt2) (used for LFV-221)
- [doublevaluessource-field-bound-rounds-to-nearest-float/model/smt2/Q1c_inner1_boost1.smt2](doublevaluessource-field-bound-rounds-to-nearest-float/model/smt2/Q1c_inner1_boost1.smt2) (used for LFV-221)
- [doublevaluessource-field-bound-rounds-to-nearest-float/model/smt2/Q2d_float_cast_monotone.smt2](doublevaluessource-field-bound-rounds-to-nearest-float/model/smt2/Q2d_float_cast_monotone.smt2) (used for LFV-221)
- [doublevaluessource-raw-bits-decoder-not-monotonic/model/fsq_bound_smt.py](doublevaluessource-raw-bits-decoder-not-monotonic/model/fsq_bound_smt.py) (used for LFV-189)
- [doublevaluessource-raw-bits-decoder-not-monotonic/model/smt2/Q2a2_double_bits_nonneg_max.smt2](doublevaluessource-raw-bits-decoder-not-monotonic/model/smt2/Q2a2_double_bits_nonneg_max.smt2) (used for LFV-189)
- [doublevaluessource-raw-bits-decoder-not-monotonic/model/smt2/Q2a_double_bits_monotone.smt2](doublevaluessource-raw-bits-decoder-not-monotonic/model/smt2/Q2a_double_bits_monotone.smt2) (used for LFV-189)
- [doublevaluessource-raw-bits-decoder-not-monotonic/model/smt2/Q2b_float_bits_monotone.smt2](doublevaluessource-raw-bits-decoder-not-monotonic/model/smt2/Q2b_float_bits_monotone.smt2) (used for LFV-189)
- [doublevaluessource-raw-bits-decoder-not-monotonic/model/smt2/Q2e_double_nan_max.smt2](doublevaluessource-raw-bits-decoder-not-monotonic/model/smt2/Q2e_double_nan_max.smt2) (used for LFV-189)
- [functionscorequery-maxscore-ignores-negative-value-clamp/model/fsq_bound_smt.py](functionscorequery-maxscore-ignores-negative-value-clamp/model/fsq_bound_smt.py) (used for LFV-203)
- [functionscorequery-maxscore-ignores-negative-value-clamp/model/smt2/Q1d_negative.smt2](functionscorequery-maxscore-ignores-negative-value-clamp/model/smt2/Q1d_negative.smt2) (used for LFV-203)
- [functionscorequery-maxscore-uses-nonscoring-inner-bound/model/fsq_bound_smt.py](functionscorequery-maxscore-uses-nonscoring-inner-bound/model/fsq_bound_smt.py) (used for LFV-211)
- [functionscorequery-maxscore-uses-nonscoring-inner-bound/model/smt2/Q1a_inner0.smt2](functionscorequery-maxscore-uses-nonscoring-inner-bound/model/smt2/Q1a_inner0.smt2) (used for LFV-211)

## How to run

- SMT-LIB: `z3 <file>.smt2` or `cvc5 <file>.smt2` (`sat` with a model is a counterexample where the query asks for one).
- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
