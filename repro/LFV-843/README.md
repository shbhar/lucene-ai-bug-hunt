# An Error (in practice an OutOfMemoryError) while an NRT reopen or commit that has no buffered documents freezes the buffered deletes and doc-values updates is replaced by IllegalStateException("Can't close queue unless all changes are applied") from the finally of DocumentsWriter.flushAllThreads, so IndexWriter stays open instead of closing itself and its next commit silently drops those acknowledged deletes and updates (with -ea an AssertionError replaces the Error; every release since 8.2.0)

Finding LFV-843 · Area: index / IndexWriter full flush (DocumentsWriter.flushAllThreads naked-delete freeze, DocumentsWriterDeleteQueue.close) · Priority: medium · Impact: data corruption · Novelty: new

Finding page: [../../bugs/LFV-843.html](../../bugs/LFV-843.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You commit documents id:1 and id:2, call deleteDocuments(id:1) and updateNumericDocValue(id:2, dv, 22) (both return sequence numbers), and then open an NRT reader from the writer or call commit() with no documents buffered, and an OutOfMemoryError strikes while the writer freezes those two buffered changes; the IndexWriter javadoc says the writer closes itself after an Error, so you expect to see the Error and a closed writer (or, if the writer stays usable, a later commit that contains both changes). Instead the finally of DocumentsWriter.flushAllThreads closes the delete queue that the flush has already swapped out, and its close() throws IllegalStateException("Can't close queue unless all changes are applied"), which replaces the Error; IndexWriter does not treat that as tragic and stays open, the old queue with both changes is abandoned, and the next commit() returns -1 with id:1 still live and dv(id:2) still 2. With -ea an AssertionError ('Can not replace a full flush queue if the queue is not closed') replaces the Error instead, and since 10.0.0 it closes the writer.

## Expected vs actual

- Expected: The caller sees the `OutOfMemoryError` and the writer has closed itself (the last commit stands).
- Actual: Assertions off: `IllegalStateException: Can't close queue unless all changes are applied`, writer open, next `commit()` returns -1, `id:1` still has 1 hit and `dv(id:2)`=2; assertions on: `AssertionError: Can not replace a full flush queue if the queue is not closed`.

## Files

- [src/org/apache/lucene/index/TestFullFlushFailedFreezeDropsDeletes.java](src/org/apache/lucene/index/TestFullFlushFailedFreezeDropsDeletes.java) (JUnit test)
- [src/org/apache/lucene/index/RealOomeDuringNakedFreezeMain.java](src/org/apache/lucene/index/RealOomeDuringNakedFreezeMain.java) (main() class)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): still reproduces.
- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the finding reproducing.
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
java -da -dsa -Dtests.asserts=false -Xmx160m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestFullFlushFailedFreezeDropsDeletes
java -da -dsa -Dtests.asserts=false -Xmx160m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.apache.lucene.index.RealOomeDuringNakedFreezeMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFailedDeleteFreezeKeepsError.java](TestFailedDeleteFreezeKeepsError.java)

1 other file(s) of the recorded repro are not exported.

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
