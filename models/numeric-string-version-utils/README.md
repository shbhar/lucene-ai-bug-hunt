# Formal models and scripts: numeric-string-version-utils

Tools: SMT-LIB (Z3/cvc5), Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- MathUtil.asinh, acosh and atanh use the textbook log formulas without range reduction: asinh and acosh return Infinity for every finite |x| &gt;= 2^512, asinh and atanh return 0 for tiny nonzero x, and acosh of a large negative x returns an infinity instead of the documented NaN, also through the expression functions asinh, acosh and atanh (bug LFV-327; [repro](../../repro/LFV-327/))
- RamUsageEstimator.sizeOfCollection adds the assumed backing-array bytes in int arithmetic, so a collection of 536,870,908 or more elements (268,435,453 with 8-byte references) gets a size estimate that is a multiple of 4 GiB too low, and negative when the elements add little (bug LFV-323; [repro](../../repro/LFV-323/))

## Files

- [mathutil-inverse-hyperbolic-range-collapse/model/ref_error.py](mathutil-inverse-hyperbolic-range-collapse/model/ref_error.py) (used for LFV-327)
- [mathutil-inverse-hyperbolic-range-collapse/model/smt-hyperbolic/p1_probe_overflow_sat.smt2](mathutil-inverse-hyperbolic-range-collapse/model/smt-hyperbolic/p1_probe_overflow_sat.smt2) (used for LFV-327)
- [mathutil-inverse-hyperbolic-range-collapse/model/smt-hyperbolic/p2_probe_asinh_zero_sat.smt2](mathutil-inverse-hyperbolic-range-collapse/model/smt-hyperbolic/p2_probe_asinh_zero_sat.smt2) (used for LFV-327)
- [mathutil-inverse-hyperbolic-range-collapse/model/smt-hyperbolic/p3_probe_atanh_zero_sat.smt2](mathutil-inverse-hyperbolic-range-collapse/model/smt-hyperbolic/p3_probe_atanh_zero_sat.smt2) (used for LFV-327)
- [mathutil-inverse-hyperbolic-range-collapse/model/smt-hyperbolic/q1_overflow_all_large.smt2](mathutil-inverse-hyperbolic-range-collapse/model/smt-hyperbolic/q1_overflow_all_large.smt2) (used for LFV-327)
- [mathutil-inverse-hyperbolic-range-collapse/model/smt-hyperbolic/q2_overflow_tight.smt2](mathutil-inverse-hyperbolic-range-collapse/model/smt-hyperbolic/q2_overflow_tight.smt2) (used for LFV-327)
- [mathutil-inverse-hyperbolic-range-collapse/model/smt-hyperbolic/q3_asinh_zero_all_small.smt2](mathutil-inverse-hyperbolic-range-collapse/model/smt-hyperbolic/q3_asinh_zero_all_small.smt2) (used for LFV-327)
- [mathutil-inverse-hyperbolic-range-collapse/model/smt-hyperbolic/q4_asinh_zero_tight.smt2](mathutil-inverse-hyperbolic-range-collapse/model/smt-hyperbolic/q4_asinh_zero_tight.smt2) (used for LFV-327)
- [mathutil-inverse-hyperbolic-range-collapse/model/smt-hyperbolic/q5_atanh_zero_all_small.smt2](mathutil-inverse-hyperbolic-range-collapse/model/smt-hyperbolic/q5_atanh_zero_all_small.smt2) (used for LFV-327)
- [mathutil-inverse-hyperbolic-range-collapse/model/smt-hyperbolic/q6_atanh_zero_tight.smt2](mathutil-inverse-hyperbolic-range-collapse/model/smt-hyperbolic/q6_atanh_zero_tight.smt2) (used for LFV-327)
- [ramusage-sizeofcollection-int-overflow/model/collection_overflow.py](ramusage-sizeofcollection-int-overflow/model/collection_overflow.py) (used for LFV-323)

## How to run

- SMT-LIB: `z3 <file>.smt2` or `cvc5 <file>.smt2` (`sat` with a model is a counterexample where the query asks for one).
- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
