# Formal models and scripts: query-cache-correctness

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- LongRange/DoubleRange#getQuery(fastMatchQuery, valueSource) reports its Weight as cacheable without asking the fast-match query, so after an in-place doc-values update the query cache keeps returning the old matches (bug LFV-320; [repro](../../repro/LFV-320/))
- LateInteractionFloatValuesSource#isCacheable always returns true although it reads the multi-vectors from binary doc values, so after an in-place update of a LateInteractionField the query cache keeps returning the old matches of a FunctionMatchQuery or DoubleRange query over it (bug LFV-336; [repro](../../repro/LFV-336/))
- LatLonShape/XYShape newSlowDocValuesBoxQuery reports Weight#isCacheable == true although it reads the shape from binary doc values, so after an in-place doc-values update the query cache keeps returning the old matches (bug LFV-308; [repro](../../repro/LFV-308/))

## Files

- [facet-range-query-ignores-fastmatch-cacheability/model/MC_QueryCacheDV.tla](facet-range-query-ignores-fastmatch-cacheability/model/MC_QueryCacheDV.tla) (used for LFV-320)
- [facet-range-query-ignores-fastmatch-cacheability/model/MC_partial.cfg](facet-range-query-ignores-fastmatch-cacheability/model/MC_partial.cfg) (used for LFV-320)
- [facet-range-query-ignores-fastmatch-cacheability/model/QueryCacheDV.tla](facet-range-query-ignores-fastmatch-cacheability/model/QueryCacheDV.tla) (used for LFV-320)
- [late-interaction-values-source-cacheable-after-dv-update/model/MC_QueryCacheDV.tla](late-interaction-values-source-cacheable-after-dv-update/model/MC_QueryCacheDV.tla) (used for LFV-336)
- [late-interaction-values-source-cacheable-after-dv-update/model/MC_const.cfg](late-interaction-values-source-cacheable-after-dv-update/model/MC_const.cfg) (used for LFV-336)
- [late-interaction-values-source-cacheable-after-dv-update/model/QueryCacheDV.tla](late-interaction-values-source-cacheable-after-dv-update/model/QueryCacheDV.tla) (used for LFV-336)
- [shape-docvalues-query-cacheable-after-dv-update/model/MC_QueryCacheDV.tla](shape-docvalues-query-cacheable-after-dv-update/model/MC_QueryCacheDV.tla) (used for LFV-308)
- [shape-docvalues-query-cacheable-after-dv-update/model/MC_const.cfg](shape-docvalues-query-cacheable-after-dv-update/model/MC_const.cfg) (used for LFV-308)
- [shape-docvalues-query-cacheable-after-dv-update/model/QueryCacheDV.tla](shape-docvalues-query-cacheable-after-dv-update/model/QueryCacheDV.tla) (used for LFV-308)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).
