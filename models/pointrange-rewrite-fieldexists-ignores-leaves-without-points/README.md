# Formal models and scripts: pointrange-rewrite-fieldexists-ignores-leaves-without-points

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- PointRangeQuery rewrites a range that covers every indexed point to FieldExistsQuery without checking that each leaf has points, so on a MultiReader whose other index stores the field without points it also matches (or, under MUST_NOT, drops) documents that have no point (finding LFV-1109; [repro](../../repro/LFV-1109/))

## Files

- [pointrange-rewrite-fieldexists-ignores-leaves-without-points/model/rewrite_soundness.py](pointrange-rewrite-fieldexists-ignores-leaves-without-points/model/rewrite_soundness.py) (used for LFV-1109)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
