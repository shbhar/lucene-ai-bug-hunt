# Concurrent HNSW merge throws once the merged segment has more than 2^31 - 2048 x numMergeWorkers vectors: the int batch-claim counter wraps past Integer.MAX_VALUE and workers are handed negative ordinals

Bug LFV-466 · Area: search / HNSW (concurrent merge: HnswConcurrentMergeBuilder batch claiming) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You enable the concurrent HNSW merge (numMergeWorkers &gt; 1) and merge into one segment whose dense vector field has, for example, IndexWriter.MAX_DOCS vectors, and you expect HnswConcurrentMergeBuilder.build(maxOrd) to add every node below maxOrd, as the HnswBuilder.build javadoc says and as the single-threaded builder does at the same size. Instead each worker claims batches of 2048 ordinals with an int workProgress.getAndAdd(2048) and stops only at its first claim at or past maxOrd, so once maxOrd &gt; 2^31 - 2048 x numMergeWorkers one of those final claims lands on 2^31, wraps to Integer.MIN_VALUE and passes the 'start &lt; maxOrd' check, and that worker is handed negative ordinals. The merge then throws on every attempt (AssertionError with assertions enabled, ArrayIndexOutOfBoundsException without, in the executed class-level repro).

## Expected vs actual

- Expected: You expect HnswConcurrentMergeBuilder.build(maxOrd) to add every node below maxOrd, as the HnswBuilder.build javadoc says and as the single-threaded builder does at the same size.
- Actual: Each worker claims batches of 2048 ordinals with an int workProgress.getAndAdd(2048) and stops only at its first claim at or past maxOrd, so once maxOrd &gt; 2^31 - 2048 x numMergeWorkers one of those final claims lands on 2^31, wraps to Integer.MIN_VALUE and passes the 'start &lt; maxOrd' check, and that worker is handed negative ordinals.

## Files

- [src/org/apache/lucene/util/hnsw/TestHnswConcurrentMergeClaimWrap.java](src/org/apache/lucene/util/hnsw/TestHnswConcurrentMergeClaimWrap.java) (JUnit test)
- [src2/org/apache/lucene/util/hnsw/TestHnswConcurrentMergeClaimWrap.java](src2/org/apache/lucene/util/hnsw/TestHnswConcurrentMergeClaimWrap.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx14g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.hnsw.TestHnswConcurrentMergeClaimWrap
java -ea -esa -Xmx14g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.util.hnsw.TestHnswConcurrentMergeClaimWrap
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/hnsw-parallel-repair-tla/](../../models/hnsw-parallel-repair-tla/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
