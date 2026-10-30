# Formal models and scripts: reqexcl-bulkscorer-excluded-run-skip-16669

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Since #16669 (10.6.0 RC1 and main), ReqExclBulkScorer no longer skips long runs of excluded doc IDs: for +R -X it scores the required clause in every 4096-doc window, even windows X fully excludes, so R's iterator steps through all of its matches inside X's runs (10,001 moves for 1,000 hits in a 10,000-doc segment, where 10.0.0-10.5.2 need 1,001; performance regression, results correct) (bug LFV-1044; [repro](../../repro/LFV-1044/))

## Files

- [reqexcl-bulkscorer-excluded-run-skip-lost-16669/model/ReqExclCostModel.py](reqexcl-bulkscorer-excluded-run-skip-lost-16669/model/ReqExclCostModel.py) (used for LFV-1044)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
