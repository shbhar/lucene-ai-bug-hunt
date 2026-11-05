# Formal models and scripts: brazilian-stemmer-step2-ira-branch-removes-ava

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- BrazilianStemmer step2 tests for the suffix 'ira' but removes 'ava', so -ira words (mentira, partirá) stay unstemmed and -ava verb forms (cantava, ficava) lose only the final 'a', and a search for one form misses the others (mentiras, cantavam, ficar) (finding LFV-1384; [repro](../../repro/LFV-1384/))

## Files

- [model/step2_rules.py](model/step2_rules.py) (used for LFV-1384)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
