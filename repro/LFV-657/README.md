# ICUTokenizer silently drops Korean words when a Hangul syllable is followed by a combining mark, soft hyphen, ZWNJ, ZWJ or U+FEFF: the Hangul rules of Default.rbbi do not attach the trailing Extend/Format character, so the segment gets rule status 0 and is skipped as a non-word (U+D55C U+AD6D U+00AD U+C5B4 keeps only U+C5B4; since 4.7.0, LUCENE-4381)

Bug LFV-657 · Area: analysis-icu / segmentation (ICUTokenizer with DefaultICUTokenizerConfig; break rules Default.rbbi -> Default.brk) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You tokenize the Korean word U+D55C U+AD6D U+C5B4 ('Korean language') with the default ICUTokenizer, once as is, once with an invisible soft hyphen (U+00AD) after its second syllable and once with a combining mark after each syllable, and expect the word to be indexed every time, because ICUTokenizer 'Breaks text into words according to UAX #29' (ICUTokenizer.java:31), whose rule WB4 attaches Extend and Format characters to the preceding letter, and StandardTokenizer indeed keeps U+D55C U+AD6D U+00AD U+C5B4 as one &lt;HANGUL&gt; token. Instead, the two Hangul rules of Default.rbbi (:127 '$HangulSyllable {200};',:191 '$HangulSyllable $HangulSyllable {200};') have no trailing $ExFm*, unlike every other rule of their group, so the generic status-0 rule (:122) gives the longer match, the segment ending in that character gets rule status 0, and ICUTokenizer skips every status-0 segment as a non-word (:209-214): U+D55C U+AD6D U+00AD U+C5B4 yields only U+C5B4, and U+D55C U+0336 U+AD6D U+0336 U+C5B4 U+0336 (strikethrough), U+D55C U+AD6D U+C5B4 U+FEFF and the Middle Korean tone-marked U+B098 U+302E U+B78F U+302E yield no token at all, silently. Lucene 4.6.1 kept all of these words; the gap came with the ICU 52.1 rule import in 4.7.0 (LUCENE-4381) and is in every release since.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The two Hangul rules of Default.rbbi (:127 '$HangulSyllable {200};',:191 '$HangulSyllable $HangulSyllable {200};') have no trailing $ExFm*, unlike every other rule of their group, so the generic status-0 rule (:122) gives the longer match, the segment ending in that character gets rule status 0, and ICUTokenizer skips every status-0 segment as a non-word (:209-214): U+D55C U+AD6D U+00AD U+C5B4 yields only U+C5B4, and U+D55C U+0336 U+AD6D U+0336 U+C5B4 U+0336 (strikethrough), U+D55C U+AD6D U+C5B4 U+FEFF and the Middle Korean tone-marked U+B098 U+302E U+B78F U+302E yield no token at all, silently.

## Files

- [src/org/apache/lucene/analysis/icu/segmentation/TestICUTokenizerHangulExtendFormat.java](src/org/apache/lucene/analysis/icu/segmentation/TestICUTokenizerHangulExtendFormat.java) (JUnit test)
- [src/demo/HangulExtendFormatMain.java](src/demo/HangulExtendFormatMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.analysis.icu.segmentation.TestICUTokenizerHangulExtendFormat

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" demo.HangulExtendFormatMain
```

The module(s) analysis-icu also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
