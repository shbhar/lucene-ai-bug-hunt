# Two overlapping IndexWriter.forceMerge calls with different maxNumSegments share one writer-wide target, so forceMerge(1) returns normally with 2 segments when a forceMerge(2) starts on another thread while its merges are still running

Bug LFV-592 · Area: index / IndexWriter forced merge (writer-wide forced-merge target shared by concurrent forceMerge calls) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call forceMerge(1) on an IndexWriter and, while it is still merging, another thread calls forceMerge(2) on the same writer. You expect forceMerge(1) to return only once the index is down to 1 segment, because its javadoc defines maxNumSegments as the 'maximum number of segments left in the index after merging finishes' and IndexWriter allows concurrent calls to any method. Instead forceMerge(1) returns normally with 2 segments: both calls share one writer-wide target, the second call overwrites it (also on the first call's running merges), and the first call's wait loop only checks that no forced merge is left, never its own target.

The test shows that `forceMerge(1)` over 3 segments (LogDocMergePolicy, mergeFactor 2, so 2 merge rounds) returns normally with 2 segments when another thread calls `forceMerge(2)` while the first round's merge is still running.

## Expected vs actual

Expected, per the `forceMerge` javadoc (maxNumSegments is the "maximum number of segments left in the index after merging finishes"): 1 segment. Actual: `segments when forceMerge(1) returned expected:<1> but was:<2>`.

## Files

- [src/org/apache/lucene/index/TestOverlappingForceMerge.java](src/org/apache/lucene/index/TestOverlappingForceMerge.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestOverlappingForceMerge
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/iw-forcemerge-wait-protocol-tla/](../../models/iw-forcemerge-wait-protocol-tla/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
