# Formal models and scripts: docvaluesstats-sorted-double-stats-decode-sortable-doubles-as-raw-bits

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- SortedDoubleDocValuesStats (lucene/misc) reads core DoubleField values as raw IEEE bits, so negative values give a silently wrong min, sum and mean (DoubleField -5.0 and 3.0 give min -0.8749999999999999) (finding LFV-1129; [repro](../../repro/LFV-1129/), [finding page](../../bugs/LFV-1129.html))

## Files

- [sorteddoubledocvaluesstats-decodes-sortable-doublefield-as-raw-bits/model/mutation_fixed_decoder.py](sorteddoubledocvaluesstats-decodes-sortable-doublefield-as-raw-bits/model/mutation_fixed_decoder.py) (used for LFV-1129)
- [sorteddoubledocvaluesstats-decodes-sortable-doublefield-as-raw-bits/model/sortable_vs_raw.py](sorteddoubledocvaluesstats-decodes-sortable-doublefield-as-raw-bits/model/sortable_vs_raw.py) (used for LFV-1129)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
