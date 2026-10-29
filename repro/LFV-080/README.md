# MemoryIndex reports the total number of point dimensions as the number of index dimensions

Bug LFV-080 · Area: memory / MemoryIndex · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, reproduced on Lucene main but not yet independently re-verified, and not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a selectively indexed point field (for example 2 dimensions, 1 index dimension) to a MemoryIndex and run a point query on it. You expect the query to work as on a normal index. Instead getNumIndexDimensions returns the total dimension count and the query throws IllegalArgumentException.

The test shows that a MemoryIndex point field with 2 dimensions and 1 index dimension cannot be queried, because MemoryIndex's PointValues.getNumIndexDimensions() returns the total dimension count (MemoryIndex.java:2196).

## Expected vs actual

Expected: IntPoint.newExactQuery("f", 10) matches the value (10, 5). Actual (-ea and -da): IllegalArgumentException 'field="f" was indexed with numIndexDimensions=2 but this query has numDims=1'.

## Files

- [src/org/apache/lucene/index/memory/TestMemoryIndexSelectiveIndexing.java](src/org/apache/lucene/index/memory/TestMemoryIndexSelectiveIndexing.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.memory.TestMemoryIndexSelectiveIndexing
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
