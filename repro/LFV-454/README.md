# kNN vector queries size each segment's result heap from the requested k, not from what the segment can return, so k = Integer.MAX_VALUE throws IllegalArgumentException and k = 2^28 runs out of a 2 GiB heap on a 3-document index (core KnnFloat/KnnByteVectorQuery without a filter: regression in 9.10.0; join DiversifyingChildren*KnnVectorQuery: since 9.8.0)

Bug LFV-454 · Area: search / kNN vector queries (per-segment collector sizing: AbstractKnnVectorQuery, TopKnnCollectorManager, TopKnnCollector; join DiversifyingNearestChildrenKnnCollectorManager) · Priority: low · Impact: crash · Novelty: new

Bug page: [../../bugs/LFV-454.html](../../bugs/LFV-454.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a KnnFloatVectorQuery with a k larger than the index, for example k = Integer.MAX_VALUE to mean 'all vectors', and expect every vector back, as k = 1000 over 3 vectors gives, and as 9.9.2 gives for any k. Instead each segment's collector is built with a heap sized from k (k + 1 longs on a single-segment index) before the segment's vector count is looked at: k = Integer.MAX_VALUE throws IllegalArgumentException('initialCapacity must be &gt; 0 and &lt; 2147483630; got: 2147483647') from TernaryLongHeap, and k = 2^28 over a 3-document index throws OutOfMemoryError with a 2 GiB heap. Lucene used to cap k at the segment's vector count ('bound k by total number of vectors to prevent oversizing data structures'); a search speed-up change dropped that cap in 9.10.0, and the join DiversifyingChildren*KnnVectorQuery never had it.

## Expected vs actual

- Expected: Every search returns the 3 documents, as k = 1000 and the filtered k = `Integer.MAX_VALUE` do.
- Actual: k = `Integer.MAX_VALUE` throws `IllegalArgumentException: initialCapacity must be > 0 and < 2147483630; got: 2147483647`, and k = 2^28 throws `OutOfMemoryError` with a 2 GiB heap.

## Files

- [src/org/apache/lucene/search/TestKnnQueryHugeK.java](src/org/apache/lucene/search/TestKnnQueryHugeK.java) (JUnit test)

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

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestKnnQueryHugeK
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestKnnQueryLargeK.java](TestKnnQueryLargeK.java)
- [TestDiversifyingKnnLargeK.java](TestDiversifyingKnnLargeK.java)
- [TestKnnQueryHugeK.java](TestKnnQueryHugeK.java)
- [TestKnnLargeKVariants.java](TestKnnLargeKVariants.java)
- [TestDiversifyingKnnLargeKVariants.java](TestDiversifyingKnnLargeKVariants.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
