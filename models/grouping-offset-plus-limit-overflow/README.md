# Formal models and scripts: grouping-offset-plus-limit-overflow

Tools: Python.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- GroupingSearch with groupLimit or groupDocsLimit = Integer.MAX_VALUE ('all remaining groups/documents') throws NegativeArraySizeException, OutOfMemoryError or an IllegalArgumentException naming -2147483648, because it does not cap the limits at maxDoc as IndexSearcher.search does and its collectors add offset + limit in int (every release since 4.0.0) (bug LFV-964; [repro](../../repro/LFV-964/))
- SearchGroup.merge (the shard-merge helper of grouped search) with topN = Integer.MAX_VALUE ('all remaining groups') throws OutOfMemoryError because it pre-sizes its result list by topN (regression in 6.2.0, LUCENE-7356), and with offset &gt;= 1 throws NullPointerException because offset + topN wraps to a negative queue bound (since 3.3.0) (bug LFV-958; [repro](../../repro/LFV-958/))
- TopGroups.merge throws NegativeArraySizeException when docOffset + docTopN overflows int, for example docOffset = 1 with docTopN = Integer.MAX_VALUE ('all remaining documents'), although docOffset 0 with the same docTopN works (bug LFV-960; [repro](../../repro/LFV-960/))

## Files

- [groupingsearch-limits-not-capped-at-maxdoc/model/overflow_sites.py](groupingsearch-limits-not-capped-at-maxdoc/model/overflow_sites.py) (used for LFV-964)
- [searchgroup-merge-presized-by-topn/model/overflow_sites.py](searchgroup-merge-presized-by-topn/model/overflow_sites.py) (used for LFV-958)
- [topgroups-merge-docoffset-plus-doctopn-overflow/model/overflow_sites.py](topgroups-merge-docoffset-plus-doctopn-overflow/model/overflow_sites.py) (used for LFV-960)

## How to run

- Python: `python3 <file>.py`; scripts that import `z3` need the `z3-solver` package.
