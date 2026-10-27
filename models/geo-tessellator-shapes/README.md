# Formal models and scripts: geo-tessellator-shapes

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- ShapeField.encodeTriangle puts the wrong edge flag on the first edge when it flips a clockwise triangle, so LatLonShape and XYShape CONTAINS queries return wrong hits (bug LFV-204; [repro](../../repro/LFV-204/), [bug page](../../bugs/LFV-204.html))

## Files

- [encodetriangle-cw-swap-edge-flags/model/encode_triangle.py](encodetriangle-cw-swap-edge-flags/model/encode_triangle.py) (used for LFV-204)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
