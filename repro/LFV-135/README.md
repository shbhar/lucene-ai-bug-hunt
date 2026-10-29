# MemorySegmentIndexInput.readBytes(long,...) past EOF on a multi-chunk MMapDirectory input reports a masked position: IllegalArgumentException 'read negative position' for a non-negative argument, or EOFException naming the wrong position

Bug LFV-135 · Area: store / MMapDirectory (MemorySegmentIndexInput absolute reads, error reporting) · Priority: low · Impact: wrong error · Novelty: known upstream

AI-assisted finding, confirmed by automated checks. Maintainer triage: Confirmed, triaged by Uwe Schindler (Lucene PMC) on 2026-10-29 (https://lists.apache.org/thread/cry1gb5bxr73vs4fk6td2rntvnpqlorr). Uwe Schindler now plans to fix it: the wrong offset is reported through a private method that the subclass can override, and Uwe will post a PR. (Earlier on 2026-10-28 Uwe had suggested ignoring it.). This site files nothing upstream.

## What it shows

You take a random-access view of an MMapDirectory file that spans more than one mmap chunk, for example randomAccessSlice(33, 143) of a 191-byte file with 128-byte chunks, and call readBytes(pos, b, 0, 1) with a position past its end; you expect an EOFException naming that position, as readByte(pos) on the same view, a single-chunk view and NIOFSDirectory give. Instead readBytes(228,...) throws an unchecked IllegalArgumentException 'read negative position (pos=-28)' and readBytes(314,...) throws an EOFException naming position 58, because the method overwrites its position argument with the offset inside the chunk before its error handler reports it.

The test shows that RandomAccessInput.readBytes(228,...) past the end of a 143-byte view of a multi-chunk MMapDirectory file (128-byte chunks, view at offset 33) reports a masked position.

## Expected vs actual

Expected: EOFException naming pos=228 (as readByte(228) gives). Actual (-ea and -da): IllegalArgumentException "read negative position (pos=-28)".

## Files

- [src/org/apache/lucene/store/TestMultiChunkReadBytesPastEOF.java](src/org/apache/lucene/store/TestMultiChunkReadBytesPastEOF.java) (JUnit test)

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
     execute --select-class org.apache.lucene.store.TestMultiChunkReadBytesPastEOF
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
