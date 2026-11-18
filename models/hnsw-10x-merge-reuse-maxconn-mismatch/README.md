# Formal models and scripts: hnsw-10x-merge-reuse-maxconn-mismatch

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- On 10.5.1, 10.5.2 and 10.6.0, an HNSW merge that reuses a graph written with a different maxConn can either fail and close the IndexWriter (maxConn raised) or commit a segment on which a kNN query throws when it reads a neighbour list longer than twice the new maxConn (maxConn lowered) (finding LFV-811; [repro](../../repro/LFV-811/))

## Files

- [hnsw-10x-merge-reuse-maxconn-mismatch/model/maxconn_flow.py](hnsw-10x-merge-reuse-maxconn-mismatch/model/maxconn_flow.py) (used for LFV-811)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
