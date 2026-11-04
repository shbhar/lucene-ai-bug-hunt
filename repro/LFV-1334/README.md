# HNSW graph search drops a whole batch of neighbours when one similarity in it is NaN: the batch gate compares bulkScore's Math.max maximum, which is NaN, with the minimum competitive score, so finite, better neighbours are marked visited and never collected or explored (for example MAXIMUM_INNER_PRODUCT vectors whose dot product overflows to +Inf + -Inf; since 10.4.0)

Finding LFV-1334 · Area: util / HNSW graph search (HnswGraphSearcher and FilteredHnswGraphSearcher batch gates over RandomVectorScorer.bulkScore) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1334.html](../../bugs/LFV-1334.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index three MAXIMUM_INNER_PRODUCT float vectors with finite components into one segment with an HNSW graph (one document and the query have components of 1e38, so their dot product overflows to +Inf + -Inf = NaN) and run KnnFloatVectorQuery with k=2. You expect the two finite best documents, doc 2 (score 4.0) and doc 0 (score 2.0), which 10.3.2 returns on the identical graph. Instead main and 10.4.0 through 10.6.0 return only doc 0: doc 2 is in the same neighbour batch as the NaN document, Math.max makes the batch maximum NaN, 'NaN &gt; minCompetitiveSimilarity()' is false even though the collector is not full, so the whole batch is skipped and doc 2 is never collected or explored.

## Expected vs actual

- Expected: `search(q, 2)` returns doc 2 (score 4.0) then doc 0 (score 2.0), as 10.3.2 does on the same graph.
- Actual: One hit, doc 0 (score 2.0): `AssertionError: expected:<2> but was:<1>`; doc 2 is missing.

## Files

- [src/org/apache/lucene/search/TestHnswNaNScoreHidesNeighbours.java](src/org/apache/lucene/search/TestHnswNaNScoreHidesNeighbours.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestHnswNaNScoreHidesNeighbours
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestHnswNaNScoreHidesNeighbours.java](TestHnswNaNScoreHidesNeighbours.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
