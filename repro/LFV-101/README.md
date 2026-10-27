# When IndexWriter#addBatch fails part-way through a dense points column, the point bytes it already copied stay in the points buffer, so documents added later to the same in-memory segment are silently indexed with the rejected batch's point values

Bug LFV-101 · Area: index / columnar addBatch (dense points, PointValuesWriter) · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-101.html](../../bugs/LFV-101.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add a batch with IndexWriter#addBatch whose dense points column (a LongColumn, or a BinaryColumn with point dimensions) has more values than fit in one 4 KiB chunk, and the batch fails after the first chunk, because Lucene's own length check rejects a value of the wrong width or because your cursor throws; Lucene rejects the batch with a non-aborting exception, the writer stays open, and you keep indexing. You expect the rejected batch to leave no trace, as Lucene promises for non-aborting exceptions and as its own ThrowMidBatchRecovers tests assert, so a document you add next with IntPoint p=7 matches p == 7. Instead the dense path had already copied the first chunk's point bytes into the field's buffer without recording doc IDs for them, so the next document is paired with the rejected batch's first value (the only live document, added with p=7, matches p == 1000 instead), every later point of that field in the segment is shifted, CheckIndex stays clean and the wrong values survive merges.

## Expected vs actual

- Expected: The 3 documents added after the rejected batch read back `[7, 8, 9]`, as in the control, where the failure hits value 1024.
- Actual: They read back `[1000, 1001, 1002]`, the rejected batch's first values, in both bug tests; the control passes.

## Files

- [src/org/apache/lucene/index/TestColumnBatchDensePointsFailure.java](src/org/apache/lucene/index/TestColumnBatchDensePointsFailure.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.index.TestColumnBatchDensePointsFailure
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestColumnBatchDensePointsFailure.java](TestColumnBatchDensePointsFailure.java)
- [2-TestColumnBatchDensePointsFailure.java](2-TestColumnBatchDensePointsFailure.java)
- [TestDensePointsPartialFailure.java](TestDensePointsPartialFailure.java)

Formal models and scripts this bug's part of the hunt used: [../../models/columnar-addbatch/](../../models/columnar-addbatch/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
