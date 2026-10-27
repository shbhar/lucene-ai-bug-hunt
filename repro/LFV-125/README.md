# PatienceKnnVectorQuery over a SeededKnnVectorQuery stores a searcher-specific seed Weight in its own field during rewrite, so every search changes the query's equals/hashCode, and searching one query object concurrently on two different readers fails

Bug LFV-125 · Area: search / kNN query layer (PatienceKnnVectorQuery over SeededKnnVectorQuery) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build PatienceKnnVectorQuery.fromSeededQuery(SeededKnnVectorQuery.fromFloatQuery(knnQuery, seedQuery)) and search it with IndexSearcher. You expect the query object to be unchanged by the search (Weight javadoc: 'searching does not modify a Query, so that a Query instance can be reused'), so it can be compared, cached and reused, including by several threads on different readers. Instead rewrite stores a new SeededKnnVectorQuery holding a seed Weight built for the current searcher in the query's own delegate field: after one search the query's hashCode has changed and it no longer equals an identical unsearched query, and when two searches of the same object on different readers overlap, the first one runs the other searcher's seed Weight on its later segments and fails with 'AssertionError: The top-reader used to create Weight is not the same as the current reader's top-reader' (-ea) or 'ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1' at TermStates.get (-da).

## Expected vs actual

- Expected: You expect the query object to be unchanged by the search (Weight javadoc: 'searching does not modify a Query, so that a Query instance can be reused'), so it can be compared, cached and reused, including by several threads on different readers.
- Actual: Rewrite stores a new SeededKnnVectorQuery holding a seed Weight built for the current searcher in the query's own delegate field: after one search the query's hashCode has changed and it no longer equals an identical unsearched query, and when two searches of the same object on different readers overlap, the first one runs the other searcher's seed Weight on its later segments and fails with 'AssertionError: The top-reader used to create Weight is not the same as the current reader's top-reader' (-ea) or 'ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1' at TermStates.get (-da).

## Files

- [src/org/apache/lucene/search/TestPatienceSeededRewriteMutation.java](src/org/apache/lucene/search/TestPatienceSeededRewriteMutation.java) (JUnit test)

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
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestPatienceSeededRewriteMutation
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/knn-query-layer/](../../models/knn-query-layer/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
