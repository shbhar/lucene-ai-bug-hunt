# Quantized vector merge asks every source reader for a field's vectors without checking that reader's FieldInfos, so re-sorting with SortingCodecReader and addIndexes fails with NullPointerException (AssertionError with -ea) when one segment lacks one of several vector fields

Bug LFV-100 · Area: codecs / quantized vector formats merge (Lucene104 OSQ, Lucene102 BBQ) with index / SortingCodecReader · Priority: medium · Impact: crash · Novelty: new

Bug page: [../../bugs/LFV-100.html](../../bugs/LFV-100.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You re-sort an index the way the SortingCodecReader javadoc describes: wrap every segment with SortingCodecReader.wrap(reader, sort) and pass them all to IndexWriter.addIndexes on a fresh index-sorted writer, with a codec that uses a quantized vector format (for example Lucene104HnswScalarQuantizedVectorsFormat), where one segment has vectors for field b but none for field a. You expect the re-sort to succeed, as it does with the default non-quantized HNSW format and as an ordinary merge of the same segments does. Instead the quantized writer's centroid code asks every source reader for field a without checking that reader's FieldInfos, SortingCodecReader wraps the missing (null) values, and addIndexes fails with a NullPointerException at SortingCodecReader.java:252 (an AssertionError at:318 with -ea), so nothing is added.

The test shows that re-sorting with `SortingCodecReader.wrap` plus one `addIndexes` call (as the class javadoc describes) fails with `Lucene104ScalarQuantizedVectorsFormat` when one re-sorted segment lacks one of two vector fields, because the quantized writer's centroid merge asks every source reader for the field without checking its FieldInfos.

## Expected vs actual

Expected `addIndexes` to add all 3 documents; actual: `AssertionError` at `SortingCodecReader.java:318` with `-ea`, `NullPointerException` at `SortingCodecReader.java:252` with `-da`.

## Files

- [src/org/apache/lucene/index/TestResortQuantizedMissingVectorField.java](src/org/apache/lucene/index/TestResortQuantizedMissingVectorField.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestResortQuantizedMissingVectorField
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
