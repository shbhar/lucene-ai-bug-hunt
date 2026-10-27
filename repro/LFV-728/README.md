# SmartChineseAnalyzer indexes whitespace characters as terms: VERTICAL TAB, FORM FEED, OGHAM SPACE MARK and the eleven spaces U+2000-U+200A (EN QUAD to HAIR SPACE, among them EN, EM and THIN SPACE) each become a one-character term, because Utility.getCharType classifies them OTHER instead of SPACE_LIKE

Bug LFV-728 · Area: analysis-smartcn / HHMM segmenter character classes (Utility.getCharType SPACE_LIKE test -> HHMMSegmenter OTHER branch -> one-character STRING term; SmartChineseAnalyzer and HMMChineseTokenizer) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index text with the stock SmartChineseAnalyzer, whose segmenter has a character class for 'Characters that act as a space' (CharType.SPACE_LIKE) and skips those characters, so you expect any space to separate words without a trace, as an ordinary space, a tab or the ideographic space U+3000 does. Instead Utility.getCharType knows only those five, and VERTICAL TAB, FORM FEED, OGHAM SPACE MARK and U+2000-U+200A fall through to OTHER and are each indexed as a one-character term: 'hello&lt;THIN SPACE&gt;world' gives [hello, U+2009, world], a TermQuery for U+2009 alone finds the document, and '中&lt;U+2009&gt;国' gives [中, U+2009, 国] where '中 国' gives [中国]. So the exact phrase "hello world" matches only 3 of 7 documents that differ only in the separator (space, tab and U+3000 match; FORM FEED, U+1680, EM SPACE and THIN SPACE miss), and HTML indented with &amp;emsp;&amp;emsp; gets two whitespace terms, with no error.

## Expected vs actual

- Expected: You expect any space to separate words without a trace, as an ordinary space, a tab or the ideographic space U+3000 does.
- Actual: Utility.getCharType knows only those five, and VERTICAL TAB, FORM FEED, OGHAM SPACE MARK and U+2000-U+200A fall through to OTHER and are each indexed as a one-character term: 'hello&lt;THIN SPACE&gt;world' gives [hello, U+2009, world], a TermQuery for U+2009 alone finds the document, and '中&lt;U+2009&gt;国' gives [中, U+2009, 国] where '中 国' gives [中国].

## Files

- [src/org/apache/lucene/analysis/cn/smart/TestUnicodeSpacesAreSkipped.java](src/org/apache/lucene/analysis/cn/smart/TestUnicodeSpacesAreSkipped.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.cn.smart.TestUnicodeSpacesAreSkipped
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
