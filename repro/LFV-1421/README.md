# BinaryRangeDocValues' public constructor javadoc says numBytesPerDimension is the 'size of each dimension (2 * encoded value size)', but the class already doubles the value and needs the encoded value size itself (what every in-tree caller passes), so code that follows the javadoc gets ArrayIndexOutOfBoundsException on the first document; documentation defect, javadoc-only fix; since 9.5.0

Finding LFV-1421 · Area: core / document: range doc values (BinaryRangeDocValues constructor javadoc) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1421.html](../../bugs/LFV-1421.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a LongRangeDocValuesField and wrap the field's BinaryDocValues in new BinaryRangeDocValues(values, 1, 2 * Long.BYTES), because the constructor's javadoc says numBytesPerDimension is the 'size of each dimension (2 * encoded value size)'. You expect nextDoc() to return the document and getPackedValue() to hold its 16-byte encoded range. Instead nextDoc() throws ArrayIndexOutOfBoundsException ('arraycopy: last source index 32 out of bounds for byte[16]'): the class already multiplies by 2 (one min and one max per dimension) and needs the size of one encoded value (Long.BYTES), which is what Lucene's own callers pass, so the javadoc doubles the width a second time.

## Expected vs actual

- Expected: Per the javadoc, `nextDoc()` returns doc 0 and the packed value holds its 16-byte range.
- Actual: `ArrayIndexOutOfBoundsException: arraycopy: last source index 32 out of bounds for byte[16]` from `nextDoc()`.

## Files

- [src/org/apache/lucene/document/TestBinaryRangeDocValuesJavadoc.java](src/org/apache/lucene/document/TestBinaryRangeDocValuesJavadoc.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestBinaryRangeDocValuesJavadoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestBinaryRangeDocValuesJavadoc.java](TestBinaryRangeDocValuesJavadoc.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
