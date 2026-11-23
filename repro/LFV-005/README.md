# HNSW merge-time graph reuse can leave the entry node with no neighbours, dropping kNN recall to zero

Finding LFV-005 · Area: search / HNSW · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-005.html](../../bugs/LFV-005.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You delete a cluster of documents around the HNSW entry node and merge, for example with forceMerge(1), so the merge reuses and repairs an existing segment's graph. You expect kNN recall to stay about the same. Instead the repair of an entry node that lost every neighbour searches from that node itself and finds no other node, an unfiltered kNN query whose k is small enough for a graph search (1 to 195 on the repro's index) starts at that isolated node and returns only it (recall@10 over 50 queries 0.906 to 0.000 on that index), and CheckIndex reports no problem.

The test shows that with the default codec, after deleting the 31-doc cluster that the HNSW entry node (doc 171) links into and calling forceMerge(1), a k=10 kNN query returns 1 hit where 10 are due. Reading the graph back shows why: the merge reuses the graph and leaves the entry with no neighbours.

## Expected vs actual

Expected: a k=10 KnnFloatVectorQuery returns 10 hits among the 1419 live docs. Actual (-ea and -da): 1 hit (doc 171).

## Files

- [src/org/apache/lucene/index/TestHnswMergeReuseIsolatedEntry.java](src/org/apache/lucene/index/TestHnswMergeReuseIsolatedEntry.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main 645d4fe14a20 (a commit of 2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestHnswMergeReuseIsolatedEntry
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

Formal models and scripts this finding's part of the hunt used: [../../models/study-hnsw-concurrent-merge/](../../models/study-hnsw-concurrent-merge/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
