# Merges through BPReorderingMergePolicy with BpVectorReorderer break document blocks even when a parent field is configured

Bug LFV-093 · Area: misc / BP merge-time reordering · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-093.html](../../bugs/LFV-093.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index documents in blocks (children followed by their parent) with IndexWriterConfig#setParentField, and use BPReorderingMergePolicy with a BpVectorReorderer over a float vector field. You expect merges to keep every block together with its parent last, because IndexWriter only lets a merge reorder documents when the parent field identifies the blocks, and setParentField promises that IndexWriter does not alter the order of a block. Instead BpVectorReorderer moves each document on its own and puts every document without a vector after all documents with one, so the merged segment's blocks are silently scrambled and ToParentBlockJoinQuery joins children to the wrong parent (59 of 60 children in one run), with no exception and with assertions on or off.

The test shows that forceMerge through BPReorderingMergePolicy with BpVectorReorderer breaks document blocks even though IndexWriterConfig#setParentField is set (2 blocks [child with vector, parent], one segment each).

## Expected vs actual

Expected each child directly before its own parent, e.g. [c0, p0, c1, p1]. Actual: [c0, c1, p0, p1], so c1 now belongs to p0's block.

## Files

- [src/org/apache/lucene/misc/index/TestBpVectorMergeKeepsBlocks.java](src/org/apache/lucene/misc/index/TestBpVectorMergeKeepsBlocks.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestBpVectorMergeKeepsBlocks
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
