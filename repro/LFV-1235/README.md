# LZ4.compress throws ArrayIndexOutOfBoundsException when a reused LZ4.FastCompressionHashTable (documented as safe to reuse) compresses a slice whose start offset plus an offset left in the table by a longer earlier input exceeds Integer.MAX_VALUE (for example the last 2^20 bytes at offset 1,098,951,424 after a 1.1e9-byte input), because FastCompressionHashTable.get rebuilds the match position as base + stored in int and the wrapped negative position passes both of its stale-entry checks (since 8.5.0)

Finding LFV-1235 · Area: util / compress (LZ4 fast compression hash table: LZ4.compress, LZ4.compressWithDictionary) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1235.html](../../bugs/LFV-1235.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You compress a byte array of 1.1e9 bytes with LZ4.compress, reusing one LZ4.FastCompressionHashTable as the javadoc allows ('can safely be reused'): first the whole array from offset 0, then its last 2^20 bytes (offset 1,098,951,424). You expect the second call to compress the slice, as it does with a fresh table. Instead it throws ArrayIndexOutOfBoundsException ('Index -2096021376 out of bounds for length 1099999997'), because FastCompressionHashTable.get rebuilds a candidate match position as base + stored offset in int, an offset left in the table by the longer first call pushes that sum past Integer.MAX_VALUE, and the wrapped negative position passes get()'s 'is this an earlier position within reach' checks and is then read.

## Expected vs actual

- Expected: The second `compress` call returns normally, as with a fresh table (1,052,688 compressed bytes that decompress back to the slice).
- Actual: `ArrayIndexOutOfBoundsException: Index -2096017425 out of bounds for length 1099999997` at `LZ4.java:372`, with assertions on and off.

## Files

- [src/org/apache/lucene/util/compress/TestLZ4ReusedTableLargeOffset.java](src/org/apache/lucene/util/compress/TestLZ4ReusedTableLargeOffset.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.compress.TestLZ4ReusedTableLargeOffset
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestLZ4ReusedTableLargeOffset.java](TestLZ4ReusedTableLargeOffset.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
