# Formal models and scripts: dw-flush-control-deletequeue-tla

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- A non-tragic exception during an NRT reopen or commit full flush aborts the DWPTs other threads filled during that flush, silently losing acknowledged documents and updates while the writer stays open (bug LFV-473; [repro](../../repro/LFV-473/))
- IndexWriter.deleteAll misses a segment flush that another thread is already running, so documents added before deleteAll survive it and are committed (bug LFV-474; [repro](../../repro/LFV-474/))
- tryDeleteDocument / tryUpdateDocValue running while commit flushes returns a sequence number above the commit's, yet its change is in that commit (bug LFV-471; [repro](../../repro/LFV-471/), [bug page](../../bugs/LFV-471.html))

## Files

- [deleteall-misses-inflight-segment-flush/model/DWFlush.tla](deleteall-misses-inflight-segment-flush/model/DWFlush.tla) (used for LFV-474)
- [deleteall-misses-inflight-segment-flush/model/MC_B3_deleteall.cfg](deleteall-misses-inflight-segment-flush/model/MC_B3_deleteall.cfg) (used for LFV-474)
- [deleteall-misses-inflight-segment-flush/model/MC_B3_deleteall.tla](deleteall-misses-inflight-segment-flush/model/MC_B3_deleteall.tla) (used for LFV-474)
- [deleteall-misses-inflight-segment-flush/model/MC_B3_fix.cfg](deleteall-misses-inflight-segment-flush/model/MC_B3_fix.cfg) (used for LFV-474)
- [deleteall-misses-inflight-segment-flush/model/MC_B3_fix.tla](deleteall-misses-inflight-segment-flush/model/MC_B3_fix.tla) (used for LFV-474)
- [deleteall-misses-inflight-segment-flush/model/MC_B_deleteall.cfg](deleteall-misses-inflight-segment-flush/model/MC_B_deleteall.cfg) (used for LFV-474)
- [deleteall-misses-inflight-segment-flush/model/MC_B_deleteall.tla](deleteall-misses-inflight-segment-flush/model/MC_B_deleteall.tla) (used for LFV-474)
- [getreader-failure-aborts-concurrent-blocked-flushes/model/DWFlush.tla](getreader-failure-aborts-concurrent-blocked-flushes/model/DWFlush.tla) (used for LFV-473)
- [getreader-failure-aborts-concurrent-blocked-flushes/model/MC_A_fix.cfg](getreader-failure-aborts-concurrent-blocked-flushes/model/MC_A_fix.cfg) (used for LFV-473)
- [getreader-failure-aborts-concurrent-blocked-flushes/model/MC_A_fix.tla](getreader-failure-aborts-concurrent-blocked-flushes/model/MC_A_fix.tla) (used for LFV-473)
- [getreader-failure-aborts-concurrent-blocked-flushes/model/MC_A_nrtfail.cfg](getreader-failure-aborts-concurrent-blocked-flushes/model/MC_A_nrtfail.cfg) (used for LFV-473)
- [getreader-failure-aborts-concurrent-blocked-flushes/model/MC_A_nrtfail.tla](getreader-failure-aborts-concurrent-blocked-flushes/model/MC_A_nrtfail.tla) (used for LFV-473)
- [trydelete-during-commit-outside-seqno-boundary/model/DWFlush.tla](trydelete-during-commit-outside-seqno-boundary/model/DWFlush.tla) (used for LFV-471)
- [trydelete-during-commit-outside-seqno-boundary/model/MC_D_try.cfg](trydelete-during-commit-outside-seqno-boundary/model/MC_D_try.cfg) (used for LFV-471)
- [trydelete-during-commit-outside-seqno-boundary/model/MC_D_try.tla](trydelete-during-commit-outside-seqno-boundary/model/MC_D_try.tla) (used for LFV-471)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).
