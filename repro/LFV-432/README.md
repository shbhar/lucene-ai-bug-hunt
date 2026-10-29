# RandomSamplingFacetsCollector never samples the first hit of a segment that starts mid-bin: createSample does not handle randomIndex == counter at the end of a segment, so a sampling bin that crosses a segment boundary loses its sample (or samples at a stale offset)

Bug LFV-432 · Area: facet / sampling (RandomSamplingFacetsCollector.createSample, cross-segment bin carry-over) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You count facets with RandomSamplingFacetsCollector on a multi-segment index for a query with many more hits than sampleSize, and expect systematic sampling: one random hit out of every run of binSize consecutive hits, with a run that is cut by a segment boundary continued in the next segment, so every hit has about a 1/binSize chance to be sampled. Instead, when the hit picked for such a run is the first hit of the next segment, createSample leaves its carry-over state unset (it tests only randomIndex &gt; counter and randomIndex &lt; counter), so that run contributes no sample, or one at a stale offset, and the first hit of a segment that starts mid-bin is never sampled. Nothing fails; the sample and the amortized counts are just smaller than the reported sampling rate implies (4 hits in segments [1 | 3], sampleSize 2: half the seeds sample 1 hit instead of 2).

The test shows that `RandomSamplingFacetsCollector` never samples the first hit of a segment that starts mid-bin: 4 hits in segments [1 | 3], sampleSize 2 (bins {0,1} {2,3}), sampler seed 1 picks hit 1 for bin {0,1}, and that bin gets no sample.

## Expected vs actual

Expected 2 sampled hits (one per bin). Actual: 1, because `createSample` handles only `randomIndex > counter` and `< counter` at the end of a segment, not `==`.

## Files

- [src/org/apache/lucene/facet/TestRandomSamplingSegmentBoundary.java](src/org/apache/lucene/facet/TestRandomSamplingSegmentBoundary.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.TestRandomSamplingSegmentBoundary
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
