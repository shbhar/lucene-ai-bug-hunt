# (Graph quality) A merge that starts from an existing HNSW graph can leave live documents out of reach of graph search: the repair of a node that lost some of its neighbours to deleted documents searches from its surviving neighbours only, so a group whose other links went through deleted documents can stay cut off; in the test the segment before the merge and a merge that builds the graph from scratch return the two documents (10.4.0 to 10.6.0, with the default codec from 10.5.1; branch_11x and main)

Finding LFV-1570 · Area: core / util.hnsw: merge-time reuse of an HNSW graph with deleted vectors (the repair of nodes that lost neighbours) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Graph quality only: nothing is lost, corrupt or thrown, and approximate kNN search promises no recall. When a merge starts from a segment's existing HNSW graph (it can when at most 40 percent of the graph's vectors are deleted) it drops the deleted nodes and repairs the nodes that lost neighbours, and you expect a kNN query to keep finding the live documents it found before the merge and that a merge building the graph from scratch finds. Instead the repair of a node that still has neighbours searches only from those neighbours, so documents whose other links all went through deleted documents can end up linked to each other alone: in the test (703 documents with a one-dimension vector on a line, the default codec, ONE document deleted, forceMerge(1)) a query with a live document's own vector and k = 2 returns two other documents, 20 and 21 away, and neither that document (distance 0) nor its neighbour (distance 1), although both came first before the merge and a search that scores every vector still returns them first; the 10.6.0 release does the same, so it is not new in 11. Credits: the change that introduced the repair, #15003, describes the step (a graph search that starts from the node's existing neighbours) and its thread mentions disconnectedness after singleton merges; that an HNSW graph can hold documents a graph search cannot reach is on public record upstream (#12627, #14214), and #16529 says that components found while merging are not connected; none of these items says that the repair as merged can still leave a group of live documents cut off, or names where its search starts as the cause.

## Expected vs actual

- Expected: You expect a kNN query to keep finding the live documents it found before the merge and that a merge building the graph from scratch finds.
- Actual: The repair of a node that still has neighbours searches only from those neighbours, so documents whose other links all went through deleted documents can end up linked to each other alone: in the test (703 documents with a one-dimension vector on a line, the default codec, ONE document deleted, forceMerge(1)) a query with a live document's own vector and k = 2 returns two other documents, 20 and 21 away, and neither that document (distance 0) nor its neighbour (distance 1), although both came first before the merge and a search that scores every vector still returns them first; the 10.6.0 release does the same, so it is not new in 11.

## Files

- [src/org/apache/lucene/index/TestHnswMergeReuseCutOffPair.java](src/org/apache/lucene/index/TestHnswMergeReuseCutOffPair.java) (JUnit test)

## Run it

- Tested on Lucene main 7b8418faa2ff (a commit of 2026-11-20): still reproduces.
- This finding is listed as new together with credits to the earlier public items around it: apache/lucene#15003 (the change that introduced the merge-time reuse of a graph with deleted vectors and its repair step; its thread names the step and the disconnectedness it was added against), #12533 (the original idea, 2023), the connectivity history #12627, #13566, #14436 and #14214, #16529 with three maintainer comments of August 2026, #15427, #16552 with #16559 (the flag rule of the same repair pass, fixed as a bug) and #16817; the finding's record in the table links each of them and says what it states. None of them says that the repair step as merged can still leave a group of live documents cut off, or names where the repair search starts as the cause. Of the tests that came with the parallel repair (#16627), one asserts that the graph is a single rooted component after the repair, on one fixed set of random vectors with every third vector deleted; the end-to-end merge test, whose javadoc says the merged graph must be rooted, asserts that no node is left without a level-0 neighbour and that a search returns k results. Both pass.
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

# run 1: assertions on, seed C0FFEE69
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=C0FFEE69 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestHnswMergeReuseCutOffPair
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
