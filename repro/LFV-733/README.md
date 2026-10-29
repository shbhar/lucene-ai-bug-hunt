# SpanMultiTermQueryWrapper's top-N span rewrites (TopTermsSpanBooleanQueryRewrite, FrequentTermsSpanBooleanQueryRewrite) do not cap their size at IndexSearcher#getMaxClauseCount, so a span-wrapped FuzzyQuery that works unwrapped throws TooManyNestedClauses

Bug LFV-733 · Area: queries / spans: SpanMultiTermQueryWrapper top-N rewrite methods vs the core IndexSearcher clause limit · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap new FuzzyQuery(term, 2, 0, 2000, true) in SpanMultiTermQueryWrapper (for example to use it inside a SpanNearQuery) and search an index where more than 1024 terms are within two edits. FuzzyQuery documents that a maxExpansions above IndexSearcher#getMaxClauseCount is replaced by the limit, and the span top-N rewrites say they 'will not overflow the boolean max clause count', so you expect the same capped expansion the plain FuzzyQuery gets (it runs with 1024 terms). Instead the span rewrite keeps up to maxExpansions terms (TopTermsSpanBooleanQueryRewrite.getMaxSize returns Integer.MAX_VALUE, and FrequentTermsSpanBooleanQueryRewrite sizes its queue with the caller's size only), and IndexSearcher.rewrite throws IndexSearcher$TooManyNestedClauses ('maxClauseCount is set to 1024').

The test shows that a default `FuzzyQuery` wrapped in `SpanMultiTermQueryWrapper` is not capped at `IndexSearcher#getMaxClauseCount` (set to 2, four one-edit terms), although the plain FuzzyQuery is, so the span query throws.

## Expected vs actual

Expected the same capped count as the plain FuzzyQuery (2 hits); actual `IndexSearcher$TooManyNestedClauses:... maxClauseCount is set to 2`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/queries/spans/TestSpanWrappedFuzzyQueryClauseLimit.java](src/org/apache/lucene/queries/spans/TestSpanWrappedFuzzyQueryClauseLimit.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.spans.TestSpanWrappedFuzzyQueryClauseLimit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
