# Default points format: after a PointTree visits a leaf and moves back to its parent, the next visitDocIDs, visitDocValues or moveToChild there fails an assertion in BKDReader (only with -ea; results are correct with -da)

Bug LFV-761 · Area: util / BKD points (BKDReader.BKDPointTree navigation: visitDocIDs/visitDocValues on a leaf reset the inner-node input; default points format) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index points with the default codec, take a PointTree from PointValues.getPointTree, go down to a leaf, visit its documents (visitDocIDs or visitDocValues), call moveToParent and then visit the parent or call moveToChild again, which the API has allowed since LUCENE-10262 (Lucene 9.1, 'This change allows random navigation of a PointValues#PointTree'), and expect the parent's documents or its first child. Instead, with assertions enabled, that next call throws AssertionError in BKDReader.BKDPointTree.resetNodeDataPosition (BKDReader.java:401): the leaf visit had reset the reader's inner-node input to a saved position that readNodeData writes only for inner nodes, so for a leaf it is stale (0 on a fresh tree), and the parent then finds the input behind its own saved position. With assertions disabled every later read seeks to an absolute position first, so all results are correct and only -ea test runs fail.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: With assertions enabled, that next call throws AssertionError in BKDReader.BKDPointTree.resetNodeDataPosition (BKDReader.java:401): the leaf visit had reset the reader's inner-node input to a saved position that readNodeData writes only for inner nodes, so for a leaf it is stale (0 on a fresh tree), and the parent then finds the input behind its own saved position.

## Files

- [src/org/apache/lucene/util/bkd/TestBKDPointTreeVisitLeafThenParent.java](src/org/apache/lucene/util/bkd/TestBKDPointTreeVisitLeafThenParent.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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

# run 1: assertions on, seed 254B0D
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=254B0D \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.bkd.TestBKDPointTreeVisitLeafThenParent
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
