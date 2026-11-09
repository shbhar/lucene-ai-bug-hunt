# On segments written by Lucene 9.0.0, FloatVectorSimilarityQuery returns at most one hit per segment: Lucene90HnswVectorsReader.search sizes its graph search by knnCollector.k(), which is 1 for the similarity-threshold collector (VectorSimilarityCollector); since 9.10.0

Finding LFV-1432 · Area: backward-codecs / Lucene90 HNSW vectors reader x search / vector similarity-threshold queries · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1432.html](../../bugs/LFV-1432.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open, on current Lucene, an index whose segment was written by Lucene 9.0.0 (three documents with EUCLIDEAN vectors [1, 0], [1, 0.1] and [0, 1]) and count FloatVectorSimilarityQuery.Adaptive("v", [1, 0], 0.5). You expect 2, the two documents with similarity 1.0 and 0.990099, which the same query returns on segments written by 9.1.0 or later and with decay 1 (the exact path). Instead the count is 1: the 9.0 reader runs its own graph search sized by the collector's k(), which is 1 for the similarity-threshold collector, so it hands over a single vector, and the query takes that as the complete result.

## Expected vs actual

- Expected: `count` is 2: the documents `[1, 0]` and `[1, 0.1]` (similarity 1.0 and 0.990) pass the 0.5 threshold.
- Actual: `count` is 1: `AssertionError: expected:<2> but was:<1>`, with assertions on and off.

## Files

- [src/org/apache/lucene/backward_codecs/lucene90/TestLucene90VectorSimilarityQuery.java](src/org/apache/lucene/backward_codecs/lucene90/TestLucene90VectorSimilarityQuery.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/backward-codecs/build/libs/lucene-backward-codecs-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.backward_codecs.lucene90.TestLucene90VectorSimilarityQuery
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestLucene90VectorSimilarityQuery.java](TestLucene90VectorSimilarityQuery.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
