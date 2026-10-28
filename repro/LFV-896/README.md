# OpenStringBuilder.write(OpenStringBuilder arr) copies as many chars as this builder's own length instead of arr's length, so it appends nothing, appends chars from arr's buffer past arr's end (NULs or stale earlier content), or throws ArrayIndexOutOfBoundsException

Bug LFV-896 · Area: analysis-common / util OpenStringBuilder (public char-buffer helper; in-tree users KStemmer and HTMLStripCharFilter) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You append one OpenStringBuilder to another with dst.write(src) and expect dst to grow by src's contents, as it does with dst.append(src) and with every other write overload of the class. Instead the method copies dst's own length worth of chars out of src's buffer: an empty dst gets nothing (writing "lucene" gives ""), "hello" plus "ab" gives "helloab" followed by 3 NUL chars, a src that was reset and refilled with "q" leaks its earlier content ("1234" becomes "1234qECR"), and a dst longer than src's backing array throws ArrayIndexOutOfBoundsException. The cause is one token: write(OpenStringBuilder arr) calls write(arr.buf, 0, len) (OpenStringBuilder.java:140), where len is the receiver's field, not arr.len.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The method copies dst's own length worth of chars out of src's buffer: an empty dst gets nothing (writing "lucene" gives ""), "hello" plus "ab" gives "helloab" followed by 3 NUL chars, a src that was reset and refilled with "q" leaks its earlier content ("1234" becomes "1234qECR"), and a dst longer than src's backing array throws ArrayIndexOutOfBoundsException.

## Files

- [src/org/apache/lucene/analysis/util/TestOpenStringBuilderWriteBuilder.java](src/org/apache/lucene/analysis/util/TestOpenStringBuilderWriteBuilder.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.util.TestOpenStringBuilderWriteBuilder
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
