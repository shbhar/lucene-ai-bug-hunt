# Formal models and scripts: migrate-upgrade-example-forcemerge-keeps-old-format

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- MIGRATE.md's 11.0 'Upgrade Example' says forceMerge(1) 'Rewrites all segments to latest format', but with the default merge policy it leaves an index that already has one segment and no deletions unchanged in its 9.x or 10.x format (documentation defect, main only; the code is correct) (bug LFV-1030; [repro](../../repro/LFV-1030/))

## Files

- [model/forcemerge_model.py](model/forcemerge_model.py) (used for LFV-1030)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
