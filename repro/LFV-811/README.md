# On 10.5.1, 10.5.2 and branch_10_6 (the 10.6.0 RC1 revision), an HNSW merge that reuses a graph written with a different maxConn either fails and closes the IndexWriter (maxConn raised) or commits a segment that no kNN query can search (maxConn lowered)

Bug LFV-811 · Area: codecs / HNSW vectors on the 10.x line (merge-time graph reuse: InitializedHnswGraphBuilder, MergingHnswGraphBuilder, ConcurrentHnswMerger; Lucene99HnswVectorsWriter.vem metadata) · Priority: high · Impact: index unusable · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a vector index with HNSW maxConn 16 (the default), later open a writer whose codec sets maxConn 32 for the same field (or go the other way), and force-merge, or add the old index with addIndexes(CodecReader...); you expect the merge to succeed and kNN search to keep working, as it does on main, and as raising did on 10.3.2. Instead, on 10.5.1, 10.5.2 and branch_10_6, raising makes the merge throw IllegalStateException 'No growth is allowed' and closes the IndexWriter, and lowering lets the merge succeed but every kNN query on the new segment throws ArrayIndexOutOfBoundsException (AssertionError 'too many neighbors' with -ea). The 10.x backport of #15003 sizes the merged graph from the reused graph's maxConn, while the merge builders insert the new vectors, and the writer records the.vem metadata, with the configured maxConn.

## Expected vs actual

- Expected: You expect the merge to succeed and kNN search to keep working, as it does on main, and as raising did on 10.3.2.
- Actual: On 10.5.1, 10.5.2 and branch_10_6, raising makes the merge throw IllegalStateException 'No growth is allowed' and closes the IndexWriter, and lowering lets the merge succeed but every kNN query on the new segment throws ArrayIndexOutOfBoundsException (AssertionError 'too many neighbors' with -ea).

## Files

- [src/org/apache/lucene/codecs/lucene99/TestHnswMergeReuseChangedMaxConn.java](src/org/apache/lucene/codecs/lucene99/TestHnswMergeReuseChangedMaxConn.java) (JUnit test)

## Run it

- Tested on Lucene branch_10_6 4ae946e5a9b9 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=10.6.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.lucene99.TestHnswMergeReuseChangedMaxConn
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/hnsw-10x-merge-reuse-maxconn-mismatch/](../../models/hnsw-10x-merge-reuse-maxconn-mismatch/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
