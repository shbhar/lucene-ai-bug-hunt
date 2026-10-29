# BpVectorReorderer scales a zero DOT_PRODUCT/COSINE centroid by 1/sqrt(0), so a split whose vectors cancel out gets a NaN centroid: with assertions on, reordering fails and a reordering merge closes the IndexWriter; with assertions off, that split is silently not refined

Bug LFV-421 · Area: misc / BP merge-time reordering (BpVectorReorderer centroid arithmetic) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index DOT_PRODUCT or COSINE float vectors and reorder them with BpVectorReorderer, by calling computeDocMap or by merging through BPReorderingMergePolicy, for example 64 legal unit vectors where docs 0..31 alternate (1,0) and (-1,0) and docs 32..63 are (0,1); you expect a valid reordering, as for any other legal vectors. Instead the half whose vectors cancel out sums to the zero vector, computeCentroid normalizes it by 1/sqrt(0) = Infinity and the centroid becomes NaN. With assertions on, VectorUtil.dotProduct then fails with 'AssertionError: not finite: NaN from &lt;[1.0, 0.0],[NaN, NaN]&gt;' and the merge path closes the IndexWriter; with assertions off nothing throws, but the NaN biases leave that split in doc-ID order.

The test shows that `BpVectorReorderer.computeDocMap` on 64 legal DOT_PRODUCT unit vectors, where docs 0..31 alternate (1, 0) and (-1, 0) and sum to zero, scales that half's centroid by 1/sqrt(0) and gets NaN.

## Expected vs actual

Expected a DocMap; actual with `-ea`: `AssertionError: not finite: NaN from <[1.0, 0.0],[NaN, NaN]>`. With `-da` the test passes, as the claim says: nothing throws, and that split is just left in doc-ID order.

## Files

- [src/org/apache/lucene/misc/index/TestBpVectorReordererZeroCentroid.java](src/org/apache/lucene/misc/index/TestBpVectorReordererZeroCentroid.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestBpVectorReordererZeroCentroid
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
