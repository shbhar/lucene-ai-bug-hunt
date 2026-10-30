# Formal models and scripts: dwfc-nextpendingflush-stale-fullflush-stock-repro

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- updateDocument racing commit can leave two live documents for one key (a lost delete) and operations above the sequence number commit returned in the commit point, because DocumentsWriterFlushControl.nextPendingFlush flushes a DWPT of the new delete queue during the running full flush (bug LFV-1045; [repro](../../repro/LFV-1045/))

## Files

- [nextpendingflush-stale-fullflush-flushes-new-queue-dwpt/model/FnbFlush.tla](nextpendingflush-stale-fullflush-flushes-new-queue-dwpt/model/FnbFlush.tla) (used for LFV-1045)
- [nextpendingflush-stale-fullflush-flushes-new-queue-dwpt/model/MC_atomic.cfg](nextpendingflush-stale-fullflush-flushes-new-queue-dwpt/model/MC_atomic.cfg) (used for LFV-1045)
- [nextpendingflush-stale-fullflush-flushes-new-queue-dwpt/model/MC_fix.cfg](nextpendingflush-stale-fullflush-flushes-new-queue-dwpt/model/MC_fix.cfg) (used for LFV-1045)
- [nextpendingflush-stale-fullflush-flushes-new-queue-dwpt/model/MC_stock.cfg](nextpendingflush-stale-fullflush-flushes-new-queue-dwpt/model/MC_stock.cfg) (used for LFV-1045)
- [nextpendingflush-stale-fullflush-flushes-new-queue-dwpt/model/MC_stock_order.cfg](nextpendingflush-stale-fullflush-flushes-new-queue-dwpt/model/MC_stock_order.cfg) (used for LFV-1045)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).
