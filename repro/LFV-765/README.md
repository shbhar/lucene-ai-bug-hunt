# RandomSamplingFacetsCollector.createManager samples the hits twice when a search has more than one slice: the reduce reads each slice's already-sampled getMatchingDocs as its original hits, so the sample is far too small and every amortized facet count is biased low (regression from #13542, 10.0.0 onward)

Bug LFV-765 · Area: facet / sampling (RandomSamplingFacetsCollector.createManager reduce, FacetsCollectorManager.reduceMatchingDocs) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-765.html](../../bugs/LFV-765.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You count facets on a sample of a large result set with RandomSamplingFacetsCollector.createManager(sampleSize, seed) on an IndexSearcher that has an executor, and expect what a one-slice search gives: about sampleSize sampled hits, taken at the rate sampleSize / totalHits that getSamplingRate reports, so that amortizeFacetCounts estimates the real counts. Instead, with two or more slices the reduce fills the reduced collector's original hits from FacetsCollectorManager.reduceMatchingDocs, which calls each slice collector's getMatchingDocs, the accessor RandomSamplingFacetsCollector overrides to return that slice's sample; the reduced collector then samples those samples again. The sample shrinks to about sampleSize^2 x numSlices / totalHits and every amortized count is low by the per-slice ratio, with no error: 2 segments of 1,024 matching docs with sampleSize 128 and one slice per segment give 16 sampled hits and an amortized count of 256 instead of 128 and 2,048, and default slicing on 2 segments of 262,144 docs with sampleSize 4,096 gives 64 sampled hits and 8,192 instead of 4,096 and 524,288.

## Expected vs actual

- Expected: With several slices, the one-slice numbers: 128 sampled, 2,048 original hits and an amortized count of 2,048 (default slicing: 1,000, 20,000 and 20,000).
- Actual: 16 sampled, 256 original hits and an amortized count of 256 (default slicing: 100, 2,000 and 2,000; 4 partitions of 65,536: 64, 4,096 and 4,096), with `getSamplingRate` unchanged.

## Files

- [src/org/apache/lucene/facet/TestSamplingManagerReduceResamples.java](src/org/apache/lucene/facet/TestSamplingManagerReduceResamples.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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
     execute --select-class org.apache.lucene.facet.TestSamplingManagerReduceResamples
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestSamplingManagerSlices.java](TestSamplingManagerSlices.java)
- [TestSamplingManagerReduceResamples.java](TestSamplingManagerReduceResamples.java)
- [TestSamplingManagerMultiSlice.java](TestSamplingManagerMultiSlice.java)
- [TestSamplingReduceV3Check.java](TestSamplingReduceV3Check.java)
- [TestFactCheckMixedSlices.java](TestFactCheckMixedSlices.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
