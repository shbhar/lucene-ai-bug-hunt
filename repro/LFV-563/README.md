# A document block rejected at a child can be flushed as a fully deleted segment without the parent field; if the merge policy keeps it (SoftDeletesRetentionMergePolicy can), merges that combine it only with segments without multi-document blocks throw 'expected parent doc field... across all segments' and close IndexWriter, again after every reopen

Bug LFV-563 · Area: index / IndexWriter document blocks (parent field) and merges · Priority: medium · Impact: index unusable · Novelty: new

Bug page: [../../bugs/LFV-563.html](../../bugs/LFV-563.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You set IndexWriterConfig#setParentField (no index sort) and a merge policy that keeps fully deleted segments, such as SoftDeletesRetentionMergePolicy, and IndexWriter rejects a document block at a child with a documented non-aborting exception (for example a term longer than MAX_TERM_LENGTH) while that block is the only content of its in-memory segment, for example the first thing indexed after a commit. You expect the rejection to leave a consistent index that keeps merging, as the addDocument javadoc promises ('if an Exception is hit... the index will be consistent'). Instead the block is flushed as a fully deleted segment whose FieldInfos have no parent field (IndexWriter registers the parent field only when it reaches a block's last document), the merge policy keeps it, and every merge that combines it only with segments holding no multi-document block throws IllegalStateException 'expected parent doc field to be "_parent " across all segments but found a segment with different field "null"' and closes IndexWriter, also after the index is reopened, until forceMergeDeletes removes the segment.

The test shows that with setParentField and SoftDeletesRetentionMergePolicy, a block rejected at its child (term &gt; MAX_TERM_LENGTH) right after a commit leaves a fully deleted segment without the parent field that breaks forceMerge (`TestRejectedBlockSegmentWithoutParentField#testForceMergeAfterBlockRejectedAtChild`).

## Expected vs actual

Expected: forceMerge(1) succeeds. Actual: IllegalStateException "expected parent doc field to be "_parent " across all segments but found a segment with different field "null""; fails with -ea and -da.

## Files

- [src/org/apache/lucene/index/TestRejectedBlockSegmentWithoutParentField.java](src/org/apache/lucene/index/TestRejectedBlockSegmentWithoutParentField.java) (JUnit test)

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
     execute --select-class org.apache.lucene.index.TestRejectedBlockSegmentWithoutParentField
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
