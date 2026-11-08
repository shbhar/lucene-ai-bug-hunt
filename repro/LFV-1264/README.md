# IndexWriter.hasUncommittedChanges() and NRT isCurrent()/openIfChanged() can briefly report no changes for an acknowledged empty-block updateDocuments delete that lands in the delete queue a concurrent full flush has just swapped out, because DocumentsWriter.anyChanges() reads only the new queue until that flush adds its ticket

Finding LFV-1264 · Area: index / IndexWriter full flush (DocumentsWriter uncommitted-change tracking) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1264.html](../../bugs/LFV-1264.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

A committed document has id:1 and the writer holds a pooled per-thread writer with no documents; thread U deletes the document with updateDocuments(new TermQuery(id:1), List.of()) while thread R opens an NRT reader, and U's call returns its sequence number before R's full flush has finished. You expect hasUncommittedChanges() to return true from then on, because the delete is acknowledged and not committed and the javadoc names only false positives. Instead it returns false until R's flush turns the old delete queue into a ticket: R computed its 'changes pending' flag before swapping delete queues, U's per-thread writer appended the delete to the old queue after that, and DocumentsWriter.anyChanges() looks only at the new queue, the buffered document count (0 for an empty block), the ticket queue and that flag; once R finishes the answer is true again and R's reader has the delete applied.

## Expected vs actual

- Expected: `hasUncommittedChanges()` returns `true` once `updateDocuments` has returned its sequence number (5).
- Actual: `java.lang.AssertionError: seqNo=5, hasUncommittedChanges()=false`, with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestHasUncommittedChangesDuringFullFlush.java](src/org/apache/lucene/index/TestHasUncommittedChangesDuringFullFlush.java) (JUnit test)

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
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestHasUncommittedChangesDuringFullFlush
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestHasUncommittedChangesDuringFullFlush.java](TestHasUncommittedChangesDuringFullFlush.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
