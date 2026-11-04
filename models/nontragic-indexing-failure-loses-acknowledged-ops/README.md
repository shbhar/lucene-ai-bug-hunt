# Formal models and scripts: nontragic-indexing-failure-loses-acknowledged-ops

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- After addDocument fails with a non-tragic exception while an in-memory segment copies its backlog of buffered deletes and doc-values updates, the segment's next document resets its delete slice past the operations that were never applied, so IndexWriter stays open and the next successful commit silently lacks an acknowledged delete of a buffered document (finding LFV-1090; [repro](../../repro/LFV-1090/), [finding page](../../bugs/LFV-1090.html))

## Files

- [finishdocuments-slice-reset-skips-unapplied-deletes/model/slice_model.py](finishdocuments-slice-reset-skips-unapplied-deletes/model/slice_model.py) (used for LFV-1090)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
