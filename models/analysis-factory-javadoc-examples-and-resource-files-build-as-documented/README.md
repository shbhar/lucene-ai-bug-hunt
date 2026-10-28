# Formal models and scripts: analysis-factory-javadoc-examples-and-resource-files-build-as-documented

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Eight analysis-factory class javadoc schema examples cannot be used as written: six are not well-formed XML (StopFilterFactory, JapaneseIterationMarkCharFilterFactory, BeiderMorseFilterFactory, OpenNLPTokenizerFactory, OpenNLPLemmatizerFilterFactory, DropIfFlaggedFilterFactory) and two name a class that is not a factory (LimitTokenOffsetFilterFactory's solr.LimitTokenOffsetFilter, PatternTypingFilterFactory's com.example.PatternTypingFilter) (documentation defect) (bug LFV-931; [repro](../../repro/LFV-931/))

## Files

- [harness/extract_examples.py](harness/extract_examples.py) (used for LFV-931)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
