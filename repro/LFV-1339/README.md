# If a OneMerge.mergeFinished override throws during an addIndexes(CodecReader...) merge, IndexWriter keeps that merge registered as running, so rollback() and close() never return (9.4.0 to main)

Finding LFV-1339 · Area: index / IndexWriter addIndexes(CodecReader...) merge source (AddIndexesMergeSource.merge, runningMerges) · Priority: low · Impact: hang · Novelty: new

Finding page: [../../bugs/LFV-1339.html](../../bugs/LFV-1339.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give IndexWriter a merge policy whose findMerges(CodecReader...) returns a OneMerge with a mergeFinished override, call addIndexes(CodecReader...), and that mergeFinished throws an IOException (the method is public and declared to throw it). You expect addIndexes to report the exception, which it does while leaving the writer open and not tragic, and rollback() or close() to then close the writer as documented. Instead AddIndexesMergeSource.merge's finally calls merge.close(..), which runs mergeFinished, before onMergeFinished(merge) with no nested finally, so the merge is never removed from runningMerges and rollback() waits forever in abortMerges (close() in waitForMerges); a deleteAll() that aborts a queued addIndexes merge reaches the same spot through abortPendingMerges, which also skips clearing its queue when the callback throws.

## Expected vs actual

- Expected: `addIndexes` throws `IOException("mergeFinished failed")`, then `rollback()` closes the writer and the test passes in about half a second, as it does with the fix.
- Actual: `rollback()` never returns (stuck in `IndexWriter.abortMerges`), so the test fails with "Test timeout exceeded (&gt;= 10000 msec)", with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestAddIndexesMergeFinishedThrows.java](src/org/apache/lucene/index/TestAddIndexesMergeFinishedThrows.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestAddIndexesMergeFinishedThrows
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestAddIndexesMergeFinishedThrows.java](TestAddIndexesMergeFinishedThrows.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
