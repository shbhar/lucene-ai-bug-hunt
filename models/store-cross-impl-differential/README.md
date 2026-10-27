# Formal models and scripts: store-cross-impl-differential

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- BufferedIndexInput.refill marks its buffer as filled before readInternal succeeds, so after a failed read the next reads of the same input return up to one buffer of stale bytes instead of re-reading or throwing (regression of LUCENE-1262 since 8.6.0) (bug LFV-492; [repro](../../repro/LFV-492/))

## Files

- [bufferedindexinput-refill-commits-state-before-read/model/bii_refill_model.py](bufferedindexinput-refill-commits-state-before-read/model/bii_refill_model.py) (used for LFV-492)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
