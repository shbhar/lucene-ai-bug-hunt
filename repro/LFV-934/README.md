# The file-format javadoc of the default segment info format (Lucene99SegmentInfoFormat) gives a wrong.si grammar: SegVersion and SegMinVersion are typed as String, but the writer stores three Int32s (and a flag byte plus three Int32s), and SegMinVersion and HasBlocks are missing from the documented sequence (documentation defect; the writer and reader agree)

Bug LFV-934 · Area: codecs / segment info (Lucene99SegmentInfoFormat.si file-format javadoc vs its writer and reader) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You decode a segment info (.si) file of the default codec by following the Lucene99SegmentInfoFormat javadoc, which gives the sequence Header, SegVersion, SegSize, IsCompoundFile, Diagnostics, Files, Attributes, IndexSort, Footer and types SegVersion (and SegMinVersion) as a String. The writer instead stores the version as three Int32s (major, minor, bugfix), then a flag byte and, when it is 1, the minimum version as three more Int32s, then SegSize, IsCompoundFile and a HasBlocks byte, so a parser built from the text reads the Int32 major version 11 as a string length and gets 11 NUL characters instead of '11.0.0', and every later field is misaligned. Lucene's own reader matches the writer, so nothing breaks at runtime; only the documentation is wrong, and it has been since 5.0.0.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/codecs/lucene99/TestLucene99SegmentInfoFormatJavadocGrammar.java](src/org/apache/lucene/codecs/lucene99/TestLucene99SegmentInfoFormatJavadocGrammar.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene99.TestLucene99SegmentInfoFormatJavadocGrammar
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
