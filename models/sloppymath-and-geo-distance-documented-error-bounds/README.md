# Formal models and scripts: sloppymath-and-geo-distance-documented-error-bounds

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Rectangle.fromPointDistance throws 'invalid longitude NaN' for some legal circles whose edge stops just short of a pole, so LatLon distance queries on them fail with IllegalArgumentException (bug LFV-771; [repro](../../repro/LFV-771/))

## Files

- [rectangle-frompointdistance-asin-arg-above-one-nan/model/box_nan_smt.py](rectangle-frompointdistance-asin-arg-above-one-nan/model/box_nan_smt.py) (used for LFV-771)
- [rectangle-frompointdistance-asin-arg-above-one-nan/model/cos_knot_smt.py](rectangle-frompointdistance-asin-arg-above-one-nan/model/cos_knot_smt.py) (used for LFV-771)
- [rectangle-frompointdistance-asin-arg-above-one-nan/model/cos_knot_smt2.py](rectangle-frompointdistance-asin-arg-above-one-nan/model/cos_knot_smt2.py) (used for LFV-771)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
