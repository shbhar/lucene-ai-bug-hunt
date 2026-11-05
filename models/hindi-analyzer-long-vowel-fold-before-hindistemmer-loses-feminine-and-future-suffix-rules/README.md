# Formal models and scripts: hindi-analyzer-long-vowel-fold-before-hindistemmer-loses-feminine-and-future-suffix-rules

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- HindiAnalyzer runs HindiNormalizationFilter before HindiStemFilter, so the 20 of HindiStemmer's 67 suffix rules that are spelled with a long vowel or candrabindu (-ती -tī, -ाती -ātī, -ेगी -egī, -ोगी -ogī, -ूंगा -ūṁgā, -ाई -āī, -नी -nī, ...) can never fire, and feminine and future verb forms keep part of their suffix: 'खाती' (khātī) is indexed as 'खात' (khāt) while 'खाता' (khātā) and 'खाते' (khāte) give 'खा' (khā) (finding LFV-1387; [repro](../../repro/LFV-1387/), [finding page](../../bugs/LFV-1387.html))

## Files

- [hindi-analyzer-normalizes-before-stemming-dead-long-vowel-suffix-rules/model/literal_fixpoint.py](hindi-analyzer-normalizes-before-stemming-dead-long-vowel-suffix-rules/model/literal_fixpoint.py) (used for LFV-1387)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
