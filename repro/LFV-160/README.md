# HNSW merge computes a graph's deleted percentage in int, so a graph with more than 21,474,836 deleted vectors (41-100% deleted) passes the 40% DELETE_PCT_THRESHOLD and is reused as the merge base; in 10.4.0-10.5.2, the 10.6.0 RC1 and branch_10x the opt-in concurrent merger then throws ArrayIndexOutOfBoundsException for an all-deleted base (main since #16794, 11.0 only, rebuilds the graph instead)

Bug LFV-160 · Area: search / HNSW (merge-time graph reuse: IncrementalHnswGraphMerger base-graph selection) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You merge an HNSW vector segment that has had most of its vectors deleted and expect Lucene to rebuild the graph or reuse a cleaner one, because the DELETE_PCT_THRESHOLD javadoc says a graph with more than 40% of its vectors deleted is never selected as the merge base. Once more than 21,474,836 vectors of one graph are deleted, the percentage ((graphSize - live) * 100) / graphSize overflows int and goes negative (-59 for 25M deleted out of 30M), so the guard passes and a graph that is up to 100% deleted becomes the base, in either reader order and also when the segment is merged alone. The default merger still finishes, pruning and repairing the reused graph. In 10.4.0 through 10.5.2, the 10.6.0 RC1 and branch_10x, and on main before #16794 (for example ea57360912ed), the opt-in concurrent merger throws ArrayIndexOutOfBoundsException for an all-deleted base; on main since #16794 (from a9779ef3c193; to be released in 11.0, not in 10.6.0) it rebuilds the graph, and only the wrong choice of merge base remains.

The test shows that IncrementalHnswGraphMerger picks a graph with 21,474,837 of its 22,000,000 vectors deleted (97.6%) as the merge base, because the int `deletePct` at IncrementalHnswGraphMerger.java:133 overflows to -97 and passes the 40% DELETE_PCT_THRESHOLD guard.

## Expected vs actual

Expected: no base graph (largestGraphReader == null, graph rebuilt from the live vectors). Actual (-ea and -da): that graph is the base (GraphReader[graphSize=22000000, liveVectorCount=525163]).

## Files

- [src/org/apache/lucene/util/hnsw/TestMergeBaseDeletePctOverflow.java](src/org/apache/lucene/util/hnsw/TestMergeBaseDeletePctOverflow.java) (JUnit test)

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
     execute --select-class org.apache.lucene.util.hnsw.TestMergeBaseDeletePctOverflow
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/hnsw-merge-reuse/](../../models/hnsw-merge-reuse/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
