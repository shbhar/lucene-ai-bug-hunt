# LatLonShape circle (distance) queries miss lines and polygons whose edge crosses the circle with no vertex inside, because Circle2D tests only the edge point closest to the centre in raw lon/lat degrees, not the point closest by haversine distance (DISJOINT matches them instead)

Finding LFV-1128 · Area: core / geo (Circle2D haversine segment test behind LatLonShape.newDistanceQuery) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

Finding page: [../../bugs/LFV-1128.html](../../bugs/LFV-1128.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a two-point LatLonShape line from (lat 60.38, lon 9.8) to (lat 59.78, lon 10.4) and run LatLonShape.newDistanceQuery with INTERSECTS and a 10 km circle around (lat 60, lon 10). The line passes 8.95 km from the centre, so you expect 1 hit, and the circle's own contains() accepts a point of the line; you get 0 hits and no error, and DISJOINT matches the line instead. When neither end is inside, Circle2D tests only the point of the segment closest to the centre in raw lon/lat degrees (here 11.19 km away), not the point closest by haversine distance, so diagonal edges that cross the outer part of the circle are missed.

## Expected vs actual

- Expected: 1 hit: the line passes 8.95 km from the centre of the 10 km circle.
- Actual: 0 hits, no error: `expected:<1> but was:<0>`, with assertions on and off.

## Files

- [src/org/apache/lucene/document/TestLatLonShapeCircleCrossingEdge.java](src/org/apache/lucene/document/TestLatLonShapeCircleCrossingEdge.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestLatLonShapeCircleCrossingEdge
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestLatLonShapeCircleCrossingEdge.java](TestLatLonShapeCircleCrossingEdge.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
