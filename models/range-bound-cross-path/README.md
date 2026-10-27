# Formal models and scripts: range-bound-cross-path

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- FunctionRangeQuery over ValueSource.fromDoubleValuesSource(...) compares bounds and values as floats, not doubles, so double and long ranges silently gain or lose hits near a bound and beyond Float.MAX_VALUE (bug LFV-409; [repro](../../repro/LFV-409/))

## Files

- [fromdoublevaluessource-range-scorer-float-precision/model/float_range_scorer_z3.py](fromdoublevaluessource-range-scorer-float-precision/model/float_range_scorer_z3.py) (used for LFV-409)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
