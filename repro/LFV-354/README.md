# WordDelimiterGraphFilter drops the hole of a swallowed last token (a punctuation-only token such as the '--' in 'foo --', or a token whose parts the flags all switch off) and any stop-word hole that token carries, because it has no end override, so the final position increment is 0 and the next value of a multi-valued field starts too early (released since 6.5.0)

Bug LFV-354 · Area: analysis / WordDelimiterGraphFilter final position increment (end of stream, accumPosInc) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-354.html](../../bugs/LFV-354.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyse 'foo --' (or 'foo the --' behind a StopFilter) with a whitespace tokenizer and WordDelimiterGraphFilter at its default flags, and expect the swallowed punctuation-only token to leave a hole that end reports as a final position increment of 1 (2 with the stop word), as the filter does mid-stream ('foo -- bar' puts bar at +2, which upstream tests assert) and as StopFilter does for 'foo the'. Instead the filter keeps that hole in an internal accumulator that only a later emitted token applies and has no end override, so the final increment is 0. In a multi-valued field with the default gap 0 the next value 'bar' then lands at position 1 instead of 2: PhraseQuery(foo, bar) matches the values 'foo --' | 'bar' while the StopFilter control 'foo the' | 'bar' does not, and the phrase QueryBuilder builds for 'foo -- bar' matches the single value 'foo -- bar' but not the two values (both executed).

## Expected vs actual

- Expected: `foo --` and `foo 12-34` end with a final position increment of 1, `foo the --` with 2, and on the values `foo --` | `bar` the phrase `foo -- bar` finds the document while `"foo bar"` does not.
- Actual: `finalPosInc expected:<1> but was:<0>` (and `<2>` / `<0>` behind the StopFilter), and the phrase counts are `[0, 1]` instead of `[1, 0]`: 4 of 5 tests fail, assertions on and off.

## Files

- [src/org/apache/lucene/analysis/miscellaneous/TestWdgfTrailingHole.java](src/org/apache/lucene/analysis/miscellaneous/TestWdgfTrailingHole.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.miscellaneous.TestWdgfTrailingHole
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestWordDelimiterGraphFilterTrailingHole.java](TestWordDelimiterGraphFilterTrailingHole.java)
- [fix-option-A.diff](fix-option-A.diff)
- [TestWdgfTrailingHole.java](TestWdgfTrailingHole.java)
- [TestWdgfFinalPosIncV1.java](TestWdgfFinalPosIncV1.java)
- [TestWdgfV3CrossValuePhrase.java](TestWdgfV3CrossValuePhrase.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
