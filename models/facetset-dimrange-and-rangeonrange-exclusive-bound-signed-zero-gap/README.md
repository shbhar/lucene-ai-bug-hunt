# Formal models and scripts: facetset-dimrange-and-rangeonrange-exclusive-bound-signed-zero-gap

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- Facet-set DimRange.fromDoubles/fromFloats (and range-on-range DoubleRange) make an exclusive bound inclusive with Math.nextUp/Math.nextDown, which skip the other signed zero, so a facet set holding -0.0 is counted by neither of the adjacent ranges [-1, 0) and [0, 1] (finding LFV-1131; [repro](../../repro/LFV-1131/))

## Files

- [model/partition_smt.py](model/partition_smt.py) (used for LFV-1131)
- [model/replay_mbt.py](model/replay_mbt.py) (used for LFV-1131)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
