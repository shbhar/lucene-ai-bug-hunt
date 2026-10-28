# GeoStandardPath outside distance measures end caps (and one-point paths) to the end point instead of the cap edge, so values are too large by up to the path half-width and Geo3DDocValuesField.newOutsidePathSort misorders documents

Bug LFV-735 · Area: spatial3d / geom path outside distance (GeoStandardPath segment endpoints; Geo3DDocValuesField.newOutsidePathSort) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-735.html](../../bugs/LFV-735.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a path with a width (GeoPathFactory.makeGeoPath, or the pathWidthMeters of Geo3DDocValuesField.newOutsidePathSort) and ask for the outside distance of a point beyond one of its ends, expecting 'the distance from a point to the closest outside edge' (GeoOutsideDistance), which is how the same path measures points beside its segments and how GeoCircle measures a circle. Instead you get the distance to the path's end point, i.e. the gap plus up to the half-width: on the unit sphere a point 0.001 rad beyond the end of a path of half-width 0.01 gets 0.011, a one-point path reports 0.011 where the same-size GeoCircle reports 0.001, the value jumps from 0 to about the half-width as a point crosses the end cap's edge, and on WGS84 the outside-path sort ranks a document 1 km beyond the end of a path of half-width 5 km (5992 m) after one 3 km beside it (3067 m). The cause is BaseSegmentEndpoint.outsideDistance, which every endpoint with a width inherits: it measures to the end point itself, which is the edge only for a zero-width path.

## Expected vs actual

- Expected: The document 1 km past the end sorts first (about 1,000 m), the one-point path reports 0.001 like the circle, and 1e-6 rad outside the cap gives about 1e-6.
- Actual: 5 of 5 fail: sort values 3,067 m (beside) before 5,992 m (past the end); one-point path 0.011 against the circle's 0.001; 0.010001 just outside the cap; 0.011 beyond the end.

## Files

- [src/org/apache/lucene/spatial3d/TestGeo3DPathOutsideDistanceEndCap.java](src/org/apache/lucene/spatial3d/TestGeo3DPathOutsideDistanceEndCap.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial3d.TestGeo3DPathOutsideDistanceEndCap
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestGeoPathOutsideDistanceEndCap.java](TestGeoPathOutsideDistanceEndCap.java)
- [TestGeo3DPathOutsideDistanceEndCap.java](TestGeo3DPathOutsideDistanceEndCap.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
