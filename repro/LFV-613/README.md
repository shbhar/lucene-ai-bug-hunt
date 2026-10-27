# BKDWriter does not cap its in-heap point buffer at the Java array limit, so with maxMBSortInHeap &gt;= 2048 MB a points merge of more than 2^31 bytes throws NegativeArraySizeException and closes the IndexWriter

Bug LFV-613 · Area: util / BKD points (BKDWriter in-heap sort buffer sizing: HeapPointWriter and the BKDRadixSelector heap writers, reached through Lucene90PointsWriter's public maxMBSortInHeap constructor) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You give a points format a sort budget of 2048 MB or more, for example new Lucene90PointsWriter(state, 512, 2200.0) in a custom codec, and merge segments that hold more than 2 GiB of points of one multi-dimensional field (in the repro 8.4M points of 16 dims x 16 bytes). You expect the merge to succeed, because BKDWriter 'uses up to the specified maxMBSortInHeap heap space for writing', spills to disk beyond it, and its constructor accepts the value. Instead BKDWriter sizes its in-heap buffer as budget / bytesPerDoc points with no cap at the Java array limit, HeapPointWriter computes bytesPerDoc * size in int, the length wraps negative, and forceMerge(1) throws NegativeArraySizeException (-2110967296) and leaves the IndexWriter closed with that tragic exception.

## Expected vs actual

- Expected: You expect the merge to succeed, because BKDWriter 'uses up to the specified maxMBSortInHeap heap space for writing', spills to disk beyond it, and its constructor accepts the value.
- Actual: BKDWriter sizes its in-heap buffer as budget / bytesPerDoc points with no cap at the Java array limit, HeapPointWriter computes bytesPerDoc * size in int, the length wraps negative, and forceMerge(1) throws NegativeArraySizeException (-2110967296) and leaves the IndexWriter closed with that tragic exception.

## Files

- [src/org/apache/lucene/util/bkd/TestBKDHeapSortBufferOverflow.java](src/org/apache/lucene/util/bkd/TestBKDHeapSortBufferOverflow.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx4g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.bkd.TestBKDHeapSortBufferOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/bkd-write-niche-configs/](../../models/bkd-write-niche-configs/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
