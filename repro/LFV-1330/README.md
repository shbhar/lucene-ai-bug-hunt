# IndexWriter#addBatch does not reject a ColumnBatch whose numDocs() is negative (DocumentsWriterPerThread.reserveDocs only asserts n &gt;= 0): with assertions off, the count is subtracted from the in-memory segment, so the next commit returns normally without documents added before the batch; with assertions on, the AssertionError closes the writer as a tragic event (since 10.5.0)

Finding LFV-1330 · Area: index / columnar addBatch (DocumentsWriterPerThread.updateBatch and reserveDocs; experimental column API) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1330.html](../../bugs/LFV-1330.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add one document, then call IndexWriter#addBatch with a ColumnBatch whose numDocs() returns -1 and that has no columns, and expect an IllegalArgumentException with the writer still open and the earlier document kept, which is what addBatch does for a batch whose count is above the document limit or whose column doc ids fall outside [0, numDocs()). Instead, with assertions off, addBatch returns normally, the negative count is subtracted from the in-memory segment's document count, and the next commit returns normally with 0 documents instead of 1. With assertions on, the 'assert n &gt;= 0' in DocumentsWriterPerThread.reserveDocs throws an AssertionError, which IndexWriter treats as a tragic event, so the writer closes.

## Expected vs actual

- Expected: `addBatch` throws `IllegalArgumentException`, the writer stays open and the commit holds 1 document.
- Actual: Assertions off: `expected:<1> but was:<0>`, the commit holds 0 documents. Assertions on: `writer closed by java.lang.AssertionError`.

## Files

- [src/org/apache/lucene/index/TestAddBatchNegativeNumDocs.java](src/org/apache/lucene/index/TestAddBatchNegativeNumDocs.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestAddBatchNegativeNumDocs
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestAddBatchNegativeNumDocs.java](TestAddBatchNegativeNumDocs.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
