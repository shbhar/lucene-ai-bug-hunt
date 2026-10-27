# SmartChineseAnalyzer and ThaiTokenizer lose a supplementary character (an emoji, a CJK Extension B ideograph) that straddles their 1024-char buffer in text with no line break, because SegmentingTokenizerBase.refill cuts the surrogate pair in half: smartcn indexes two U+FFFD halves and ThaiTokenizer drops the character

Bug LFV-233 · Area: analysis / SegmentingTokenizerBase buffer refill (smartcn HMMChineseTokenizer and SmartChineseAnalyzer; ThaiTokenizer and ThaiAnalyzer) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a Chinese paragraph of more than 1024 chars with no line break through the stock SmartChineseAnalyzer, and it contains an emoji or a rare ideograph such as U+20000; you expect that character to be searchable wherever it falls, as it is one position earlier or later. Instead, when its first UTF-16 half is the last char of the tokenizer's 1024-char buffer, SegmentingTokenizerBase cuts the pair in two: smartcn emits the halves U+D840 and U+DC00 as two terms, which the index stores as U+FFFD, and ThaiTokenizer drops the character. A TermQuery for U+20000 then finds 0 documents instead of 1, with no error.

## Expected vs actual

- Expected: You expect that character to be searchable wherever it falls, as it is one position earlier or later.
- Actual: When its first UTF-16 half is the last char of the tokenizer's 1024-char buffer, SegmentingTokenizerBase cuts the pair in two: smartcn emits the halves U+D840 and U+DC00 as two terms, which the index stores as U+FFFD, and ThaiTokenizer drops the character.

## Files

- [src/org/apache/lucene/analysis/util/TestSegmentingTokenizerBaseSurrogateSplit.java](src/org/apache/lucene/analysis/util/TestSegmentingTokenizerBaseSurrogateSplit.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/smartcn/build/libs/lucene-analysis-smartcn-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.util.TestSegmentingTokenizerBaseSurrogateSplit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
