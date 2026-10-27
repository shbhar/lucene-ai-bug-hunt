# Formal models and scripts: postings-lucene104

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Since 9.12.0 the default postings writer stores the byte length of a level-1 block's competitive impacts in a signed short, so with a custom Similarity whose norms do not fit in a byte, a term with 8,192 or more postings in one segment (4,096 before 10.4) can need more than 32,767 bytes: flush fails an assertion with -ea, and with -da the segment is written but that term's postings then throw on every read or are read back wrong (bug LFV-183; [repro](../../repro/LFV-183/))
- Postings docIDRunEnd returns Integer.MIN_VALUE for dense blocks after a term's last level-1 skip block when doc IDs are within 8,192 (4,096 before 10.4) of 2^31, so CheckIndex at level 2 or higher reports a healthy segment of about 2^31 documents as corrupt (bug LFV-170; [repro](../../repro/LFV-170/))

## Files

- [docidrunend-level1-tail-overflow/model/skip_bounds.py](docidrunend-level1-tail-overflow/model/skip_bounds.py) (used for LFV-170)
- [level1-impacts-length-short-overflow/model/skip_bounds.py](level1-impacts-length-short-overflow/model/skip_bounds.py) (used for LFV-183)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
