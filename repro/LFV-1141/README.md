# kNN and vector-similarity queries accept an all-zero query target on a COSINE field, which the field itself rejects; the cosine is NaN, so the search returns no hits or the HNSW entry document with score NaN, with no error

Finding LFV-1141 · Area: core / kNN vector queries (COSINE) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, reproduced on Lucene main but not yet independently re-verified, and not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index COSINE vectors and search with an all-zero query vector, which KnnFloatVectorField would reject ('zero vector not allowed with cosine similarity function'). You expect the query to be rejected too, or to return valid COSINE scores in [0, 1]. Instead it is accepted and scores NaN against every document: similarity queries and exact-search segments return no hits, an unfiltered kNN search returns the HNSW entry document of each graph-searched segment with score NaN, and with assertions enabled VectorUtil.cosine's isFinite assertion fails.

## Expected vs actual

- Expected: You expect the query to be rejected too, or to return valid COSINE scores in [0, 1].
- Actual: It is accepted and scores NaN against every document: similarity queries and exact-search segments return no hits, an unfiltered kNN search returns the HNSW entry document of each graph-searched segment with score NaN, and with assertions enabled VectorUtil.cosine's isFinite assertion fails.

## Files

- [src/org/apache/lucene/search/TestKnnCosineZeroQueryTarget.java](src/org/apache/lucene/search/TestKnnCosineZeroQueryTarget.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestKnnCosineZeroQueryTarget
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this finding's part of the hunt used: [../../models/cosine-zero-query-vector-silent-empty-results-known-15540-row-or-dnp/](../../models/cosine-zero-query-vector-silent-empty-results-known-15540-row-or-dnp/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
