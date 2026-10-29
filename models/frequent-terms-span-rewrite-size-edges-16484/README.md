# Formal models and scripts: frequent-terms-span-rewrite-size-edges-16484

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- SpanMultiTermQueryWrapper.FrequentTermsSpanBooleanQueryRewrite (new in 10.6.0) sizes its priority queue from maxSize before collecting any term, so maxSize &gt;= 2147483631 (for example Integer.MAX_VALUE as 'no limit') makes every rewrite throw IllegalArgumentException, and a large maxSize allocates a (maxSize + 1)-slot array on every rewrite (bug LFV-944; [repro](../../repro/LFV-944/), [bug page](../../bugs/LFV-944.html))

## Files

- [frequent-terms-span-rewrite-heap-sized-by-maxsize/model/pq_sizing.py](frequent-terms-span-rewrite-heap-sized-by-maxsize/model/pq_sizing.py) (used for LFV-944)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
