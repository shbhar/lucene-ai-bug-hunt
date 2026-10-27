# Formal models and scripts: japanese-completion-fifo

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- JapaneseCompletionFilter expands a reading into every combination of romaji spellings with no bound, so 24-32 kana (for example 32 x "ン", or 32 x "し" in QUERY mode) throw OutOfMemoryError inside one incrementToken call (bug LFV-346; [repro](../../repro/LFV-346/))

## Files

- [romaji-expansion-unbounded-cartesian-product/model/romaji_growth.py](romaji-expansion-unbounded-cartesian-product/model/romaji_growth.py) (used for LFV-346)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
