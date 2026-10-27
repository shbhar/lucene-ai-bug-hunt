# Formal models and scripts: iw-blocking-calls-watchdog-failure-visibility

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- IndexWriter.close never returns when a background merge fails while a forced-merge cascade merge is still pending (forceMerge(n, false), then close): the tragic rollback is skipped because close is already closing, getNextMerge refuses the pending merge once the tragedy is set, and waitForMerges never checks the tragedy (since 9.3.0) (bug LFV-648; [repro](../../repro/LFV-648/))

## Files

- [close-waitformerges-stranded-pending-merge-after-tragedy/model/CloseWaitForMerges.tla](close-waitformerges-stranded-pending-merge-after-tragedy/model/CloseWaitForMerges.tla) (used for LFV-648)
- [close-waitformerges-stranded-pending-merge-after-tragedy/model/MC_fix.cfg](close-waitformerges-stranded-pending-merge-after-tragedy/model/MC_fix.cfg) (used for LFV-648)
- [close-waitformerges-stranded-pending-merge-after-tragedy/model/MC_mmc1_stock.cfg](close-waitformerges-stranded-pending-merge-after-tragedy/model/MC_mmc1_stock.cfg) (used for LFV-648)
- [close-waitformerges-stranded-pending-merge-after-tragedy/model/MC_mmc2_fix.cfg](close-waitformerges-stranded-pending-merge-after-tragedy/model/MC_mmc2_fix.cfg) (used for LFV-648)
- [close-waitformerges-stranded-pending-merge-after-tragedy/model/MC_mmc2_stock.cfg](close-waitformerges-stranded-pending-merge-after-tragedy/model/MC_mmc2_stock.cfg) (used for LFV-648)
- [close-waitformerges-stranded-pending-merge-after-tragedy/model/MC_nocascade.cfg](close-waitformerges-stranded-pending-merge-after-tragedy/model/MC_nocascade.cfg) (used for LFV-648)
- [close-waitformerges-stranded-pending-merge-after-tragedy/model/MC_nofail.cfg](close-waitformerges-stranded-pending-merge-after-tragedy/model/MC_nofail.cfg) (used for LFV-648)
- [close-waitformerges-stranded-pending-merge-after-tragedy/model/MC_stock.cfg](close-waitformerges-stranded-pending-merge-after-tragedy/model/MC_stock.cfg) (used for LFV-648)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).
