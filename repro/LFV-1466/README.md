# After one document overflows an in-memory segment's 2 GiB doc-values byte pool (a non-aborting ArithmeticException), the next document's new SORTED or SORTED_SET value is silently stored as another value, because ByteBlockPool.nextBuffer installs the new block before its overflow check throws

Finding LFV-1466 · Area: util / ByteBlockPool (the in-memory doc-values byte pool shared by IndexingChain's SORTED and SORTED_SET writers) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1466.html](../../bugs/LFV-1466.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index one document whose distinct SortedSetDocValuesField values (each up to 32766 bytes) add up to more than the in-memory segment's 2 GiB doc-values byte pool; addDocument throws ArithmeticException 'integer overflow', the document is deleted and the writer stays open, as for any non-aborting failure. You expect the next document, which has one SortedDocValuesField value "b", to be stored with "b" (or itself rejected). Instead its value is written into the pool's new block but recorded at the start of the previous block, so the segment is flushed with another value already in the pool (in the model run, the rejected document's last 32763-byte value) and no error, because ByteBlockPool.nextBuffer installs the new block, increments bufferUpto and resets byteUpto before its Math.addExact overflow check throws, leaving byteOffset one block behind.

## Expected vs actual

- Expected: The value added after the failed `nextBuffer()` reads back as `"b"`.
- Actual: It reads back as `"a"`, the value stored before the failure: `AssertionError: expected:<[62]> but was:<[61]>`, with assertions on and off.

## Files

- [src/org/apache/lucene/util/TestByteBlockPoolFailedNextBuffer.java](src/org/apache/lucene/util/TestByteBlockPoolFailedNextBuffer.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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
     execute --select-class org.apache.lucene.util.TestByteBlockPoolFailedNextBuffer
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestByteBlockPoolFailedNextBuffer.java](TestByteBlockPoolFailedNextBuffer.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
