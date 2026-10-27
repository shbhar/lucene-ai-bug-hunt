# Formal models and scripts: count-stats-modules

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- DynamicRangeUtil computes DynamicRangeInfo.centroid ('the average value in the range') from an unchecked long sum of the range's values, so when one range's values sum past Long.MAX_VALUE (for example six epoch-nanosecond timestamps of today) it silently returns a centroid outside [min, max], typically negative for positive values (bug LFV-428; [repro](../../repro/LFV-428/))

## Files

- [dynamic-range-centroid-long-sum-overflow/model/centroid_overflow_z3.py](dynamic-range-centroid-long-sum-overflow/model/centroid_overflow_z3.py) (used for LFV-428)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
