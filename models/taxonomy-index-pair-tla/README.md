# Formal models and scripts: taxonomy-index-pair-tla

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- ReindexingEnrichedDirectoryTaxonomyWriter.reindexWithNewOrdinalData rebuilds only the last committed taxonomy, so categories added since that commit are dropped and their ordinals are later reused for other categories (bug LFV-481; [repro](../../repro/LFV-481/), [bug page](../../bugs/LFV-481.html))
- SearcherTaxonomyManager's IndexWriter (near-real-time) constructors open the taxonomy reader before the index reader, so a manager created while another thread indexes can hand out a first pair whose searcher holds documents with categories the paired taxonomy reader does not know (facet counting throws ArrayIndexOutOfBoundsException until the index changes and the manager refreshes) (bug LFV-489; [repro](../../repro/LFV-489/))

## Files

- [reindex-ordinal-data-drops-uncommitted-categories/model/TaxoReindex.tla](reindex-ordinal-data-drops-uncommitted-categories/model/TaxoReindex.tla) (used for LFV-481)
- [reindex-ordinal-data-drops-uncommitted-categories/model/X_reindex_commit.cfg](reindex-ordinal-data-drops-uncommitted-categories/model/X_reindex_commit.cfg) (used for LFV-481)
- [reindex-ordinal-data-drops-uncommitted-categories/model/X_reindex_writer.cfg](reindex-ordinal-data-drops-uncommitted-categories/model/X_reindex_writer.cfg) (used for LFV-481)
- [reindex-ordinal-data-drops-uncommitted-categories/model/X_reindex_writer_probe.cfg](reindex-ordinal-data-drops-uncommitted-categories/model/X_reindex_writer_probe.cfg) (used for LFV-481)
- [searchertaxonomymanager-nrt-ctor-opens-taxonomy-before-index/model/MC_nrt_ascoded.cfg](searchertaxonomymanager-nrt-ctor-opens-taxonomy-before-index/model/MC_nrt_ascoded.cfg) (used for LFV-489)
- [searchertaxonomymanager-nrt-ctor-opens-taxonomy-before-index/model/MC_nrt_ascoded_seqctor.cfg](searchertaxonomymanager-nrt-ctor-opens-taxonomy-before-index/model/MC_nrt_ascoded_seqctor.cfg) (used for LFV-489)
- [searchertaxonomymanager-nrt-ctor-opens-taxonomy-before-index/model/MC_nrt_ascoded_seqctor_big.cfg](searchertaxonomymanager-nrt-ctor-opens-taxonomy-before-index/model/MC_nrt_ascoded_seqctor_big.cfg) (used for LFV-489)
- [searchertaxonomymanager-nrt-ctor-opens-taxonomy-before-index/model/MC_nrt_fixed.cfg](searchertaxonomymanager-nrt-ctor-opens-taxonomy-before-index/model/MC_nrt_fixed.cfg) (used for LFV-489)
- [searchertaxonomymanager-nrt-ctor-opens-taxonomy-before-index/model/MC_nrt_fixed_big.cfg](searchertaxonomymanager-nrt-ctor-opens-taxonomy-before-index/model/MC_nrt_fixed_big.cfg) (used for LFV-489)
- [searchertaxonomymanager-nrt-ctor-opens-taxonomy-before-index/model/MC_nrt_fixed_probe.cfg](searchertaxonomymanager-nrt-ctor-opens-taxonomy-before-index/model/MC_nrt_fixed_probe.cfg) (used for LFV-489)
- [searchertaxonomymanager-nrt-ctor-opens-taxonomy-before-index/model/MC_nrt_refresh_mut.cfg](searchertaxonomymanager-nrt-ctor-opens-taxonomy-before-index/model/MC_nrt_refresh_mut.cfg) (used for LFV-489)
- [searchertaxonomymanager-nrt-ctor-opens-taxonomy-before-index/model/TaxoPair.tla](searchertaxonomymanager-nrt-ctor-opens-taxonomy-before-index/model/TaxoPair.tla) (used for LFV-489)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).
