# geo3d path distance methods (GeoStandardPath, GeoDegeneratePath) and Geo3DDocValuesField.newPathSort throw RuntimeException "Can't find world intersection" for a point inside the path that sits at the edge of the 1e-12 tolerance band of a segment's cutoff plane

Bug LFV-742 · Area: spatial3d / geom path distances (GeoStandardPath and GeoDegeneratePath PathSegment foot selection; Geo3DDocValuesField.newPathSort) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a geo3d path (GeoPathFactory.makeGeoPath, or the path of Geo3DDocValuesField.newPathSort) and ask for the distance of a point that the path reports as inside it, expecting a number (GeoDistance.computeDistance documents a distance, or +Infinity outside the shape). Instead, when the point sits at the very edge of the 1e-12 tolerance band of a segment's cutoff plane, the call throws RuntimeException("Can't find world intersection..."): on the unit sphere a point 3.4e-6 rad beside a path of width 1e-4 and 1e-12 rad past its middle point throws instead of returning about 0.1000034, the same happens for a zero-width path's nearest and centre distance, and a 500 km path sort whose middle point sits at a stored document's decoded longitude makes IndexSearcher.search throw. The cause: the segment's tolerant section test accepts the point, then the same tolerant test rejects both rounded perpendicular feet of the point on the path, and the code throws instead of picking one.

The test shows that `GeoPath.computeDistance` throws for a point the path itself reports as within, when the point sits at the edge of the 1e-12 tolerance band of a segment's cutoff plane (ulp-chosen point on a SPHERE path).

## Expected vs actual

Expected an arc distance of about 0.1 + 3.4487e-6; actual `RuntimeException: Can't find world intersection for point...` from `GeoStandardPath$PathSegment.pathDistance` (-ea and -da).

## Files

- [src/org/apache/lucene/spatial3d/geom/TestGeoPathDistanceAtCutoffPlane.java](src/org/apache/lucene/spatial3d/geom/TestGeoPathDistanceAtCutoffPlane.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial3d.geom.TestGeoPathDistanceAtCutoffPlane
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
