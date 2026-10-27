# Formal models and scripts: numeric-dv-bulk-values

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Bulk NumericDocValues#longValues (new in 10.5.0) throws on a valid dense segment for one exact request size per byte-aligned width, because its size guard checks the unpadded byte count against Integer.MAX_VALUE and then adds the padding bytes in int (40/48-bit: wraps to Integer.MIN_VALUE, 'read past EOF'; 16/24/56-bit: a byte above the JVM limit) (bug LFV-381; [repro](../../repro/LFV-381/))

## Files

- [bulk-decode-size-guard-ignores-padding/model/q_bulk_bytecount.py](bulk-decode-size-guard-ignores-padding/model/q_bulk_bytecount.py) (used for LFV-381)
- [bulk-decode-size-guard-ignores-padding/model/q_bulk_bytecount_fixed.py](bulk-decode-size-guard-ignores-padding/model/q_bulk_bytecount_fixed.py) (used for LFV-381)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
