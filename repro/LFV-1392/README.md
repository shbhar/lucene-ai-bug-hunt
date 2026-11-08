# With a soft-deletes field configured, a merge whose OneMerge#wrapForMerge keeps hard-deleted documents live can make IndexWriter write a segment that declares a document it never stored: mergeMiddle's re-wrap drops every kept hard-deleted document but subtracts only those that carry a soft-deletes value, so with -da the merge succeeds silently, the phantom document has no stored fields, CheckIndex level 3 fails and no later merge can process the segment; since 7.5.0 (LUCENE-8381)

Finding LFV-1392 · Area: index / IndexWriter merge soft-delete accounting (mergeMiddle re-wrap of wrapper-kept hard-deleted documents x OneMerge#wrapForMerge, OneMergeWrappingMergePolicy) · Priority: medium · Impact: data corruption · Novelty: new

Finding page: [../../bugs/LFV-1392.html](../../bugs/LFV-1392.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You set IndexWriterConfig#setSoftDeletesField and a merge policy whose OneMerge#wrapForMerge returns a FilterCodecReader that keeps every document (getLiveDocs() null, numDocs() == maxDoc()), index three documents in one segment, give one of them a soft-deletes value, then hard-delete it and one other document, and call forceMerge(1); you expect a consistent merged segment, whether IndexWriter drops the two hard-deleted documents (the intent of LUCENE-8381) or keeps them (as it does without a soft-deletes field). Instead IndexWriter re-wraps the reader with the hard live docs, which drop both hard-deleted documents, but subtracts only the one that has a soft value, so it writes one stored document into a segment with maxDoc 2. With assertions off the merge succeeds, numDocs and count(MatchAllDocsQuery) report 2, reading the second document's stored fields throws IndexOutOfBoundsException, CheckIndex fails at level 3 (the default level passes), and every later merge of that segment fails, even without the wrapper; with -ea the merge fails at SegmentMerger's assertion 'numMerged=1 vs mergeState.segmentInfo.maxDoc()=2'.

## Expected vs actual

- Expected: The merged segment stores every document it counts: `numDocs` 1 (hard-deleted documents dropped) or 3 (kept).
- Actual: `maxDoc` 2 but 1 stored document: with assertions off reading doc 1 throws `IndexOutOfBoundsException`; with assertions on `forceMerge` fails on `numMerged=1 vs mergeState.segmentInfo.maxDoc()=2`.

## Files

- [src/org/apache/lucene/index/TestMergeWrapperKeepsHardDeletedDocs.java](src/org/apache/lucene/index/TestMergeWrapperKeepsHardDeletedDocs.java) (JUnit test)

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

# run 1: assertions on, seed 5EED7011
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=5EED7011 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestMergeWrapperKeepsHardDeletedDocs
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestMergeWrapperKeepsHardDeletedDocs.java](TestMergeWrapperKeepsHardDeletedDocs.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
