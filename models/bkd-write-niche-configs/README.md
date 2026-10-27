# Formal models and scripts: bkd-write-niche-configs

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- BKDWriter does not cap its in-heap point buffer at the Java array limit, so with maxMBSortInHeap &gt;= 2048 MB a points merge of more than 2^31 bytes throws NegativeArraySizeException and closes the IndexWriter (bug LFV-613; [repro](../../repro/LFV-613/))

## Files

- [bkd-heap-sort-buffer-int-overflow/model/alloc_overflow.py](bkd-heap-sort-buffer-int-overflow/model/alloc_overflow.py) (used for LFV-613)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
