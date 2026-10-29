# Geo3D circles with a radius between about 2e-5 m and 12 cm take the inside of their cut plane from the sign of rounding noise, so Geo3DPoint.newDistanceQuery with such a radius can match every point document on the planet

Bug LFV-212 · Area: spatial3d / geom circles (GeoStandardCircle cut plane and SidedPlane sidedness, behind Geo3DPoint.newDistanceQuery and GeoCircleFactory.makeGeoCircle) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a Geo3D distance query with a very small radius, for example Geo3DPoint.newDistanceQuery with 1.24 cm, and expect it to match only points near the centre, or to be rejected as too small. The circle's cut plane passes within about r^2/2 of the circle's centre (here about 2e-18), far below double rounding, and SidedPlane takes the plane's inside from the sign of evaluate(center), which at that size is rounding noise. When the noise has the wrong sign the circle is the whole planet minus a speck, and the query silently returns every indexed point: in the repro, all 3 documents thousands of kilometres away.

The test shows that `Geo3DPoint.newDistanceQuery` with a 1.24 cm radius (WGS84) builds a circle whose cut plane faces the wrong way, so the query matches a document at the centre's antipode.

## Expected vs actual

Expected 0 hits for the antipode; actual: 1 (`hits for the antipode expected:<0> but was:<1>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/spatial3d/TestGeo3DTinyRadiusMatchesWholeWorld.java](src/org/apache/lucene/spatial3d/TestGeo3DTinyRadiusMatchesWholeWorld.java) (JUnit test)

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
     execute --select-class org.apache.lucene.spatial3d.TestGeo3DTinyRadiusMatchesWholeWorld
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
