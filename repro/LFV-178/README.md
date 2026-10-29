# LogMergePolicy.findForcedDeletesMerges does not skip segments that are already merging, so IndexWriter rejects the whole run and forceMergeDeletes(true) returns with deletes left in segments that were never merging

Bug LFV-178 · Area: index / merge policy (LogMergePolicy forced-deletes selection, IndexWriter.forceMergeDeletes) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use LogDocMergePolicy or LogByteSizeMergePolicy and call IndexWriter.forceMergeDeletes(true) while another merge is pending or running, for example the natural merge that the call's own flush starts. You expect what LogMergePolicy's javadoc says ('Finds merges necessary to force-merge all deletes from the index'): every segment with deletes that is not already being merged is merged before the blocking call returns. Instead the policy groups adjacent segments with deletes into runs without skipping the ones that are already merging, IndexWriter rejects every run that contains such a segment (it only writes an infoStream message), and the call returns with the other segments of that run still holding their deletes, for example a 5000-document segment that keeps all 500 of its deletes.

The test shows that with LogDocMergePolicy (mergeFactor 2), `forceMergeDeletes(true)` called while the natural merge of segments b and c is pending returns with segment a's deleted doc still in place. The policy groups a with the already-merging b, and IndexWriter rejects that merge.

## Expected vs actual

Expected 0 deleted docs after the call; actual 1 (`expected:<0> but was:<1>`), with -ea and -da.

## Files

- [src/org/apache/lucene/index/TestLogMergePolicyForceMergeDeletesWhileMerging.java](src/org/apache/lucene/index/TestLogMergePolicyForceMergeDeletesWhileMerging.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestLogMergePolicyForceMergeDeletesWhileMerging
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
