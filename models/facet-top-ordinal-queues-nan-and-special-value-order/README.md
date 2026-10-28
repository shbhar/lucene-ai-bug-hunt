# Formal models and scripts: facet-top-ordinal-queues-nan-and-special-value-order

Tools: SMT-LIB (Z3/cvc5), Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Float taxonomy facets: if one child label aggregates to NaN, getTopChildren can return a lower-valued label in place of a higher one (a=5, b=NaN, c=10 gives top-1 [a]), because TopOrdAndFloatQueue compares values with &lt; and &gt;; a regression in 9.11.0 from #12966 (bug LFV-825; [repro](../../repro/LFV-825/))
- Taxonomy Facets#getAllDims sorts float-valued dims with a NaN-unsafe &lt; / &gt; comparator, so one dim whose value is NaN puts the other dims out of value order, breaks the documented rule that getTopDims equals the first entries of getAllDims, and with 32 or more dims can make getAllDims throw 'Comparison method violates its general contract!' (released since 4.7.0) (bug LFV-820; [repro](../../repro/LFV-820/))

## Files

- [getalldims-float-comparator-not-nan-safe/model/gen_queries.py](getalldims-float-comparator-not-nan-safe/model/gen_queries.py) (used for LFV-820)
- [getalldims-float-comparator-not-nan-safe/model/q4_getalldims_order_transitivity_with_nan.smt2](getalldims-float-comparator-not-nan-safe/model/q4_getalldims_order_transitivity_with_nan.smt2) (used for LFV-820)
- [getalldims-float-comparator-not-nan-safe/model/q4b_getalldims_order_transitive_without_nan.smt2](getalldims-float-comparator-not-nan-safe/model/q4b_getalldims_order_transitive_without_nan.smt2) (used for LFV-820)
- [getalldims-float-comparator-not-nan-safe/model/q5_topdims_vs_alldims_disagree_with_nan.smt2](getalldims-float-comparator-not-nan-safe/model/q5_topdims_vs_alldims_disagree_with_nan.smt2) (used for LFV-820)
- [getalldims-float-comparator-not-nan-safe/model/q5b_topdims_vs_alldims_agree_otherwise.smt2](getalldims-float-comparator-not-nan-safe/model/q5b_topdims_vs_alldims_agree_otherwise.smt2) (used for LFV-820)
- [topordandfloatqueue-nan-breaks-heap-order/model/gen_queries.py](topordandfloatqueue-nan-breaks-heap-order/model/gen_queries.py) (used for LFV-825)
- [topordandfloatqueue-nan-breaks-heap-order/model/gen_vacuity.py](topordandfloatqueue-nan-breaks-heap-order/model/gen_vacuity.py) (used for LFV-825)
- [topordandfloatqueue-nan-breaks-heap-order/model/q1_lt_q_transitivity_with_nan.smt2](topordandfloatqueue-nan-breaks-heap-order/model/q1_lt_q_transitivity_with_nan.smt2) (used for LFV-825)
- [topordandfloatqueue-nan-breaks-heap-order/model/q1b_lt_q_witness_repro_shape.smt2](topordandfloatqueue-nan-breaks-heap-order/model/q1b_lt_q_witness_repro_shape.smt2) (used for LFV-825)
- [topordandfloatqueue-nan-breaks-heap-order/model/q2_lt_q_transitive_without_nan.smt2](topordandfloatqueue-nan-breaks-heap-order/model/q2_lt_q_transitive_without_nan.smt2) (used for LFV-825)
- [topordandfloatqueue-nan-breaks-heap-order/model/q3_fix_transitive_all_floats.smt2](topordandfloatqueue-nan-breaks-heap-order/model/q3_fix_transitive_all_floats.smt2) (used for LFV-825)
- [topordandfloatqueue-nan-breaks-heap-order/model/q3c_fix_agrees_except_nan_and_signed_zero.smt2](topordandfloatqueue-nan-breaks-heap-order/model/q3c_fix_agrees_except_nan_and_signed_zero.smt2) (used for LFV-825)

## How to run

- SMT-LIB: `z3 <file>.smt2` or `cvc5 <file>.smt2` (`sat` with a model is a counterexample where the query asks for one).
- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
