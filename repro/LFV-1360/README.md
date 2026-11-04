# Geo3DPoint.newDistanceQuery accepts a negative radius (for example -1 m) instead of throwing the IllegalArgumentException its javadoc promises, and silently builds the radius-0 query, while LatLonPoint.newDistanceQuery rejects the same radius

Finding LFV-1360 · Area: spatial3d / Geo3DPoint distance-query factory (argument validation: Geo3DUtil.fromDistance, GeoCircleFactory.makeGeoCircle) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1360.html](../../bugs/LFV-1360.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call Geo3DPoint.newDistanceQuery("f", PlanetModel.WGS84, 0, 0, -1) with a negative radius, for example a computed radius that went below zero, and expect the IllegalArgumentException its javadoc promises (radiusMeters 'must be non-negative and finite'; '@throws IllegalArgumentException if ... radius is invalid'), which LatLonPoint.newDistanceQuery throws for the same value ("radiusMeters: '-1.0' is invalid"). Instead no exception is thrown: Geo3DUtil.fromDistance checks only the latitude and longitude, and GeoCircleFactory.makeGeoCircle turns every angle below its resolution threshold, negative ones included, into a GeoDegeneratePoint at the centre before GeoStandardCircle's own 'Cutoff angle out of bounds' check can run. The query you get is equal() to the radius-0 query, so the caller's mistake silently becomes an (almost) empty result.

## Expected vs actual

- Expected: `Geo3DPoint.newDistanceQuery("f", PlanetModel.WGS84, 0, 0, -1)` throws `IllegalArgumentException`, as its javadoc says and as `LatLonPoint` does.
- Actual: Nothing is thrown (`Expected exception IllegalArgumentException but no exception was thrown`): the call returns the radius-0 query.

## Files

- [src/org/apache/lucene/spatial3d/TestGeo3DDistanceQueryNegativeRadius.java](src/org/apache/lucene/spatial3d/TestGeo3DDistanceQueryNegativeRadius.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial3d.TestGeo3DDistanceQueryNegativeRadius
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestGeo3DDistanceQueryNegativeRadius.java](TestGeo3DDistanceQueryNegativeRadius.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
