# Formal models and scripts: termvectors-compressing

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- addIndexes(CodecReader) from a reader whose term vectors list a field with zero terms writes a term-vector chunk that the default codec cannot read back, and every later merge that re-encodes the segment closes the IndexWriter (bug LFV-244; [repro](../../repro/LFV-244/), [bug page](../../bugs/LFV-244.html))

## Files

- [tv-writer-accepts-zero-term-field/model/tv_chunk_layout.py](tv-writer-accepts-zero-term-field/model/tv_chunk_layout.py) (used for LFV-244)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
