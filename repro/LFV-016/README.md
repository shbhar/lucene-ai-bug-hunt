# deleteDocuments(Query) on a doc-values field is evaluated against stale values when doc-values updates are pending

Bug LFV-016 · Area: index / doc-values updates · Priority: high · Impact: data corruption · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You update a doc-values field and then delete documents with a query on that field before the updates are written. You expect the delete to see the updated values. Instead delete-by-query is resolved against the written values, and inside a packet deletes run before doc-values updates, so the wrong documents are deleted or kept.

## Expected vs actual

- Expected: You expect the delete to see the updated values.
- Actual: Delete-by-query is resolved against the written values, and inside a packet deletes run before doc-values updates, so the wrong documents are deleted or kept.

## Files

- [src/TestVerifyDvDeleteQueryStaleness.java](src/TestVerifyDvDeleteQueryStaleness.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- Recorded outcome of run 1 on Lucene main a9779ef3c193: 9 test(s) run, 6 failed (testA1_deleteNewValueAfterUpdate_flushedSegment, testA2_deleteOldValueAfterUpdate_flushedSegment, testA3_deleteNewValueAfterUpdate_inRamDoc, testB1_crossPacket_resolvedButUnwrittenUpdate, testB2_tryUpdateDocValueThenDelete, testS1_softDeleteThenPurgeByExistsQuery).
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
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestVerifyDvDeleteQueryStaleness
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
