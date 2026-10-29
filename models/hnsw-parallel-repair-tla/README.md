# Formal models and scripts: hnsw-parallel-repair-tla

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- HNSW merge with graph reuse throws BufferAllocationException when the reused graph has more than 805,306,368 vectors (bug LFV-458; [repro](../../repro/LFV-458/))

## Files

- [hnsw-merge-ordmap-hashmap-capacity/model/capacity_bound.py](hnsw-merge-ordmap-hashmap-capacity/model/capacity_bound.py) (used for LFV-458)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
