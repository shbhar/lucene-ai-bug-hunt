# AnalyzingInfixSuggester.lookup ignores position increments, so a token that the query analyzer stacks on the word still being typed becomes a required exact term: with ASCIIFoldingFilter(preserveOriginal=true) the prefix 'naï' returns no suggestions, even for 'naïve art' (every release 4.4.0 to 10.6.0, and main)

Finding LFV-1397 · Area: suggest / AnalyzingInfixSuggester and BlendedInfixSuggester lookup query construction · Priority: low · Impact: minor · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an AnalyzingInfixSuggester with one analyzer (StandardTokenizer + ASCIIFoldingFilter(preserveOriginal=true)), add the single suggestion 'naïve art' and call lookup("naï", 10, true, false), expecting [naïve art] because 'naï' is a prefix of the indexed word 'naïve'. Instead you get []: the filter emits the folded 'nai' and then the original 'naï' at the same position (increment 0), lookup never reads position increments and turns every emitted token except the last into a required exact TermQuery, so it requires the exact term 'nai', which no suggestion holds, and only 'naï' gets the prefix query. The same root drops suggestions for other stacking query analyzers (WordDelimiterFilter with PRESERVE_ORIGINAL: 'wi-f' finds nothing for 'wi-fi router') and in BlendedInfixSuggester, which inherits the lookup.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: You get []: the filter emits the folded 'nai' and then the original 'naï' at the same position (increment 0), lookup never reads position increments and turns every emitted token except the last into a required exact TermQuery, so it requires the exact term 'nai', which no suggestion holds, and only 'naï' gets the prefix query.

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestInfixLookupStackedPrefix.java](src/org/apache/lucene/search/suggest/analyzing/TestInfixLookupStackedPrefix.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestInfixLookupStackedPrefix
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
