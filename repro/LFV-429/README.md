# Reordering merges (BPReorderingMergePolicy) silently break document blocks written before Lucene 9.9, because IndexWriter's reorder guard reads the unrecorded LeafMetaData#hasBlocks flag as 'no blocks'

Bug LFV-429 · Area: index / IndexWriter merge-time reordering (OneMerge#reorder guard; misc BPReorderingMergePolicy) · Priority: low · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-429.html](../../bugs/LFV-429.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You have an index with document blocks (children followed by their parent, added with IndexWriter#addDocuments) that was written by Lucene 9.8 or earlier, open it with a current Lucene, and force-merge it through BPReorderingMergePolicy. You expect the merge to leave the blocks alone, because since 9.11 IndexWriter lets a merge reorder documents only when there are no blocks or a parent field identifies them, and such an old index cannot have a parent field. Instead the guard trusts LeafMetaData#hasBlocks, which was never recorded before 9.9 and reads false for those segments, so the reorderer renumbers the documents one by one and interleaves the blocks, with no exception and a clean CheckIndex, while the same documents written by 9.9.2 are left intact.

The test shows that a reordering forceMerge (BPReorderingMergePolicy) tears apart document blocks written by Lucene 9.8.0, because IndexWriter's reorder guard reads the unrecorded pre-9.9 `LeafMetaData#hasBlocks` as "no blocks" (fixture `blocks.9.8.0.zip`: 3 child+parent blocks in 2 segments).

## Expected vs actual

Expected the merged segment's docs, in pairs, to be the blocks `[c0p0, c1p1, c2p2]`; actual: `[c0c1, c2p0, p1p2]` (doc order c0 c1 c2 p0 p1 p2), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/misc/index/TestReorderPre99Blocks.java](src/org/apache/lucene/misc/index/TestReorderPre99Blocks.java) (JUnit test)

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

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
