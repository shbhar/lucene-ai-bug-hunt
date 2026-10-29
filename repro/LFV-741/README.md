# On a zero-width geo3d path (GeoDegeneratePath), computePathCenterDistance with DistanceStyle.LINEAR_SQUARED or NORMAL_SQUARED returns the unsquared distance and computeOutsideDistance returns the fourth power, instead of the squared distance, because the 2022 path refactor converted the path components to aggregation form but not the two places that combine them (regression since 9.5.0)

Bug LFV-741 · Area: spatial3d / geom path distances (GeoDegeneratePath, the zero-width path; DistanceStyle aggregation forms) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a zero-width path with GeoPathFactory.makeGeoPath(planetModel, 0, points) and call computePathCenterDistance or computeOutsideDistance with DistanceStyle.LINEAR_SQUARED (or NORMAL_SQUARED), expecting the square of the LINEAR (or NORMAL) distance, which is what DistanceStyle's 'convert to aggregation form, aggregate, convert back' contract gives, what the same path with width 1e-6 returns and what Lucene 9.4.2 returned. Instead, for a point 0.01 rad beside the path, computePathCenterDistance returns about 0.01 (the unsquared distance) and computeOutsideDistance returns about 1e-8 (the fourth power) where about 1e-4 is expected. The cause is an incomplete edit in the 2022 path refactor: GeoDegeneratePath's components now return the square root (the aggregation form of the squared styles), but computePathCenterDistance never squares the minimum back, and PathSegment.outsideDistance still returns the plain square, which outsideDistance then squares a second time.

The test shows that on a zero-width geo3d path (`GeoPathFactory.makeGeoPath(SPHERE, 0, points)`) the squared distance style `LINEAR_SQUARED` is not the square of `LINEAR`: `computePathCenterDistance` returns the unsquared distance and `computeOutsideDistance` the fourth power.

## Expected vs actual

Expected `LINEAR^2` = 9.9999e-5 from both calls; actual on main `computePathCenterDistance(LINEAR_SQUARED)` = 0.0099999 (`expected:<9.999916666944443E-5> but was:<0.009999958333385416>`), and, once that site is fixed, `computeOutsideDistance(LINEAR_SQUARED)` = 9.9998e-9.

## Files

- [src/org/apache/lucene/spatial3d/geom/TestGeoDegeneratePathSquaredStyles.java](src/org/apache/lucene/spatial3d/geom/TestGeoDegeneratePathSquaredStyles.java) (JUnit test)

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
     execute --select-class org.apache.lucene.spatial3d.geom.TestGeoDegeneratePathSquaredStyles
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
