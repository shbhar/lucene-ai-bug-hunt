# LatLonPoint.newDistanceFeatureQuery can skip a competitive document under top-k pruning: computeMaxDistance returns the lower end of its 1 m binary search, and the pruning box built from that radius adds only a 7 cm pad

Bug LFV-379 · Area: core / document: LatLonPoint distance feature query (dynamic pruning through setMinCompetitiveScore) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index points with LatLonPoint plus LatLonDocValuesField and run searcher.search(LatLonPoint.newDistanceFeatureQuery("loc", 1, 0, 0, 1000), 1), expecting the closest document, because Scorable#setMinCompetitiveScore lets a scorer skip only documents that score below the threshold and Lucene's own TestLatLonPointDistanceFeatureQuery asserts that pruned (TOP_SCORES) and unpruned (COMPLETE) top-k agree for this query. Instead, once a document 999.999 m due north (score 0.50000024) fills the queue, a document 999.803 m due north (score 0.5000492) is never visited and the farther one is returned. The scorer turns the threshold into a radius with a binary search that stops when the interval is 1 m wide and keeps the lower end (999.729 m, although every distance up to 999.999 m still competes), and the box it then searches adds only 7 cm to that radius, so competitive documents just beyond the box edge are dropped.

The test shows that top-k pruning in LatLonPoint.newDistanceFeatureQuery skips a closer, higher-scoring document: the 1 m bisection keeps the lower end of its interval and the pruning box adds only a 7 cm pad.

## Expected vs actual

Expected `search(newDistanceFeatureQuery("loc", 1, 0, 0, 1000), 1)` to return the last doc (999.803 m due north); actual doc 0 (999.999 m due north) (`expected:<2001> but was:<0>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/document/TestLatLonDistanceFeatureMinCompetitiveGap.java](src/org/apache/lucene/document/TestLatLonDistanceFeatureMinCompetitiveGap.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.TestLatLonDistanceFeatureMinCompetitiveGap
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/distance-feature-pruning/](../../models/distance-feature-pruning/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
