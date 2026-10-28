# ConcatenatingTokenStream loses a source's trailing position increment (for example a removed stop word) when the next source produces no tokens, so later tokens are indexed too close and phrase queries over-match

Bug LFV-488 · Area: analysis / common miscellaneous (ConcatenatingTokenStream#incrementToken and #end: carry-over of child final position increments) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-488.html](../../bugs/LFV-488.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build one field from several token streams with ConcatenatingTokenStream, for example the values 'alpha the', '' and 'beta', each run through a StopFilter that removes 'the'. You expect the hole left by the removed 'the' to survive, as it does when no empty value sits in between and as IndexWriter keeps it for the same values in a multi-valued field, because LUCENE-8650 (7.7.0) made the class carry each source's final position increment over to the next token. Instead each exhausted source overwrites the pending increment instead of adding to it, and end reports only the last source's own increment, so a source with no tokens erases the hole: 'beta' is indexed directly after 'alpha', the exact phrase "alpha beta" matches this document but not the single value 'alpha the beta', and with an empty last source end reports 0 instead of 1.

## Expected vs actual

- Expected: `bar` gets increment 2 with or without the empty source, `end` reports 1 after an empty last source, and `beta` sits at position 2 as in the multi-valued field.
- Actual: `bar` gets 1, `end` reports 0, and `beta` sits at position 1 with 1 hit for the exact phrase "alpha beta": 4 tests, 3 failed.

## Files

- [src/org/apache/lucene/analysis/miscellaneous/TestConcatenatingEmptySourceHoles.java](src/org/apache/lucene/analysis/miscellaneous/TestConcatenatingEmptySourceHoles.java) (JUnit test)
- [src/ConcatEmptySourceProbe.java](src/ConcatEmptySourceProbe.java) (main() class)

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
     execute --select-class org.apache.lucene.analysis.miscellaneous.TestConcatenatingEmptySourceHoles

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" ConcatEmptySourceProbe
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestConcatenatingTokenStreamEmptySources.java](TestConcatenatingTokenStreamEmptySources.java)
- [TestConcatenatingEmptySourceHoles.java](TestConcatenatingEmptySourceHoles.java)
- [TestConcatEmptySourcePositions.java](TestConcatEmptySourcePositions.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
