# Formal models and scripts: cjk-compiled-dictionary-source-round-trip

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Nori's DictionaryBuilder checks that rows sharing a left context id have the same part of speech only with a Java assert, so with assertions off a custom system dictionary silently gives those words the part of speech of another row and KoreanPartOfSpeechStopFilter drops or keeps them wrongly (bug LFV-717; [repro](../../repro/LFV-717/))
- Nori's DictionaryBuilder rejects a left context id of 8192 or more but silently accepts such a right context id, which its 14-bit field cannot hold, so the compiled dictionary returns a wrapped right id (changing segmentation) or makes KoreanTokenizer throw IndexOutOfBoundsException (bug LFV-710; [repro](../../repro/LFV-710/))

## Files

- [model/explore_ko.py](model/explore_ko.py) (used for LFV-710, LFV-717)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
