# ShingleFilter with unigrams on (its default) after a stop filter covers a hole at the start of the text only with a filler shingle whose position length jumps over the next word's position, while the filler-only unigram is suppressed, so the token graph is disconnected and QueryBuilder's graph analysis silently drops that word: 'the lord' becomes body:_ lord and misses a document 'lord' (since 6.4.0)

Finding LFV-1457 · Area: analysis-common / shingle (ShingleFilter, ShingleAnalyzerWrapper) as the query-time producer for core QueryBuilder graph analysis (util/graph GraphTokenStreamFiniteStrings) and the query parsers built on QueryBuilder · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyse queries with ShingleFilter after a stop filter (ShingleAnalyzerWrapper over EnglishAnalyzer, or WhitespaceTokenizer, StopFilter and ShingleFilter with its defaults) and search for 'the lord' with QueryBuilder or the classic QueryParser; you expect the query to keep the word 'lord' that the analyzer emits, so a document 'lord' (or 'Lord of the Rings') is found, as it is with graph queries switched off. Instead the removed 'the' leaves a hole at the start, and ShingleFilter covers it only with the filler shingle '_ lord' (position 0, position length 2): the filler-only unigram that would reach position 1, where the unigram 'lord' starts, is never output, so the token graph is disconnected. QueryBuilder's graph analysis keeps only start-to-end paths, builds body:_ lord and finds nothing, with no error.

## Expected vs actual

- Expected: You expect the query to keep the word 'lord' that the analyzer emits, so a document 'lord' (or 'Lord of the Rings') is found, as it is with graph queries switched off.
- Actual: The removed 'the' leaves a hole at the start, and ShingleFilter covers it only with the filler shingle '_ lord' (position 0, position length 2): the filler-only unigram that would reach position 1, where the unigram 'lord' starts, is never output, so the token graph is disconnected.

## Files

- [src/org/apache/lucene/analysis/shingle/TestShingleFilterHoleQuery.java](src/org/apache/lucene/analysis/shingle/TestShingleFilterHoleQuery.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.shingle.TestShingleFilterHoleQuery
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
