# Formal models and scripts: concurrent-collector-managers

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- IndexSearcher throws IllegalArgumentException for any reader that contains a leaf with maxDoc == 0 (for example a ParallelLeafReader with no sub-readers): the whole-segment LeafReaderContextPartition of that leaf fails a bounds check meant for real sub-range partitions (regression in 10.0.0) (bug LFV-202; [repro](../../repro/LFV-202/))

## Files

- [leafslice-rejects-zero-doc-leaf/model/slice_partitions.py](leafslice-rejects-zero-doc-leaf/model/slice_partitions.py) (used for LFV-202)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
