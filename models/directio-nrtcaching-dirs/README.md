# Formal models and scripts: directio-nrtcaching-dirs

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- DirectIODirectory accepts a mergeBufferSize that is not a multiple of the file system block size, and then every direct-I/O read fails, so merges hit a tragic exception; a size below the block size can also give a zero-capacity write buffer (BufferOverflowException, or writeBytes making no progress) (bug LFV-226; [repro](../../repro/LFV-226/))
- A seek or skipBytes far past the end of a small DirectIODirectory slice throws IllegalArgumentException('Negative position') instead of EOFException, because pos + offset overflows (bug LFV-224; [repro](../../repro/LFV-224/))

## Files

- [directio-merge-buffer-size-not-block-multiple/model/dio_bmc.py](directio-merge-buffer-size-not-block-multiple/model/dio_bmc.py) (used for LFV-226)
- [directio-merge-buffer-size-not-block-multiple/model/dio_model.py](directio-merge-buffer-size-not-block-multiple/model/dio_model.py) (used for LFV-226)
- [directio-slice-seek-offset-overflow/model/dio_induct.py](directio-slice-seek-offset-overflow/model/dio_induct.py) (used for LFV-224)
- [directio-slice-seek-offset-overflow/model/dio_model.py](directio-slice-seek-offset-overflow/model/dio_model.py) (used for LFV-224)
- [directio-slice-seek-offset-overflow/model/dio_seek_overflow.py](directio-slice-seek-offset-overflow/model/dio_seek_overflow.py) (used for LFV-224)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
