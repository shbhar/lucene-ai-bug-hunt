# kNN and vector-similarity query constructors keep the caller's query-vector array instead of copying it, so a caller that refills that buffer before the query runs silently changes which documents the query matches and what it is equal to

Bug LFV-758 · Area: search / vector queries (KnnFloat/Byte/Float16VectorQuery, Float/Byte/Float16VectorSimilarityQuery; join DiversifyingChildren*KnnVectorQuery) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You decode a query embedding into a scratch float, build new KnnFloatVectorQuery(field, scratch, k), decode the next embedding into the same array, build a second query and OR the two; you expect each query to search the vector it was built from, as TermQuery does when built from a reused BytesRef. Instead both queries now search the second vector and compare equal, so the OR returns 1 hit instead of 2 (the neighbour of the first vector is lost); the same holds for KnnByteVectorQuery, KnnFloat16VectorQuery, the three vector-similarity queries and the join DiversifyingChildren*KnnVectorQuery classes. The constructors store the caller's array itself (VectorUtil.checkFinite returns its argument) and search, equals and hashCode all read it, although getTargetCopy returns a copy precisely so that callers cannot reach that array.

## Expected vs actual

- Expected: You expect each query to search the vector it was built from, as TermQuery does when built from a reused BytesRef.
- Actual: Both queries now search the second vector and compare equal, so the OR returns 1 hit instead of 2 (the neighbour of the first vector is lost); the same holds for KnnByteVectorQuery, KnnFloat16VectorQuery, the three vector-similarity queries and the join DiversifyingChildren*KnnVectorQuery classes.

## Files

- [src/org/apache/lucene/search/TestVectorQueryTargetAliasing.java](src/org/apache/lucene/search/TestVectorQueryTargetAliasing.java) (JUnit test)
- [src/org/apache/lucene/search/TestVectorQueryTargetAliasingRelease.java](src/org/apache/lucene/search/TestVectorQueryTargetAliasingRelease.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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
     execute --select-class org.apache.lucene.search.TestVectorQueryTargetAliasing

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestVectorQueryTargetAliasingRelease
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
