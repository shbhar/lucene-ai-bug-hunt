# Formal models and scripts: spatial3d-geometry

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Geo3DPoint distance queries with a radius below about 1 km silently return wrong results in both directions: each document's decoded point lies a few millimetres off the planet surface and is tested against a circle plane nearly tangent to the surface, so a 10 m query misses documents at its own centre and matches documents about 100 m away (bug LFV-209; [repro](../../repro/LFV-209/))

## Files

- [geo3d-offsurface-decoded-point-tangent-plane/model/cap_quantization.py](geo3d-offsurface-decoded-point-tangent-plane/model/cap_quantization.py) (used for LFV-209)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
