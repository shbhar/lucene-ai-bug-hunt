# Quantized vector merge asks every source reader for a field's vectors without checking that reader's FieldInfos, so re-sorting with SortingCodecReader and addIndexes fails with NullPointerException (AssertionError with -ea) when one segment lacks one of several vector fields

Bug LFV-100 · Area: codecs / quantized vector formats merge (Lucene104 OSQ, Lucene102 BBQ) with index / SortingCodecReader · Priority: medium · Impact: crash · Novelty: new

Bug page: [../../bugs/LFV-100.html](../../bugs/LFV-100.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You re-sort an index the way the SortingCodecReader javadoc describes: wrap every segment with SortingCodecReader.wrap(reader, sort) and pass them all to IndexWriter.addIndexes on a fresh index-sorted writer, with a codec that uses a quantized vector format (for example Lucene104HnswScalarQuantizedVectorsFormat), where one segment has vectors for field b but none for field a. You expect the re-sort to succeed, as it does with the default non-quantized HNSW format and as an ordinary merge of the same segments does. Instead the quantized writer's centroid code asks every source reader for field a without checking that reader's FieldInfos, SortingCodecReader wraps the missing (null) values, and addIndexes fails with a NullPointerException at SortingCodecReader.java:252 (an AssertionError at:318 with -ea), so nothing is added.

## Expected vs actual

- Expected: `addIndexes` succeeds: 6 documents, 3 of them with a `vec` vector, as with the non-quantized format.
- Actual: `addIndexes` throws `NullPointerException` at `SortingCodecReader.java:252` (`AssertionError` at `:318` with `-ea`) and adds 0 of 6 documents.

## Files

- [src/org/apache/lucene/index/TestSortingCodecReaderMissingVectorField.java](src/org/apache/lucene/index/TestSortingCodecReaderMissingVectorField.java) (JUnit test)
- [src/org/apache/lucene/index/TestSortingCodecReaderMissingVectorField102.java](src/org/apache/lucene/index/TestSortingCodecReaderMissingVectorField102.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestSortingCodecReaderMissingVectorField

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestSortingCodecReaderMissingVectorField102
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestSortingCodecReaderQuantizedMissingField.java](TestSortingCodecReaderQuantizedMissingField.java)
- [TestSortingCodecReaderMissingVectorField.java](TestSortingCodecReaderMissingVectorField.java)
- [TestResortQuantizedMissingField.java](TestResortQuantizedMissingField.java)
- [TestResortQuantizedMissingFieldFp16.java](TestResortQuantizedMissingFieldFp16.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
