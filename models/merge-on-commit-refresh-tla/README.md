# Formal models and scripts: merge-on-commit-refresh-tla

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- IndexWriter keeps a full-flush merge that registerMerge rejected: commit does not throw the IllegalStateException that MergePolicy#findFullFlushMerges documents, the rejected merge's readers stay open after IndexWriter.close, and commit/getReader wait the full maxFullFlushMergeWaitMillis for a merge that never runs (bug LFV-502; [repro](../../repro/LFV-502/))

## Files

- [fullflush-merge-rejected-by-registermerge-still-awaited/model/PITMerge.tla](fullflush-merge-rejected-by-registermerge-still-awaited/model/PITMerge.tla) (used for LFV-502)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).
