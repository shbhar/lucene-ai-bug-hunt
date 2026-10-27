# IndexSearcher#count hangs forever when Weight#count throws, for example on a query timeout, on a segment split into intra-segment partitions

Bug LFV-045 · Area: search / concurrency · Priority: medium · Impact: hang · Novelty: new

Bug page: [../../bugs/LFV-045.html](../../bugs/LFV-045.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with an executor and intra-segment partitions (IndexSearcher.slices(..., allowSegmentPartitions=true)) and call IndexSearcher#count, and the first partition of a segment to arrive throws from Weight#count, for example the ExitingReaderException of a stock ExitableDirectoryReader timeout. You expect the exception to propagate, as TaskExecutor#invokeAll promises. Instead TotalHitCountCollectorManager completes the segment's shared future only on success or CollectionTerminatedException, so the other partitions wait in an unbounded get and the call never returns (with no test hooks, 124/200 and 176/200 calls hung on main, 44/50 on 10.5.2).

## Expected vs actual

- Expected: `count` throws `ExitingReaderException` (or the `IOException`) at once, as the unsplit control does.
- Actual: `count` never returns: the caller waits in `TaskExecutor.collectResults` and a pool thread in `CompletableFuture.get` at `TotalHitCountCollectorManager.java:131`; 2 of 3 tests fail.

## Files

- [src/TestVerifyCountPartitionHang.java](src/TestVerifyCountPartitionHang.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
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
     execute --select-class TestVerifyCountPartitionHang
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestIndexSearcherCountPartitionHang.java](TestIndexSearcherCountPartitionHang.java)
- [TestCountPartitionHangHookFree.java](TestCountPartitionHangHookFree.java)
- [fix-a-with-test.diff](fix-a-with-test.diff)

Formal models and scripts this bug's part of the hunt used: [../../models/study-search-concurrency-model/](../../models/study-search-concurrency-model/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
