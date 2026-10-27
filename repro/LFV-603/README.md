# KoreanTokenizer gives the parts of a decompounded word offsets counted back from the end of the word while their text is cut from the start of the word, so when the parts do not cover the whole word (a user-dictionary entry with a shorter segmentation such as '세종시 세종', or 7 compounds of the bundled dictionary such as 픽토그램) the offsets point at other characters (픽토 at [1,3) covers '토그')

Bug LFV-603 · Area: analysis / nori KoreanTokenizer (Viterbi decompound backtrace; UserDictionary and bundled-dictionary compounds) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze text containing 픽토그램 ('pictogram') with the default KoreanAnalyzer, or 세종시 with the user dictionary entry '세종시 세종', and expect each decompounded part's offsets to cover the characters its text came from, as they do when the parts cover the whole word (가락지나물 gives 가락지[0,3) and 나물[3,5)). Instead nori cuts each part's text from the start of the word but counts its offsets back from the end of the word, so 픽토그램 gives 픽토[1,3), covering '토그', and 그[3,4), covering '램', and '세종시 세종' gives 세종[1,3), covering '종시'; highlighters mark the wrong characters. Lucene accepts both inputs without a check: UserDictionary rejects only a segmentation longer than the word, and 7 compounds in the bundled dictionary have parts that do not add up to the word.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Nori cuts each part's text from the start of the word but counts its offsets back from the end of the word, so 픽토그램 gives 픽토[1,3), covering '토그', and 그[3,4), covering '램', and '세종시 세종' gives 세종[1,3), covering '종시'; highlighters mark the wrong characters.

## Files

- [src/org/apache/lucene/analysis/ko/TestDecompoundPartOffsets.java](src/org/apache/lucene/analysis/ko/TestDecompoundPartOffsets.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/nori/build/libs/lucene-analysis-nori-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ko.TestDecompoundPartOffsets
```

The module(s) analysis-nori also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/subtoken-offset-containment-sweep/](../../models/subtoken-offset-containment-sweep/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
