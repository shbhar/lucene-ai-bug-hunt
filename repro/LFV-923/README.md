# IndexSearcher's cumulative maxClauseCount check never counts a query held by a values source (FunctionScoreQuery.boostByQuery's boost query, DoubleValuesSource.fromQuery, QueryValueSource): the function queries' visit stops at the source and each embedded query is checked alone with a fresh budget, so the total number of executed clauses is unbounded

Bug LFV-923 · Area: queries / function queries (FunctionScoreQuery, FunctionMatchQuery, FunctionRangeQuery and FunctionQuery QueryVisitor traversal of DoubleValuesSource/ValueSource; consumer: IndexSearcher's cumulative clause limit) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You search FunctionScoreQuery.boostByQuery(in, boostMatch, 2f), where in and boostMatch are each a 600-term disjunction (1,200 term clauses that all run), with the default maxClauseCount of 1024. You expect TooManyNestedClauses, which you do get for a BooleanQuery holding the same clauses (even when half of them are scoring-only SHOULD or MUST_NOT clauses), because the check counts 'total clauses cumulatively in all of its children' and LUCENE-8811 made it cumulative so that splitting clauses over sub-queries no longer bypasses it. Instead the search runs with correct hits: FunctionScoreQuery.visit visits only `in`, FunctionMatchQuery, FunctionRangeQuery and FunctionQuery.visit call visitLeaf(this), DoubleValuesSource and ValueSource have no visit hook, and the embedded query is rewritten later with searcher.rewrite, which checks it alone with a fresh count; FunctionScoreQuery(fromQuery), FunctionMatchQuery(fromQuery) and FunctionQuery/FunctionRangeQuery over QueryValueSource behave the same.

## Expected vs actual

- Expected: You expect TooManyNestedClauses, which you do get for a BooleanQuery holding the same clauses (even when half of them are scoring-only SHOULD or MUST_NOT clauses), because the check counts 'total clauses cumulatively in all of its children' and LUCENE-8811 made it cumulative so that splitting clauses over sub-queries no longer bypasses it.
- Actual: The search runs with correct hits: FunctionScoreQuery.visit visits only `in`, FunctionMatchQuery, FunctionRangeQuery and FunctionQuery.visit call visitLeaf(this), DoubleValuesSource and ValueSource have no visit hook, and the embedded query is rewritten later with searcher.rewrite, which checks it alone with a fresh count; FunctionScoreQuery(fromQuery), FunctionMatchQuery(fromQuery) and FunctionQuery/FunctionRangeQuery over QueryValueSource behave the same.

## Files

- [src/org/apache/lucene/queries/function/TestFunctionQueriesVisitEmbeddedQuery.java](src/org/apache/lucene/queries/function/TestFunctionQueriesVisitEmbeddedQuery.java) (JUnit test)

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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.function.TestFunctionQueriesVisitEmbeddedQuery
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
