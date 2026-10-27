# Formal models and scripts: smartcn-hhmm-segmentation-reference

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- smartcn: the double-hashing step of BigramDictionary (AbstractDictionary.hash2, an operator-precedence slip) is 0 for some Chinese word + ASCII or Latin-1 punctuation pairs, so each such bigram lookup re-reads one slot 402,136 times (about 0.7 ms instead of about 0.1 us) and SmartChineseAnalyzer analyzes ordinary prose 2-7x slower and text dense in such pairs 67x-174x slower, with unchanged tokens (bug LFV-709; [repro](../../repro/LFV-709/))

## Files

- [bigramdictionary-zero-probe-step-full-table-scan/model/hash2_step.py](bigramdictionary-zero-probe-step-full-table-scan/model/hash2_step.py) (used for LFV-709)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
