# PackedInts.getWriterNoHeader's documented unknown-count mode (valueCount -1) throws on the first add, or already in the factory, whenever mem is at least byteBlockCount + 8 * byteValueCount (for PACKED every width from 127 bytes, PackedInts.DEFAULT_BUFFER_SIZE included), because BulkOperation.computeIterations treats -1 as a real count and sizes the write buffers for 0 (or -1) values; latent, no Lucene code uses the -1 mode (since 4.0.0-BETA)

Finding LFV-1199 · Area: util / packed ints (PackedInts.getWriterNoHeader, PackedWriter, BulkOperation.computeIterations) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1199.html](../../bugs/LFV-1199.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create a headerless packed-ints writer with PackedInts.getWriterNoHeader(out, PACKED, -1, 7, PackedInts.DEFAULT_BUFFER_SIZE), which the javadoc allows ('just pass -1 as valueCount' to start writing without knowing how many values will follow), add the value 5 and call finish(), and you expect to read 5 back, as you can with mem 0 or with a known count. Instead the first add throws ArrayIndexOutOfBoundsException 'Index 0 out of bounds for length 0' at PackedWriter.add, with -ea and -da: BulkOperation.computeIterations applies a cap meant for a known count ('don't allocate for more than the size of the reader'), which for -1 returns 0 iterations (or -1 at 8, 16, ..., 64 bits per value, where the factory throws NegativeArraySizeException), so the writer gets empty buffers. Every PACKED width fails once mem is 127 bytes or more; no Lucene code uses the -1 mode, so only code that calls this internal writer API itself hits it.

## Expected vs actual

- Expected: `it.next()` returns 5 and the test passes.
- Actual: `java.lang.ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0` at `PackedWriter.add(PackedWriter.java:63)`, with assertions on and off.

## Files

- [src/org/apache/lucene/util/packed/TestPackedWriterUnknownValueCount.java](src/org/apache/lucene/util/packed/TestPackedWriterUnknownValueCount.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.packed.TestPackedWriterUnknownValueCount
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestPackedWriterUnknownValueCount.java](TestPackedWriterUnknownValueCount.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
