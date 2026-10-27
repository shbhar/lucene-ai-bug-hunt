# openIfChanged returns null and isCurrent stays true after a flush when the only changes are doc-values updates

Bug LFV-001 · Area: index / IndexWriter NRT · Priority: high · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-001.html](../../bugs/LFV-001.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open an NRT reader, update a doc-values field (or soft-delete through updateDocValues), and then call flush, a no-op forceMerge(1), or open readers with writeAllDeletes=true. You expect DirectoryReader.openIfChanged(reader, writer) to return a reader that sees the change. Instead it returns null and reader.isCurrent is true, so SearcherManager keeps the stale searcher, and hasUncommittedChanges can also return false while updates are uncommitted.

## Expected vs actual

- Expected: `openIfChanged` returns a reader that sees the change, as in the control, and `hasUncommittedChanges` is true until the commit.
- Actual: 5 of 6 tests fail on main, 10.5.2 and 9.12.3: `openIfChanged` returns null while a fresh reader sees the change, and `hasUncommittedChanges` returns false; the control passes.

## Files

- [src/org/apache/lucene/index/TestFlushHidesDocValuesUpdatesFromNRT.java](src/org/apache/lucene/index/TestFlushHidesDocValuesUpdatesFromNRT.java) (JUnit test)
- [src/org/apache/lucene/index/TestGivenBugHasUncommittedChanges.java](src/org/apache/lucene/index/TestGivenBugHasUncommittedChanges.java) (JUnit test)
- [src/org/apache/lucene/index/TestGivenBugPrimaryNode.java](src/org/apache/lucene/index/TestGivenBugPrimaryNode.java) (JUnit test)
- [src/org/apache/lucene/index/TestGivenBugProbes.java](src/org/apache/lucene/index/TestGivenBugProbes.java) (JUnit test)
- [src/org/apache/lucene/index/TestGivenBugRamPressure.java](src/org/apache/lucene/index/TestGivenBugRamPressure.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- Recorded outcome of run 1 on main a9779ef3c193: 32 test(s) run, 19 failed (testNumericUpdateThenFlush, testSoftDeleteThenFlush, testHasUncommittedChangesAfterAutoAppliedDocValuesUpdates, testPrimaryNodeFlushAndRefreshPublishesDocValuesUpdate, testApplyAllDeletesFalseReader, testBinaryUpdateThenFlush).
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/replicator/build/libs/lucene-replicator-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx4g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestFlushHidesDocValuesUpdatesFromNRT \
       --select-class org.apache.lucene.index.TestGivenBugProbes \
       --select-class org.apache.lucene.index.TestGivenBugRamPressure \
       --select-class org.apache.lucene.index.TestGivenBugHasUncommittedChanges \
       --select-class org.apache.lucene.index.TestGivenBugPrimaryNode
```

The module(s) facet, replicator also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestOpenIfChangedAfterDocValuesOnlyFlush.java](TestOpenIfChangedAfterDocValuesOnlyFlush.java)
- [fixB-applied-ea57360912ed.diff](fixB-applied-ea57360912ed.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
