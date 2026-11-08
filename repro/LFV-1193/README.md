# An interrupted wait in IndexWriter's flush stall skips the waiter deregistration (DocumentsWriterStallControl.waitIfStalled), so with assertions enabled the same thread's next addDocument during a stall of the same writer fails an internal assert and IndexWriter closes itself as a tragic event, losing its uncommitted documents (tragic close since 10.0.0; the skipped deregistration since 4.0)

Finding LFV-1193 · Area: index / IndexWriter flush stall control (DocumentsWriterStallControl.waitIfStalled, interrupt path) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1193.html](../../bugs/LFV-1193.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

With assertions enabled, you interrupt a thread that is blocked in addDocument because IndexWriter has stalled indexing to let its flushes catch up; as the IndexWriter javadoc documents, the call throws ThreadInterruptedException and the writer stays open. You expect that thread's later calls in a stall to leave the writer open too, interrupted or not. Instead the first interrupt skipped the step that removes the thread from the stall control's waiter list, so the thread's next wait in a stall of the same writer fails the 'registered only once' assert, and IndexWriter treats that AssertionError as a tragic event and closes, discarding the uncommitted documents (since 10.0.0; without assertions nothing visible happens).

## Expected vs actual

- Expected: Both interrupted `addDocument` calls throw `ThreadInterruptedException` and the writer stays open.
- Actual: The 2nd call throws `java.lang.AssertionError` at `DocumentsWriterStallControl.incWaiters(DocumentsWriterStallControl.java:86)` and the writer closes itself.

## Files

- [src/org/apache/lucene/index/TestStalledAddDocumentInterrupt.java](src/org/apache/lucene/index/TestStalledAddDocumentInterrupt.java) (JUnit test)

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
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestStalledAddDocumentInterrupt
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestStalledAddDocumentInterrupt.java](TestStalledAddDocumentInterrupt.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
