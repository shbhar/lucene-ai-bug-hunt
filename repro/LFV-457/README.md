# Intervals.analyzedText keeps only the first of the tokens stacked at a position whenever the analyzed text is a token graph (a multi-word synonym or a WordDelimiterGraphFilter catenation elsewhere in the text), so interval queries silently miss documents, even documents with exactly the query words (AssertionError under -ea)

Bug LFV-457 · Area: queries / intervals (Intervals.analyzedText: IntervalBuilder.analyzeGraph; flexible StandardQueryParser fn: interval functions) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-457.html](../../bugs/LFV-457.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an interval query with Intervals.analyzedText("wifi dog", analyzer, "f", 0, true) (or the flexible StandardQueryParser's fn:or("wifi dog")), where the query analyzer's SynonymGraphFilter has the rules wifi =&gt; 'wi fi' and dog =&gt; canine. You expect it to match 'wifi dog', 'wifi canine', 'wi fi dog' and 'wi fi canine', as QueryBuilder's phrase query with the same analyzer does and as analyzedText("dog") alone does with or(canine,dog). Instead, because 'wifi' has a two-position synonym, the builder keeps only the first token stacked at the 'dog' position, builds or(BLOCK(wi,fi,canine),BLOCK(wifi,canine)) and misses the two 'dog' documents, including the one that holds exactly the query text; with assertions enabled it throws AssertionError.

## Expected vs actual

- Expected: 4 of 4 documents for `analyzedText("guinea pig dogs")`, in every `(maxGaps, ordered)` mode.
- Actual: 2 of 4 (`or(BLOCK(cavy,dogs),BLOCK(guinea,pig,dogs))`, `dog` dropped) with assertions off; `AssertionError` at `IntervalBuilder.java:233` with them on.

## Files

- [src/org/apache/lucene/queries/intervals/TestAnalyzedTextGraphStackedSynonyms.java](src/org/apache/lucene/queries/intervals/TestAnalyzedTextGraphStackedSynonyms.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestAnalyzedTextGraphStackedSynonyms
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestAnalyzedTextGraphStackedTokens.java](TestAnalyzedTextGraphStackedTokens.java)
- [TestAnalyzedTextGraphStackedSynonyms.java](TestAnalyzedTextGraphStackedSynonyms.java)
- [or-stacked-tokens.diff](or-stacked-tokens.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
