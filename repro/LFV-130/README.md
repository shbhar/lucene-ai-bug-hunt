# After a read hits EOF on a multi-chunk MMapDirectory input, seek past EOF succeeds and later reads return bytes from the wrong offset

Bug LFV-130 · Area: store / MMapDirectory (MemorySegmentIndexInput sequential reads and seek) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open a file that spans more than one mmap chunk with MMapDirectory (in the repro, a 40-byte file with 16-byte chunks), read until a read throws EOFException, and then seek the same input past the end, which IndexInput#seek says must throw EOFException. Instead seek(50) succeeds, getFilePointer returns 50 and readByte returns 34, the byte at offset 34, because the two EOF branches leave the cached chunk index one past the last chunk and seek then checks the target against the wrong chunk. Every target in the window that mirrors the last chunk one chunk further on (48 to 56 here) is accepted this way, and a later clone of the input throws java.lang.AssertionError for the same reason.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: seek(50) succeeds, getFilePointer returns 50 and readByte returns 34, the byte at offset 34, because the two EOF branches leave the cached chunk index one past the last chunk and seek then checks the target against the wrong chunk.

## Files

- [src/org/apache/lucene/store/TestMultiChunkSeekPastEOFAfterEOF.java](src/org/apache/lucene/store/TestMultiChunkSeekPastEOFAfterEOF.java) (JUnit test)

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
     execute --select-class org.apache.lucene.store.TestMultiChunkSeekPastEOFAfterEOF
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
