# A kNN query built with a null KnnSearchStrategy, documented as 'the default strategy will be used', throws NullPointerException when a seeded HNSW search runs (SeededKnnVectorQuery, or the optimistic re-entry pass when a segment is re-entered)

Bug LFV-114 · Area: search / kNN query layer (KnnSearchStrategy.Seeded, optimistic re-entry pass, SeededKnnVectorQuery) · Priority: medium · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a kNN query with an explicit null search strategy, for example new KnnFloatVectorQuery(field, target, k, null, null), whose javadoc says 'If null, the default strategy will be used', and run it wrapped in SeededKnnVectorQuery, or as a plain query on a multi-segment index where the optimistic second pass re-enters a segment. You expect the same hits as with KnnSearchStrategy.Hnsw.DEFAULT. Instead Lucene wraps the null in KnnSearchStrategy.Seeded for the seeded HNSW walk, and Seeded.nextVectorsBlock calls originalStrategy.nextVectorsBlock without a null check, so the search throws 'NullPointerException: Cannot invoke KnnSearchStrategy.nextVectorsBlock because this.originalStrategy is null' (KnnSearchStrategy.java:169) and returns nothing.

## Expected vs actual

- Expected: You expect the same hits as with KnnSearchStrategy.Hnsw.DEFAULT.
- Actual: Lucene wraps the null in KnnSearchStrategy.Seeded for the seeded HNSW walk, and Seeded.nextVectorsBlock calls originalStrategy.nextVectorsBlock without a null check, so the search throws 'NullPointerException: Cannot invoke KnnSearchStrategy.nextVectorsBlock because this.originalStrategy is null' (KnnSearchStrategy.java:169) and returns nothing.

## Files

- [src/org/apache/lucene/search/TestKnnNullSearchStrategy.java](src/org/apache/lucene/search/TestKnnNullSearchStrategy.java) (JUnit test)
- [src/org/apache/lucene/search/join/TestDiversifyingKnnNullSearchStrategy.java](src/org/apache/lucene/search/join/TestDiversifyingKnnNullSearchStrategy.java) (JUnit test)
- [src2/org/apache/lucene/search/TestKnnNullSearchStrategy.java](src2/org/apache/lucene/search/TestKnnNullSearchStrategy.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestKnnNullSearchStrategy \
       --select-class org.apache.lucene.search.join.TestDiversifyingKnnNullSearchStrategy
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestKnnNullSearchStrategy
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/knn-query-layer/](../../models/knn-query-layer/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
