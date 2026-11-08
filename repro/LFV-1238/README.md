# HnswGraphSearcher.search throws NegativeArraySizeException (-2) on every search of a non-empty HnswGraph whose maxConn() is the public constant HnswGraph.UNKNOWN_MAX_CONN, a value its own dispatch deliberately sends to the unfiltered searcher, because that searcher sizes its bulk-scoring scratch as graph.maxConn() * 2 (since 10.3.0)

Finding LFV-1238 · Area: core / util/hnsw: HnswGraphSearcher (bulk-scoring scratch of the unfiltered searcher) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1238.html](../../bugs/LFV-1238.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You implement HnswGraph for a graph whose M you do not know, so maxConn() returns the public constant HnswGraph.UNKNOWN_MAX_CONN (-1), and search it with HnswGraphSearcher.search, whose dispatch handles that value on purpose by skipping only the filtered searcher ('We can only use filtered search if we know the maxConn'). You expect the nearest neighbours, as the same graph gives when it reports a known maxConn and as 10.2.x gives. Instead every search throws java.lang.NegativeArraySizeException: -2 from HnswGraphSearcher.prepareScratchState, because since 10.3.0 the unfiltered searcher allocates its bulk-scoring scratch as new int[graph.maxConn() * 2] (in searchLevel, and since 10.4.0 also in findBestEntryPoint for graphs with more than one level).

## Expected vs actual

- Expected: One hit, node 0, as on 10.2.2 and as on main when `maxConn()` returns 16.
- Actual: `java.lang.NegativeArraySizeException: -2` at `HnswGraphSearcher.prepareScratchState(HnswGraphSearcher.java:371)`, with assertions on and off.

## Files

- [src/org/apache/lucene/util/hnsw/TestHnswSearchUnknownMaxConn.java](src/org/apache/lucene/util/hnsw/TestHnswSearchUnknownMaxConn.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.hnsw.TestHnswSearchUnknownMaxConn
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestHnswSearchUnknownMaxConn.java](TestHnswSearchUnknownMaxConn.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
