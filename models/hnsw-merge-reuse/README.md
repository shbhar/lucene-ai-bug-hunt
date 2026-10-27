# Formal models and scripts: hnsw-merge-reuse

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- HNSW merge computes a graph's deleted percentage in int, so a graph with more than 21,474,836 deleted vectors (41-100% deleted) passes the 40% DELETE_PCT_THRESHOLD and is reused as the merge base; on main before #16794 the opt-in concurrent merger then threw ArrayIndexOutOfBoundsException for an all-deleted base (bug LFV-160; [repro](../../repro/LFV-160/))

## Files

- [hnsw-merge-base-deletepct-int-overflow/model/q_deletepct_overflow.py](hnsw-merge-base-deletepct-int-overflow/model/q_deletepct_overflow.py) (used for LFV-160)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
