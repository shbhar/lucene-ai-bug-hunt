# Formal models and scripts: query-object-contracts

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- DisjunctionMaxQuery and LatLonPoint.newDistanceFeatureQuery accept a NaN tie-breaker or pivot distance (the pivot also +Infinity) because their range checks are false for NaN, so hits score NaN and a NaN query is not equal to itself (bug LFV-304; [repro](../../repro/LFV-304/))

## Files

- [query-range-checks-admit-nan/model/fp_param_contracts.py](query-range-checks-admit-nan/model/fp_param_contracts.py) (used for LFV-304)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
