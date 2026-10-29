# HNSW merge with graph reuse throws BufferAllocationException when the reused graph has more than 805,306,368 vectors

Bug LFV-458 · Area: search / HNSW (merge-time graph reuse: old-to-new ordinal mapping in IncrementalHnswGraphMerger and ConcurrentHnswMerger) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You merge an HNSW vector field in which one segment's graph has more than 805,306,368 vectors (Lucene writes such a segment without error, for example by merging smaller graphs) and expect the merge to reuse that graph as it does at any smaller size, because no HNSW vector-count limit is documented or enforced below IndexWriter.MAX_DOCS. Instead both mergers presize an IntIntHashMap to the graph size to map old to new ordinals, and that map's fixed 0.75 load factor and 2^30-slot table cap make its constructor throw BufferAllocationException ('Maximum array size exceeded for this load factor') before any other work. The merge fails again on every retry, so such a segment can never be merged with graph reuse; by code reading the IndexWriter is also closed as a tragic event.

The test shows that on main an HNSW merge (IncrementalHnswGraphMerger, used by both mergers) that reuses a graph of 805,306,369 vectors fails while mapping ordinals, because it presizes an `IntIntHashMap` to the graph size. The graph and vectors are stubs, so nothing large is allocated.

## Expected vs actual

Expected: the merge maps the ordinals and starts copying the graph (the stub then throws the test's `GraphCopyStarted`). Actual: `BufferAllocationException: Maximum array size exceeded for this load factor (elements: 805306369, load factor: 0.750000)` from `getNewOrdMapping`.

## Files

- [src/org/apache/lucene/util/hnsw/TestHnswMergeLargeGraphOrdMapping.java](src/org/apache/lucene/util/hnsw/TestHnswMergeLargeGraphOrdMapping.java) (JUnit test)

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
     execute --select-class org.apache.lucene.util.hnsw.TestHnswMergeLargeGraphOrdMapping
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/hnsw-parallel-repair-tla/](../../models/hnsw-parallel-repair-tla/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
