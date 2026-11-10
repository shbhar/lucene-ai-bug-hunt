# Formal models and scripts: latlonshape-slow-docvalues-box-query-crossing-contains-runs-points-index-clauses-silent-zero-instead-of-rejection-low-plus-index-vs-docvalues-parity-leg-gpt-records-line

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- LatLonShape.newSlowDocValuesBoxQuery rejects CONTAINS for every box except a dateline-crossing one, where it returns a points-index query that silently matches nothing on a doc-values-only field (since 9.4.0) (finding LFV-1474; [repro](../../repro/LFV-1474/), [finding page](../../bugs/LFV-1474.html))

## Files

- [latlonshape-slow-docvalues-box-query-crossing-contains-skips-relation-check/model/smt/factory_branches.py](latlonshape-slow-docvalues-box-query-crossing-contains-skips-relation-check/model/smt/factory_branches.py) (used for LFV-1474)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
