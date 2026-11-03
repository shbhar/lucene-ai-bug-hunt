# BBoxStrategy and PointVectorStrategy miss a shape on a query edge at 0 when the shape's coordinate and the edge are zeros of opposite sign (-0.0 vs +0.0), so BBoxStrategy finds a point Intersecting a rectangle but not Within it

Finding LFV-1113 · Area: spatial-extras / BBoxStrategy and PointVectorStrategy query construction (inclusive DoublePoint range bounds taken unchanged from spatial4j rectangles: BBoxStrategy.makeNumericRangeQuery, PointVectorStrategy.rangeQuery) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1113.html](../../bugs/LFV-1113.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a point stored at longitude -0.0 (for example parsed from 'POINT(-0 5)') with BBoxStrategy and PointVectorStrategy and query the rectangle Rect(minX=0.0, maxX=10, minY=0, maxY=10); you expect the point to match Intersects and IsWithin in both strategies, because SpatialOperation.evaluate says it lies on the rectangle's west edge (spatial4j compares coordinates as primitives, so -0.0 == 0.0). Instead BBoxStrategy finds it for Intersects but not for IsWithin, and PointVectorStrategy finds it for neither, with no error. Both strategies pass the rectangle's 0.0 edge unchanged as an inclusive DoublePoint range bound, and DoublePoint orders -0.0 below +0.0; BBoxStrategy's Intersects uses only exclusive bounds, which it moves with Math.nextUp/Math.nextDown, and those give the same result for both zeros.

## Expected vs actual

- Expected: `[1, 1, 1, 1]`: the point is on the rectangle's west edge, so all four queries find it.
- Actual: `[1, 0, 0, 0]`, no error: `expected:<[1, [1, 1, 1]]> but was:<[1, [0, 0, 0]]>`, with assertions on and off.

## Files

- [src/org/apache/lucene/spatial/TestNegativeZeroOnZeroEdge.java](src/org/apache/lucene/spatial/TestNegativeZeroOnZeroEdge.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/spatial-extras/build/libs/lucene-spatial-extras-$V.jar
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.TestNegativeZeroOnZeroEdge
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestNegativeZeroOnZeroEdge.java](TestNegativeZeroOnZeroEdge.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
