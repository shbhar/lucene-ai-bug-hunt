# ByteSliceReader's final-slice test (nextIndex + newSize &gt;= endIndex) overflows int when a postings stream's last slice ends exactly at the 2^31-byte end of the in-RAM byte pool, so IndexWriter silently commits up to 3 wrong postings bytes that CheckIndex accepts

Bug LFV-386 · Area: index / in-RAM postings byte slices (ByteSliceReader, TermsHashPerField) · Priority: low · Impact: data corruption · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add one very large document whose postings fill the indexing thread's in-RAM byte pool to its very last byte (global offset 2^31), and you expect the flushed segment to hold exactly the positions and payloads you indexed, or a loud failure such as the ArithmeticException Lucene has thrown since 9.8.0 once the pool would grow further. Instead, if one term's postings stream ends in the slice that occupies the pool's last bytes, ByteSliceReader.nextSlice computes nextIndex + newSize = 2^31 in int, which wraps negative, so it treats that final slice as non-final, reads its last 3 data bytes plus the level marker as a forwarding address, and returns the stream's last 1-3 bytes from an unrelated part of the pool. IndexWriter commits with no exception (with -ea and -da) and CheckIndex reports the segment clean; in the executed run the last 3 bytes of a 355-byte payload, 0x41 0x42 0x43, read back as 0x11.

The test shows that when a byte-slice stream's final slice ends exactly at the byte pool's 2^31 end, ByteSliceReader.nextSlice's int test nextIndex + newSize &gt;= endIndex wraps, so the reader treats the final slice as non-final and returns its last 3 bytes from elsewhere in the pool (small heap: the first 65,535 blocks share one array).

## Expected vs actual

Expected: the 14 bytes written (1..14) read back unchanged. Actual (-ea and -da): arrays first differed at element [11]; expected:&lt;12&gt; but was:&lt;0&gt; (the last 3 bytes read back as 0, 0, 0).

## Files

- [src/org/apache/lucene/index/TestByteSliceReaderFinalSliceAtPoolEnd.java](src/org/apache/lucene/index/TestByteSliceReaderFinalSliceAtPoolEnd.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestByteSliceReaderFinalSliceAtPoolEnd
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
