# Formal models and scripts: boolean-rewrite-dispatch

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- BM25Similarity/StableTflSimilarity k3 query-term saturation is skipped for duplicate SHOULD clauses when 2 &lt;= minimumNumberShouldMatch &lt; number of SHOULD clauses (bug LFV-103; [repro](../../repro/LFV-103/))

## Files

- [k3-saturation-skipped-when-msm-ge-2/model/k3_dedup_z3.py](k3-saturation-skipped-when-msm-ge-2/model/k3_dedup_z3.py) (used for LFV-103)
- [k3-saturation-skipped-when-msm-ge-2/model/rewrite_rules_z3.py](k3-saturation-skipped-when-msm-ge-2/model/rewrite_rules_z3.py) (used for LFV-103)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
