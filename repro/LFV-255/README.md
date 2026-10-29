# The file-format javadoc of the default term-vectors format (Lucene90TermVectorsFormat) contradicts the.tvd bytes in three places: the Flags bit meaning is inverted, FieldNums are called delta-encoded but are the plain field numbers, and AvgCharsPerTerm is called absent without positions but is written whenever a field has offsets (documentation defect; the writer and reader agree)

Bug LFV-255 · Area: codecs / term vectors (Lucene90TermVectorsFormat.tvd file-format javadoc vs Lucene90CompressingTermVectorsWriter / Reader) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You decode a.tvd chunk of the default term-vectors format by following its javadoc, which says the Flags Bit is 1 when all fields of the chunk have the same options, that FieldNums is a delta-encoded list, and that AvgCharsPerTerm is absent unless some field has both positions and offsets. The writer does the opposite on all three: it writes 0 for 'same options' (then one flag per distinct field) and 1 otherwise, it stores the plain sorted field numbers (fields 1, 3, 6 are stored as 1, 3, 6, which summed as deltas give 1, 4, 10), and it writes one AvgCharsPerTerm value per distinct field whenever any field has offsets, so a parser built from the text misreads the chunk. Lucene's own reader matches the writer, so nothing breaks at runtime; only the documentation is wrong, and it has been since it was written in 2013.

The test shows that the `Lucene90TermVectorsFormat` javadoc inverts the.tvd Flags bit: a chunk whose fields all have the same options is written with Bit 0, not 1 (documentation defect; writer and reader agree).

## Expected vs actual

Expected (javadoc): Bit 1 for a one-document, one-field chunk. Actual: `Flags Bit of a one-document, one-field chunk expected:<1> but was:<0>` (same with -ea and -da).

## Files

- [src/org/apache/lucene/codecs/lucene90/TestTermVectorsFormatJavadocFlagsBit.java](src/org/apache/lucene/codecs/lucene90/TestTermVectorsFormatJavadocFlagsBit.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene90.TestTermVectorsFormatJavadocFlagsBit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
