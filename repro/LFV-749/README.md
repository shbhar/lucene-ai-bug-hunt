# JapaneseTokenizer and KoreanTokenizer with discardPunctuation=true (the default) keep punctuation and symbols outside the BMP (U+10100, U+1E95E, emoji such as U+1F600) as tokens, because isPunctuation classifies a single UTF-16 unit (the high surrogate), while BMP characters of the same general category are dropped (released since 3.6.0 and 7.4.0)

Bug LFV-749 · Area: analysis / kuromoji and nori Viterbi tokenizers, discardPunctuation (ja.ViterbiNBest.isPunctuation and its three discard sites, ko.Viterbi.shouldFilterToken) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You tokenize '日本 😀 日本' or '한국 𐄀 한국' (U+1F600, U+10100) with JapaneseTokenizer or KoreanTokenizer and discardPunctuation=true, the default, and expect [日本, 日本] and [한국, 한국], because the javadoc says punctuation tokens are dropped and both tokenizers drop every BMP character of the same Unicode general category (Po U+3001, So U+2605, Sm U+2207). Instead the supplementary character is kept as a token ([日本, 😀, 日本], [한국, 𐄀, 한국]): isPunctuation(char) calls Character.getType on the high surrogate alone, whose type is SURROGATE, so the discard test at the token start never sees the real category. No error is raised; the default KoreanAnalyzer indexes the extra token, and the default JapaneseAnalyzer removes it only later, through its part-of-speech stop filter, which leaves a position gap.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The supplementary character is kept as a token ([日本, 😀, 日本], [한국, 𐄀, 한국]): isPunctuation(char) calls Character.getType on the high surrogate alone, whose type is SURROGATE, so the discard test at the token start never sees the real category.

## Files

- [src/org/apache/lucene/analysis/TestDiscardPunctuationSupplementary.java](src/org/apache/lucene/analysis/TestDiscardPunctuationSupplementary.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/kuromoji/build/libs/lucene-analysis-kuromoji-$V.jar
CP=$CP:$LUCENE/lucene/analysis/nori/build/libs/lucene-analysis-nori-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.TestDiscardPunctuationSupplementary
```

The module(s) analysis-kuromoji, analysis-nori also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
