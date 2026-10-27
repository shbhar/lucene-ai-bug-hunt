# ThaiRepeatFilter gives the repeated word the offsets of a deleted character or of the following markup instead of the Maiyamok's, when a CharFilter removed characters right after the Maiyamok (a zero-width space in the default ThaiAnalyzer, a closing tag with HTMLStripCharFilter)

Bug LFV-231 · Area: analysis / Thai (ThaiRepeatFilter offset split; ThaiAnalyzer with ThaiCharFilter, or a custom chain with HTMLStripCharFilter) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyze 'บ่อยๆ' + U+200B + 'ครับ' (a word written with the repetition mark ๆ, then a zero-width space) with the stock ThaiAnalyzer and expect what happens without the zero-width space, and what the filter's CHANGES entry promises ('exact OffsetAttribute and highlighter compatibility'): the base token บ่อย covers the word [0,4) and the repeated token บ่อย covers the ๆ [4,5). Instead the base token gets [0,5), which includes the ๆ, and the repeated token gets [5,6), which is only the removed zero-width space: ThaiRepeatFilter assumes that the last characters of the token's offset span are its trailing ๆ, but the CharFilter's offset correction put the removed character at the end of that span. With HTMLStripCharFilter the repeated token points at the last character of the tag after the word instead, so a highlighter writes its markup inside the tag; terms and positions are correct and nothing fails.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The base token gets [0,5), which includes the ๆ, and the repeated token gets [5,6), which is only the removed zero-width space: ThaiRepeatFilter assumes that the last characters of the token's offset span are its trailing ๆ, but the CharFilter's offset correction put the removed character at the end of that span.

## Files

- [src/org/apache/lucene/analysis/th/TestThaiRepeatFilterZeroWidthOffsets.java](src/org/apache/lucene/analysis/th/TestThaiRepeatFilterZeroWidthOffsets.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.th.TestThaiRepeatFilterZeroWidthOffsets
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
