# Formal models and scripts: datainput-impls-strings

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- ByteBuffersDataOutput.copyBytes narrows its long length to a signed int, so a copy of 2 GiB or more (for example Directory.copyFrom into a ByteBuffersDirectory) silently copies nothing or only (int) numBytes bytes (bug LFV-107; [repro](../../repro/LFV-107/))

## Files

- [bbdo-copybytes-int-truncation/model/copybytes_cast_z3.py](bbdo-copybytes-int-truncation/model/copybytes_cast_z3.py) (used for LFV-107)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
