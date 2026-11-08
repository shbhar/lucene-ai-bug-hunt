# Formal models and scripts: hasdeletions-false-for-acknowledged-delete-in-swapped-out-queue-or-unpublished-flush-ticket-low

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- IndexWriter.hasDeletions() returns false for an acknowledged buffered delete while another thread's commit(), flush() or NRT reader open is writing a segment, because DocumentsWriter.anyDeletions() reads only the current delete queue and not the deletes frozen into flush tickets that are not yet published (since 4.0.0) (finding LFV-1403; [repro](../../repro/LFV-1403/), [finding page](../../bugs/LFV-1403.html))

## Files

- [hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/HasDelWindow.tla](hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/HasDelWindow.tla) (used for LFV-1403)
- [hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_fix.cfg](hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_fix.cfg) (used for LFV-1403)
- [hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_fixLate.cfg](hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_fixLate.cfg) (used for LFV-1403)
- [hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_fixRev.cfg](hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_fixRev.cfg) (used for LFV-1403)
- [hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_fix_VacSwapped.cfg](hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_fix_VacSwapped.cfg) (used for LFV-1403)
- [hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_fix_VacTicket.cfg](hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_fix_VacTicket.cfg) (used for LFV-1403)
- [hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_stock.cfg](hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_stock.cfg) (used for LFV-1403)
- [hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_stock_WinA.cfg](hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_stock_WinA.cfg) (used for LFV-1403)
- [hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_stock_WinB.cfg](hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_stock_WinB.cfg) (used for LFV-1403)
- [hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_ticketOnly.cfg](hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_ticketOnly.cfg) (used for LFV-1403)
- [hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_ticketOnly_WinA.cfg](hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_ticketOnly_WinA.cfg) (used for LFV-1403)
- [hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_ticketOnly_WinB.cfg](hasdeletions-misses-deletes-held-in-swapped-out-queue-and-unpublished-flush-tickets/model/MC_ticketOnly_WinB.cfg) (used for LFV-1403)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the finding when the model encodes the current code).
