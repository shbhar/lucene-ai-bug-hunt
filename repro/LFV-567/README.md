# FunctionQuery, FunctionRangeQuery and ValueSource sort fields share one non-thread-safe context map across the slices of an executor search, and scale (ScaleFloatFunction) writes to it during the search, so when that write grows the map the search throws NullPointerException or silently returns different scores

Bug LFV-567 · Area: queries / function queries (ValueSource context map under concurrent search) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with a FunctionQuery (or FunctionRangeQuery, or a sort by ValueSource.getSortField) whose function uses scale together with about twenty context-storing sources such as query, on an IndexSearcher built with an executor, and expect the same hits and scores as on a searcher without one, because IndexSearcher is documented as completely thread safe. Instead all slices share the Weight's one IdentityHashMap context (documented 'non-threadsafe'), ScaleFloatFunction.getValues puts its min/max entry into it from the slice threads, and when that put resizes the map other slices miss entries such as "searcher". The search then throws NullPointerException, or, on a searcher with a non-default Similarity, QueryValueSource silently rebuilds its weight with the default Similarity and the scores and top hits change.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: All slices share the Weight's one IdentityHashMap context (documented 'non-threadsafe'), ScaleFloatFunction.getValues puts its min/max entry into it from the slice threads, and when that put resizes the map other slices miss entries such as "searcher".

## Files

- [src/org/apache/lucene/queries/function/TestFunctionQueryScaleConcurrentSlices.java](src/org/apache/lucene/queries/function/TestFunctionQueryScaleConcurrentSlices.java) (JUnit test)
- [src2/org/apache/lucene/queries/function/TestFunctionQueryScaleConcurrentSlices.java](src2/org/apache/lucene/queries/function/TestFunctionQueryScaleConcurrentSlices.java) (JUnit test)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.function.TestFunctionQueryScaleConcurrentSlices
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.function.TestFunctionQueryScaleConcurrentSlices
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
