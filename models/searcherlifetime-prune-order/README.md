# Formal models and scripts: searcherlifetime-prune-order

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- SearcherLifetimeManager.prune orders searchers by the time each was first recorded instead of newest to oldest, so when an older searcher is first recorded after the current one, PruneByAge drops the current searcher (even right after record() returned a token for it) and keeps the older one (finding LFV-1066; [repro](../../repro/LFV-1066/), [finding page](../../bugs/LFV-1066.html))

## Files

- [searcherlifetime-prune-sorts-by-first-record-time/model/MC_fixHybrid_LiveKept.cfg](searcherlifetime-prune-sorts-by-first-record-time/model/MC_fixHybrid_LiveKept.cfg) (used for LFV-1066)
- [searcherlifetime-prune-sorts-by-first-record-time/model/MC_fixHybrid_NewestFirst.cfg](searcherlifetime-prune-sorts-by-first-record-time/model/MC_fixHybrid_NewestFirst.cfg) (used for LFV-1066)
- [searcherlifetime-prune-sorts-by-first-record-time/model/MC_fixHybrid_ProbeNoLateRecord.cfg](searcherlifetime-prune-sorts-by-first-record-time/model/MC_fixHybrid_ProbeNoLateRecord.cfg) (used for LFV-1066)
- [searcherlifetime-prune-sorts-by-first-record-time/model/MC_fixHybrid_ProbeNoPrune.cfg](searcherlifetime-prune-sorts-by-first-record-time/model/MC_fixHybrid_ProbeNoPrune.cfg) (used for LFV-1066)
- [searcherlifetime-prune-sorts-by-first-record-time/model/MC_fixHybrid_RetentionBoundOwn.cfg](searcherlifetime-prune-sorts-by-first-record-time/model/MC_fixHybrid_RetentionBoundOwn.cfg) (used for LFV-1066)
- [searcherlifetime-prune-sorts-by-first-record-time/model/MC_fixHybrid_TokenWindow.cfg](searcherlifetime-prune-sorts-by-first-record-time/model/MC_fixHybrid_TokenWindow.cfg) (used for LFV-1066)
- [searcherlifetime-prune-sorts-by-first-record-time/model/MC_stock_LiveKept.cfg](searcherlifetime-prune-sorts-by-first-record-time/model/MC_stock_LiveKept.cfg) (used for LFV-1066)
- [searcherlifetime-prune-sorts-by-first-record-time/model/MC_stock_TokenWindow.cfg](searcherlifetime-prune-sorts-by-first-record-time/model/MC_stock_TokenWindow.cfg) (used for LFV-1066)
- [searcherlifetime-prune-sorts-by-first-record-time/model/SlmOrder.tla](searcherlifetime-prune-sorts-by-first-record-time/model/SlmOrder.tla) (used for LFV-1066)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the finding when the model encodes the current code).
