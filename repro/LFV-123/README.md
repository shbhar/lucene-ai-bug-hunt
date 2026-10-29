# AbstractKnnVectorQuery#mergeLeafResults is documented to return hits in any order, but rewrite reads the k-th score from the last entry and DocAndScoreQuery the max score from the first, so an override that only reorders the hits changes the results

Bug LFV-123 · Area: search / kNN query rewrite (AbstractKnnVectorQuery#mergeLeafResults extension point, optimistic re-entry pass) and DocAndScoreQuery max score · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You subclass KnnFloatVectorQuery and override mergeLeafResults, whose javadoc says it returns 'index-level kNN results (no constraint on their ordering)' and is 'useful for reading and / or modifying the final results', so that it returns exactly the default merge's hits in another order (same documents, same scores). You expect the same search results as the stock query. Instead rewrite takes the k-th score from the last entry and DocAndScoreQuery the max score from the first, so with doc-id order a top-1 search for the kNN query OR a 0.5-boosted term returns doc 0 (score 0.51) instead of the exact match, doc 200000 (score 1.0), and on a 10-segment index the k = 50 query re-enters no segment and returns farther documents (first differing doc 16000 instead of 19950).

The test shows that a `KnnFloatVectorQuery` whose `mergeLeafResults` returns the default hits in doc-id order (its javadoc allows any order) gets a rewritten scorer whose `getMaxScore` is taken from the first hit, so the bound is below a score that the scorer then returns.

## Expected vs actual

Expected `getMaxScore >= 1.0` (doc 1 is the exact match); actual `getMaxScore 0.5 < score 1.0`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/TestKnnMergeLeafResultsOrder.java](src/org/apache/lucene/search/TestKnnMergeLeafResultsOrder.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.TestKnnMergeLeafResultsOrder
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/knn-query-layer/](../../models/knn-query-layer/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
