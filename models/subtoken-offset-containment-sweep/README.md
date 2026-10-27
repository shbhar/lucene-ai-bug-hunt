# Formal models and scripts: subtoken-offset-containment-sweep

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- KoreanTokenizer gives the parts of a decompounded word offsets counted back from the end of the word while their text is cut from the start of the word, so when the parts do not cover the whole word (a user-dictionary entry with a shorter segmentation such as '세종시 세종', or 7 compounds of the bundled dictionary such as 픽토그램) the offsets point at other characters (픽토 at [1,3) covers '토그') (bug LFV-603; [repro](../../repro/LFV-603/))

## Files

- [nori-decompound-offsets-right-aligned-text-left-sliced/model/mecab_compound_check.py](nori-decompound-offsets-right-aligned-text-left-sliced/model/mecab_compound_check.py) (used for LFV-603)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
