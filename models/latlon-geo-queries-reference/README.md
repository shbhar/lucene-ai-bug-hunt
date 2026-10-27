# Formal models and scripts: latlon-geo-queries-reference

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- LatLonPoint and LatLonDocValuesField box queries at exactly latitude 90 (box lat 90..90) or longitude 180..180 match nothing through newBoxQuery/newSlowBoxQuery but match the edge point through the Rectangle2D-based paths of the same field, so one point is reported as neither INTERSECTS nor DISJOINT (8.8.0 to main) (bug LFV-578; [repro](../../repro/LFV-578/))

## Files

- [model/smt/encode_ceil.py](model/smt/encode_ceil.py) (used for LFV-578)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
