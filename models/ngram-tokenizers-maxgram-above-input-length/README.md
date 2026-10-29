# Formal models and scripts: ngram-tokenizers-maxgram-above-input-length

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- NGramTokenizer and EdgeNGramTokenizer accept maxGram = Integer.MAX_VALUE, but their int buffer sizing wraps to a 1022-char read buffer and leaves the 16-char default term buffer, so the first text of 17 or more chars throws (ArrayIndexOutOfBoundsException up to 1021 chars, an unrelated IllegalArgumentException from 1022) (bug LFV-1007; [repro](../../repro/LFV-1007/))

## Files

- [ngram-tokenizer-maxgram-unbounded-buffer-size-int-overflow/model/ngram_int32.py](ngram-tokenizer-maxgram-unbounded-buffer-size-int-overflow/model/ngram_int32.py) (used for LFV-1007)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
