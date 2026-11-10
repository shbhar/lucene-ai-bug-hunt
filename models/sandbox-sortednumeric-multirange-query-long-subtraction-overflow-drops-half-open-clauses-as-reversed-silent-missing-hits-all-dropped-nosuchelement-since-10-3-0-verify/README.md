# Formal models and scripts: sandbox-sortednumeric-multirange-query-long-subtraction-overflow-drops-half-open-clauses-as-reversed-silent-missing-hits-all-dropped-nosuchelement-since-10-3-0-verify

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- Sandbox SortedNumericDocValuesMultiRangeQuery classifies each clause by the sign of 'lower - upper', which overflows, so a legal half-open clause such as [Long.MIN_VALUE, 10] or [-5, Long.MAX_VALUE] given together with another clause is silently dropped as if it were reversed (values 5 and 150 with the clauses [Long.MIN_VALUE, 10] and [100, 200] count 1, not 2); since 10.3.0 (finding LFV-1463; [repro](../../repro/LFV-1463/), [finding page](../../bugs/LFV-1463.html))

## Files

- [numeric-multirange-clause-classification-long-subtraction-overflow/model/q_classify.py](numeric-multirange-clause-classification-long-subtraction-overflow/model/q_classify.py) (used for LFV-1463)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
