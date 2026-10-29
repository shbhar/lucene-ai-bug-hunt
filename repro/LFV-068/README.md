# HNSW addConnections descends from the worst upper-level candidate instead of the best

Bug LFV-068 · Area: search / HNSW · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

During merge-time HNSW graph repair and rebalancing, addConnections descends through the upper levels to find entry points. You expect it to descend from the best candidate, as its javadoc says. Instead it pops from a min-heap and descends from the worst; the naive fixes cut distance computations by 10-30% but lowered recall in 8 of 8 region-delete configurations.

The test shows that when merge-time repair (`InitializedHnswGraphBuilder.initGraph`) re-links a node from scratch, `addConnections` descends from the worst upper-level candidate (the min-heap `popNode()`), not the best.

## Expected vs actual

Expected: X (at 1), whose only neighbor was deleted, is linked through the level-1 node E (at 0), the one closest to X. Actual (-ea and -da): it is linked through P (at 100) (`expected:<0> but was:<1>`).

## Files

- [src/org/apache/lucene/util/hnsw/TestAddConnectionsRepairDescent.java](src/org/apache/lucene/util/hnsw/TestAddConnectionsRepairDescent.java) (JUnit test)

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
     execute --select-class org.apache.lucene.util.hnsw.TestAddConnectionsRepairDescent
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/study-hnsw-concurrent-merge/](../../models/study-hnsw-concurrent-merge/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
