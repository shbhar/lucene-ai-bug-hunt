# Formal models and scripts

AI-assisted findings, confirmed by automated checks; apart from the bugs whose page names a maintainer triage, not yet triaged by a human Lucene committer; this site files nothing upstream.

The small text models (TLA+, Alloy, SMT-LIB, Dafny) and Python scripts (SMT queries, exhaustive checkers, scanners) of the parts of the hunt that found a public bug. A unit lists the bugs its files were used for; that a unit holds a model does not mean the model gave the first signal (each bug's record names its first finder).

| Unit | Tools | Bugs found |
|---|---|---|
| [analysis-factory-javadoc-examples-and-resource-files-build-as-documented](analysis-factory-javadoc-examples-and-resource-files-build-as-documented/) | Python | LFV-931 |
| [analysis-loaders-release-streams-and-temp-files-on-failure](analysis-loaders-release-streams-and-temp-files-on-failure/) | TLA+/TLC | LFV-1006, LFV-1010 |
| [analysis-rule-tables-second-batch](analysis-rule-tables-second-batch/) | Python | LFV-688 |
| [analysis-spi-chain-fuzz](analysis-spi-chain-fuzz/) | Python | LFV-439 |
| [ascii-folding-table-vs-unicode-character-database](ascii-folding-table-vs-unicode-character-database/) | Python | LFV-818 |
| [assert-only-input-checks-sweep-siblings-of-16919](assert-only-input-checks-sweep-siblings-of-16919/) | Python | LFV-1013 |
| [bitsets-livedocs-acceptdocs](bitsets-livedocs-acceptdocs/) | Python | LFV-139, LFV-147, LFV-137, LFV-133 |
| [bkd-write-niche-configs](bkd-write-niche-configs/) | Python | LFV-613 |
| [boolean-rewrite-dispatch](boolean-rewrite-dispatch/) | Python | LFV-103 |
| [bulk-overload-precondition-matrix](bulk-overload-precondition-matrix/) | Python | LFV-475 |
| [bytebuffers-output-block-expansion-recycler-and-view-aliasing](bytebuffers-output-block-expansion-recycler-and-view-aliasing/) | TLA+/TLC | LFV-836 |
| [cjk-compiled-dictionary-source-round-trip](cjk-compiled-dictionary-source-round-trip/) | Python | LFV-717, LFV-710 |
| [classification](classification/) | Python | LFV-382, LFV-380, LFV-395, LFV-388 |
| [codec-format-javadoc-grammar-overflow-liv-cfe-extension-table](codec-format-javadoc-grammar-overflow-liv-cfe-extension-table/) | Python | LFV-972, LFV-970, LFV-974, LFV-971 |
| [codecreader-wrappers](codecreader-wrappers/) | Python | LFV-278, LFV-318, LFV-285 |
| [columnar-addbatch](columnar-addbatch/) | Python | LFV-097, LFV-101, LFV-094 |
| [concurrent-collector-managers](concurrent-collector-managers/) | Python | LFV-202 |
| [core-fst-package-examples-compile-and-print-documented-outputs](core-fst-package-examples-compile-and-print-documented-outputs/) | Python | LFV-1027 |
| [count-stats-modules](count-stats-modules/) | Python | LFV-428 |
| [count-totalhits](count-totalhits/) | Alloy | LFV-086 |
| [datainput-impls-strings](datainput-impls-strings/) | Python | LFV-107 |
| [directio-nrtcaching-dirs](directio-nrtcaching-dirs/) | Python | LFV-226 |
| [dismax-synonym-combined](dismax-synonym-combined/) | Python | LFV-272 |
| [distance-feature-pruning](distance-feature-pruning/) | SMT-LIB (Z3/cvc5), Python | LFV-379 |
| [docandscorequery-public-factory-count-and-iterator-contracts](docandscorequery-public-factory-count-and-iterator-contracts/) | Python | LFV-955 |
| [document-field-factories](document-field-factories/) | Python | LFV-140 |
| [dv-norms-cursor-coherence](dv-norms-cursor-coherence/) | Python | LFV-415, LFV-419, LFV-418 |
| [dw-flush-control-deletequeue-tla](dw-flush-control-deletequeue-tla/) | TLA+/TLC | LFV-473, LFV-474, LFV-471 |
| [executor-pluggable-behaviour-sweep](executor-pluggable-behaviour-sweep/) | TLA+/TLC | LFV-637 |
| [explanation-formula-strings-vs-their-own-operand-values](explanation-formula-strings-vs-their-own-operand-values/) | Python | LFV-922, LFV-921, LFV-920 |
| [expressions-shallow-large-expression-escapes-documented-exceptions](expressions-shallow-large-expression-escapes-documented-exceptions/) | Python | LFV-906 |
| [facet-top-ordinal-queues-nan-and-special-value-order](facet-top-ordinal-queues-nan-and-special-value-order/) | SMT-LIB (Z3/cvc5), Python | LFV-825, LFV-820 |
| [facets-topn-and-topndims-queues-presized-uncapped](facets-topn-and-topndims-queues-presized-uncapped/) | Python | LFV-994, LFV-983 |
| [failed-commit-then-crash-durability-single-fault](failed-commit-then-crash-durability-single-fault/) | TLA+/TLC | LFV-839 |
| [feature-saturation-sigmoid-cancellation](feature-saturation-sigmoid-cancellation/) | SMT-LIB (Z3/cvc5) | LFV-898 |
| [featurefield](featurefield/) | SMT-LIB (Z3/cvc5) | LFV-258 |
| [frequent-terms-span-rewrite-size-edges-16484](frequent-terms-span-rewrite-size-edges-16484/) | Python | LFV-944 |
| [frozen-updates-apply-merge-carryover-tla](frozen-updates-apply-merge-carryover-tla/) | TLA+/TLC | LFV-596 |
| [function-queries-bmw](function-queries-bmw/) | SMT-LIB (Z3/cvc5), Python | LFV-211, LFV-203, LFV-189, LFV-214, LFV-221 |
| [fuzzy-levenshtein](fuzzy-levenshtein/) | Python | LFV-350 |
| [geo-tessellator-shapes](geo-tessellator-shapes/) | Python | LFV-204 |
| [geo-text-parsers-deep-nesting-vm-errors-siblings-of-16423](geo-text-parsers-deep-nesting-vm-errors-siblings-of-16423/) | Python | LFV-952 |
| [grouping-offset-plus-limit-overflow](grouping-offset-plus-limit-overflow/) | Python | LFV-964, LFV-958, LFV-960 |
| [groupingsearch-cache-limit-applied-per-slice-10-6](groupingsearch-cache-limit-applied-per-slice-10-6/) | Python | LFV-910 |
| [hardlink-copy-unwrap-bypasses-intermediate-directory-wrappers](hardlink-copy-unwrap-bypasses-intermediate-directory-wrappers/) | TLA+/TLC | LFV-912 |
| [hnsw-10x-merge-reuse-maxconn-mismatch](hnsw-10x-merge-reuse-maxconn-mismatch/) | Python | LFV-811 |
| [hnsw-merge-reuse](hnsw-merge-reuse/) | Python | LFV-160 |
| [hnsw-parallel-repair-tla](hnsw-parallel-repair-tla/) | Python | LFV-458 |
| [htmlstrip-tag-open-name-class-vs-html-tokenizer-reference](htmlstrip-tag-open-name-class-vs-html-tokenizer-reference/) | Python | LFV-878 |
| [hunspell](hunspell/) | Python | LFV-373, LFV-377, LFV-374 |
| [hunspell-set-encoding-names-vs-c-hunspell-reference](hunspell-set-encoding-names-vs-c-hunspell-reference/) | Python | LFV-933, LFV-928 |
| [hunspell-synthetic-dictionary-differential](hunspell-synthetic-dictionary-differential/) | Python | LFV-731 |
| [hunspell-wordform-generator-munch-differential](hunspell-wordform-generator-munch-differential/) | Python | LFV-641, LFV-634 |
| [indexeddisi-dv-bulk](indexeddisi-dv-bulk/) | Python | LFV-169, LFV-165 |
| [indexfiledeleter-refcount-tla](indexfiledeleter-refcount-tla/) | TLA+/TLC | LFV-472 |
| [indexingchain-schema](indexingchain-schema/) | Alloy, Python | LFV-118, LFV-116, LFV-127, LFV-124 |
| [indexrearranger-and-pkindexsplitter-failed-run-cleanup](indexrearranger-and-pkindexsplitter-failed-run-cleanup/) | Python | LFV-1011 |
| [indri-query-family-composite-score-boost-visitor-explain-laws](indri-query-family-composite-score-boost-visitor-explain-laws/) | Python | LFV-874, LFV-872, LFV-871, LFV-875 |
| [iw-blocking-calls-watchdog-failure-visibility](iw-blocking-calls-watchdog-failure-visibility/) | TLA+/TLC | LFV-648 |
| [iw-forcemerge-wait-protocol-tla](iw-forcemerge-wait-protocol-tla/) | TLA+/TLC | LFV-590, LFV-587, LFV-586, LFV-592 |
| [japanese-completion-fifo](japanese-completion-fifo/) | Python | LFV-346 |
| [javadoc-package-and-overview-examples-call-removed-apis-siblings-of-16904](javadoc-package-and-overview-examples-call-removed-apis-siblings-of-16904/) | Python | LFV-989, LFV-992, LFV-987, LFV-984, LFV-988, LFV-980 |
| [knn-query-layer](knn-query-layer/) | TLA+/TLC | LFV-106, LFV-114, LFV-117, LFV-125, LFV-123 |
| [kuromoji-completion-romaji-map-vs-hepburn-kunrei-wapuro-tables](kuromoji-completion-romaji-map-vs-hepburn-kunrei-wapuro-tables/) | Python | LFV-849, LFV-850, LFV-848, LFV-854 |
| [latlon-geo-queries-reference](latlon-geo-queries-reference/) | Python | LFV-578 |
| [legacy-int7-int4-scalar-quantized-scoring-fp-smt](legacy-int7-int4-scalar-quantized-scoring-fp-smt/) | Python | LFV-841 |
| [lmdirichlet-mu-zero-limit-and-multisimilarity-norm-combsum](lmdirichlet-mu-zero-limit-and-multisimilarity-norm-combsum/) | Python | LFV-902 |
| [locks-pending-deletes](locks-pending-deletes/) | TLA+/TLC | LFV-343 |
| [lruquerycache-partitioned-clear-and-callback-serialization](lruquerycache-partitioned-clear-and-callback-serialization/) | TLA+/TLC | LFV-991, LFV-1001, LFV-1000 |
| [memory-bitset-collector-manager](memory-bitset-collector-manager/) | TLA+/TLC | LFV-348 |
| [merge-on-commit-refresh-tla](merge-on-commit-refresh-tla/) | TLA+/TLC | LFV-502 |
| [migrate-upgrade-example-forcemerge-keeps-old-format](migrate-upgrade-example-forcemerge-keeps-old-format/) | Python | LFV-1030 |
| [misc-reorder-split](misc-reorder-split/) | Python | LFV-531 |
| [monitor-lifecycle-persistence-mbt](monitor-lifecycle-persistence-mbt/) | TLA+/TLC | LFV-499, LFV-501 |
| [monitor-matcher-agreement](monitor-matcher-agreement/) | Python | LFV-852, LFV-853, LFV-856 |
| [monitor-presearcher](monitor-presearcher/) | TLA+/TLC | LFV-332 |
| [ngram-tokenizers-maxgram-above-input-length](ngram-tokenizers-maxgram-above-input-length/) | Python | LFV-1007 |
| [nrt-reference-managers](nrt-reference-managers/) | TLA+/TLC | LFV-250, LFV-252, LFV-312, LFV-264, LFV-309 |
| [numeric-dv-bulk-values](numeric-dv-bulk-values/) | Python | LFV-381 |
| [numeric-string-version-utils](numeric-string-version-utils/) | SMT-LIB (Z3/cvc5), Python | LFV-327, LFV-323 |
| [offline-sorter](offline-sorter/) | TLA+/TLC | LFV-384, LFV-357 |
| [osq-quantization-new](osq-quantization-new/) | Python | LFV-084 |
| [overflow-at-scale-2](overflow-at-scale-2/) | SMT-LIB (Z3/cvc5), Python | LFV-394, LFV-397, LFV-401, LFV-387, LFV-383 |
| [packed-ints-monotonic](packed-ints-monotonic/) | Python | LFV-148 |
| [parallel-reader-advertises-index-sort-of-shadowed-sort-field](parallel-reader-advertises-index-sort-of-shadowed-sort-field/) | Python | LFV-981 |
| [postings-lucene104](postings-lucene104/) | Python | LFV-183, LFV-170 |
| [priorityqueue-topdocs-merge](priorityqueue-topdocs-merge/) | Dafny | LFV-262, LFV-267, LFV-294 |
| [queries-mlt-commonterms-reference](queries-mlt-commonterms-reference/) | Python | LFV-571, LFV-559, LFV-553 |
| [query-cache-correctness](query-cache-correctness/) | TLA+/TLC | LFV-308, LFV-320, LFV-336 |
| [query-object-contracts](query-object-contracts/) | Python | LFV-304 |
| [queryparser-classic](queryparser-classic/) | Python | LFV-319, LFV-284 |
| [queryparser-flexible-simple-xml](queryparser-flexible-simple-xml/) | Python | LFV-356, LFV-398 |
| [range-bound-cross-path](range-bound-cross-path/) | Python | LFV-409 |
| [rescorers-nearest-and-rescoretopn-presized-by-unbounded-topn](rescorers-nearest-and-rescoretopn-presized-by-unbounded-topn/) | Python | LFV-997, LFV-995 |
| [rescoretopn-negative-rescore-values-maxscore-hang](rescoretopn-negative-rescore-values-maxscore-hang/) | SMT-LIB (Z3/cvc5), Python | LFV-1015, LFV-1016 |
| [sloppymath-and-geo-distance-documented-error-bounds](sloppymath-and-geo-distance-documented-error-bounds/) | Python | LFV-771 |
| [slow-codec-reader-vector-metadata-answered-past-wrappers](slow-codec-reader-vector-metadata-answered-past-wrappers/) | Alloy | LFV-911 |
| [smartcn-hhmm-segmentation-reference](smartcn-hhmm-segmentation-reference/) | Python | LFV-709 |
| [snapshot-deletion-policies-commit-retention-model-based](snapshot-deletion-policies-commit-retention-model-based/) | TLA+/TLC | LFV-890 |
| [sortednumeric-dv-update](sortednumeric-dv-update/) | Alloy | LFV-134 |
| [sorters-selectors](sorters-selectors/) | Python | LFV-372 |
| [span-containing-within-lockstep-misses-nested-little-spans](span-containing-within-lockstep-misses-nested-little-spans/) | TLA+/TLC | LFV-909 |
| [spatial-extras](spatial-extras/) | Python | LFV-369 |
| [spatial-extras-composite-and-prefix-tree-query-cross-check](spatial-extras-composite-and-prefix-tree-query-cross-check/) | Python | LFV-705 |
| [spatial3d-geometry](spatial3d-geometry/) | Python | LFV-209 |
| [spellchecker-candidate-budget-int-overflow](spellchecker-candidate-budget-int-overflow/) | Python | LFV-986, LFV-996 |
| [store-cross-impl-differential](store-cross-impl-differential/) | Python | LFV-492 |
| [storedfields-compressing](storedfields-compressing/) | Python | LFV-172, LFV-186, LFV-163 |
| [study-automaton-verification](study-automaton-verification/) | TLA+/TLC, Dafny | LFV-049, LFV-055, LFV-057, LFV-062 |
| [study-bkd-points-proof](study-bkd-points-proof/) | Dafny, Python | LFV-006, LFV-078, LFV-050, LFV-059, LFV-051 |
| [study-fst-verification](study-fst-verification/) | Alloy, Dafny, Python | LFV-052, LFV-007, LFV-070, LFV-067 |
| [study-hnsw-concurrent-merge](study-hnsw-concurrent-merge/) | TLA+/TLC, Python | LFV-005, LFV-061, LFV-068 |
| [study-overflow-at-scale](study-overflow-at-scale/) | SMT-LIB (Z3/cvc5), Python | LFV-048, LFV-003, LFV-058, LFV-066, LFV-069 |
| [study-quantization-smt](study-quantization-smt/) | SMT-LIB (Z3/cvc5), Python | LFV-047, LFV-046, LFV-064, LFV-079, LFV-065 |
| [study-search-concurrency-model](study-search-concurrency-model/) | TLA+/TLC, Python | LFV-045, LFV-053, LFV-054, LFV-077, LFV-056 |
| [study-simd-equivalence](study-simd-equivalence/) | SMT-LIB (Z3/cvc5), Python | LFV-044, LFV-071 |
| [study-similarity-impacts-smt](study-similarity-impacts-smt/) | Python | LFV-063, LFV-072, LFV-073 |
| [study-topk-pruning-proof](study-topk-pruning-proof/) | SMT-LIB (Z3/cvc5), Dafny, Python | LFV-060 |
| [subtoken-offset-containment-sweep](subtoken-offset-containment-sweep/) | Python | LFV-603 |
| [suggester-lookup-num-overflow-and-presize](suggester-lookup-num-overflow-and-presize/) | Python | LFV-999, LFV-998, LFV-1003 |
| [suggesters](suggesters/) | TLA+/TLC | LFV-263 |
| [taxonomy-index-pair-tla](taxonomy-index-pair-tla/) | TLA+/TLC | LFV-481, LFV-489 |
| [taxonomy-writer-concurrent-add](taxonomy-writer-concurrent-add/) | TLA+/TLC | LFV-521, LFV-520, LFV-512 |
| [termvectors-compressing](termvectors-compressing/) | Python | LFV-244 |
| [timeout-partial-results](timeout-partial-results/) | Python | LFV-465 |
| [twin-family-static-diff](twin-family-static-diff/) | Python | LFV-427 |
| [uax29-word-boundaries-reference](uax29-word-boundaries-reference/) | Python | LFV-734 |
| [unicode-conversions](unicode-conversions/) | Python | LFV-385 |
| [upstream-delta-columnar-dense-vector-batch](upstream-delta-columnar-dense-vector-batch/) | Python | LFV-579 |
| [vectorutil-providers](vectorutil-providers/) | Python | LFV-138 |
| [wand-blockmax-bounds](wand-blockmax-bounds/) | Python | LFV-104, LFV-113 |
| [word2vec-synonym-provider-vs-exact-cosine-reference](word2vec-synonym-provider-vs-exact-cosine-reference/) | Python | LFV-870 |
