# Formal models and scripts: cosine-zero-query-vector-silent-empty-results-known-15540-row-or-dnp

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- kNN and vector-similarity queries accept an all-zero query target on a COSINE field, which the field itself rejects; the cosine is NaN, so the search returns no hits or the HNSW entry document with score NaN, with no error (finding LFV-1141; [repro](../../repro/LFV-1141/))

## Files

- [model/cosine_zero_target.py](model/cosine_zero_target.py) (used for LFV-1141)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
