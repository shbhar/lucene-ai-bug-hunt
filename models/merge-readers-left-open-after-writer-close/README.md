# Formal models and scripts: merge-readers-left-open-after-writer-close

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- With SerialMergeScheduler, a rollback() whose wait for a running merge is cut short by an interrupt can close the writer under that merge (run with a merge inside another thread's forceMerge(1) or flush() call): the merge then fails before it releases its segment readers, and the files they had open stay open after the writer is closed (known upstream: the interrupted wait is described on #6947) (finding LFV-1564; [repro](../../repro/LFV-1564/))

## Files

- [interrupted-abortmerges-wait-closes-reader-pool-under-running-merge/model/rollback_merge_handover.py](interrupted-abortmerges-wait-closes-reader-pool-under-running-merge/model/rollback_merge_handover.py) (used for LFV-1564)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
