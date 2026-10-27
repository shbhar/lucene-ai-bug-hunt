# With a soft-deletes field configured, IndexWriter's merge assumes OneMerge#wrapForMerge only revives documents: for each merged segment without hard deletes, every live document that a merge wrapper hides (which the javadoc allows) lowers the new segment's soft-delete count by one, so the merge throws 'invalid softDelCount=-1' when nothing is revived, and under SoftDeletesRetentionMergePolicy commits a too-low count (numDocs and IndexSearcher#count over-count, CheckIndex rejects it, later merges of it throw); regression since 7.7.0 (LUCENE-8569)

Bug LFV-730 · Area: index / IndexWriter merge soft-delete accounting (mergeMiddle carry-over of soft deletes x OneMerge#wrapForMerge, OneMergeWrappingMergePolicy, SoftDeletesRetentionMergePolicy) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-730.html](../../bugs/LFV-730.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You set IndexWriterConfig#setSoftDeletesField and a merge policy whose OneMerge#wrapForMerge hides expired documents (its javadoc says a wrapper may 'add/remove fields or documents'), index two segments without hard deletes, and call forceMerge(1); you expect the merge to drop the expired document and succeed, as the same merge does without a soft-deletes field, or with one when the segment happens to hold a hard delete. Instead IndexWriter derives the carried-over soft deletes as getSoftDelCount - wrappedReader.numDeletedDocs, which counts each hidden live document as a dropped soft delete, so the count comes out -1 and the merge throws IllegalArgumentException 'invalid softDelCount=-1 (maxDoc=2)'. With SoftDeletesRetentionMergePolicy on top (one retained soft-deleted document) the merge silently commits softDelCount=0, so numDocs and IndexSearcher#count report 3 where 2 documents are live, CheckIndex reports 'actual soft deletes: 1 but expected: 0', and after the retention window the next merge of that segment throws 'invalid softDelCount=-1 (maxDoc=3)'.

## Expected vs actual

- Expected: `forceMerge(1)` keeps a and c; with retention the segment records 1 soft delete and every count says 2.
- Actual: `invalid softDelCount=-1 (maxDoc=2)`; with retention `softDelCount=0`, count 3 for 2 live documents, CheckIndex `actual soft deletes: 1 but expected: 0`, then `invalid softDelCount=-1 (maxDoc=3)`.

## Files

- [src/org/apache/lucene/index/TestMergeWrapperRemovingDocsWithSoftDeletes.java](src/org/apache/lucene/index/TestMergeWrapperRemovingDocsWithSoftDeletes.java) (JUnit test)
- [src2/org/apache/lucene/index/TestMergeWrapperRemovingDocsWithSoftDeletes.java](src2/org/apache/lucene/index/TestMergeWrapperRemovingDocsWithSoftDeletes.java) (JUnit test)

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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestMergeWrapperRemovingDocsWithSoftDeletes
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestMergeWrapperRemovingDocsWithSoftDeletes
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestSoftDeletesRemovingMergeWrapper.java](TestSoftDeletesRemovingMergeWrapper.java)
- [TestMergeWrapperRemovingDocsWithSoftDeletes.java](TestMergeWrapperRemovingDocsWithSoftDeletes.java)
- [TestMergeWrapperRemovesDocsSoftDeletes.java](TestMergeWrapperRemovesDocsSoftDeletes.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
