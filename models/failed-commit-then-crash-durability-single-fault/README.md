# Formal models and scripts: failed-commit-then-crash-durability-single-fault

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- If Directory.rename(pending_segments_N, segments_N) throws after it took effect, the failed commit leaves the new segments_N in the index (SegmentInfos.finishCommit deletes only pending_segments_N), and IndexWriter then deletes files that this newest segments_N references, so DirectoryReader.open and a new IndexWriter fail until the file is removed by hand (every release since 5.0.0; the rename-branch residual of LUCENE-9477) (bug LFV-839; [repro](../../repro/LFV-839/))

## Files

- [finishcommit-rename-failure-leaves-segments-file/model/FailedCommitDurability.tla](finishcommit-rename-failure-leaves-segments-file/model/FailedCommitDurability.tla) (used for LFV-839)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).
