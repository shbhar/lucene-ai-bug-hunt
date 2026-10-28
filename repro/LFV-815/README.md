# SortRescorer (and Expression#getRescorer) never rewrites its Sort against the searcher, unlike IndexSearcher#search(Query, int, Sort): sorts that need a rewrite throw, and a legacy query function sort silently uses the default Similarity

Bug LFV-815 · Area: search / rescoring (SortRescorer; expressions ExpressionRescorer inherits it) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You rescore the top hits with new SortRescorer(sort) or expression.getRescorer(bindings) and expect the same order and sort values that IndexSearcher#search(query, n, sort) gives for the same Sort on the same searcher, because DoubleValuesSource tells clients to rewrite against the searcher and IndexSearcher does that for sorted searches. Instead SortRescorer builds its collector from the Sort as given (SortRescorer.java:48-49): for a sort on new QueryValueSource(q, 0).asDoubleValuesSource on a ClassicSimilarity searcher the hits come back in BM25 order with BM25 values (doc0=0.1468, doc1=0.1246 instead of doc1=1.0, doc0=0.894), and ValueSource sort fields or fromQuery sorts throw. Passing sort.rewrite(searcher) inside rescore fixes every case.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: SortRescorer builds its collector from the Sort as given (SortRescorer.java:48-49): for a sort on new QueryValueSource(q, 0).asDoubleValuesSource on a ClassicSimilarity searcher the hits come back in BM25 order with BM25 values (doc0=0.1468, doc1=0.1246 instead of doc1=1.0, doc0=0.894), and ValueSource sort fields or fromQuery sorts throw.

## Files

- [src/org/apache/lucene/queries/function/TestSortRescorerRewritesSort.java](src/org/apache/lucene/queries/function/TestSortRescorerRewritesSort.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/expressions/build/libs/lucene-expressions-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.function.TestSortRescorerRewritesSort
```

The module(s) expressions also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
