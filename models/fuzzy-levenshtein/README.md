# Formal models and scripts: fuzzy-levenshtein

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- LuceneLevenshteinDistance.getDistance returns 0 for two empty strings and max(n, m) (for example 3.0) when one string is empty, instead of a similarity score (a 2011 regression from LUCENE-3662) (bug LFV-350; [repro](../../repro/LFV-350/))

## Files

- [model/fuzzy_prune_smt.py](model/fuzzy_prune_smt.py) (used for LFV-350)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
