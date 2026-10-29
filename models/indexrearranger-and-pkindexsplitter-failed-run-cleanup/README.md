# Formal models and scripts: indexrearranger-and-pkindexsplitter-failed-run-cleanup

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- A failed IndexRearranger.execute throws without waiting for its other tasks, and closes the input reader and the IndexWriter while those tasks still use them (bug LFV-1011; [repro](../../repro/LFV-1011/))

## Files

- [indexrearranger-failed-execute-closes-writer-and-input-under-running-tasks/model/analyze_mbt.py](indexrearranger-failed-execute-closes-writer-and-input-under-running-tasks/model/analyze_mbt.py) (used for LFV-1011)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
