# DirectIODirectory accepts a mergeBufferSize that is not a multiple of the file system block size, and then every direct-I/O read fails, so merges hit a tragic exception; a size below the block size can also give a zero-capacity write buffer (BufferOverflowException, or writeBytes making no progress)

Bug LFV-226 · Area: misc / store DirectIODirectory (direct-I/O merge buffers) · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create DirectIODirectory(FSDirectory, mergeBufferSize, minBytesDirect) with a mergeBufferSize that is not a multiple of the file system block size (for example 100000, or 4 * 4096 + 100, on a 4 KiB-block file system) and expect merges to read and write through direct I/O as usual, because the constructor documents the value only as 'Size of buffer to use for merging' and accepts any int. Instead every fresh top-level DirectIOIndexInput fails on its first read with IllegalArgumentException 'Negative position' or IOException 'Channel position... is not a multiple of the block size', so a merge that reads through direct I/O fails while opening its segment readers and IndexWriter closes on a tragic exception (the index on disk stays intact, but the error sometimes surfaces as CorruptIndexException); with a size below the block size, some fresh outputs throw BufferOverflowException on the first writeByte and writeBytes on such an output loops without progress. The cause is that the direct buffers are aligned slices whose capacity is always a block multiple (and can be 0), while DirectIOIndexInput starts its file position at -mergeBufferSize and then advances it by the buffer's capacity, so the first read lands at -(mergeBufferSize mod blockSize) or at blockSize - (mergeBufferSize mod blockSize).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: Every fresh top-level DirectIOIndexInput fails on its first read with IllegalArgumentException 'Negative position' or IOException 'Channel position... is not a multiple of the block size', so a merge that reads through direct I/O fails while opening its segment readers and IndexWriter closes on a tragic exception (the index on disk stays intact, but the error sometimes surfaces as CorruptIndexException); with a size below the block size, some fresh outputs throw BufferOverflowException on the first writeByte and writeBytes on such an output loops without progress.

## Files

- [src/org/apache/lucene/misc/store/TestDirectIOMergeBufferSize.java](src/org/apache/lucene/misc/store/TestDirectIOMergeBufferSize.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.store.TestDirectIOMergeBufferSize
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/directio-nrtcaching-dirs/](../../models/directio-nrtcaching-dirs/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
