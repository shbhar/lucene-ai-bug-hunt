# ByteBuffersDataOutput's documented largest block size LIMIT_MAX_BITS_PER_BLOCK = 31 is off by one: the expert constructor (and FSTCompiler.getOnHeapReaderWriter(31)) accepts 31 block bits, but 1 &lt;&lt; 31 is Integer.MIN_VALUE, so the output cannot hold a single byte and its first write throws IllegalArgumentException 'capacity &lt; 0'

Finding LFV-1216 · Area: core / store (ByteBuffersDataOutput block-size limit; also util/fst FSTCompiler.getOnHeapReaderWriter) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1216.html](../../bugs/LFV-1216.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a ByteBuffersDataOutput with the expert constructor and 31 block bits, the value of the public constant LIMIT_MAX_BITS_PER_BLOCK, documented as the 'Largest maxBitsPerBlock allowed' (the constructor rejects 32 with 'too large, must not exceed 31' and accepts 31), or you call FSTCompiler.getOnHeapReaderWriter(31), and expect either a usable output or the same IllegalArgumentException at construction that 32 gets. Instead the output is created, and its first writeByte throws IllegalArgumentException 'capacity &lt; 0: (-2147483648 &lt; 0)' from ByteBuffer.allocate, because appendBlock computes the block size as 1 &lt;&lt; 31 in int, which is Integer.MIN_VALUE. No Java ByteBuffer can hold 2^31 bytes, so 31 is never a usable block size, and Lucene's own FST read side caps the same value at 30.

## Expected vs actual

- Expected: The constructor rejects 31 (as it rejects 32), or the output stores the byte.
- Actual: `IllegalArgumentException: capacity < 0: (-2147483648 < 0)` from `ByteBuffer.allocate` at `ByteBuffersDataOutput.appendBlock(ByteBuffersDataOutput.java:525)`, on the first `writeByte`.

## Files

- [src/org/apache/lucene/store/TestByteBuffersDataOutputBlockBits31.java](src/org/apache/lucene/store/TestByteBuffersDataOutputBlockBits31.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestByteBuffersDataOutputBlockBits31
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestByteBuffersDataOutputBlockBits31.java](TestByteBuffersDataOutputBlockBits31.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
