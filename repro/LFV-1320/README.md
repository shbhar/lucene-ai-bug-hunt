# ScalarQuantizer.fromVectorsAutoInterval ignores the data and returns the first pair of its quantile search grid once one sampled vector's ten nearest-neighbour scores all tie (for example 11 identical vectors): that vector's correlation term is 0/0 = NaN, the NaN makes the mean NaN for every candidate pair, and the NaN-to-0.0 fallback then scores every pair 0.0 (every release from 9.11.0 through 10.6.0, and main)

Finding LFV-1320 · Area: core / util/quantization: ScalarQuantizer dynamic (auto) confidence interval search (ScoreErrorCorrelator) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1320.html](../../bugs/LFV-1320.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call ScalarQuantizer.fromVectorsAutoInterval (the dynamic confidence interval) on 20 random 8-dim vectors, and again on the same 20 plus 11 identical far-away vectors that change neither the candidate quantile grid nor any original vector's nearest neighbours. You expect the same best-correlated quantile pair both times, (-0.96047044, 0.957112) at 4 bits. Instead the second call returns (-0.9538363, 0.9618902), which is simply the first pair of the 16 x 16 search grid: each copy's ten neighbour scores tie, so its correlation term 1 - errors.var() / scoreVariance is 0/0 = NaN, the NaN makes the mean NaN, the fallback 'Double.isNaN(corr.mean) ? 0.0 : corr.mean' scores every candidate pair 0.0, and the first pair wins every comparison.

## Expected vs actual

- Expected: Both calls return `ScalarQuantizer{minQuantile=-0.96047044, maxQuantile=0.957112, bits=4}`.
- Actual: The second call returns `ScalarQuantizer{minQuantile=-0.9538363, maxQuantile=0.9618902, bits=4}`, the grid's first pair.

## Files

- [src/TestAutoIntervalTiedNeighbourScores.java](src/TestAutoIntervalTiedNeighbourScores.java) (JUnit test)

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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestAutoIntervalTiedNeighbourScores
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestAutoIntervalTiedNeighbourScores.java](TestAutoIntervalTiedNeighbourScores.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
