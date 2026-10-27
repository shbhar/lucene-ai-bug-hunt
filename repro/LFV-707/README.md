# SmartChineseAnalyzer silently drops letters and letter-numbers as if they were punctuation, among them the Chinese zero U+3007 (二〇〇八年 -&gt; 二, 八, 年) and the Roman numerals Ⅰ-Ⅻ (第Ⅲ章 and 第Ⅳ章 analyze identically), because Utility.getCharType classifies four whole Unicode blocks as DELIMITER

Bug LFV-707 · Area: analysis-smartcn / HHMM segmenter character classes (Utility.getCharType -> HHMMSegmenter DELIMITER token -> SegTokenFilter ',' -> default stop list; SmartChineseAnalyzer and HMMChineseTokenizer) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index Chinese text with the stock SmartChineseAnalyzer, whose javadoc says its default stop list is 'simply a list of punctuation', so you expect only punctuation to disappear. Instead Utility.getCharType returns DELIMITER for four whole code-unit ranges (0x0021-0x00BB, 0x2010-0x2642, 0x3001-0x301E, 0xFE30-0xFF63) that also hold 248 letters and letter-numbers, among them U+3007 〇 and the Roman numerals Ⅰ-Ⅻ; each one becomes ',' and the stop list removes it. So 二〇〇八年 ('2008') analyzes to [二, 八, 年], 第Ⅲ章 and 第Ⅳ章 ('chapter III', 'chapter IV') give the same terms and positions, and the phrase query for 第Ⅲ章 matches both documents (2 hits, 1 expected), with no error.

## Expected vs actual

- Expected: You expect only punctuation to disappear.
- Actual: Utility.getCharType returns DELIMITER for four whole code-unit ranges (0x0021-0x00BB, 0x2010-0x2642, 0x3001-0x301E, 0xFE30-0xFF63) that also hold 248 letters and letter-numbers, among them U+3007 〇 and the Roman numerals Ⅰ-Ⅻ; each one becomes ',' and the stop list removes it.

## Files

- [src/org/apache/lucene/analysis/cn/smart/TestNumbersAndLettersNotPunctuation.java](src/org/apache/lucene/analysis/cn/smart/TestNumbersAndLettersNotPunctuation.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/smartcn/build/libs/lucene-analysis-smartcn-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.cn.smart.TestNumbersAndLettersNotPunctuation
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
