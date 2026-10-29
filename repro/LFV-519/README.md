# WordBreakSpellChecker.suggestWordCombinations does not honour maxEvaluations: the counter is compared with == and the break leaves only the inner loop, so every later left term is still evaluated and a limit of 0 or less is ignored (the #12077 fix in 9.5.0 covered only suggestWordBreaks)

Bug LFV-519 · Area: suggest / spell (WordBreakSpellChecker word combinations) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call WordBreakSpellChecker.setMaxEvaluations(n), documented as 'the maximum number of word combinations to evaluate', and then suggestWordCombinations, and you expect at most n combined-term lookups and so at most n suggestions. Instead the limit cuts off only the rest of the one left term at which the counter first equals n, and every later left term is evaluated in full: with every 2-6 letter run of 'abcdef' indexed, the query [a, b, c, d, e, f], maxChanges=5 and maxEvaluations=1, 11 of the 15 combinations are evaluated and returned, and maxEvaluations=0 evaluates all 15. Nothing is thrown and the work stays bounded by the number of terms times maxChanges; only the configured cap is not applied.

The test shows that WordBreakSpellChecker.suggestWordCombinations ignores setMaxEvaluations(1) when maxChanges=2 (index {ab, bc, bcd}, terms [a, b, c, d]).

## Expected vs actual

Expected at most 1 suggestion, because only 1 combination may be evaluated. Actual: 3 suggestions (ab, bc, bcd).

## Files

- [src/org/apache/lucene/search/spell/TestWordBreakCombineMaxEvaluations.java](src/org/apache/lucene/search/spell/TestWordBreakCombineMaxEvaluations.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.spell.TestWordBreakCombineMaxEvaluations
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
