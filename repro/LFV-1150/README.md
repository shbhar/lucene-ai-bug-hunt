# Compressor.compress casts ByteBuffersDataInput.length() to int, so an input of 2^32 + k bytes is compressed as only its first k bytes and the call returns normally

Finding LFV-1150 · Area: codecs / compressing (Compressor.compress in CompressionMode FAST, FAST_DECOMPRESSION, HIGH_COMPRESSION and the Lucene90 preset-dict compressors) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1150.html](../../bugs/LFV-1150.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a valid ByteBuffersDataInput of 2^32 + k bytes (here 4097 views of one 1 MiB buffer, so nothing is copied) and call CompressionMode.FAST.newCompressor().compress(in, out); you expect the whole input to be compressed, or an exception if it is too large. Instead compress returns normally after reading and compressing only the first k bytes (1 MiB of 4,296,015,872), leaving the input positioned at k, and the block it wrote decodes to just that prefix. All five core compressors start with 'final int len = (int) buffersInput.length()' (or length() - position() in the preset-dict modes), and for a length of 2^32 + k that cast yields k.

## Expected vs actual

- Expected: All 4,296,015,872 bytes are consumed, or `compress` throws (with the fix: `ArithmeticException`).
- Actual: `compress` returns normally after 1,048,576 bytes: `AssertionError: expected:<4296015872> but was:<1048576>`, with assertions on and off.

## Files

- [src/org/apache/lucene/codecs/compressing/TestCompressorInputOver4GiB.java](src/org/apache/lucene/codecs/compressing/TestCompressorInputOver4GiB.java) (JUnit test)

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
     execute --select-class org.apache.lucene.codecs.compressing.TestCompressorInputOver4GiB
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCompressorInputOver4GiB.java](TestCompressorInputOver4GiB.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
