# HNSW merge-time graph reuse can leave the entry node with no neighbours, dropping kNN recall to zero

Bug LFV-005 · Area: search / HNSW · Priority: high · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-005.html](../../bugs/LFV-005.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You delete a cluster of documents around the HNSW entry node and merge, for example with forceMerge(1), so the merge reuses and repairs the biggest segment's graph. You expect kNN recall to stay about the same. Instead the repair cannot reconnect an entry node that lost every neighbour, every search starts at that isolated node and returns only it (recall@10 0.906 to 0.000 in the repro), and CheckIndex reports no problem.

## Expected vs actual

- Expected: recall@10 stays about 0.906 after `forceMerge(1)` (0.910 when the merge rebuilds the graph, as on 10.4.0 and 10.5.0).
- Actual: recall@10 0.000: every query returns 1 hit, doc 171 (0.106 after 200 more documents and a second merge), and `CheckIndex` reports no problems.

## Files

- [src/org/apache/lucene/util/hnsw/TestVerifyCe2EntryIsolation.java](src/org/apache/lucene/util/hnsw/TestVerifyCe2EntryIsolation.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- Recorded outcome of run 1 on main a9779ef3c193: 8 test(s) run, 5 failed (testConcurrentMergerSingletonForceMerge, testDefaultCodecForceMergeWithNewSmallSegment, testDefaultCodecSingletonForceMerge, testConcurrentBridgeDeleted, testSerialBridgeDeleted).
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
java -ea -esa -Xmx4g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.hnsw.TestVerifyCe2EntryIsolation
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [EntryIsolationRepro.java](EntryIsolationRepro.java)
- [test-main.diff](test-main.diff)

Formal models and scripts this bug's part of the hunt used: [../../models/study-hnsw-concurrent-merge/](../../models/study-hnsw-concurrent-merge/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
