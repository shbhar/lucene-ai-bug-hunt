# Formal models and scripts: analysis-factories-unread-parameters

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- MinHashFilterFactory, DropIfFlaggedFilterFactory, FixedShingleFilterFactory and PatternCaptureGroupFilterFactory accept a parameter they do not read instead of refusing it with IllegalArgumentException (the rule of LUCENE-4877 since 4.3.0), so with the other arguments valid a misspelt optional key, such as preserveOriginal for preserve_original, is ignored without an error (Lucene's own TestDropIfFlaggedFilterFactory passes such a key); run on 10.6.0, branch_11x, main and each factory's first release (4.4.0, 6.2.0, 7.4.0, 8.9.0); known outside Lucene (finding LFV-1546; [repro](../../repro/LFV-1546/))

## Files

- [model/item1-census/static_scan.py](model/item1-census/static_scan.py) (used for LFV-1546)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
