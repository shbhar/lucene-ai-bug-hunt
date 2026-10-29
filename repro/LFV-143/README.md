# Byte DOT_PRODUCT scores (VectorUtil.dotProductScore and the two Panama byte scorers) compute len * 2^15 in int, so from 65,536 dimensions every score is mirrored around 0.5 (closer vectors score lower) and at 131,072 dimensions scores become +/-Infinity or NaN

Bug LFV-143 · Area: util / VectorUtil byte DOT_PRODUCT score x internal/vectorization / Panama MemorySegment byte scorers · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You score two byte vectors of 65,536 dimensions with VectorSimilarityFunction.DOT_PRODUCT (or VectorUtil.dotProductScore, or run a KnnByteVectorQuery on a byte DOT_PRODUCT field indexed through a format that raises getMaxDimensions) and expect the documented score 0.5 + dot / (len * 2^15): within [0, 1] and higher for more similar vectors. Instead the divisor len * (1 &lt;&lt; 15) is multiplied in int before the cast to float, so from 65,536 dimensions it wraps to a negative number and every score is mirrored around 0.5: a vector identical to an all-ones query scores 0.49996948 and its opposite 0.5000305, and kNN returns the documents in exactly reverse order. At 131,072 dimensions the divisor wraps to 0 and the scores are +Infinity, -Infinity or NaN.

The test shows that byte `VectorSimilarityFunction.DOT_PRODUCT.compare` at 65,536 dims scores a vector identical to the query below its opposite, because `VectorUtil.dotProductScore` computes `len * (1 << 15)` in `int`.

## Expected vs actual

Expected same &gt; opposite (0.5000305 vs 0.49996948); actual same=0.49996948, opposite=0.5000305, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/util/TestByteDotProductScoreDenominator.java](src/org/apache/lucene/util/TestByteDotProductScoreDenominator.java) (JUnit test)

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
     execute --select-class org.apache.lucene.util.TestByteDotProductScoreDenominator
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
