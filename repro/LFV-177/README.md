# TieredMergePolicy forceMerge(n) with n &gt; 1 can leave more than n segments, because segments too large to re-merge are not counted toward n

Bug LFV-177 · Area: index / TieredMergePolicy forced merges (findForcedMerges, IndexWriter.forceMerge with maxNumSegments > 1) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call IndexWriter.forceMerge(2) (or any n &gt; 1) with the default TieredMergePolicy on an index that holds one very large segment without deletes plus a few small segments, and expect at most n segments afterwards, as the forceMerge javadoc ('maximum number of segments left in the index after merging finishes') and TieredMergePolicy's 'maxNumSegments wins' note promise; merging only the small segments would get there without breaking any size limit. Instead findForcedMerges sets the large segment aside as too big to re-merge but never counts it against n, so it treats the small segments as already merged (or stops one merge early) and forceMerge returns with n + 1 or more segments. In the repro an all-default policy returns no merge at all for [10 GB, 1 GB, 1 GB] at n = 2, and IndexWriter.forceMerge(2) on a 1 MB segment plus three 10-document segments (setMaxMergedSegmentMB(0.1)) stops at 3 segments.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: findForcedMerges sets the large segment aside as too big to re-merge but never counts it against n, so it treats the small segments as already merged (or stops one merge early) and forceMerge returns with n + 1 or more segments.

## Files

- [src/org/apache/lucene/index/TestTieredForceMergeMaxNumSegments.java](src/org/apache/lucene/index/TestTieredForceMergeMaxNumSegments.java) (JUnit test)

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
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF -Dtests.method=testIndexWriterForceMergeTwo \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestTieredForceMergeMaxNumSegments
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
