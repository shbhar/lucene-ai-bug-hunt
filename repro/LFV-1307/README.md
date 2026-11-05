# With assertions enabled, the first document fails an internal assertion (and since 10.0.0 IndexWriter then closes itself) when setRAMBufferSizeMB is given a huge value such as Double.MAX_VALUE or +Infinity, because DocumentsWriterFlushControl.assertMemory doubles the RAM buffer in long arithmetic and its upper bound wraps negative (-ea only; every release since 4.0.0)

Finding LFV-1307 · Area: index / IndexWriter flush control (DocumentsWriterFlushControl.assertMemory) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1307.html](../../bugs/LFV-1307.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure an IndexWriter with setRAMBufferSizeMB(Double.POSITIVE_INFINITY) (or Double.MAX_VALUE, or any value of 2^42 MB or more), which the setter accepts because it rejects only non-positive sizes, and add one small document with assertions enabled. You expect the document to be indexed, as it is with assertions disabled. Instead addDocument throws AssertionError 'actual mem: ... expected mem: -2 byte' and, since 10.0.0, the writer closes itself as a tragic event (4.x to 9.x keep it open), because DocumentsWriterFlushControl.assertMemory casts the buffer to a long (saturating at Long.MAX_VALUE) and then computes its upper bound as 2 * ramBufferBytes in long arithmetic, which wraps negative.

## Expected vs actual

- Expected: The document is indexed and `numDocs` is 1, as with assertions off.
- Actual: `addDocument` throws `AssertionError: actual mem: 0 byte, expected mem: -2 byte, ... ramBufferBytes=9223372036854775807, maxConfiguredRamBuffer=Infinity` and the writer closes itself (main; on 4.x to 9.x the writer stays open).

## Files

- [src/org/apache/lucene/index/TestInfiniteRamBuffer.java](src/org/apache/lucene/index/TestInfiniteRamBuffer.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestInfiniteRamBuffer
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestInfiniteRamBuffer.java](TestInfiniteRamBuffer.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
