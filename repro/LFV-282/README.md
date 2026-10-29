# Geo3DPoint.newPolygonQuery turns a degenerate polygon (for example three vertices on the equator) into an empty GeoCompositePolygon that is meant to match nothing, but every search with the query throws NullPointerException; Geo3DPoint.newShapeQuery with an empty GeoCompositePolygon fails the same way

Bug LFV-282 · Area: spatial3d / Geo3DPoint polygon and shape queries (Geo3DUtil.fromPolygon, PointInGeo3DShapeQuery, PointInShapeIntersectVisitor) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build Geo3DPoint.newPolygonQuery from an org.apache.lucene.geo.Polygon that Polygon accepts but whose vertices all lie on one great circle, for example lat {0,0,0,0} and lon {0,1,2,0}, and expect a query that matches nothing: Geo3DUtil.fromPolygon recognizes the polygon as degenerate ('// Polygon is degenerate') and deliberately replaces it with an empty GeoCompositePolygon. Instead every search with the query throws NullPointerException ('Cannot invoke Double.doubleValue because the return value of XYZBounds.getMinimumX is null'), because the empty shape adds nothing to its XYZBounds and the PointInShapeIntersectVisitor constructor unboxes the null bounds. Geo3DPoint.newShapeQuery with an empty GeoCompositePolygon fails the same way, while LatLonPoint.newPolygonQuery returns 0 hits for the same Polygon and newLargePolygonQuery rejects it with IllegalArgumentException when the query is built.

The test shows that `Geo3DPoint.newPolygonQuery` for a legal Polygon whose vertices all lie on the equator (lat {0,0,0,0}, lon {0,1,2,0}) builds an empty GeoCompositePolygon, and searching with that query throws.

## Expected vs actual

Expected `count == 0`; actual `NullPointerException` (`XYZBounds.getMinimumX()` is null) at `PointInShapeIntersectVisitor.<init>:48` (-ea and -da).

## Files

- [src/org/apache/lucene/spatial3d/TestGeo3DDegeneratePolygonQuery.java](src/org/apache/lucene/spatial3d/TestGeo3DDegeneratePolygonQuery.java) (JUnit test)

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
     execute --select-class org.apache.lucene.spatial3d.TestGeo3DDegeneratePolygonQuery
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
