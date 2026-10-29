# ICUTokenizer silently drops a word that is directly followed by ZERO WIDTH JOINER and an emoji, together with the emoji, because the joined 'word ZWJ emoji' segment gets rule status 0 (UAX #29 WB3c chain in Default.rbbi) and the emoji check looks only at its first code point

Bug LFV-659 · Area: analysis / ICU (ICUTokenizer rule status: Default.rbbi WB3c chain, BreakIteratorWrapper emoji status, status-0 skip) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-659.html](../../bugs/LFV-659.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index text through the stock ICUTokenizer in which a word runs straight into an invisible ZERO WIDTH JOINER (U+200D) and then an emoji, for example 'abc' + U+200D + U+1F44D, or a Malayalam word written in the older ZWJ chillu form followed by an emoji, and expect the word and the emoji to be tokens, as they are with a space before the emoji, without the ZWJ, or with StandardTokenizer. Instead neither appears: Unicode rule WB3c (ZWJ x Extended_Pictographic) keeps 'word ZWJ emoji' in one segment, the break rules give that segment rule status 0 ('not a word'), BreakIteratorWrapper's emoji check looks only at the segment's first code point (a letter), and ICUTokenizer skips every status-0 segment, so 'hello world' + U+200D + U+1F44D + ' again' gives only [hello, again]. A TermQuery for the lost word finds nothing, with no error.

The test shows that ICUTokenizer silently drops the word "abc" (and the emoji) from the input "abc" + U+200D (ZERO WIDTH JOINER) + U+1F44D.

## Expected vs actual

Expected: some token contains "abc". Actual: no tokens at all (`expected a token containing "abc", got []`, with -ea and -da).

## Files

- [src/org/apache/lucene/analysis/icu/segmentation/TestICUTokenizerWordZwjEmoji.java](src/org/apache/lucene/analysis/icu/segmentation/TestICUTokenizerWordZwjEmoji.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/icu/build/libs/lucene-analysis-icu-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.icu.segmentation.TestICUTokenizerWordZwjEmoji
```

The module(s) analysis-icu also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
