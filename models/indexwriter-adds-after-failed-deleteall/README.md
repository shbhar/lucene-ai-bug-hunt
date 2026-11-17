# Formal models and scripts: indexwriter-adds-after-failed-deleteall

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- If IndexWriter's InfoStream throws while deleteAll() discards a buffered in-memory segment, the discarded buffer stays in the writer's pool and the writer stays open, so documents added afterwards can be missing from later commits that return normally (every release from 8.6.0 to 10.6.0, branch_11x and main) (finding LFV-1531; [repro](../../repro/LFV-1531/), [finding page](../../bugs/LFV-1531.html))

## Files

- [doonabort-stall-update-throw-leaves-aborted-buffer-in-pool/model/mbt/analyze.py](doonabort-stall-update-throw-leaves-aborted-buffer-in-pool/model/mbt/analyze.py) (used for LFV-1531)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
