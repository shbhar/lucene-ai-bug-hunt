# TemporalMergePolicy.forceMergeDeletes merges segments older than maxAgeSeconds, from different time windows, into one segment, because findForcedDeletesMerges does not skip the -1 'old data' bucket that findMerges and forceMerge skip (every release since 10.5.0)

Finding LFV-1345 · Area: core / index: TemporalMergePolicy (forced-deletes selection in findForcedDeletesMerges, reached through IndexWriter.forceMergeDeletes) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1345.html](../../bugs/LFV-1345.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index time-series data with TemporalMergePolicy and setMaxAgeSeconds(30 days), so two segments whose newest timestamps are 2020-01-01 and 2021-01-01 are older than the limit and lie in different time windows, delete one of the four documents in each, and call IndexWriter.forceMergeDeletes(). You expect the two segments to stay apart: the class javadoc says segments older than setMaxAgeSeconds are not merged ('skipped during merge selection') and that segments from different time windows are never merged together, even during forceMerge, and forceMerge(1) and natural merges do skip them. Instead findForcedDeletesMerges treats the single -1 'old data' bucket, which holds every over-age segment whatever its window, as an ordinary window and merges the two segments into one (maxDoc 6, holding 2020 and 2021 data); without the age limit the same call keeps them apart, so turning on a setting meant to avoid cold-data I/O makes forceMergeDeletes rewrite and mix cold data.

## Expected vs actual

- Expected: 2 segments remain: both are past the age limit and in different time windows.
- Actual: `AssertionError: expected:<2> but was:<1>`: the 2020 and 2021 segments were merged into one.

## Files

- [src/org/apache/lucene/index/TestTemporalMergePolicyForceMergeDeletes.java](src/org/apache/lucene/index/TestTemporalMergePolicyForceMergeDeletes.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestTemporalMergePolicyForceMergeDeletes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestTemporalMergePolicyForceMergeDeletes.java](TestTemporalMergePolicyForceMergeDeletes.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
