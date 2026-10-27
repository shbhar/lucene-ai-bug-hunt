# Formal models and scripts: indexfiledeleter-refcount-tla

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- Merge-on-refresh never releases the file references of a merged segment that is dropped because all its documents were deleted during the merge, so IndexWriter leaves that segment's files on disk until the next writer opens the index (bug LFV-472; [repro](../../repro/LFV-472/))

## Files

- [getreader-merge-dropped-segment-refcount-leak/model/IFDRefcount.tla](getreader-merge-dropped-segment-refcount-leak/model/IFDRefcount.tla) (used for LFV-472)
- [getreader-merge-dropped-segment-refcount-leak/model/MC_full_fix.cfg](getreader-merge-dropped-segment-refcount-leak/model/MC_full_fix.cfg) (used for LFV-472)
- [getreader-merge-dropped-segment-refcount-leak/model/MC_full_fix_seq.cfg](getreader-merge-dropped-segment-refcount-leak/model/MC_full_fix_seq.cfg) (used for LFV-472)
- [getreader-merge-dropped-segment-refcount-leak/model/MC_pit_leak.cfg](getreader-merge-dropped-segment-refcount-leak/model/MC_pit_leak.cfg) (used for LFV-472)
- [getreader-merge-dropped-segment-refcount-leak/model/MC_pit_noleak.cfg](getreader-merge-dropped-segment-refcount-leak/model/MC_pit_noleak.cfg) (used for LFV-472)
- [getreader-merge-dropped-segment-refcount-leak/model/MC_probe_dropped.cfg](getreader-merge-dropped-segment-refcount-leak/model/MC_probe_dropped.cfg) (used for LFV-472)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).
