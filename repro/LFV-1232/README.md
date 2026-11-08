# Lucene90CompressingTermVectorsWriter.ramBytesUsed() counts its four int[] position and offset buffers at one byte per entry, so IndexWriter under-counts buffered term-vector heap (about 9.6 MB missing for one pending document with 2^20 term-vector positions and offsets)

Finding LFV-1232 · Area: codecs / term vectors (Lucene90 compressing term-vectors writer; IndexWriter per-thread RAM accounting) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1232.html](../../bugs/LFV-1232.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index documents with a field that stores term vectors with positions or offsets (default codec and IndexWriterConfig) and expect IndexWriter's RAM accounting, which since 8.7.0 adds the term-vectors writer's ramBytesUsed() so that flush and stall decisions see its heap, to count the writer's buffered positions and offsets in bytes. Instead Lucene90CompressingTermVectorsWriter.ramBytesUsed() adds the lengths of its four int[] buffers (positions, start offsets, lengths, payload lengths) as if each entry took one byte: with one pending document holding one term with 2^20 positions and offsets, the writer reports 3,217,630 bytes while those three int[] alone take at least 12,582,912 bytes (with the one-line fix it reports 12,867,380). Indexed data and search results are unaffected; IndexWriter only flushes later than ramBufferSizeMB and the per-thread hard limit intend.

## Expected vs actual

- Expected: `IndexWriter.ramBytesUsed()` is at least 786,432 bytes, the three buffered `int[]` of at least 65,536 entries each (with the fix: 1,023,612).
- Actual: `AssertionError: 356924 < 786432`, with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestTermVectorsRamBytesUsed.java](src/org/apache/lucene/index/TestTermVectorsRamBytesUsed.java) (JUnit test)

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
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestTermVectorsRamBytesUsed
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestTermVectorsRamBytesUsed.java](TestTermVectorsRamBytesUsed.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
