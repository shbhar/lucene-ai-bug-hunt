# Formal models and scripts: hnsw-10x-merge-reuse-maxconn-mismatch

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- On 10.5.1, 10.5.2 and branch_10_6 (the 10.6.0 RC1 revision), an HNSW merge that reuses a graph written with a different maxConn either fails and closes the IndexWriter (maxConn raised) or commits a segment that no kNN query can search (maxConn lowered) (bug LFV-811; [repro](../../repro/LFV-811/))

## Files

- [hnsw-10x-merge-reuse-maxconn-mismatch/model/maxconn_flow.py](hnsw-10x-merge-reuse-maxconn-mismatch/model/maxconn_flow.py) (used for LFV-811)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
