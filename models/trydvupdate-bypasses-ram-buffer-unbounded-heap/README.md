# Formal models and scripts: trydvupdate-bypasses-ram-buffer-unbounded-heap

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- IndexWriter.tryUpdateDocValue never checks the RAM buffer for the doc-values updates it adds, so they pile up on the heap without bound until the next refresh, flush() or commit (finding LFV-1075; [repro](../../repro/LFV-1075/))

## Files

- [tryupdatedocvalue-skips-reader-pool-ram-check/model/predict_mbt.py](tryupdatedocvalue-skips-reader-pool-ram-check/model/predict_mbt.py) (used for LFV-1075)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
