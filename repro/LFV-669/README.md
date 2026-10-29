# TeluguNormalizer composes ఒ + ౌ (U+0C12 LETTER O + U+0C4C VOWEL SIGN AU) into AU (U+0C14) but never applies its own AU -&gt; OO fold to the result, so in a chain without IndicNormalizationFilter in front the two spellings of AU normalize to different terms

Bug LFV-669 · Area: analysis-common / Telugu normalizer (TeluguNormalizer.normalize; TeluguNormalizationFilter and the 'teluguNormalization' factory) · Priority: low · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-669.html](../../bugs/LFV-669.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze Telugu text with TeluguNormalizationFilter (or the 'teluguNormalization' factory) without IndicNormalizationFilter in front of it, as in the factory javadoc example. The normalizer folds the letter AU (ఔ, U+0C14) to OO (ఓ, U+0C13), a rule the upstream test pins, and it also recognizes the two-character spelling ఒ + ౌ (U+0C12 U+0C4C) as AU, so both spellings of a word such as ఔషధం ('medicine') should give the same term. Instead the composition writes AU at a position that the single left-to-right pass never visits again, so ఒౌషధం keeps AU (term ఔషధం) while ఔషధం becomes ఓషధం, and the two spellings silently stop matching each other.

The test shows that TeluguNormalizationFilter composes ఒ + ౌ (U+0C12 U+0C4C) into AU (ఔ, U+0C14) but never applies its own AU -&gt; OO fold to it.

## Expected vs actual

Expected `ఒౌషధం` to normalize to `ఓషధం` (OO), as precomposed `ఔషధం` does; actual: `ఔషధం` (AU kept): `term 0 expected:<[ఓ]షధం> but was:<[ఔ]షధం>`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/analysis/te/TestTeluguComposedAu.java](src/org/apache/lucene/analysis/te/TestTeluguComposedAu.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.te.TestTeluguComposedAu
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
