# Formal models and scripts: indexwriter-rollback-throwing-infostream

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- If IndexWriter's InfoStream throws at the first log message of the rollback step ('rollback'), close() or rollback() throws and the writer is left closing, not closed: the write lock stays held and a later close() or rollback() waits until its thread is interrupted (run through rollback() on 25 releases from 2.9.4 to 10.6.0, on branch_11x and on main, and through close() on 10 of them from 4.10.0 on and on both branches; before 4.7.1 only a later close() waits; after a rollback() that threw while a prepareCommit() was pending, a later close() throws IllegalStateException instead of waiting when commit on close is on (the default; run on 10.6.0, branch_11x and main); the interrupt was run on 9.1.0, 10.6.0, branch_11x and main; needs an InfoStream that throws at that message) (finding LFV-1543; [repro](../../repro/LFV-1543/), [finding page](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1543.html))

## Files

- [rollback-first-log-line-throw-leaves-writer-closing-write-lock-held/model/census_table.py](rollback-first-log-line-throw-leaves-writer-closing-write-lock-held/model/census_table.py) (used for LFV-1543)
- [rollback-first-log-line-throw-leaves-writer-closing-write-lock-held/model/scan_sites.py](rollback-first-log-line-throw-leaves-writer-closing-write-lock-held/model/scan_sites.py) (used for LFV-1543)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
