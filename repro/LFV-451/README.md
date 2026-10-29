# An un-delete through IndexWriter#tryUpdateDocValue during a merge of the document's segment returns success and is visible to NRT readers, but the merge commit silently drops the document

Bug LFV-451 · Area: index / soft deletes (tryUpdateDocValue un-delete vs. a running merge's carry-over) · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-451.html](../../bugs/LFV-451.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

With a soft-deletes field, you soft-delete a document and later restore it with tryUpdateDocValue(reader, docID, &lt;soft-deletes field = null&gt;) while a merge of its segment is running. You expect what the javadoc promises: the segment has not been merged away, so the update succeeds and returns a sequence number &gt; 0 and the document stays live (otherwise the call returns -1 and you retry). Instead the call does return &gt; 0 and NRT readers show the document again, but the merge, which already treated it as deleted and left it out, commits without it, so the restored document disappears with no error and the next commit persists the loss.

The test shows that a soft-deletes un-delete through `IndexWriter#tryUpdateDocValue(reader, docID, <soft field = null>)`, made while a merge of the document's segment is running (from the merged-segment warmer), returns a sequence number &gt; 0, and the merge then commits without the document.

## Expected vs actual

Expected, per the `tryUpdateDocValue` javadoc: either `-1` or the acknowledged un-delete survives (numDocs 2). Actual: `seqNo=7 numDocs=1`.

## Files

- [src/org/apache/lucene/index/TestTryUndeleteDuringMerge.java](src/org/apache/lucene/index/TestTryUndeleteDuringMerge.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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
     execute --select-class org.apache.lucene.index.TestTryUndeleteDuringMerge
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
