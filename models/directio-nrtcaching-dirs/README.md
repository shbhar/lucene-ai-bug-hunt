# Formal models and scripts: directio-nrtcaching-dirs

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- DirectIODirectory accepts a mergeBufferSize that is not a multiple of the file system block size, and then every direct-I/O read fails, so merges hit a tragic exception; a size below the block size can also give a zero-capacity write buffer (BufferOverflowException, or writeBytes making no progress) (bug LFV-226; [repro](../../repro/LFV-226/))

## Files

- [directio-merge-buffer-size-not-block-multiple/model/dio_bmc.py](directio-merge-buffer-size-not-block-multiple/model/dio_bmc.py) (used for LFV-226)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
