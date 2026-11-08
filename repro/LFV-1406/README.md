# An NRT reader opened with applyAllDeletes=false starts a merge-on-getReader merge whose readers miss a tryUpdateDocValue un-delete that returned &gt; 0 but is not yet written (another thread's older update packet is still being applied), so the merge drops the document and the next commit persists the loss (since 8.7.0; with the default merge settings since 9.3.0)

Finding LFV-1406 · Area: index / soft deletes (tryUpdateDocValue un-delete vs. merge-on-getReader of an NRT open with applyAllDeletes=false: IndexWriter.getReader, IndexWriter.preparePointInTimeMerge, ReaderPool.writeAllDocValuesUpdates, ReadersAndUpdates.getReaderForMerge) · Priority: low · Impact: data corruption · Novelty: new

Finding page: [../../bugs/LFV-1406.html](../../bugs/LFV-1406.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

With a soft-deletes field, you restore a soft-deleted document a with tryUpdateDocValue(reader, docID, &lt;soft-deletes field = null&gt;), which returns a sequence number &gt; 0 while another thread is still applying an older delete, and then open an NRT reader with DirectoryReader.open(writer, false, false). You expect what the javadocs promise: that reader may not show the restore yet, but unapplied changes 'remain buffered (in IndexWriter) so that they will be applied in the future', so a is in the next commit. Instead the open's merge-on-getReader merge reads a as still deleted and drops it from the writer, so the next commit, a fresh NRT reader and a re-open all have count(id:a) = 0, and nothing reports an error.

## Expected vs actual

- Expected: `tryUpdateDocValue` returns &gt; 0 and the committed index has 1 hit for `id:a`.
- Actual: It returns &gt; 0 (9), but the commit has 0 hits for `id:a`: `expected:<1> but was:<0>` at line 114.

## Files

- [src/org/apache/lucene/index/TestTryUndeleteLostToMergeOnGetReaderWithoutApplyingDeletes.java](src/org/apache/lucene/index/TestTryUndeleteLostToMergeOnGetReaderWithoutApplyingDeletes.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
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
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestTryUndeleteLostToMergeOnGetReaderWithoutApplyingDeletes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestTryUndeleteLostToMergeOnGetReaderWithoutApplyingDeletes.java](TestTryUndeleteLostToMergeOnGetReaderWithoutApplyingDeletes.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
