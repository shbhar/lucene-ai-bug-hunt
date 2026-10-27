# Formal models and scripts: misc-reorder-split

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- MultiPassIndexSplitter's round-robin split loses or duplicates the last few documents of an index at about IndexWriter.MAX_DOCS for most part counts of 145 or more, because its int test (j + numParts - i) % numParts overflows (bug LFV-531; [repro](../../repro/LFV-531/))

## Files

- [multipass-splitter-round-robin-int-overflow/model/multipass_partition.py](multipass-splitter-round-robin-int-overflow/model/multipass_partition.py) (used for LFV-531)
- [multipass-splitter-round-robin-int-overflow/model/multipass_rr_overflow_exhaustive.py](multipass-splitter-round-robin-int-overflow/model/multipass_rr_overflow_exhaustive.py) (used for LFV-531)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
