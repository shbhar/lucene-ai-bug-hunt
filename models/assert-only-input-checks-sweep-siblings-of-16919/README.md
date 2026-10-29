# Formal models and scripts: assert-only-input-checks-sweep-siblings-of-16919

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Four Lucene test methods never run because their names do not start with lowercase 'test' (TestXYRectangle.tesInvalidMinMaxX/Y, TestOperations.TestOptionalOptionalAcceptsState0, TestCommonGramsFilter.TestFirstAndLastStopWord); the two TestXYRectangle ones would also fail if run, because they look for '5 &gt; 4' while XYRectangle reports '5.0 &gt; 4.0', so XYRectangle's reversed-bounds check has had no running test since 2020 (bug LFV-1013; [repro](../../repro/LFV-1013/))

## Files

- [test-methods-misnamed-never-run/model/scan_misnamed_tests.py](test-methods-misnamed-never-run/model/scan_misnamed_tests.py) (used for LFV-1013)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
