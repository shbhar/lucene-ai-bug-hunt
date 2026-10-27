# PointVectorStrategy's distance value source gives a document without a point the distance 180 * multiplier even in a non-geo (Cartesian) SpatialContext, so an ascending distance sort lists it before real documents farther than 180 units and makeRecipDistanceValueSource scores it as if it were 180 units away (the other strategies that implement makeDistanceValueSource use Double.MAX_VALUE there; regression in 7.0.0 from LUCENE-7737)

Bug LFV-777 · Area: spatial-extras / PointVectorStrategy distance value source (vector/DistanceValueSource missing-document value; makeDistanceValueSource, makeRecipDistanceValueSource) · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index points with PointVectorStrategy in a non-geo SpatialContext (for example world bounds -1000..1000), add a document without a point, and sort a query by makeDistanceValueSource(point).getSortField(false), expecting the point-less document last, as BBoxStrategy, SerializedDVStrategy and the prefix-tree strategies do: in a non-geo context they give it Double.MAX_VALUE, the rule LUCENE-4208 set for all strategies ('180 if geo, Double.MAX_VALUE otherwise'). Instead it gets distance 180.0, so with a document at (500, 0) and the query point (0, 0) the ascending order is none,far instead of far,none, and makeRecipDistanceValueSource over the query box -1000..1000 scores it 0.440 against 0.220 for a real document 500 units away. The cause: vector/DistanceValueSource sets nullValue = 180 * multiplier with no isGeo branch; the branch was dropped in the 7.0 port from ValueSource to DoubleValuesSource (LUCENE-7737).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: It gets distance 180.0, so with a document at (500, 0) and the query point (0, 0) the ascending order is none,far instead of far,none, and makeRecipDistanceValueSource over the query box -1000..1000 scores it 0.440 against 0.220 for a real document 500 units away.

## Files

- [src/org/apache/lucene/spatial/vector/TestPointVectorMissingDistanceNonGeo.java](src/org/apache/lucene/spatial/vector/TestPointVectorMissingDistanceNonGeo.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/spatial-extras/build/libs/lucene-spatial-extras-$V.jar
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.vector.TestPointVectorMissingDistanceNonGeo
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
