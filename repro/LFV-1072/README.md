# WordBreakSpellChecker.suggestWordCombinations still applies minSuggestionFrequency under SUGGEST_MORE_POPULAR, although the setter says it does not apply in that mode (suggestWordBreaks follows the setter), so valid combined words are silently dropped

Finding LFV-1072 · Area: suggest / spell (WordBreakSpellChecker word combinations) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1072.html](../../bugs/LFV-1072.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure a WordBreakSpellChecker with setMinSuggestionFrequency(2), whose javadoc says the minimum is 'Not applicable when used with SUGGEST_MORE_POPULAR', and ask suggestWordCombinations for SUGGEST_MORE_POPULAR suggestions; you expect every indexed combined word that is at least as frequent as its most popular part, which is the method's own MORE_POPULAR rule and what suggestWordBreaks does with the same setting. Instead the minimum still filters combinations: with sun, flower and sunflower in one document (docFreq 1 each), suggestWordCombinations([f:sun, f:flower], 5, reader, SUGGEST_MORE_POPULAR) returns no suggestion instead of sunflower. The cause is the check combinedTermFreq &gt;= minSuggestionFrequency (WordBreakSpellChecker.java:208), which runs after the MORE_POPULAR rule in every mode.

## Expected vs actual

- Expected: One suggestion, `sunflower`: docFreq 1, at least as frequent as its parts `sun` and `flower` (0 each).
- Actual: No suggestion: `expected:<1> but was:<0>`, with assertions on and off; no error.

## Files

- [src/org/apache/lucene/search/spell/TestWordBreakCombineMorePopularMinFreq.java](src/org/apache/lucene/search/spell/TestWordBreakCombineMorePopularMinFreq.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): still reproduces.
- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.spell.TestWordBreakCombineMorePopularMinFreq
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestWordBreakCombineMorePopularMinFreq.java](TestWordBreakCombineMorePopularMinFreq.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
