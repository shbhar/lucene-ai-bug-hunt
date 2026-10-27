# IndexWriter.forceMergeDeletes returns a MergeObserver that also tracks merges IndexWriter refused to register, so the observer never completes and await blocks forever

Bug LFV-207 · Area: index / IndexWriter forceMergeDeletes MergeObserver (registerMerge, MergePolicy.MergeObserver) · Priority: low · Impact: hang · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use LogDocMergePolicy or LogByteSizeMergePolicy (or a custom merge policy), call IndexWriter.forceMergeDeletes(false) while another merge is already running on one of the segments with deletes, and wait on the returned MergeObserver, whose javadoc says it lets you 'monitor merge progress and wait for completion'. IndexWriter refuses to register the conflicting forced-deletes merge (it only logs 'reject merge'), so that merge never runs, but it still puts the merge into the observer. The observer then reports 0 of 1 merges complete forever: await blocks forever even after the writer is closed, await(timeout) returns false, and the blocking forceMergeDeletes returns at once with an incomplete observer although its javadoc says 'merges will already be complete when it returns'.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/index/TestMergeObserverRejectedMerge.java](src/org/apache/lucene/index/TestMergeObserverRejectedMerge.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.index.TestMergeObserverRejectedMerge
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
