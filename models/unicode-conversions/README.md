# Formal models and scripts: unicode-conversions

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- A single indexed token of 715,827,878 or more UTF-16 units closes IndexWriter and drops uncommitted documents instead of being rejected as an immense term (bug LFV-385; [repro](../../repro/LFV-385/))

## Files

- [huge-token-utf8-sizing-closes-indexwriter/model/utf8_length_bounds_z3.py](huge-token-utf8-sizing-closes-indexwriter/model/utf8_length_bounds_z3.py) (used for LFV-385)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
