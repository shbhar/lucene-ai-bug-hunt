# Vector-similarity threshold queries (Float/Byte/Float16VectorSimilarityQuery at the default decay) explain every document whose exact similarity clears the threshold as a match, but return only what the approximate graph search collected, so explain calls documents the query does not return matches; inside a BooleanQuery or FunctionScoreQuery that explain then reports another document's score, fails an assertion or throws NullPointerException

Bug LFV-864 · Area: core / search: vector-similarity queries (AbstractVectorSimilarityQuery Weight.explain vs scorerSupplier), with consequences in BooleanWeight.explain and FunctionScoreQuery.explain · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a FloatVectorSimilarityQuery.Adaptive (or the Byte or Float16 variant) with the default decay and call IndexSearcher.explain for a document, and you expect explain to agree with the hits, as the kNN queries do: a document the query does not return is explained as a non-match. Instead explain applies the exact threshold test, so a document above the threshold that the approximate graph search did not collect is explained as '... = Score above threshold' although it is not a hit, and Weight.matches of the same Weight returns null for it. Wrapped in a BooleanQuery, explain of such a non-hit returns a match with the next hit's score (-da), fails 'assert advanced == doc' (-ea) or throws NullPointerException when the segment has no vector hits, FunctionScoreQuery's explain throws the same NullPointerException, and the hits of a hybrid 'term OR vector' query can be explained with a vector contribution that is not in their score.

## Expected vs actual

- Expected: You expect explain to agree with the hits, as the kNN queries do: a document the query does not return is explained as a non-match.
- Actual: Explain applies the exact threshold test, so a document above the threshold that the approximate graph search did not collect is explained as '... = Score above threshold' although it is not a hit, and Weight.matches of the same Weight returns null for it.

## Files

- [src/org/apache/lucene/search/TestExplainVectorSimilarityClause.java](src/org/apache/lucene/search/TestExplainVectorSimilarityClause.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:-OmitStackTraceInFastThrow -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestExplainVectorSimilarityClause
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
