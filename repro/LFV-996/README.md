# SpellChecker.suggestSimilar computes its hit budget 10 * numSug in int, so numSug = Integer.MAX_VALUE ('all suggestions'), 214748365 and every other count whose product wraps to &lt;= 0 throw IllegalArgumentException 'numHits must be &gt; 0' instead of returning the suggestions, and counts whose product wraps to a small positive value silently return fewer suggestions

Bug LFV-996 · Area: suggest / spell checking (SpellChecker.suggestSimilar hit budget 10 * numSug and SuggestWordQueue size) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an n-gram spell index with SpellChecker and call suggestSimilar(word, Integer.MAX_VALUE) to get all suggestions; you expect the same 9 suggestions that numSug 1000 returns on a 10-word dictionary. Instead SpellChecker multiplies numSug by 10 in int to size its search, the product wraps to -10, and the search throws IllegalArgumentException 'numHits must be &gt; 0; please use TotalHitCountCollectorManager...', naming a parameter the caller never passed; the same happens for half of all counts from 214,748,365 up, and in the blocks between (for example 429,496,730, whose product wraps to 4) the call silently returns fewer suggestions after allocating a queue of numSug slots. The fix is two lines: compute the budget in long and saturate it at Integer.MAX_VALUE, and size the queue by min(numSug, hits.length).

The test shows that SpellChecker.suggestSimilar(word, Integer.MAX_VALUE) computes its hit budget 10 * numSug in int, which wraps to -10, so the search rejects it.

## Expected vs actual

Expected `[lucene]` for `lucenx` on a one-word dictionary; actual `IllegalArgumentException: numHits must be > 0;...`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/spell/TestSpellCheckerNumSugOverflow.java](src/org/apache/lucene/search/spell/TestSpellCheckerNumSugOverflow.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.spell.TestSpellCheckerNumSugOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/spellchecker-candidate-budget-int-overflow/](../../models/spellchecker-candidate-budget-int-overflow/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
