# Formal models and scripts: knn-query-layer

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Vector similarity queries report isCacheable == true although their per-segment result depends on live docs, the search timeout and the filter, so a shared query cache serves one reader's or one search's result to another (bug LFV-106; [repro](../../repro/LFV-106/), [bug page](../../bugs/LFV-106.html))
- A kNN query built with a null KnnSearchStrategy, documented as 'the default strategy will be used', throws NullPointerException when a seeded HNSW search runs (SeededKnnVectorQuery, or the optimistic re-entry pass when a segment is re-entered) (bug LFV-114; [repro](../../repro/LFV-114/))
- A multi-segment kNN query's optimistic second pass, when cut short by the IndexSearcher timeout, replaces the hits a complete first pass already found, so a later timeout can return fewer or farther hits than an earlier one, down to 0 hits (bug LFV-117; [repro](../../repro/LFV-117/))
- PatienceKnnVectorQuery over a SeededKnnVectorQuery stores a searcher-specific seed Weight in its own field during rewrite, so every search changes the query's equals/hashCode, and searching one query object concurrently on two different readers fails (bug LFV-125; [repro](../../repro/LFV-125/))
- AbstractKnnVectorQuery#mergeLeafResults is documented to return hits in any order, but rewrite reads the k-th score from the last entry and DocAndScoreQuery the max score from the first, so an override that only reorders the hits changes the results (bug LFV-123; [repro](../../repro/LFV-123/))

## Files

- [model/KnnRewrite.tla](model/KnnRewrite.tla) (used for LFV-114, LFV-117, LFV-123)
- [model/MCKnnRewrite.tla](model/MCKnnRewrite.tla) (used for LFV-117)
- [model/MCKnnRewrite2.tla](model/MCKnnRewrite2.tla) (used for LFV-117)
- [model/MC_B_unfiltered_timeout.cfg](model/MC_B_unfiltered_timeout.cfg) (used for LFV-117)
- [model/MC_E_filtered_mixed_timeout.cfg](model/MC_E_filtered_mixed_timeout.cfg) (used for LFV-117)
- [model/MC_SharedDelegate_FixedFALSE.cfg](model/MC_SharedDelegate_FixedFALSE.cfg) (used for LFV-125)
- [model/MC_SharedDelegate_FixedTRUE.cfg](model/MC_SharedDelegate_FixedTRUE.cfg) (used for LFV-125)
- [model/MC_SimQueryCache_CacheableFALSE.cfg](model/MC_SimQueryCache_CacheableFALSE.cfg) (used for LFV-106)
- [model/MC_SimQueryCache_CacheableTRUE.cfg](model/MC_SimQueryCache_CacheableTRUE.cfg) (used for LFV-106)
- [model/SharedDelegate.tla](model/SharedDelegate.tla) (used for LFV-125)
- [model/SimQueryCache.tla](model/SimQueryCache.tla) (used for LFV-106)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).
