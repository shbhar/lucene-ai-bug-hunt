# Formal models and scripts: spatial-extras-composite-and-prefix-tree-query-cross-check

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- PackedQuadPrefixTree in a geo context indexes a point at longitude +180 under the -180 edge cell, but point queries look in the +180 edge cell, so RPT and CompositeSpatialStrategy Intersects/Contains with that same point miss the document (bug LFV-705; [repro](../../repro/LFV-705/), [bug page](../../bugs/LFV-705.html))

## Files

- [packedquad-geo-plus180-point-index-query-cell-mismatch/model/smt/tiebreak.py](packedquad-geo-plus180-point-index-query-cell-mismatch/model/smt/tiebreak.py) (used for LFV-705)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
