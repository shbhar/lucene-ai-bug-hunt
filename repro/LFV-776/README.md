# DoubleRange#getQuery(Query, DoubleValuesSource) and LongRange#getQuery(Query, LongValuesSource) never call rewrite(searcher) on the values source, so a source that needs rewriting (DoubleValuesSource.fromQuery, IndexReaderFunctions) throws 'must be rewritten', and a legacy QueryValueSource view silently ignores the searcher's Similarity

Bug LFV-776 · Area: facet / range drill-down and filter queries (DoubleRange/LongRange.ValueSourceQuery) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a range filter with DoubleRange#getQuery(null, DoubleValuesSource.fromQuery(new TermQuery(...))) (or LongRange#getQuery with the source's toLongValuesSource view) and run it on an IndexSearcher, and expect it to match the documents whose query score falls in the range, as FunctionMatchQuery does with the same source, because core says Queries that use a DoubleValuesSource call rewrite in createWeight. Instead the search throws UnsupportedOperationException 'This DoubleValuesSource must be rewritten': the range query hands the source to getValues without ever rewriting it against the searcher. With a legacy QueryValueSource view on a searcher that uses ClassicSimilarity the same omission is silent: the source scores with a fresh default-Similarity searcher, and the range [top score, top score] matches 0 documents where FunctionMatchQuery matches 3.

The test shows that `DoubleRange#getQuery(null, DoubleValuesSource.fromQuery(new TermQuery(f:a)))` on a one-document index never rewrites the values source against the searcher.

## Expected vs actual

Expected: count 1 (the document scores &gt; 0; values-source queries call `rewrite()` in `createWeight`); actual (-ea and -da): `UnsupportedOperationException: This DoubleValuesSource must be rewritten` from `DoubleRange.ValueSourceQuery`.

## Files

- [src/org/apache/lucene/facet/range/TestDoubleRangeGetQueryRewritesSource.java](src/org/apache/lucene/facet/range/TestDoubleRangeGetQueryRewritesSource.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.range.TestDoubleRangeGetQueryRewritesSource
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
