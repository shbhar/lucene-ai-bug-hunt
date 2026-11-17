# Formal models and scripts: success-flag-refactors-cleanup-behaviour

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- In no release (10.6.0 does not have it; 11.0.0 would be the first): on the 11.0 branch and main, with reader pooling off and no near-real-time reader opened from the writer, when IndexWriter fails to resolve buffered deletes and the clean-up after it fails too, only the clean-up failure is thrown and recorded as the tragic exception; the first failure is reported nowhere (10.6.0 throws the first failure with the clean-up failure as suppressed) (finding LFV-1506; [repro](../../repro/LFV-1506/), [finding page](../../bugs/LFV-1506.html))

## Files

- [model/census/census.py](model/census/census.py) (used for LFV-1506)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
