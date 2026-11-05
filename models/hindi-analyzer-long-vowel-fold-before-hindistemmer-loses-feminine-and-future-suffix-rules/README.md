# Formal models and scripts: hindi-analyzer-long-vowel-fold-before-hindistemmer-loses-feminine-and-future-suffix-rules

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- HindiAnalyzer runs HindiNormalizationFilter before HindiStemFilter, so the 20 of HindiStemmer's 67 suffix rules that are spelled with a long vowel or candrabindu (-ती, -ाती, -ेगी, -ोगी, -ूंगा, -ाई, -नी, ...) can never fire, and feminine and future verb forms keep part of their suffix: 'खाती' is indexed as 'खात' while 'खाता' and 'खाते' give 'खा' (finding LFV-1387; [repro](../../repro/LFV-1387/))

## Files

- [hindi-analyzer-normalizes-before-stemming-dead-long-vowel-suffix-rules/model/literal_fixpoint.py](hindi-analyzer-normalizes-before-stemming-dead-long-vowel-suffix-rules/model/literal_fixpoint.py) (used for LFV-1387)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
