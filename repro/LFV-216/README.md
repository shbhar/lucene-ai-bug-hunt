# Expressions: ExpressionValueSource.equals/hashCode ignore the functions an expression was compiled with, so the same expression text compiled with different custom functions compares equal and the query cache can return one query's matches for the other

Bug LFV-216 · Area: expressions / ExpressionValueSource equality (cache keys of FunctionMatchQuery and facet DoubleRange drill-down queries built on expressions) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You compile the same expression text 'f(x)' twice with custom function maps that bind f to different functions (Math.abs in one, negation in the other), wrap each in a FunctionMatchQuery with the same 'v &gt; 0' predicate, and expect two different queries, because on documents with x = -3, -2, -1, 1, 2 the first matches 5 documents and the second 3. Instead the two value sources, and therefore the two queries, compare equal with equal hash codes: ExpressionValueSource.equals/hashCode look only at the source text, the bound variables and needsScores, not at the functions the text was compiled with. A query cache then answers the second query with the first query's cached matches, so count returns 5 instead of 3, with no error.

The test shows that `f(x)` compiled with f = Math.abs and `f(x)` compiled with f = negate give equal `ExpressionValueSource`s, so through a query cache the second `FunctionMatchQuery(..., v > 0)` gets the first one's cached matches.

## Expected vs actual

Expected `count` = 3 for -x &gt; 0 on x = -3, -2, -1, 1, 2; actual: 5, the abs(x) &gt; 0 result (`expected:<3> but was:<5>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/expressions/TestExpressionFunctionsQueryCacheKey.java](src/org/apache/lucene/expressions/TestExpressionFunctionsQueryCacheKey.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/expressions/build/libs/lucene-expressions-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.expressions.TestExpressionFunctionsQueryCacheKey
```

The module(s) expressions also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
