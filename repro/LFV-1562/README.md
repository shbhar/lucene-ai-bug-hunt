# With a soft-deletes field, an IOException from a read inside IndexWriter's first soft-deletes scan of a segment (injected during a near-real-time open with no delete or update buffered) leaves the writer open with the scan undone; a later delete by term of an already soft-deleted document can then be counted twice, and the next commit can drop a segment that still holds a live document, with no exception when assertions are off (with assertions on Lucene's own assert closes the writer; run on nine releases from 7.4.0 to 10.6.0, on branch_11x and on main)

Finding LFV-1562 · Area: index / IndexWriter reader pool and soft-deletes accounting (ReadersAndUpdates.getReader, PendingSoftDeletes) · Priority: low · Impact: data corruption · Novelty: new

Finding page: [../../bugs/LFV-1562.html](../../bugs/LFV-1562.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use soft deletes (IndexWriterConfig.setSoftDeletesField). A near-real-time open, DirectoryReader.open(writer), called with no delete or update buffered in the writer, throws an IOException from a read; the writer stays open and reports no tragic exception, so you go on: you delete by id a document that is already soft-deleted in that segment (or has an older, soft-deleted version there), and commit. You expect the commit to lose only that document. Instead the deletion can be counted twice, because the read failed while the writer was scanning the segment's soft deletes and the writer kept the segment reader with the scan undone: in the test (2 documents in one segment, NoMergePolicy, assertions off) the writer takes the segment for fully deleted and drops it, so the other document, which no call deleted, is missing from the commit and no later call threw; with assertions on, commit() stops at Lucene's own assert ('softDeleteCount doesn't match 1 != 0'), the writer closes and the previous commit stays. rollback() after the failed open avoids it. That going on with a writer after a failed reopen can end in inconsistency or data loss was said by Simon Willnauer on #9738 in 2019, without this mechanism.

## Expected vs actual

- Expected: The commit holds document 2, which no call deleted: `reader.numDocs()` is 1.
- Actual: Assertions off: `expected:<1> but was:<0>`, the commit has no document and neither the delete nor the commit threw. Assertions on: `commit()` throws `AssertionError: softDeleteCount doesn't match 1 != 0`.

## Files

- [src/org/apache/lucene/index/TestSoftDeletesAfterFailedNrtOpen.java](src/org/apache/lucene/index/TestSoftDeletesAfterFailedNrtOpen.java) (JUnit test)

## Run it

- Tested on Lucene main 1a59f96fc6de (a commit of 2026-11-21): still reproduces.
- Tested on Lucene main d7162870c1fd (a commit of 2026-11-19): reproduced.
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
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestSoftDeletesAfterFailedNrtOpen
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestSoftDeletesAfterFailedNrtOpen.java](TestSoftDeletesAfterFailedNrtOpen.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
