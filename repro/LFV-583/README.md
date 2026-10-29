# SuggestStopFilter.end leaves out the positions of the stop words it removes at the end of the input (StopFilter counts them), so a suggester with SuggestStopFilter in its query analyzer returns extra suggestions for text that ends in removed stop words and a space (two or more stop words for AnalyzingSuggester and FuzzySuggester, one for the document suggester; wrong end since 4.5.0, visible in suggesters since 6.5.0)

Bug LFV-583 · Area: suggest / SuggestStopFilter (query analysis of the analyzing and document suggesters) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-583.html](../../bugs/LFV-583.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an AnalyzingSuggester over 'one direction' and 'one of the best' with a StopFilter index analyzer and a SuggestStopFilter query analyzer, as the SuggestStopFilter javadoc recommends, and look up 'one of the ' (the user has typed 'the' and a space). You expect what a StopFilter query analyzer gives, [one of the best], because the class is documented as 'Like StopFilter' except that it keeps an unterminated last stop word, and StopFilter adds the positions of removed trailing stop words to the final position increment in end. Instead SuggestStopFilter.end reports the input's own final increment (0 instead of 2), so the lookup automaton has too few trailing holes and the lookup also returns 'one direction'; the tokens and the final offset are correct.

The test shows that `SuggestStopFilter` removes the separator-terminated trailing stop words of `"one of the "` but its `end()` leaves their positions out of the final position increment.

## Expected vs actual

Expected: 2, as `StopFilter` reports on the same input (the class is documented as "Like StopFilter"). Actual: `expected:<2> but was:<0>` (-ea and -da).

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestSuggestStopFilterTrailingHoles.java](src/org/apache/lucene/search/suggest/analyzing/TestSuggestStopFilterTrailingHoles.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestSuggestStopFilterTrailingHoles
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
