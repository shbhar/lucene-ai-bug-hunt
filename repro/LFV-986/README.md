# DirectSpellChecker.suggestSimilar computes its candidate budget numSug * maxInspections in int: with setMaxInspections(Integer.MAX_VALUE) ('inspect everything') and any even numSug the budget wraps to -numSug, so the call throws NullPointerException or silently returns no suggestions

Bug LFV-986 · Area: suggest / spell checking (DirectSpellChecker.suggestSimilar candidate budget numSug * maxInspections) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-986.html](../../bugs/LFV-986.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You tell DirectSpellChecker to inspect as many candidates as it likes with setMaxInspections(Integer.MAX_VALUE) and ask suggestSimilar for 2 suggestions; you expect the best suggestions, at least as good as with a budget of 1000. Instead the internal budget numSug * maxInspections overflows int to -2: if the word has a one-edit neighbour the call throws NullPointerException, and if its only neighbours are two edits away it returns an empty array with no error. The outcome flips with the parity of numSug (1 and 3 work, 2 and 4 fail), and the one-line fix is to compute the budget in long and cap it at Integer.MAX_VALUE.

The test shows that `DirectSpellChecker` with `setMaxInspections(Integer.MAX_VALUE)` returns no suggestions for `luxeny` against an index holding only `lucene` when asked for 2 suggestions, because `numSug * maxInspections` wraps to -2.

## Expected vs actual

Expected `[lucene]` (what the default maxInspections returns); actual: `[]`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/spell/TestDirectSpellCheckerMaxInspections.java](src/org/apache/lucene/search/spell/TestDirectSpellCheckerMaxInspections.java) (JUnit test)

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
     execute --select-class org.apache.lucene.search.spell.TestDirectSpellCheckerMaxInspections
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/spellchecker-candidate-budget-int-overflow/](../../models/spellchecker-candidate-budget-int-overflow/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
