# After a read hits EOF on a multi-chunk MMapDirectory input, seek past EOF succeeds and later reads return bytes from the wrong offset

Bug LFV-130 · Area: store / MMapDirectory (MemorySegmentIndexInput sequential reads and seek) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks. Maintainer triage: Won't fix, triaged by Uwe Schindler (Lucene PMC) on 2026-10-29 (https://github.com/apache/lucene/issues/16937#issuecomment-6508981328). Uwe Schindler explained that once a read or seek throws an IOException (EOFException included), an IndexInput's state is undetermined and it should no longer be used, so the later seek and read are not a bug; Uwe opened #16951 to document this in IndexInput (and may still make it always throw EOFException if that is cheap). Uwe's comment names LFV-140 but links and describes this bug. This site files nothing upstream.

## What it shows

You open a file that spans more than one mmap chunk with MMapDirectory (in the repro, a 40-byte file with 16-byte chunks), read until a read throws EOFException, and then seek the same input past the end, which IndexInput#seek says must throw EOFException. Instead seek(50) succeeds, getFilePointer returns 50 and readByte returns 34, the byte at offset 34, because the two EOF branches leave the cached chunk index one past the last chunk and seek then checks the target against the wrong chunk. Every target in the window that mirrors the last chunk one chunk further on (48 to 56 here) is accepted this way, and a later clone of the input throws java.lang.AssertionError for the same reason.

The test shows that on a multi-chunk MMapDirectory input (40-byte file, 16-byte chunks), after readByte() hits EOF, seek(50) past EOF succeeds and the next read returns a byte from one chunk earlier.

## Expected vs actual

Expected: `seek(50)` throws EOFException (IndexInput#seek). Actual: `expected EOFException from seek(50) on a 40-byte file, but seek succeeded and readByte()=34`.

## Files

- [src/org/apache/lucene/store/TestMMapSeekPastEOFAfterReadEOF.java](src/org/apache/lucene/store/TestMMapSeekPastEOFAfterReadEOF.java) (JUnit test)

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
     execute --select-class org.apache.lucene.store.TestMMapSeekPastEOFAfterReadEOF
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
