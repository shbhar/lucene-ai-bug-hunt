# ConditionalTokenFilter (ProtectedTermFilter, CustomAnalyzer when/whenTerm) reports a final position increment of 0 when the last token went through the wrapped filters, so a trailing hole from upstream (such as a removed trailing stop word) is lost and the next value of a multi-valued field starts one position too early (since 7.4.0)

Bug LFV-542 · Area: analysis / common miscellaneous (ConditionalTokenFilter#end and OneTimeWrapper#end: final position increment on the delegate branch) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You analyse 'abc the' with WhitespaceTokenizer, a StopFilter that removes 'the', and a ProtectedTermFilter that wraps a LowerCaseFilter (or the CustomAnalyzer equivalent whenTerm(...).addTokenFilter("lowercase").endwhen), and expect end to report the removed stop word as a final position increment of 1, as the same chain does without the condition and as the class itself does when the last token is protected ('x the' gives 1). Instead it reports 0 whenever the last token went through the wrapped filter, although LowerCaseFilter never touches positions: end keeps the wrapped chain's end state, and the internal wrapper that feeds that chain resets the increment to 0. In a multi-valued field ['abc the', 'def'] with the default gap 0, 'def' is then indexed at position 1 instead of 2, so the PhraseQuery "abc def" matches the document (1 hit) where the plain chain gives 0 (executed).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: It reports 0 whenever the last token went through the wrapped filter, although LowerCaseFilter never touches positions: end keeps the wrapped chain's end state, and the internal wrapper that feeds that chain resets the increment to 0.

## Files

- [src/org/apache/lucene/analysis/miscellaneous/TestConditionalTokenFilterFinalPositionIncrement.java](src/org/apache/lucene/analysis/miscellaneous/TestConditionalTokenFilterFinalPositionIncrement.java) (JUnit test)
- [src/repro/ConditionalEndPosIncMain.java](src/repro/ConditionalEndPosIncMain.java) (main() class)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.analysis.miscellaneous.TestConditionalTokenFilterFinalPositionIncrement

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" repro.ConditionalEndPosIncMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
