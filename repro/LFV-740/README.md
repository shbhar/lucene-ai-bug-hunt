# geo3d NORMAL and NORMAL_SQUARED point distances are wrong on WGS84 and other ellipsoids because Vector.normalDistance assumes a unit-length vector, so the NORMAL distance from a point to itself is up to about 0.0045 planet units (0.00165, about 10.5 km, at latitude 0.3 rad) instead of 0

Bug LFV-740 · Area: spatial3d / geom distance styles (Vector.normalDistance[Squared]; DistanceStyle.NORMAL and NORMAL_SQUARED point-to-point distances in GeoCircle and GeoPath) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a geo3d circle or path on PlanetModel.WGS84 (or call Vector.normalDistance or DistanceStyle.NORMAL.computeDistance on two GeoPoints) and ask for the NORMAL or NORMAL_SQUARED distance, which Vector's javadoc defines as 'the normal (perpendicular) distance to a vector described by a vector taken from the origin', and expect 0 for a point and itself and about 7.85e-6 planet units for a point 50 m from a circle's centre. Instead both come out as 0.00165 (about 10.5 km) at latitude 0.3 rad, because Vector.normalDistanceSquared projects onto the receiver with t = dotProduct(v) and never divides by |this|^2, which is exact only for unit vectors, while WGS84 surface points have lengths between about 0.998 and 1.0011. The same offset enters every NORMAL and NORMAL_SQUARED circle and path distance on non-spherical planet models, with no error; on SPHERE the values are exact.

The test shows that geo3d `Vector.normalDistance` assumes a unit-length receiver, so on WGS84 the normal distance from a point to itself is not 0.

## Expected vs actual

Expected 0 for `p.normalDistance(p)` (WGS84 point at latitude 0.3 rad); actual `expected:<0.0> but was:<0.0016507737062451833>` (-ea and -da).

## Files

- [src/org/apache/lucene/spatial3d/geom/TestNormalDistanceOnEllipsoid.java](src/org/apache/lucene/spatial3d/geom/TestNormalDistanceOnEllipsoid.java) (JUnit test)

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
     execute --select-class org.apache.lucene.spatial3d.geom.TestNormalDistanceOnEllipsoid
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
