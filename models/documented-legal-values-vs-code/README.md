# Formal models and scripts: documented-legal-values-vs-code

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- FieldType's javadoc still describes null as 'no doc values': setDocValuesType documents '@param type DocValues type, or null if no DocValues should be stored' but null throws NullPointerException, and docValuesType() documents 'The default is null' but returns DocValuesType.NONE (documentation defect, stale since 5.0.0; the code is intended and must not change) (finding LFV-1498; [repro](../../repro/LFV-1498/), [finding page](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1498.html))

## Files

- [fieldtype-setdocvaluestype-javadoc-allows-null/model/nullable_conflicts.py](fieldtype-setdocvaluestype-javadoc-allows-null/model/nullable_conflicts.py) (used for LFV-1498)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
