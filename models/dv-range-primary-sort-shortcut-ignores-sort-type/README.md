# Formal models and scripts: dv-range-primary-sort-shortcut-ignores-sort-type

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Doc-values exact and range queries silently miss or add documents on a skip-indexed field that holds raw float/double bits and that the index is sorted by as FLOAT or DOUBLE: SortedNumericDocValuesRangeQuery's index-sort shortcut checks the sort field's name but not its type (bug LFV-1047; [repro](../../repro/LFV-1047/))

## Files

- [dv-range-primary-sort-shortcut-ignores-sort-type/model/sortkey_order_embedding.py](dv-range-primary-sort-shortcut-ignores-sort-type/model/sortkey_order_embedding.py) (used for LFV-1047)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
