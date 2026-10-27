# WordBreakSpellChecker.suggestWordBreaks drops better suggestions when maxChanges &gt;= 3: the full-queue early return added in 9.12.0 (GITHUB#12100) assumes a breadth-first order that the depth-first recursion does not follow

Bug LFV-538 · Area: suggest / spell (WordBreakSpellChecker word breaks) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index some words and call WordBreakSpellChecker.suggestWordBreaks with setMaxChanges(3) or more, and you expect the maxSuggestions splits of the term that rank best by Lucene's own order (fewest breaks, then highest frequency). Instead, once an earlier branch of the search has filled the queue, a later branch returns before it looks for its own splits, so a better split can be missing: with the index {a, ab, b, c, d, e}, the term 'abcde', maxChanges=4 and maxSuggestions=1 the method returns [a b c d e] (4 breaks) instead of [ab c d e] (3 breaks), while asking for 2 suggestions returns both in the right order. No error is raised, raising maxChanges from 3 to 4 makes the answer worse, and 9.11.1 returns the right split.

## Expected vs actual

- Expected: You expect the maxSuggestions splits of the term that rank best by Lucene's own order (fewest breaks, then highest frequency).
- Actual: Once an earlier branch of the search has filled the queue, a later branch returns before it looks for its own splits, so a better split can be missing: with the index {a, ab, b, c, d, e}, the term 'abcde', maxChanges=4 and maxSuggestions=1 the method returns [a b c d e] (4 breaks) instead of [ab c d e] (3 breaks), while asking for 2 suggestions returns both in the right order.

## Files

- [src/org/apache/lucene/search/spell/TestWordBreakPruneDeeperQueued.java](src/org/apache/lucene/search/spell/TestWordBreakPruneDeeperQueued.java) (JUnit test)

## Run it

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
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.spell.TestWordBreakPruneDeeperQueued
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
