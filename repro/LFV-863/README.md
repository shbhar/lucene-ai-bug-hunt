# IndexSearcher.explain throws NullPointerException (AssertionError with -ea) for a span query inside a BooleanQuery that Lucene weights without scores (a FILTER or MUST_NOT clause, or the query of a FunctionScoreQuery whose source does not need scores), because BooleanWeight.explain calls score on scorers built with COMPLETE_NO_SCORES

Bug LFV-863 · Area: search / BooleanWeight.explain (span and payload-span queries under non-scoring clauses; FunctionScoreQuery; Monitor ExplainingMatch) · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search with a BooleanQuery such as '+body:quick -(spanNear([body:brown, body:fox], 0, true) body:dog)', which ComplexPhraseQueryParser builds from 'quick AND NOT ("brown fox" OR dog)', and call IndexSearcher.explain to see why a document matched or not; you expect an Explanation. Instead explain throws NullPointerException (AssertionError with -ea): Lucene builds the weight of a FILTER or MUST_NOT clause, or of the query inside a FunctionScoreQuery whose source does not read scores, without scores, but BooleanWeight.explain still asks that weight's scorers for score, and a span scorer built without scores has no similarity scorer. Search results are correct and only explain fails, but Monitor's ExplainingMatch reports such a query as an error and loses its match.

## Expected vs actual

- Expected: You expect an Explanation.
- Actual: Explain throws NullPointerException (AssertionError with -ea): Lucene builds the weight of a FILTER or MUST_NOT clause, or of the query inside a FunctionScoreQuery whose source does not read scores, without scores, but BooleanWeight.explain still asks that weight's scorers for score, and a span scorer built without scores has no similarity scorer.

## Files

- [src/org/apache/lucene/search/TestExplainNonScoringBooleanClause.java](src/org/apache/lucene/search/TestExplainNonScoringBooleanClause.java) (JUnit test)
- [src/org/apache/lucene/monitor/TestExplainingMatchSpanFilter.java](src/org/apache/lucene/monitor/TestExplainingMatchSpanFilter.java) (JUnit test)
- [src/org/apache/lucene/queryparser/surround/query/TestSurroundNotExplain.java](src/org/apache/lucene/queryparser/surround/query/TestSurroundNotExplain.java) (JUnit test)
- [src/ExplainingMatcherProbe.java](src/ExplainingMatcherProbe.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/monitor/build/libs/lucene-monitor-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:-OmitStackTraceInFastThrow -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestExplainNonScoringBooleanClause \
       --select-class org.apache.lucene.monitor.TestExplainingMatchSpanFilter \
       --select-class org.apache.lucene.queryparser.surround.query.TestSurroundNotExplain

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" ExplainingMatcherProbe
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
