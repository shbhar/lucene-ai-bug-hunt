# JapaneseTokenizer and KoreanTokenizer cut an unknown word at the 1024-char cap (MAX_UNKNOWN_WORD_LENGTH) even in the middle of a surrogate pair, so a supplementary character (an emoji, a CJK Extension B ideograph) at char 1023 of a long same-class run is emitted as two unpaired surrogate halves and indexed as U+FFFD

Bug LFV-459 · Area: analysis / kuromoji and nori Viterbi unknown-word length cap (JapaneseTokenizer and the 'japanese' factory; KoreanTokenizer, KoreanAnalyzer and the 'korean' factory) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze a long run of text that kuromoji or nori treat as one unknown word, for example 1,200 chars of Thai without spaces through the 'japanese' tokenizer, or 400 x (U+1F441 U+FE0F) emoji through the default KoreanAnalyzer, and the run holds an emoji or a rare ideograph such as U+20000; you expect every term to consist of whole characters, as it does when that character sits one position earlier or later. Instead both tokenizers stop the unknown word at exactly 1024 UTF-16 chars without checking for a surrogate pair, so when the character's first half is char 1023, term 0 ends in a lone high surrogate and term 1 starts with the lone low surrogate. The index silently stores both halves as U+FFFD, and in the unigram modes a TermQuery for the character finds 0 documents instead of 1.

## Expected vs actual

- Expected: You expect every term to consist of whole characters, as it does when that character sits one position earlier or later.
- Actual: Both tokenizers stop the unknown word at exactly 1024 UTF-16 chars without checking for a surrogate pair, so when the character's first half is char 1023, term 0 ends in a lone high surrogate and term 1 starts with the lone low surrogate.

## Files

- [src/org/apache/lucene/analysis/ja/TestJapaneseUnknownWordSurrogatePair.java](src/org/apache/lucene/analysis/ja/TestJapaneseUnknownWordSurrogatePair.java) (JUnit test)
- [src/org/apache/lucene/analysis/ko/TestKoreanUnknownWordSurrogatePair.java](src/org/apache/lucene/analysis/ko/TestKoreanUnknownWordSurrogatePair.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
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
CP=$CP:$LUCENE/lucene/analysis/kuromoji/build/libs/lucene-analysis-kuromoji-$V.jar
CP=$CP:$LUCENE/lucene/analysis/nori/build/libs/lucene-analysis-nori-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ja.TestJapaneseUnknownWordSurrogatePair \
       --select-class org.apache.lucene.analysis.ko.TestKoreanUnknownWordSurrogatePair
```

The module(s) analysis-kuromoji, analysis-nori also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
