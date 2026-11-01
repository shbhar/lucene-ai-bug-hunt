# Formal models and scripts: never-judged-backlog-closure-low-and-not-a-bug

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Findings

- DirectoryReader.open and openIfChanged with an ExecutorService never close the segment readers of tasks the executor already accepted when a later submit is rejected (or when the opening thread is interrupted while it waits), so those segments' files stay open (finding LFV-1061; [repro](../../repro/LFV-1061/), [finding page](../../bugs/LFV-1061.html))
- DirectoryReader.openIfChanged on a reader opened with a leaf sorter leaks segment reader references when the sorter throws while the new reader is built, so closing the old reader no longer closes the shared segments and the new segment's reader is never closed (finding LFV-1059; [repro](../../repro/LFV-1059/), [finding page](../../bugs/LFV-1059.html))

## Files

- [parallel-segment-open-abandons-submitted-readers/model/ReaderLifecycle.tla](parallel-segment-open-abandons-submitted-readers/model/ReaderLifecycle.tla) (used for LFV-1061)
- [parallel-segment-open-abandons-submitted-readers/model/exec-interrupt2-wFALSE-fixAll.cfg](parallel-segment-open-abandons-submitted-readers/model/exec-interrupt2-wFALSE-fixAll.cfg) (used for LFV-1061)
- [parallel-segment-open-abandons-submitted-readers/model/exec-interrupt2-wFALSE.cfg](parallel-segment-open-abandons-submitted-readers/model/exec-interrupt2-wFALSE.cfg) (used for LFV-1061)
- [parallel-segment-open-abandons-submitted-readers/model/exec-rejectAt3-wFALSE-fixAll.cfg](parallel-segment-open-abandons-submitted-readers/model/exec-rejectAt3-wFALSE-fixAll.cfg) (used for LFV-1061)
- [parallel-segment-open-abandons-submitted-readers/model/exec-rejectAt3-wFALSE.cfg](parallel-segment-open-abandons-submitted-readers/model/exec-rejectAt3-wFALSE.cfg) (used for LFV-1061)
- [reopen-leaf-sorter-failure-leaks-segment-readers/model/ReaderLifecycle.tla](reopen-leaf-sorter-failure-leaks-segment-readers/model/ReaderLifecycle.tla) (used for LFV-1059)
- [reopen-leaf-sorter-failure-leaks-segment-readers/model/exec-ctorThrow-wFALSE-fixAll.cfg](reopen-leaf-sorter-failure-leaks-segment-readers/model/exec-ctorThrow-wFALSE-fixAll.cfg) (used for LFV-1059)
- [reopen-leaf-sorter-failure-leaks-segment-readers/model/exec-ctorThrow-wFALSE.cfg](reopen-leaf-sorter-failure-leaks-segment-readers/model/exec-ctorThrow-wFALSE.cfg) (used for LFV-1059)
- [reopen-leaf-sorter-failure-leaks-segment-readers/model/seq-ctorThrow-wFALSE-fixAll.cfg](reopen-leaf-sorter-failure-leaks-segment-readers/model/seq-ctorThrow-wFALSE-fixAll.cfg) (used for LFV-1059)
- [reopen-leaf-sorter-failure-leaks-segment-readers/model/seq-ctorThrow-wFALSE.cfg](reopen-leaf-sorter-failure-leaks-segment-readers/model/seq-ctorThrow-wFALSE.cfg) (used for LFV-1059)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the finding when the model encodes the current code).
