# Bulk NumericDocValues#longValues (new in 10.5.0) throws on a valid dense segment for one exact request size per byte-aligned width, because its size guard checks the unpadded byte count against Integer.MAX_VALUE and then adds the padding bytes in int (40/48-bit: wraps to Integer.MIN_VALUE, 'read past EOF'; 16/24/56-bit: a byte above the JVM limit)

Bug LFV-381 · Area: codecs / doc values (Lucene90DocValuesProducer dense byte-aligned bulk decode, NumericDocValues#longValues) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a dense numeric doc-values field that the default codec stores as one fixed 40-bit width, in a segment of 429,496,729 documents, and read every value with a single NumericDocValues#longValues call, expecting what the advanceExact/longValue loop in its javadoc returns. Instead Lucene90DocValuesProducer.bulkDecodeByteAlignedValues checks only the unpadded byte count (5 x 429,496,729 = 2,147,483,645) against Integer.MAX_VALUE and then adds 3 padding bytes in int arithmetic, which wraps to Integer.MIN_VALUE; the negative length passes the slice and allocation checks, and the call throws EOFException 'read past EOF (pos=0)' on a valid file (ArrayIndexOutOfBoundsException with NIOFS or an in-heap directory). The same wrap happens at 357,913,941 docs for 48-bit values, and at one size each for 16, 24 and 56 bits the padded count asks for a byte longer than the JVM allows (OutOfMemoryError 'Requested array size exceeds VM limit'), while the per-document loop and a batch one document smaller return every value.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Lucene90DocValuesProducer.bulkDecodeByteAlignedValues checks only the unpadded byte count (5 x 429,496,729 = 2,147,483,645) against Integer.MAX_VALUE and then adds 3 padding bytes in int arithmetic, which wraps to Integer.MIN_VALUE; the negative length passes the slice and allocation checks, and the call throws EOFException 'read past EOF (pos=0)' on a valid file (ArrayIndexOutOfBoundsException with NIOFS or an in-heap directory).

## Files

- [src/org/apache/lucene/index/TestNumericBulkDecodePaddedSize.java](src/org/apache/lucene/index/TestNumericBulkDecodePaddedSize.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed B0B94
java -da -dsa -Dtests.asserts=false -Xmx12g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.monster=true -Dbulkdecode.dir=heap -Dtests.seed=B0B94 -Dtests.method=test48BitsWholeSegment \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestNumericBulkDecodePaddedSize
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/numeric-dv-bulk-values/](../../models/numeric-dv-bulk-values/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
