# Formal models and scripts: docandscorequery-public-factory-count-and-iterator-contracts

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- DocAndScoreQuery.createDocAndScoreQuery (public on main since #16686) keeps repeated doc ids: a document listed twice is counted and returned twice, and a repeated first document of a segment is iterated by the previous segment as doc id == maxDoc (a deleted or MUST_NOT-excluded document comes back, or a search with deletions or a doc-values sort throws) (bug LFV-955; [repro](../../repro/LFV-955/))

## Files

- [docandscorequery-factory-accepts-repeated-docs/model/findsegstarts_z3.py](docandscorequery-factory-accepts-repeated-docs/model/findsegstarts_z3.py) (used for LFV-955)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
