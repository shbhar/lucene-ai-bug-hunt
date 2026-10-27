# Formal models and scripts: analysis-rule-tables-second-batch

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- SpanishPluralStemmer's special-case list has the singular 'contrarreloj' where the plural 'contrarrelojes' belongs, so the singular is cut to 'contrarrel', the plural stems to 'contrarreloje', and the two forms never match (bug LFV-688; [repro](../../repro/LFV-688/))

## Files

- [spanish-plural-special-case-contrarreloj-singular/model/es_plural_rows.py](spanish-plural-special-case-contrarreloj-singular/model/es_plural_rows.py) (used for LFV-688)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
