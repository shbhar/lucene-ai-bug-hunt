# FlatBitVectorsScorer's RandomVectorScorerSupplier.copy() does not copy the vector values, so the copy's scorers read the original ByteVectorValues and the concurrent merge workers of HnswBitVectorsFormat (numMergeWorkers &gt; 1) share one OffHeapByteVectorValues and its IndexInput (regression of #14181, since 10.2.0)

Finding LFV-1358 · Area: codecs / bit vectors (FlatBitVectorsScorer.BitRandomVectorScorerSupplier, used by the concurrent HNSW merge of HnswBitVectorsFormat) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1358.html](../../bugs/LFV-1358.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index bit vectors with HnswBitVectorsFormat set up for concurrent merges (numMergeWorkers &gt; 1 and an executor), and each merge worker gets scorerSupplier.copy(), which RandomVectorScorerSupplier documents as 'will copy the underlying vectorValues so the copy is safe to be used in other threads'. Instead, FlatBitVectorsScorer's supplier makes a copy of the values in its constructor (targetVectors) and never uses it: scorer() reads the original vectorValues and copy() wraps that same instance, so all workers read one shared, non-thread-safe OffHeapByteVectorValues and its IndexInput. The one-test repro shows it directly (a scorer from supplier.copy() makes 2 reads on the original values instead of 0, on main with -ea and -da and on 10.2.0 to 10.6.0, while 10.1.0 passes); in a concurrent merge the race gives occasionally wrong build-time scores.

## Expected vs actual

- Expected: The scorer from `supplier.copy()` reads only its own copy of the values: 0 reads on the original.
- Actual: `java.lang.AssertionError: expected:<0> but was:<2>`: both reads, in `setScoringOrdinal(0)` and `score(1)`, went to the original.

## Files

- [src/org/apache/lucene/codecs/bitvectors/TestFlatBitVectorsScorerCopy.java](src/org/apache/lucene/codecs/bitvectors/TestFlatBitVectorsScorerCopy.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.codecs.bitvectors.TestFlatBitVectorsScorerCopy
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestFlatBitVectorsScorerCopy.java](TestFlatBitVectorsScorerCopy.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
