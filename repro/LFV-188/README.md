# MergeObserver.await and await(timeout) return true after a failed or aborted merge, although their javadoc says they return false on error

Bug LFV-188 · Area: index / IndexWriter forceMergeDeletes MergeObserver (MergePolicy.MergeObserver, MergeSpecification.await) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call IndexWriter.forceMergeDeletes(false), which starts the merges in the background and returns a MergeObserver, and then call observer.await to learn whether they worked; its javadoc says it returns true if all merges completed successfully and false on error. Instead it returns true even when the merge failed with an IOException or was aborted by rollback or deleteAll, because every merge's completion future is completed normally with a success flag that await never reads. A caller that trusts the result believes the deleted documents were merged away although the merge never finished.

The test shows that after `IndexWriter.forceMergeDeletes(false)` whose merge fails with an IOException, `MergeObserver.await()` reports success.

## Expected vs actual

Expected `await()` == false (its javadoc: "true if all merges completed successfully..., false on error"); actual `true`, with `-ea` and `-da`, while the writer has recorded the merge failure as its tragic exception.

## Files

- [src/org/apache/lucene/index/TestMergeObserverAwaitOnFailure.java](src/org/apache/lucene/index/TestMergeObserverAwaitOnFailure.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestMergeObserverAwaitOnFailure
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
