# FlatVectorsReader's class javadoc says searching it iterates every vector and scores them, but its search methods return nothing on purpose, so a codec whose vectors format is Lucene99FlatVectorsFormat (the custom-Codec setup that CHANGES 9.12.0 advertises) silently gets no hits from unfiltered kNN and default-decay vector-similarity queries; documentation defect, javadoc-only fix; since 9.12.0

Finding LFV-1435 · Area: core / codecs: flat vector formats (FlatVectorsReader class javadoc) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1435.html](../../bugs/LFV-1435.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index three float vectors with a FilterCodec whose knnVectorsFormat() returns new Lucene99FlatVectorsFormat(...), which is how CHANGES 9.12.0 says a flat format is configured, and run an unfiltered KnnFloatVectorQuery with k = 1. FlatVectorsReader's class javadoc says 'When searching this reader, it iterates every vector in the index and scores them', so you expect the nearest vector. Instead you get 0 hits and no error: the reader holds all 3 vectors, but its search methods are empty on purpose (#13469: flat data is stored, not indexed), and the javadoc, written before the class had a search method, was never updated.

## Expected vs actual

- Expected: `IndexSearcher.count` is 1: the reader scores its one vector, as its class javadoc says.
- Actual: `count` is 0 (`expected:<1> but was:<0>`), with no error, assertions on and off, on main, 10.6.0 and 9.12.0.

## Files

- [src/org/apache/lucene/codecs/hnsw/TestFlatVectorsReaderSearch.java](src/org/apache/lucene/codecs/hnsw/TestFlatVectorsReaderSearch.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.hnsw.TestFlatVectorsReaderSearch
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFlatVectorsReaderSearch.java](TestFlatVectorsReaderSearch.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
