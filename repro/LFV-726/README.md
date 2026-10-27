# addIndexes(CodecReader...) takes the reserved document count and the new segment's soft-delete count from the readers before OneMerge#wrapForMerge, so a merge whose wrapper removes documents over-reserves documents and, with soft deletes, commits a wrong soft-delete count that can drop live documents or corrupt a later merge (since 9.9.0)

Bug LFV-726 · Area: index / IndexWriter addIndexes(CodecReader...) accounting (reserveDocs, addIndexesReaderMerge soft-delete count) x MergePolicy#findMerges(CodecReader...) / OneMerge#wrapForMerge · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-726.html](../../bugs/LFV-726.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You install a merge policy whose findMerges(CodecReader...) returns a OneMerge whose wrapForMerge removes documents (its javadoc allows this: 'add/remove fields or documents'), for example to drop soft-deleted or expired documents while importing, and call IndexWriter.addIndexes(CodecReader...); you expect the writer to account for the documents the merge actually wrote, as the regular merge path does. Instead addIndexes reserves the live-document count of the unwrapped readers, so getPendingNumDocs stays too high for the rest of the writer session, and with soft deletes it stores the unwrapped readers' soft-delete count in the new segment, so DocStats.numDocs under-counts and CheckIndex (level 2 and up) reports 'actual soft deletes: 0 but expected: 1'. With assertions off, later operations then build on that wrong count: a live document can vanish from the committed index, and a later ordinary forceMerge can merge two documents into one doc ID (a search for one returns the other) or fail with CorruptIndexException.

## Expected vs actual

- Expected: The new segment records 0 soft deletes, `numDocs` and `getPendingNumDocs` are 2, and after `forceMerge(1)` the index holds `c`, `a2` and `z` as 3 distinct documents.
- Actual: 1 soft delete recorded for a segment with none, `getPendingNumDocs` 3, and after `forceMerge(1)` `maxDoc` is 2 (assertions off) or the writer's assertion `pendingNumDocs 4 != 3 totalMaxDoc` fires (assertions on).

## Files

- [src/org/apache/lucene/index/TestAddIndexesMergeWrapperRemovingDocs.java](src/org/apache/lucene/index/TestAddIndexesMergeWrapperRemovingDocs.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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
     execute --select-class org.apache.lucene.index.TestAddIndexesMergeWrapperRemovingDocs
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestAddIndexesWrapForMergeAccounting.java](TestAddIndexesWrapForMergeAccounting.java)
- [TestAddIndexesMergeWrapperRemovingDocs.java](TestAddIndexesMergeWrapperRemovingDocs.java)
- [TestAddIndexesDroppingWrapperV1.java](TestAddIndexesDroppingWrapperV1.java)
- [DefaultCodecForceMergeAfterAddIndexes.java](DefaultCodecForceMergeAfterAddIndexes.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
