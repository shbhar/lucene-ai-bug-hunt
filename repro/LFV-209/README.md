# Geo3DPoint distance queries with a radius below about 1 km silently return wrong results in both directions: each document's decoded point lies a few millimetres off the planet surface and is tested against a circle plane nearly tangent to the surface, so a 10 m query misses documents at its own centre and matches documents about 100 m away

Bug LFV-209 · Area: spatial3d / Geo3DPoint shape queries (PointInShapeIntersectVisitor per-document test against GeoStandardCircle and other cap-shaped boundaries) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index locations as Geo3DPoint on the default WGS84 model and run Geo3DPoint.newDistanceQuery with a small radius, say 10 m, expecting the points within 10 m (the javadoc allows up to 0.5% error). Instead the query misses about half of the points inside the radius, even a document located exactly at the query centre, and returns points about 100 m away, while LatLonPoint gives the right answer on the same data. The cause is that each document's stored x/y/z is decoded to a point a few millimetres off the planet surface, and that point is tested against the circle's cutting plane, which for a small circle meets the surface at a grazing angle and turns millimetres into 100-190 m.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The query misses about half of the points inside the radius, even a document located exactly at the query centre, and returns points about 100 m away, while LatLonPoint gives the right answer on the same data.

## Files

- [src/org/apache/lucene/spatial3d/TestGeo3DSmallDistanceQuery.java](src/org/apache/lucene/spatial3d/TestGeo3DSmallDistanceQuery.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial3d.TestGeo3DSmallDistanceQuery
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/spatial3d-geometry/](../../models/spatial3d-geometry/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
