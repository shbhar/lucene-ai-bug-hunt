# Lucene90CompressingTermVectorsWriter never closes the Compressor it creates (neither in close() nor in its failed-constructor cleanup), so with a Deflate-based CompressionMode such as HIGH_COMPRESSION every term-vectors writer leaves its native java.util.zip.Deflater for the garbage collector to free, while the stored-fields writer closes its compressor

Finding LFV-1267 · Area: codecs / compressing term-vectors writer (Lucene90CompressingTermVectorsWriter with a custom CompressionMode) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1267.html](../../bugs/LFV-1267.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure a codec whose term vectors use Lucene90CompressingTermVectorsFormat with CompressionMode.HIGH_COMPRESSION, index one document with a term-vector field and commit. You expect the term-vectors writer to close the Compressor it created when it closes, as the stored-fields writer has done since LUCENE-7647, because the Deflate compressor frees its native Deflater only in close(). Instead the writer's close() and its failed-constructor cleanup close only its output streams, so the compressor is never closed and its Deflater's native memory waits for a garbage collection.

## Expected vs actual

- Expected: 0 compressors left open after the `IndexWriter` closes.
- Actual: `AssertionError: expected:<0> but was:<1>`: the term-vectors writer never closed its compressor.

## Files

- [src/org/apache/lucene/codecs/lucene90/compressing/TestTermVectorsWriterClosesCompressor.java](src/org/apache/lucene/codecs/lucene90/compressing/TestTermVectorsWriterClosesCompressor.java) (JUnit test)

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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene90.compressing.TestTermVectorsWriterClosesCompressor
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestTermVectorsWriterClosesCompressor.java](TestTermVectorsWriterClosesCompressor.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
