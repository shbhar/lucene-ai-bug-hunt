# Formal models and scripts: reader-arena-refcount-tla

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- When DirectoryReader.openIfChanged on a FilterDirectoryReader (for example SoftDeletesDirectoryReaderWrapper) fails while wrapping the reopened reader, the newly opened inner reader is never closed, so the segment readers it shares with the old reader, and its files, leak (finding LFV-495; [repro](../../repro/LFV-495/), [finding page](../../bugs/LFV-495.html))
- DirectoryReader.open and openIfChanged with an ExecutorService never close the segment readers of tasks the executor already accepted when a later submit is rejected (or when the opening thread is interrupted while it waits), so those segments' files stay open (finding LFV-1061; [repro](../../repro/LFV-1061/), [finding page](../../bugs/LFV-1061.html))
- DirectoryReader.openIfChanged on a reader opened with a leaf sorter leaks segment reader references when the sorter throws while the new reader is built, so closing the old reader no longer closes the shared segments and the new segment's reader is never closed (finding LFV-1059; [repro](../../repro/LFV-1059/), [finding page](../../bugs/LFV-1059.html))

## Files

- [filterdirectoryreader-reopen-wrap-failure-leaks-new-reader/model/ReaderLifecycle.tla](filterdirectoryreader-reopen-wrap-failure-leaks-new-reader/model/ReaderLifecycle.tla) (used for LFV-495)
- [filterdirectoryreader-reopen-wrap-failure-leaks-new-reader/model/exec-wrapThrow-wTRUE.cfg](filterdirectoryreader-reopen-wrap-failure-leaks-new-reader/model/exec-wrapThrow-wTRUE.cfg) (used for LFV-495)
- [filterdirectoryreader-reopen-wrap-failure-leaks-new-reader/model/seq-wrapThrow-wTRUE-fixAll.cfg](filterdirectoryreader-reopen-wrap-failure-leaks-new-reader/model/seq-wrapThrow-wTRUE-fixAll.cfg) (used for LFV-495)
- [filterdirectoryreader-reopen-wrap-failure-leaks-new-reader/model/seq-wrapThrow-wTRUE.cfg](filterdirectoryreader-reopen-wrap-failure-leaks-new-reader/model/seq-wrapThrow-wTRUE.cfg) (used for LFV-495)
- [parallel-segment-open-abandons-submitted-readers/model/ReaderLifecycle.tla](parallel-segment-open-abandons-submitted-readers/model/ReaderLifecycle.tla) (used for LFV-1061)
- [parallel-segment-open-abandons-submitted-readers/model/exec-interrupt2-wFALSE-fixAll.cfg](parallel-segment-open-abandons-submitted-readers/model/exec-interrupt2-wFALSE-fixAll.cfg) (used for LFV-1061)
- [parallel-segment-open-abandons-submitted-readers/model/exec-interrupt2-wFALSE.cfg](parallel-segment-open-abandons-submitted-readers/model/exec-interrupt2-wFALSE.cfg) (used for LFV-1061)
- [parallel-segment-open-abandons-submitted-readers/model/exec-rejectAt3-wFALSE-fixAll.cfg](parallel-segment-open-abandons-submitted-readers/model/exec-rejectAt3-wFALSE-fixAll.cfg) (used for LFV-1061)
- [parallel-segment-open-abandons-submitted-readers/model/exec-rejectAt3-wFALSE.cfg](parallel-segment-open-abandons-submitted-readers/model/exec-rejectAt3-wFALSE.cfg) (used for LFV-1061)
- [reopen-leaf-sorter-failure-leaks-segment-readers/model/ReaderLifecycle.tla](reopen-leaf-sorter-failure-leaks-segment-readers/model/ReaderLifecycle.tla) (used for LFV-1059)
- [reopen-leaf-sorter-failure-leaks-segment-readers/model/seq-ctorThrow-wFALSE-fixAll.cfg](reopen-leaf-sorter-failure-leaks-segment-readers/model/seq-ctorThrow-wFALSE-fixAll.cfg) (used for LFV-1059)
- [reopen-leaf-sorter-failure-leaks-segment-readers/model/seq-ctorThrow-wFALSE.cfg](reopen-leaf-sorter-failure-leaks-segment-readers/model/seq-ctorThrow-wFALSE.cfg) (used for LFV-1059)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the finding when the model encodes the current code).
