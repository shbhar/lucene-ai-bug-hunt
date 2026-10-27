# Formal models and scripts: document-field-factories

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- DoubleRange and FloatRange accept the bounds [0.0, -0.0] and store them inverted, so range queries silently miss such documents and contradict each other (WITHIN [0,0] but not INTERSECTS [0,0]) (bug LFV-140; [repro](../../repro/LFV-140/))

## Files

- [range-validation-accepts-positive-zero-to-negative-zero/model/q_signed_zero_validation.py](range-validation-accepts-positive-zero-to-negative-zero/model/q_signed_zero_validation.py) (used for LFV-140)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
