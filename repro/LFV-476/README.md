# ByteBuffersDataInput readShort/readInt/readLong throw IndexOutOfBoundsException instead of EOFException on a read past the end of input longer than one block (ByteBuffersDirectory files over 1 KiB)

Bug LFV-476 · Area: store / ByteBuffersDataInput sequential reads (ByteBuffersDirectory, ByteBuffersIndexInput, NRTCachingDirectory RAM tier) · Priority: low · Impact: wrong error · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a file of more than 1 KiB to a ByteBuffersDirectory (say 1,030 bytes), seek to 2 bytes before the end and call readInt, and expect an EOFException: readByte and readBytes of the same input throw it, NIOFSDirectory and MMapDirectory throw it for the same bytes, and BaseDirectoryTestCase#testIntsUnderflow requires it of every Directory. Instead readInt throws an unchecked IndexOutOfBoundsException (at the exact end of a 2,048-byte input an ArrayIndexOutOfBoundsException), and readShort, readLong, readShorts and readInts do the same, while a file that fits in the first 1 KiB block still gives EOFException. The cause is the fast path added in 9.0.0 (LUCENE-10149): it checks the read against the block size (blockOffset + width &lt;= blockMask), not against the end of the data in the last block, and reads with an absolute ByteBuffer get whose exception nothing translates.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: readInt throws an unchecked IndexOutOfBoundsException (at the exact end of a 2,048-byte input an ArrayIndexOutOfBoundsException), and readShort, readLong, readShorts and readInts do the same, while a file that fits in the first 1 KiB block still gives EOFException.

## Files

- [src/org/apache/lucene/store/TestByteBuffersReadPastEOF.java](src/org/apache/lucene/store/TestByteBuffersReadPastEOF.java) (JUnit test)
- [src/ByteBuffersReadPastEOFMain.java](src/ByteBuffersReadPastEOFMain.java) (main() class)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.store.TestByteBuffersReadPastEOF

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" ByteBuffersReadPastEOFMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
