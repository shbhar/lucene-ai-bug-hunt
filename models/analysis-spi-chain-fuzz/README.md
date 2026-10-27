# Formal models and scripts: analysis-spi-chain-fuzz

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- DictionaryCompoundWordTokenFilterFactory (main only) warns 'onlyLongestMatch has been removed' when the key is absent and stays silent when onlyLongestMatch="false" is passed (bug LFV-439; [repro](../../repro/LFV-439/))

## Files

- [dictionarycompound-factory-onlylongestmatch-ignored/model/scan_factories.py](dictionarycompound-factory-onlylongestmatch-ignored/model/scan_factories.py) (used for LFV-439)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
