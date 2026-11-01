# JapaneseNumberFilter and KoreanNumberFilter emit a composed number on the attribute state of the token after it, so the number takes that token's part of speech, reading, position increment and position length (車は三台 with は removed by a StopFilter gives 3 +1 名詞-接尾-助数詞 instead of 3 +2 名詞-数) (known: #10130, Japanese part of speech) (released since 6.0.0 / 8.2.0)

Finding LFV-1058 · Area: analysis / kuromoji and nori number filters (JapaneseNumberFilter, KoreanNumberFilter: attribute state of the composed number token) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add JapaneseNumberFilter (or KoreanNumberFilter) to an analysis chain and expect a composed number such as 3 (from 三) to keep the attributes of the numerals it replaces: the javadoc says its part of speech and reading come from 'the last token used to compose the normalized number', and as a replacement it should sit at the position of its first numeral. Instead the filter reads one token past the number to find its end, captures that look-ahead token's state and overwrites only the term and offsets, so in 車は三台 with は removed by a StopFilter the number comes out as '3 +1 名詞-接尾-助数詞' (the increment and part of speech of 台) instead of '3 +2 名詞-数'. No error is raised; a part-of-speech stop filter after it then drops numbers that are followed by a particle or a space (the case reported in #10130), and a hole in front of the number disappears, so phrase queries over it can miss.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The filter reads one token past the number to find its end, captures that look-ahead token's state and overwrites only the term and offsets, so in 車は三台 with は removed by a StopFilter the number comes out as '3 +1 名詞-接尾-助数詞' (the increment and part of speech of 台) instead of '3 +2 名詞-数'.

## Files

- [src/org/apache/lucene/analysis/ja/TestJapaneseNumberFilterAttributes.java](src/org/apache/lucene/analysis/ja/TestJapaneseNumberFilterAttributes.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/kuromoji/build/libs/lucene-analysis-kuromoji-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ja.TestJapaneseNumberFilterAttributes
```

The module(s) analysis-kuromoji also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
