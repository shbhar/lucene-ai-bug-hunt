# Formal models and scripts: signed-zero-equals-hashcode-remaining-query-valuesource-sort-sites

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- spatial3d Vector (and so GeoPoint and the Geo3DPoint distance and box queries), Plane and the longitude-slice and degenerate latitude/longitude shapes compare doubles with == in equals() but hash Double.doubleToLongBits, so objects built with 0.0 and -0.0 are equal yet have different hash codes (since 5.2.0) (finding LFV-1341; [repro](../../repro/LFV-1341/), [finding page](../../bugs/LFV-1341.html))
- Six function value sources (ConstValueSource, DoubleConstValueSource, LinearFloatFunction, ScaleFloatFunction, RangeMapFloatFunction, ReciprocalFloatFunction) and CommonTermsQuery's minimum-should-match fractions compare float/double parameters with == in equals() but hash their bits, so objects built with 0.0 and -0.0 are equal yet have different hash codes (since 4.0.0) (finding LFV-1335; [repro](../../repro/LFV-1335/), [finding page](../../bugs/LFV-1335.html))
- BlendedTermQuery.DisjunctionMaxRewrite, LatLonPoint.newDistanceFeatureQuery and XYDocValuesField.newDistanceSort compare float/double parameters with == in equals() but hash their bits, so objects built with 0.0 and -0.0 are equal yet have different hash codes (since 5.3.0) (finding LFV-1337; [repro](../../repro/LFV-1337/), [finding page](../../bugs/LFV-1337.html))

## Files

- [model/bytecode_scan.py](model/bytecode_scan.py) (used for LFV-1335, LFV-1337, LFV-1341)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
