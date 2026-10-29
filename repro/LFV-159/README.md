# HnswGraph.DenseNodesIterator.consume (the level-0 node iterator of every HNSW graph) does not advance its cursor, so draining level 0 in chunks with consume returns the first chunk forever and a nextInt after consume starts again at node 0 (latent: Lucene itself never calls consume on level 0)

Bug LFV-159 · Area: util / HNSW graph node iterators (HnswGraph.NodesIterator, DenseNodesIterator) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You take the level-0 node iterator of an HNSW graph (getNodesOnLevel(0), a DenseNodesIterator over the node ids [0, size)) and read it in chunks with consume(buf), expecting each call to hand out the next block of ids, as the javadoc ('Consume integers from the iterator') and the two sibling iterator classes do. Instead DenseNodesIterator.consume copies the block but never moves its cursor, so every call returns the same first block, hasNext stays true and the loop never ends; a nextInt after consume also starts again at 0. Lucene's own code never calls consume on level 0, so indexes and search results are not affected.

The test shows that `HnswGraph.DenseNodesIterator.consume()` (the level-0 node iterator) does not advance its cursor, so a second `consume()` returns the first chunk again.

## Expected vs actual

Expected the second chunk of `new DenseNodesIterator(4)` read into an `int[2]` to be `[2, 3]`; actual `[0, 1]`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/util/hnsw/TestDenseNodesIteratorConsume.java](src/org/apache/lucene/util/hnsw/TestDenseNodesIteratorConsume.java) (JUnit test)

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
     execute --select-class org.apache.lucene.util.hnsw.TestDenseNodesIteratorConsume
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
