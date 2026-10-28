# Formal models and scripts: ascii-folding-table-vs-unicode-character-database

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- ASCIIFoldingFilter folds six letters to the ASCII letter of the opposite case (ǥ U+01E5 and ǧ U+01E7 to 'G', insular ꞅ U+A785 to 'S' and Ꞅ U+A784 to 's', Ɐ U+2C6F to 'a', Ꜿ U+A73E to 'c'; stretched ʗ U+0297 to 'C' is contested), so a LowerCaseFilter-then-ASCIIFoldingFilter chain indexes 'naǧib' as 'naGib' and the ASCII query 'nagib' finds nothing (bug LFV-818; [repro](../../repro/LFV-818/), [bug page](../../bugs/LFV-818.html))

## Files

- [asciifolding-table-letters-in-wrong-case-group/model/move_cases.py](asciifolding-table-letters-in-wrong-case-group/model/move_cases.py) (used for LFV-818)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
