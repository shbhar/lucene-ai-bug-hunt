# Formal models and scripts: dismax-synonym-combined

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- SynonymQuery adds up boosted term frequencies in float in heap order, so identical documents can score differently and explain can differ from the hit score; with more than 2^24 occurrences in one field a score can exceed getMaxScore (bug LFV-272; [repro](../../repro/LFV-272/))

## Files

- [synonym-scorer-float-freq-sum/model/synonym_freq_lemmas.py](synonym-scorer-float-freq-sum/model/synonym_freq_lemmas.py) (used for LFV-272)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
