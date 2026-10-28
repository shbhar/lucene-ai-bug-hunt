# Formal models and scripts: groupingsearch-cache-limit-applied-per-slice-10-6

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- GroupingSearch.setCaching / setCachingInMB limit is applied per slice since #16247 (10.6.0 release-candidate regression): with an IndexSearcher that has an executor, a query matching more documents than the limit is replayed from per-slice caches holding up to slices x limit instead of being purged and re-run (results unchanged) (bug LFV-910; [repro](../../repro/LFV-910/))

## Files

- [groupingsearch-cache-limit-applied-per-slice/model/cache_bound.py](groupingsearch-cache-limit-applied-per-slice/model/cache_bound.py) (used for LFV-910)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
