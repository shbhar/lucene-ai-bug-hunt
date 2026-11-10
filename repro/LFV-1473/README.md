# A FunctionQuery over QueryValueSource, TFValueSource or TermFreqValueSource (query(), tf(), termfreq()) fails with a plain RuntimeException instead of the declared IOException when a postings read fails, because the three value sources still catch and rewrap it as they had to before 7.0 (since 7.0.0)

Finding LFV-1473 · Area: queries / function queries: value sources (QueryValueSource, TFValueSource, TermFreqValueSource) and their FunctionValues accessors, used by FunctionQuery · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1473.html](../../bugs/LFV-1473.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run IndexSearcher.search(new FunctionQuery(new QueryValueSource(new TermQuery(f:a), 0f)), 10) on a reader whose postings read fails with IOException, as failing storage can. You expect that IOException: since 7.0.0 the FunctionValues accessors (floatVal, intVal, exists, objectVal, toString(int)) and ValueFiller.fillValue declare it, FunctionQuery's scorer and IndexSearcher.search declare it, and the core DoubleValuesSource.fromQuery over the same query (FunctionScoreQuery) delivers it. Instead the search throws java.lang.RuntimeException('caught exception in QueryDocVals(f:a) doc=0') wrapping it, because QueryValueSource's FunctionValues (floatVal, exists and its ValueFiller), TFValueSource.floatVal and TermFreqValueSource.intVal still catch IOException and rethrow it as RuntimeException, a wrap that was needed before LUCENE-7407 added 'throws IOException' to these accessors and that the same change removed only from JoinDocFreqValueSource.

## Expected vs actual

- Expected: `searcher.search` throws `java.io.IOException: simulated read failure`, the exception every method on the path declares.
- Actual: It throws `java.lang.RuntimeException: caught exception in QueryDocVals(f:a) doc=0`, with the `IOException` only as its cause.

## Files

- [src/org/apache/lucene/queries/function/TestQueryValueSourceIOException.java](src/org/apache/lucene/queries/function/TestQueryValueSourceIOException.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.function.TestQueryValueSourceIOException
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestQueryValueSourceIOException.java](TestQueryValueSourceIOException.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
