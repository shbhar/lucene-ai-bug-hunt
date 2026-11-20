# Formal models and scripts: merge-commit-source-release-doc-values-updates

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- With reader pooling off, a merge commit can close IndexWriter with a tragic NoSuchFileException: it releases its merged-away source segments one by one, a release writes that segment's still-pending doc-values update and checkpoints, and the next source's overlay fold then opens an update file that this checkpoint deleted (branch_11x and main with the default overlay limit, 10.6.0 with setMaxDocValuesOverlays set; known upstream, #17048 and #11750) (finding LFV-1563; [repro](../../repro/LFV-1563/))

## Files

- [merge-commit-writes-merged-away-sources-pending-dv-updates-and-checkpoints-between-them/model/commit_release_model.py](merge-commit-writes-merged-away-sources-pending-dv-updates-and-checkpoints-between-them/model/commit_release_model.py) (used for LFV-1563)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
