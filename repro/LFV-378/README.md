# Geo3d reports two shapes that touch at a single point as nested (WITHIN/CONTAINS) instead of overlapping when the touch point is a circle's one sampled edge point, so IsWithin and Contains spatial queries (SerializedDVStrategy, CompositeSpatialStrategy) return circles that lie mostly outside the query shape

Bug LFV-378 · Area: spatial3d / geom shape relationships (GeoBaseAreaShape.getRelationship with GeoStandardCircle's single edge point and Plane.intersects' tangency test), reached through the spatial-extras Geo3d spatial4j adapter (Geo3dShape.relate, SpatialOperation IsWithin/Contains, SerializedDVStrategy, CompositeSpatialStrategy) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create a circle and a box (or a second circle) that touch at exactly one point, for example with the Geo3d spatial context a 20-degree circle centred at latitude 55 and a box whose southern edge is latitude 75, and expect relate to report INTERSECTS (shapes that share a point intersect, and spatial3d may only weaken WITHIN to OVERLAPS, never the reverse). Instead it reports WITHIN or CONTAINS, as if one shape were inside the other: GeoBaseAreaShape.getRelationship infers nesting from sampled edge points plus a plane-intersection test, a standard circle has a single edge point, and when that point lies on the other shape's boundary the 1e-12 membership test counts it as inside while Plane.intersects' one-solution test misses the touch. So SerializedDVStrategy's IsWithin(box) returns the circle although its centre is 20 degrees outside the box, while radius 19.9 or 20.1 degrees gives the correct DISJOINT or INTERSECTS.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: It reports WITHIN or CONTAINS, as if one shape were inside the other: GeoBaseAreaShape.getRelationship infers nesting from sampled edge points plus a plane-intersection test, a standard circle has a single edge point, and when that point lies on the other shape's boundary the 1e-12 membership test counts it as inside while Plane.intersects' one-solution test misses the touch.

## Files

- [src/org/apache/lucene/spatial/spatial4j/TestGeo3dTangentShapesRelation.java](src/org/apache/lucene/spatial/spatial4j/TestGeo3dTangentShapesRelation.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/spatial-extras/build/libs/lucene-spatial-extras-$V.jar
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.spatial4j.TestGeo3dTangentShapesRelation
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
