# ComplexPhraseQueryParser accepts a quoted phrase without a positive clause (run: the stop word "the" and "to be or not to be" with EnglishAnalyzer, the quoted negation "-quick") and its query rewrites to a SpanNearQuery without clauses and without a field (for the quoted negation: a SpanNotQuery around one); a search with it over a MemoryIndex throws NullPointerException, while over an index in a directory it returns no hit on the builds run from 9.1.0 on (known upstream: the issue #9712, LUCENE-8666)

Finding LFV-1590 · Area: queryparser / complexPhrase: ComplexPhraseQueryParser (ComplexPhraseQuery.rewrite building a SpanNearQuery from no clause); reached over a MemoryIndex (memory, monitor, unified highlighter) · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse a quoted phrase whose only word the analyzer removes, for example "the" with EnglishAnalyzer, with ComplexPhraseQueryParser and search with the query over a MemoryIndex. You expect no hit: that is what the classic QueryParser's query for the same string gives over the same MemoryIndex and what the same ComplexPhraseQueryParser query gives over an index in a directory (run on 10.6.0, branch_11x and main); no javadoc sentence about this parser states it, and the SpanNearQuery constructor asks its caller for 'at least 2' clauses. Instead parse() returns normally and the search throws NullPointerException, because the phrase is left without a clause, the parser builds a SpanNearQuery from the empty clause list, that query has no field, and the MemoryIndex is asked for the terms of a null field name: the one test (the quoted "the", EnglishAnalyzer, IndexSearcher.count) fails with and without assertions on the 10.6.0 release, on branch_11x and on main, both unreleased.

## Expected vs actual

- Expected: You expect no hit: that is what the classic QueryParser's query for the same string gives over the same MemoryIndex and what the same ComplexPhraseQueryParser query gives over an index in a directory (run on 10.6.0, branch_11x and main); no javadoc sentence about this parser states it, and the SpanNearQuery constructor asks its caller for 'at least 2' clauses.
- Actual: parse() returns normally and the search throws NullPointerException, because the phrase is left without a clause, the parser builds a SpanNearQuery from the empty clause list, that query has no field, and the MemoryIndex is asked for the terms of a null field name: the one test (the quoted "the", EnglishAnalyzer, IndexSearcher.count) fails with and without assertions on the 10.6.0 release, on branch_11x and on main, both unreleased.

## Files

- [src/TestComplexPhraseQuotedStopWordOverMemoryIndex.java](src/TestComplexPhraseQuotedStopWordOverMemoryIndex.java) (JUnit test)

## Run it

- Tested on Lucene main 77d56fefb355 (a commit of 2026-11-25): reproduced on 2026-11-26, with assertions on and off.
- Tested on Lucene branch_11x 4e8fd3dc3bc4 (a commit of 2026-11-25): reproduced on 2026-11-26, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene main 8506598d7a98 (a commit of 2026-11-25): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 55ef56a69344 (a commit of 2026-11-25): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Not new in 11.0: the test fails the same way with the 10.6.0 release jars, on builds of branch_11x (55ef56a69344 and 4e8fd3dc3bc4) and of Lucene main (8506598d7a98 and 77d56fefb355), with and without assertions. Known upstream: the construction is the subject of the Lucene issue #9712 (LUCENE-8666, opened in 2019, open when read on 2026-11-25). Over an index in a directory the same query returns no hit, with no error, on the builds that ran from 9.1.0 on (a separate program: no hit on the 23 releases it ran from 9.1.0 to 10.6.0, NullPointerException on the 13 it ran from 5.4.0 to 9.0.0); the exception needs a reader that throws for a null field name, in the runs a MemoryIndex. What the test does: it adds the text 'the quick brown fox' to a MemoryIndex with EnglishAnalyzer, parses the quoted stop word "the" with ComplexPhraseQueryParser (default settings, the same analyzer) and expects IndexSearcher.count of the parsed query to be 0, because the analyzer removes the phrase's only word. The count throws java.lang.NullPointerException under SpanNearQuery$SpanNearWeight.getSpans, from the MemoryIndex's lookup of a null field name, so the test's assertion is not reached. The test is plain JUnit 4 with no randomness; the seed in the command is not read. On the older releases a first form of the test ran, a LuceneTestCase with one more assertion (the classic QueryParser's query for the same string counts 0 over the same MemoryIndex), unchanged from 9.1.0 on and as a copy adapted to the older API before that: 5.4.0 is the first release that throws and 5.3.2 passes. A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there; for the release, set V=10.6.0 and use the nine 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-analysis-common, lucene-facet, lucene-memory, lucene-queries, lucene-queryparser, lucene-sandbox) in place of the nine $LUCENE jars. The run needs no jar beyond those that the block names. For the Gradle alternative at the end of this page the module is lucene/monitor, whose tests have the parser, the analyzer and the MemoryIndex on their class path (in lucene/queryparser the test does not compile, because that module's tests have neither the analyzer nor the MemoryIndex): with the test copied to lucene/monitor/src/test/ it reports 1 test completed, 1 failed, with java.lang.NullPointerException, on branch_11x 55ef56a69344 and on main 8506598d7a98, with -Ptests.asserts=true and with -Ptests.asserts=false.
- This finding was re-run on Lucene main 82cf7465bb8f on 2026-11-26: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.NullPointerException thrown under SpanNearQuery$SpanNearWeight.getSpans from the MemoryIndex's field lookup, before the test's assertion is reached.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 78A972
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=78A972 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestComplexPhraseQuotedStopWordOverMemoryIndex
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
