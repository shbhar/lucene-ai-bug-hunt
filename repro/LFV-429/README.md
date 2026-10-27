# Reordering merges (BPReorderingMergePolicy) silently break document blocks written before Lucene 9.9, because IndexWriter's reorder guard reads the unrecorded LeafMetaData#hasBlocks flag as 'no blocks'

Bug LFV-429 · Area: index / IndexWriter merge-time reordering (OneMerge#reorder guard; misc BPReorderingMergePolicy) · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-429.html](../../bugs/LFV-429.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You have an index with document blocks (children followed by their parent, added with IndexWriter#addDocuments) that was written by Lucene 9.8 or earlier, open it with a current Lucene, and force-merge it through BPReorderingMergePolicy. You expect the merge to leave the blocks alone, because since 9.11 IndexWriter lets a merge reorder documents only when there are no blocks or a parent field identifies them, and such an old index cannot have a parent field. Instead the guard trusts LeafMetaData#hasBlocks, which was never recorded before 9.9 and reads false for those segments, so the reorderer renumbers the documents one by one and interleaves the blocks, with no exception and a clean CheckIndex, while the same documents written by 9.9.2 are left intact.

## Expected vs actual

- Expected: 0 of 257 children paired with another parent (or with none) after the merge, as for the same blocks written by 9.9.2.
- Actual: 257 of 257 children paired with another parent (or with none), merged segment `hasBlocks=false` (139 of 257 with `BPReorderingMergePolicy`).

## Files

- [src/org/apache/lucene/misc/index/TestReorderPre99Blocks.java](src/org/apache/lucene/misc/index/TestReorderPre99Blocks.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/backward-codecs/build/libs/lucene-backward-codecs-$V.jar
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestReorderPre99Blocks
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestReorderingMergeKeepsPre99Blocks.java](TestReorderingMergeKeepsPre99Blocks.java)
- [fix-with-test.diff](fix-with-test.diff)
- [TestReorderPre99Blocks.java](TestReorderPre99Blocks.java)
- [TestReorderingMergeOldBlocks.java](TestReorderingMergeOldBlocks.java)
- [MakeBlockIndex.java](MakeBlockIndex.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
