# Formal models and scripts: bulk-overload-precondition-matrix

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Multi-dimensional PointInSetQuery skips segments that contain matching points, because its segment early exit uses the lexicographically first and last set points as per-dimension bounds (bug LFV-475; [repro](../../repro/LFV-475/), [bug page](../../bugs/LFV-475.html))

## Files

- [pointinset-multidim-lexicographic-bounds-early-exit/model/early_exit_smt.py](pointinset-multidim-lexicographic-bounds-early-exit/model/early_exit_smt.py) (used for LFV-475)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
