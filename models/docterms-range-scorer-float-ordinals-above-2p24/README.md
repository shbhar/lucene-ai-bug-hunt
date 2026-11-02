# Formal models and scripts: docterms-range-scorer-float-ordinals-above-2p24

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- FunctionRangeQuery on a string field (BytesRefFieldSource / SortedSetFieldSource) compares term ordinals as float, so in a segment with more than 2^24 distinct values of the field it also matches values just outside the range bounds (finding LFV-1108; [repro](../../repro/LFV-1108/), [finding page](../../bugs/LFV-1108.html))

## Files

- [model/ord_float_compare_z3.py](model/ord_float_compare_z3.py) (used for LFV-1108)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
