# Intervals.analyzedText keeps only the first of the tokens stacked at a position whenever the analyzed text is a token graph (a multi-word synonym or a WordDelimiterGraphFilter catenation elsewhere in the text), so interval queries silently miss documents, even documents with exactly the query words (AssertionError under -ea)

Bug LFV-457 · Area: queries / intervals (Intervals.analyzedText: IntervalBuilder.analyzeGraph; flexible StandardQueryParser fn: interval functions) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-457.html](../../bugs/LFV-457.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an interval query with Intervals.analyzedText("wifi dog", analyzer, "f", 0, true) (or the flexible StandardQueryParser's fn:or("wifi dog")), where the query analyzer's SynonymGraphFilter has the rules wifi =&gt; 'wi fi' and dog =&gt; canine. You expect it to match 'wifi dog', 'wifi canine', 'wi fi dog' and 'wi fi canine', as QueryBuilder's phrase query with the same analyzer does and as analyzedText("dog") alone does with or(canine,dog). Instead, because 'wifi' has a two-position synonym, the builder keeps only the first token stacked at the 'dog' position, builds or(BLOCK(wi,fi,canine),BLOCK(wifi,canine)) and misses the two 'dog' documents, including the one that holds exactly the query text; with assertions enabled it throws AssertionError.

The test shows that Intervals.analyzedText over the token graph of "wifi dog" ("wi fi" | "wifi", then "canine" and "dog" stacked) keeps only the first stacked token, so the interval query misses the document "wifi dog".

## Expected vs actual

Expected 1 hit; actual 0 hits with -da (`or(BLOCK(wi,fi,canine),BLOCK(wifi,canine)) expected:<1> but was:<0>`), and an AssertionError at IntervalBuilder.analyzeGraph with -ea.

## Files

- [src/org/apache/lucene/queries/intervals/TestAnalyzedTextGraphStackedToken.java](src/org/apache/lucene/queries/intervals/TestAnalyzedTextGraphStackedToken.java) (JUnit test)

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
     execute --select-class org.apache.lucene.queries.intervals.TestAnalyzedTextGraphStackedToken
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
