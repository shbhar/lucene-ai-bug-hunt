# If IndexWriter's InfoStream throws while deleteAll() discards a buffered in-memory segment, the discarded buffer stays in the writer's pool and the writer stays open, so documents added afterwards can be missing from later commits that return normally (every release from 8.6.0 to 10.6.0, branch_11x and main)

Finding LFV-1531 · Area: index / IndexWriter deleteAll (DocumentsWriter.lockAndAbortAll, DocumentsWriterFlushControl.doOnAbort, the pool of in-memory segment buffers) · Priority: low · Impact: minor · Novelty: new

Finding page: [https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1531.html](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-1531.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You turn on IndexWriter's InfoStream, add a document and call deleteAll(); while Lucene discards the buffered document, one logging call throws an exception, so deleteAll() throws it and the writer stays open. You expect an open writer to keep what it acknowledges: you add document 2 and commit, both calls return normally and the commit's sequence number covers the add, so document 2 should be in the index. Instead the discarded buffer was never taken out of the writer's pool, document 2 is indexed into it and dropped, and the committed index does not contain it; if a commit, flush or NRT reader comes first, then with assertions off every document added afterwards is missing from every later commit for the rest of that writer's life.

## Expected vs actual

- Expected: `docFreq(id:2)` is 1: the document whose `addDocument()` and `commit()` returned after the failed `deleteAll()` is in the index.
- Actual: `docFreq(id:2)` is 0: `java.lang.AssertionError: expected:<1> but was:<0>`, with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestAddDocumentAfterFailedDeleteAll.java](src/org/apache/lucene/index/TestAddDocumentAfterFailedDeleteAll.java) (JUnit test)

## Run it

- Tested on Lucene main 9965d34e3c77 (a commit of 2026-11-16): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 9d2b5a15427a (a commit of 2026-11-16): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- What each build does: the test fails the same way on Lucene main, on branch_11x and with the 10.6.0 release jars, with and without assertions. What the test does: it gives the writer a silent InfoStream whose isEnabled("DWFC") throws IllegalStateException once, adds one empty document, calls deleteAll(), which must throw, adds one document with id 2 and commits; a new reader must then find docFreq(id:2) = 1. It fails with java.lang.AssertionError: expected:&lt;1&gt; but was:&lt;0&gt;: both calls returned and the commit does not have the document. With assertions on, the failing run also carries a suppressed AssertionError (netBytes has unexpected value) from the writer's close(); that is not the claimed failure. The test draws no random value and failed the same way with every seed that was run. With the jars of an older release the same test (before 9.1.0 with the import org.apache.lucene.util.LuceneTestCase and that release's test-framework dependencies) fails in the same way from 8.7.0 on; on 8.6.0 to 8.6.3 the add after the failed deleteAll() throws FileAlreadyExistsException: File already exists: _0.fdt instead; 8.5.2 passes. On a checkout of branch_11x at 9d2b5a15427a, which builds 11.0.0-SNAPSHOT, set V=11.0.0-SNAPSHOT; for the release, put the three 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework) in place of the three $LUCENE jars. The file at the top of this folder, which the finding page shows, is the same test with one comment wrapped onto two lines; it is the form for the Gradle alternative at the end of this page, whose module is lucene/core.
- This finding was re-run on Lucene main 82cf7465bb8f on 2026-11-26: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.AssertionError: expected:&lt;1&gt; but was:&lt;0&gt;.
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

# run 1: assertions off (production-like), seed 840C0FFEE
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=840C0FFEE \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestAddDocumentAfterFailedDeleteAll
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Files shown on the finding page

- [TestAddDocumentAfterFailedDeleteAll.java](TestAddDocumentAfterFailedDeleteAll.java)

Formal models and scripts this finding's part of the hunt used: [../../models/indexwriter-adds-after-failed-deleteall/](../../models/indexwriter-adds-after-failed-deleteall/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
