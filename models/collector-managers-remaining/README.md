# Formal models and scripts: collector-managers-remaining

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- Grouping LongRangeFactory and DoubleRangeFactory give the last range below max the full width, so its reported group bounds run past max and overlap the range for values &gt;= max (finding LFV-407; [repro](../../repro/LFV-407/), [finding page](../../bugs/LFV-407.html))
- Grouping DoubleRangeFactory picks a value's bucket and computes that bucket's bounds with separately rounded arithmetic, so with a decimal width such as 0.1 the returned range can exclude the value and adjacent ranges leave gaps or overlap (DoubleRangeFactory(0, 0.1, 10).getRange(0.6) = DoubleRange(0.5, 0.6)) (finding LFV-412; [repro](../../repro/LFV-412/), [finding page](../../bugs/LFV-412.html))
- Grouping DoubleRangeFactory gives the below-min group the lower bound Double.MIN_VALUE (4.9E-324, the smallest positive double) instead of the most negative value, so that group's DoubleRange label does not contain its own members (finding LFV-411; [repro](../../repro/LFV-411/), [finding page](../../bugs/LFV-411.html))

## Files

- [doublerangefactory-below-min-label-double-min-value/model/range_factory.py](doublerangefactory-below-min-label-double-min-value/model/range_factory.py) (used for LFV-411)
- [doublerangefactory-label-boundaries-rounding-gap/model/range_factory.py](doublerangefactory-label-boundaries-rounding-gap/model/range_factory.py) (used for LFV-412)
- [range-factory-final-range-crosses-max/model/range_factory.py](range-factory-final-range-crosses-max/model/range_factory.py) (used for LFV-407)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
