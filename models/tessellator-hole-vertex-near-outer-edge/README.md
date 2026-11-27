# Formal models and scripts: tessellator-hole-vertex-near-outer-edge

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- Tessellator can return a triangle that lies over part of a hole of a valid polygon when a hole vertex is within rounding of an outer edge: for one such polygon LatLonShape indexes it without an error and a point well inside the hole matches (finding LFV-1600; [repro](../../repro/LFV-1600/))

## Files

- [tools/mycheck.py](tools/mycheck.py) (used for LFV-1600)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
