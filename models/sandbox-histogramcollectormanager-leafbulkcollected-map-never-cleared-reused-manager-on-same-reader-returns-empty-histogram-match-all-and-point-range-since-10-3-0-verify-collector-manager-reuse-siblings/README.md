# Formal models and scripts: sandbox-histogramcollectormanager-leafbulkcollected-map-never-cleared-reused-manager-on-same-reader-returns-empty-histogram-match-all-and-point-range-since-10-3-0-verify-collector-manager-reuse-siblings

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- Sandbox HistogramCollectorManager never clears its bulk-collected leaf map, so a second search with the same manager on the same reader silently returns an empty histogram for every segment on the point-tree path (since 10.3.0) (finding LFV-1467; [repro](../../repro/LFV-1467/))

## Files

- [histogramcollectormanager-bulk-collected-leaves-never-cleared/model/tla/HistMgr.tla](histogramcollectormanager-bulk-collected-leaves-never-cleared/model/tla/HistMgr.tla) (used for LFV-1467)
- [histogramcollectormanager-bulk-collected-leaves-never-cleared/model/tla/MC_fix_conc.cfg](histogramcollectormanager-bulk-collected-leaves-never-cleared/model/tla/MC_fix_conc.cfg) (used for LFV-1467)
- [histogramcollectormanager-bulk-collected-leaves-never-cleared/model/tla/MC_fix_fail.cfg](histogramcollectormanager-bulk-collected-leaves-never-cleared/model/tla/MC_fix_fail.cfg) (used for LFV-1467)
- [histogramcollectormanager-bulk-collected-leaves-never-cleared/model/tla/MC_fix_seq.cfg](histogramcollectormanager-bulk-collected-leaves-never-cleared/model/tla/MC_fix_seq.cfg) (used for LFV-1467)
- [histogramcollectormanager-bulk-collected-leaves-never-cleared/model/tla/MC_mutant_nodedup.cfg](histogramcollectormanager-bulk-collected-leaves-never-cleared/model/tla/MC_mutant_nodedup.cfg) (used for LFV-1467)
- [histogramcollectormanager-bulk-collected-leaves-never-cleared/model/tla/MC_stock_reopen.cfg](histogramcollectormanager-bulk-collected-leaves-never-cleared/model/tla/MC_stock_reopen.cfg) (used for LFV-1467)
- [histogramcollectormanager-bulk-collected-leaves-never-cleared/model/tla/MC_stock_seq.cfg](histogramcollectormanager-bulk-collected-leaves-never-cleared/model/tla/MC_stock_seq.cfg) (used for LFV-1467)
- [histogramcollectormanager-bulk-collected-leaves-never-cleared/model/tla/MC_vac_fix_seq.cfg](histogramcollectormanager-bulk-collected-leaves-never-cleared/model/tla/MC_vac_fix_seq.cfg) (used for LFV-1467)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the finding when the model encodes the current code).
