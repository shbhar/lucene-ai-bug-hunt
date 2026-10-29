# GeoPath.computeDistance with DistanceStyle.LINEAR_SQUARED or NORMAL_SQUARED on a path of width &gt; 0 (GeoStandardPath) returns the fourth power of the distance instead of its square for points inside a path segment, because the segment's result is converted back from aggregation form twice (regression since 9.5.0)

Bug LFV-271 · Area: spatial3d / geom path distances (GeoStandardPath component tree: PathSegment, BaseSegmentEndpoint, PathNode; DistanceStyle aggregation forms) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a path with GeoPathFactory.makeGeoPath and a width &gt; 0 and call computeDistance with DistanceStyle.LINEAR_SQUARED (or NORMAL_SQUARED), expecting the square of the LINEAR (or NORMAL) path distance, which is what DistanceStyle's 'convert to aggregation form, aggregate, convert back' contract gives and what end-cap points, the zero-width path and Lucene 9.4.2 return. For a point inside a path segment you get the square of that value instead: 0.00803 rather than 0.0896 for a point whose LINEAR distance is 0.299, so the value jumps about 6x across a segment's end cutoff plane and can rank a farther point ahead of a nearer one. The cause is a leftover conversion from the 2022 path refactor: PathSegment.distance already converts its result back from aggregation form, and GeoStandardPath.distance converts the component tree's result back a second time.

The test shows that `GeoPath.computeDistance(DistanceStyle.LINEAR_SQUARED, p)` on a width-0.1 path (a `GeoStandardPath`) for a point inside the path segment.

## Expected vs actual

Expected: LINEAR^2 = 0.0896. Actual (-ea and -da): 0.00803 = LINEAR^4, the result is converted back from aggregation form twice.

## Files

- [src/org/apache/lucene/spatial3d/geom/TestGeoPathSquaredDistance.java](src/org/apache/lucene/spatial3d/geom/TestGeoPathSquaredDistance.java) (JUnit test)

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
     execute --select-class org.apache.lucene.spatial3d.geom.TestGeoPathSquaredDistance
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
