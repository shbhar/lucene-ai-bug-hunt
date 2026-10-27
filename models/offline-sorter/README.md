# Formal models and scripts: offline-sorter

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- OfflineSorter loses a partitionsInRAM permit whenever its ExecutorService rejects a partition-sort task, so a later sort on the same sorter waits forever even though the executor is idle (released since 6.6.0) (bug LFV-384; [repro](../../repro/LFV-384/))
- OfflineSorter.sort with an ExecutorService runs its failure cleanup before the partition-sort and merge tasks it already submitted have finished, so each task still queued or running leaves a temp file (prefix_sort_N.tmp) behind that nothing deletes (released since 6.6.0) (bug LFV-357; [repro](../../repro/LFV-357/))

## Files

- [offlinesorter-failure-cleanup-ignores-inflight-tasks/model/C-failclean-mt2-mp1-w0.cfg](offlinesorter-failure-cleanup-ignores-inflight-tasks/model/C-failclean-mt2-mp1-w0.cfg) (used for LFV-357)
- [offlinesorter-failure-cleanup-ignores-inflight-tasks/model/C-failclean-mt2-mp1-w1.cfg](offlinesorter-failure-cleanup-ignores-inflight-tasks/model/C-failclean-mt2-mp1-w1.cfg) (used for LFV-357)
- [offlinesorter-failure-cleanup-ignores-inflight-tasks/model/OfflineSortProto.tla](offlinesorter-failure-cleanup-ignores-inflight-tasks/model/OfflineSortProto.tla) (used for LFV-357)
- [offlinesorter-rejected-submit-leaks-partition-permit/model/B-fifo-fault1-mt2-mp1-w1.cfg](offlinesorter-rejected-submit-leaks-partition-permit/model/B-fifo-fault1-mt2-mp1-w1.cfg) (used for LFV-384)
- [offlinesorter-rejected-submit-leaks-partition-permit/model/E-reject-mt2-mp1-w1.cfg](offlinesorter-rejected-submit-leaks-partition-permit/model/E-reject-mt2-mp1-w1.cfg) (used for LFV-384)
- [offlinesorter-rejected-submit-leaks-partition-permit/model/OfflineSortProto.tla](offlinesorter-rejected-submit-leaks-partition-permit/model/OfflineSortProto.tla) (used for LFV-384)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).
