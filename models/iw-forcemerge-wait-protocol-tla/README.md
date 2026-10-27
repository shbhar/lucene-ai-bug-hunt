# Formal models and scripts: iw-forcemerge-wait-protocol-tla

Tools: TLA+/TLC.

These are the working models and scripts this part of the hunt used. Some encode the code as it is (a counterexample is the expected result), others a fixed or correct variant; read the header comment of each file. Paths inside them were replaced by placeholders such as `$LUCENE` (a Lucene checkout) and `$WORK`.

## Bugs found

- IndexWriter.forceMerge(n) can return normally with more than n segments when a merge that was already pending or running at the call fails: forceMerge adopts that merge without updating its mergeGen, so addMergeException drops the failure, and the tragic event is published only after mergeFinish has woken the waiting forceMerge (bug LFV-590; [repro](../../repro/LFV-590/), [bug page](../../bugs/LFV-590.html))
- With NoMergeScheduler and a merge policy that selects merges (for example the default TieredMergePolicy), IndexWriter.close (default commit-on-close), forceMerge(1) and a blocking forceMergeDeletes never return: IndexWriter registers the policy's merges as pending, NoMergeScheduler never runs or removes them, and all three calls wait until no merge is pending (bug LFV-587; [repro](../../repro/LFV-587/))
- Since 9.4.0, IndexWriter.addIndexes(CodecReader...) blocks indefinitely (until another thread interrupts it or rolls the writer back) when the writer uses NoMergeScheduler, also together with NoMergePolicy as the NoMergeScheduler javadoc recommends: the reader merge is handed to the merge scheduler, which never runs it, and is then awaited with no time limit (bug LFV-586; [repro](../../repro/LFV-586/))
- Two overlapping IndexWriter.forceMerge calls with different maxNumSegments share one writer-wide target, so forceMerge(1) returns normally with 2 segments when a forceMerge(2) starts on another thread while its merges are still running (bug LFV-592; [repro](../../repro/LFV-592/))

## Files

- [forcemerge-adopted-merge-failure-dropped-by-mergegen/model/ForceMergeWait.tla](forcemerge-adopted-merge-failure-dropped-by-mergegen/model/ForceMergeWait.tla) (used for LFV-586, LFV-587, LFV-590)
- [forcemerge-adopted-merge-failure-dropped-by-mergegen/model/MC.tla](forcemerge-adopted-merge-failure-dropped-by-mergegen/model/MC.tla) (used for LFV-590)
- [forcemerge-adopted-merge-failure-dropped-by-mergegen/model/fixboth1.cfg](forcemerge-adopted-merge-failure-dropped-by-mergegen/model/fixboth1.cfg) (used for LFV-590)
- [forcemerge-adopted-merge-failure-dropped-by-mergegen/model/fixgen1.cfg](forcemerge-adopted-merge-failure-dropped-by-mergegen/model/fixgen1.cfg) (used for LFV-590)
- [forcemerge-adopted-merge-failure-dropped-by-mergegen/model/fixtrag1.cfg](forcemerge-adopted-merge-failure-dropped-by-mergegen/model/fixtrag1.cfg) (used for LFV-590)
- [forcemerge-adopted-merge-failure-dropped-by-mergegen/model/seq1_nofail.cfg](forcemerge-adopted-merge-failure-dropped-by-mergegen/model/seq1_nofail.cfg) (used for LFV-590)
- [forcemerge-adopted-merge-failure-dropped-by-mergegen/model/stock1.cfg](forcemerge-adopted-merge-failure-dropped-by-mergegen/model/stock1.cfg) (used for LFV-590)
- [overlapping-forcemerge-calls-share-maxnumsegments/model/ForceMergeWait.tla](overlapping-forcemerge-calls-share-maxnumsegments/model/ForceMergeWait.tla) (used for LFV-592)
- [overlapping-forcemerge-calls-share-maxnumsegments/model/MC.tla](overlapping-forcemerge-calls-share-maxnumsegments/model/MC.tla) (used for LFV-592)
- [overlapping-forcemerge-calls-share-maxnumsegments/model/two_nofail_diff.cfg](overlapping-forcemerge-calls-share-maxnumsegments/model/two_nofail_diff.cfg) (used for LFV-592)
- [overlapping-forcemerge-calls-share-maxnumsegments/model/two_nofail_same.cfg](overlapping-forcemerge-calls-share-maxnumsegments/model/two_nofail_same.cfg) (used for LFV-592)

## How to run

- TLA+ with TLC: `java -cp tla2tools.jar tlc2.TLC -config <Model>.cfg <Model>.tla` (a counterexample trace is the bug when the model encodes the current code).
