# (Graph quality) A merge that starts from an existing HNSW graph can leave live documents out of reach of a graph search that starts at the graph's entry node: the repair of a node that lost some of its neighbours to deleted documents searches from its surviving neighbours only, so a group whose other links went through deleted documents can stay cut off; in the test the segment before the merge and a merge that builds the graph from scratch return the two documents (10.4.0 to 10.6.0, with the default codec from 10.5.1; branch_11x and main)

Finding LFV-1570 · Area: core / util.hnsw: merge-time reuse of an HNSW graph with deleted vectors (the repair of nodes that lost neighbours) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1570.html](../../bugs/LFV-1570.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Graph quality only: nothing is lost, corrupt or thrown, and approximate kNN search promises no recall. When a merge starts from a segment's existing HNSW graph (it can when the deleted share of the graph's vectors, rounded down to a whole percent, is at most 40) it drops the deleted nodes and repairs the nodes whose loss of neighbours crosses a threshold, and you expect a kNN query to keep finding the live documents it found before the merge and that a merge building the graph from scratch finds. Instead the repair of a node that still has neighbours searches only from those neighbours, so documents whose other links all went through deleted documents can end up linked to each other alone: in the test (703 documents with a one-dimension vector on a line, the default codec, ONE document deleted, forceMerge(1)) a query with a live document's own vector and k = 2 returns two other documents, 20 and 21 away, and neither that document (distance 0) nor its neighbour (distance 1), although both came first before the merge and a search that scores every vector still returns them first; the 10.6.0 release does the same, so it is not new in 11. Credits: the change that introduced the repair, #15003, describes the step (a graph search that starts from the node's existing neighbours) and its thread mentions disconnectedness after singleton merges; that an HNSW graph can hold documents that a search from its entry node cannot reach is on public record upstream (#12627, #14214), and a comment on #16529 says that the fixing of disconnected components during merging was reverted and never restored; none of these four items says, in its description, comments, reviews or inline comments as read on 2026-11-23 (#12627, whose last comment is of 2024, was searched by key words and read at each match), that the repair as merged can still leave a group of live documents cut off, or names where its search starts as the cause.

## Expected vs actual

- Expected: [A, B] after `forceMerge(1)`, as before the merge: A is 0 and B is 1 away from the query.
- Actual: [r0, r1], the documents at 10 and 11, 20 and 21 away (`java.lang.AssertionError: after forceMerge(1) expected:<[A, B]> but was:<[r0, r1]>` at line 52), with no exception from Lucene, assertions on and off.

## Files

- [src/org/apache/lucene/index/TestHnswMergeReuseCutOffPair.java](src/org/apache/lucene/index/TestHnswMergeReuseCutOffPair.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Not new in 11.0: the test fails the same way with the 10.6.0 release jars, on a build of branch_11x (262e019500ec) and on Lucene main (b5a4546a5964), with and without assertions; it also fails with the 10.5.1 release jars and passes with the 10.5.0 release jars, whose merge builds the graph from scratch. Graph quality only: nothing is lost, corrupt or thrown, and approximate kNN search promises no recall. What the test does: it indexes 703 documents with a one-dimension EUCLIDEAN vector into one committed segment with the default codec (A at -10, B at -11, C at 0 and r0 to r699 at 10 to 709), deletes C and asks a KnnFloatVectorQuery with A's own vector for the 2 nearest documents, before and after forceMerge(1); it expects [A, B] both times. The first assertion holds; after the merge the run reports 1 test found and 1 test failed, with java.lang.AssertionError: after forceMerge(1) expected:&lt;[A, B]&gt; but was:&lt;[r0, r1]&gt;; Lucene itself throws no exception. That query's graph search starts at the graph's entry node; on the same index a graph search that is seeded at A or B (the experimental SeededKnnVectorQuery) returned [A, B], and the query with k = 702, for which every vector is scored, returned A and B first. The commands below print one run, with assertions on; with -da -dsa -Dtests.asserts=false in place of -ea -esa the outcome is the same. The test has no randomness of its own and names the default codec; the outcome was the same under each seed that was run. On a branch_11x checkout set V=11.0.0-SNAPSHOT; for the release, set V=10.6.0 and use the three 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework) in place of the three $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/core: with the test copied to lucene/core/src/test/org/apache/lucene/index/ it reports 1 test completed, 1 failed, with the same message, on branch_11x 262e019500ec and on main b5a4546a5964, with -Ptests.asserts=true and with -Ptests.asserts=false. This finding is listed as new together with credits to the earlier public items around it: apache/lucene#15003 (the change that introduced the merge-time reuse of a graph with deleted vectors and its repair step; its thread names the step and the disconnectedness it was added against), #12533 (the original idea, 2023), the connectivity history #12627, #13566, #14436 and #14214, #16529 with three maintainer comments of August 2026, #15427, #16552 with #16559 (the flag rule of the same repair pass, fixed as a bug) and #16817; the finding's record in the table links each of them and says what it states. None of them says, in its description, comments, reviews or inline comments as read on 2026-11-23 (#12627, whose last comment is of 2024, was searched by key words and read at each match), that the repair step as merged can still leave a group of live documents cut off, or names where the repair search starts as the cause. Of the tests that came with the parallel repair (#16627), one asserts that the graph is a single rooted component after the repair, on one fixed set of random vectors with every third vector deleted; the end-to-end merge test, whose javadoc says the merged graph must be rooted, asserts that no node is left without a level-0 neighbour and that a search returns k results. Both pass.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
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

## Files shown on the finding page

- [TestHnswMergeReuseCutOffPair.java](TestHnswMergeReuseCutOffPair.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
