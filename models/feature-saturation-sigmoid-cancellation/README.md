# Formal models and scripts: feature-saturation-sigmoid-cancellation

Tools: SMT-LIB (Z3/cvc5).

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- FeatureField saturation and sigmoid queries and IntervalQuery scoring compute w*(1 - k/(S+k)) instead of the documented w*S/(S+k) (and the same rewrite for the sigmoid), so documents whose value is far below the pivot score exactly 0.0 or tie with documents that have different values (bug LFV-898; [repro](../../repro/LFV-898/))

## Files

- [saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/q1_zero_iff_sum_absorbs.smt2](saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/q1_zero_iff_sum_absorbs.smt2) (used for LFV-898)
- [saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/q2a_zero_needs_ratio_le_2m24.smt2](saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/q2a_zero_needs_ratio_le_2m24.smt2) (used for LFV-898)
- [saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/q2b_ratio_lt_2m25_always_zero.smt2](saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/q2b_ratio_lt_2m25_always_zero.smt2) (used for LFV-898)
- [saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/v1-unbounded-weight/q1_zero_iff_sum_absorbs.smt2](saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/v1-unbounded-weight/q1_zero_iff_sum_absorbs.smt2) (used for LFV-898)
- [saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/v1-unbounded-weight/q2a_zero_needs_ratio_le_2m24.smt2](saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/v1-unbounded-weight/q2a_zero_needs_ratio_le_2m24.smt2) (used for LFV-898)
- [saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/v1-unbounded-weight/q2b_ratio_lt_2m25_always_zero.smt2](saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/v1-unbounded-weight/q2b_ratio_lt_2m25_always_zero.smt2) (used for LFV-898)
- [saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/v1-unbounded-weight/v1_zero_reachable.smt2](saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/v1-unbounded-weight/v1_zero_reachable.smt2) (used for LFV-898)
- [saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/v1-unbounded-weight/v2_subnormal_pivot_never_zero.smt2](saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/v1-unbounded-weight/v2_subnormal_pivot_never_zero.smt2) (used for LFV-898)
- [saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/v1-unbounded-weight/v3_mutant_bound_2m25_sat.smt2](saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/v1-unbounded-weight/v3_mutant_bound_2m25_sat.smt2) (used for LFV-898)
- [saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/v1_zero_reachable.smt2](saturation-sigmoid-one-minus-rewrite-cancellation/model/smt/v1_zero_reachable.smt2) (used for LFV-898)

## How to run

- SMT-LIB: `z3 <file>.smt2` or `cvc5 <file>.smt2` (`sat` with a model is a counterexample where the query asks for one).
