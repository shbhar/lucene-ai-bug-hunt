# Formal models and scripts: sandbox-multirange-point-builders-and-phrasewildcardquery-builder-hand-their-own-list-to-built-queries-builder-reuse-rewrites-earlier-query-and-its-cache-key-since-8-3-0-verify-low

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- Sandbox MultiRangeQuery point builders (IntPoint, LongPoint, FloatPoint, DoublePointMultiRangeBuilder) and PhraseWildcardQuery.Builder hand the built query the builder's own list, so adding to the builder after build() silently changes the earlier query's hits, equals/hashCode and toString (since 8.3.0) (finding LFV-1469; [repro](../../repro/LFV-1469/), [finding page](../../bugs/LFV-1469.html))

## Files

- [model/reuse_scan.py](model/reuse_scan.py) (used for LFV-1469)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
