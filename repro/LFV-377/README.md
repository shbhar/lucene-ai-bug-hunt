# Hunspell spell-checker: a CHECKCOMPOUNDPATTERN simplified compound form (the optional replacement field) after the first compound part is checked from the start of the whole word, so valid compounds such as dampfschiffahrt (dampf + schiff|fahrt with 'ff f ff') are rejected and some words (schiffschiffahrt, or auu with 'u u u') make spell/suggest throw StackOverflowError (expandReplacement returns offset 0 over an array that still holds the earlier parts)

Bug LFV-377 · Area: analysis / hunspell (Hunspell.spell and suggest compound checking, CHECKCOMPOUNDPATTERN replacements) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-377.html](../../bugs/LFV-377.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a Hunspell dictionary whose CHECKCOMPOUNDPATTERN rule has a replacement, which hunspell(5) documents as 'The optional replacement parameter allows simplified compound form' (for example 'CHECKCOMPOUNDPATTERN ff f ff' from the Hunspell ChangeLog, for pre-reform German Schiffahrt = Schiff + Fahrt), and expect Hunspell.spell to accept the simplified form wherever it occurs in a compound, as native Hunspell 1.3.2 and 1.7.2 do for dampfschiffahrt, donaudampfschiffahrt, barfozar and barfur, and to return true or false for every word. Instead Lucene accepts schiffahrt but rejects dampfschiffahrt and donaudampfschiffahrt (and barfozar, barfur and barbozan on the content of its own checkcompoundpattern2/3 fixtures), and spell/suggest throw StackOverflowError for schiffschiffahrt, for 'auu' with the one-word dictionary au and 'CHECKCOMPOUNDPATTERN u u u', and for words of native Hunspell's own checkcompoundpattern4 (Telugu sandhi) fixture. The cause: CheckCompoundPattern.expandReplacement builds the expanded word from the start of the whole word (word.chars[0.. word.offset + breakPos], which keeps the earlier compound parts for the FORCEUCASE check) but returns it as a CharsRef with offset 0, while its callers index it relative to the current part, so at the second and later boundaries the stem lookup starts at the beginning of the word and the remaining tail never gets shorter.

The test shows that a CHECKCOMPOUNDPATTERN replacement after the first compound part is checked from the start of the word (rule `u u u`, root `au/x`), so the recursion never ends.

## Expected vs actual

Expected: `spell("auu")` returns false (C Hunspell: misspelled, suggestion `au`). Actual: `StackOverflowError`.

## Files

- [src/org/apache/lucene/analysis/hunspell/TestCompoundPatternReplacementRecursion.java](src/org/apache/lucene/analysis/hunspell/TestCompoundPatternReplacementRecursion.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.hunspell.TestCompoundPatternReplacementRecursion
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/hunspell/](../../models/hunspell/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
