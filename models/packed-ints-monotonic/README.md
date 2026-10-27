# Formal models and scripts: packed-ints-monotonic

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- (Latent) MonotonicBlockPackedWriter writes a block whose values end close to Long.MAX_VALUE with too few bits, so MonotonicBlockPackedReader returns wrong values: min + (long) (avg * i) overflows in flush (bug LFV-148; [repro](../../repro/LFV-148/))

## Files

- [monotonic-block-packed-writer-min-overflow/model/monotonic_block_writer.py](monotonic-block-packed-writer-min-overflow/model/monotonic_block_writer.py) (used for LFV-148)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
