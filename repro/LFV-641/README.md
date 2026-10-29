# Hunspell rejects, cannot stem and does not generate twofold-suffix words (a second suffix allowed by the first suffix's continuation class) when either suffix class has cross product N, although cross product only governs prefix+suffix combinations: with 'SFX S 0 st/E', 'SFX E N 2' (en, e) and raich/SE, spell("raichsten") is false; the real Luxembourgish dictionary rejects räichsten

Bug LFV-641 · Area: analysis / hunspell (Stemmer affix stripping: Hunspell.spell, getRoots, analyzeSimpleWord, Stemmer.stem, HunspellStemFilter; WordFormGenerator.getAllWordForms) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-641.html](../../bugs/LFV-641.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a Hunspell dictionary with a twofold suffix, where the first suffix's continuation class allows a second suffix (for example 'SFX S Y 1 / SFX S 0 st/E.', 'SFX E N 2 / SFX E 0 en. / SFX E 0 e.' and the entry raich/SE), and spell-check or stem raichsten. You expect it to be accepted and stemmed to raich: hunspell(5) defines the cross-product field only as the 'permission to combine prefixes and suffixes', no prefix is involved, and C Hunspell accepts the word with N as with Y. Instead Lucene rejects raichsten and raichste, returns no stems and leaves them out of getAllWordForms("raich"), because Stemmer.applyAffix (Stemmer.java:621), Stemmer.isAffixCompatible (:564) and WordFormGenerator.expand (:251) follow a suffix+suffix chain only when the suffix classes are cross product Y; the real Luxembourgish dictionary shows the same for räichsten, räichste, räichstem and räichster.

The test shows that with the hunspell(5) twofold-suffix example (drink/X, `SFX X 0 able/Y`, `SFX Y 0 s`) and cross product N on class Y, `Hunspell.spell("drinkables")` is false, although cross product only governs prefix+suffix combinations.

## Expected vs actual

Expected `spell("drinkables")` true (as with `SFX Y Y`); actual false, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/hunspell/TestTwofoldSuffixCrossProduct.java](src/org/apache/lucene/analysis/hunspell/TestTwofoldSuffixCrossProduct.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.hunspell.TestTwofoldSuffixCrossProduct
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/hunspell-wordform-generator-munch-differential/](../../models/hunspell-wordform-generator-munch-differential/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
