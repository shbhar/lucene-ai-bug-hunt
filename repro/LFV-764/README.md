# XYShape point queries can miss a polygon at one of its own vertices: Component2D.pointInTriangle rejects the second vertex (b) of a clockwise triangle, and ShapeField.encodeTriangle makes XY triangles counter-clockwise only on their sortable-int encodings, so a stored triangle can decode clockwise; an INTERSECTS point query at that vertex misses the polygon and DISJOINT returns it (since 8.5.0)

Bug LFV-764 · Area: geo / XYShape point queries (Component2D.pointInTriangle; ShapeField.encodeTriangle orientation for XY) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-764.html](../../bugs/LFV-764.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index the clockwise triangle (0,1) (1,2) (3,3) as an XYPolygon with XYShape.createIndexableFields and run XYShape.newPointQuery at each of its corners; you expect every corner to match the polygon (INTERSECTS) and none to be DISJOINT from it, as happens for the same triangle moved by (8,8) and for every point of its edges. Instead the INTERSECTS query at (1,2) finds nothing and the DISJOINT query at (1,2) returns the document: ShapeField.encodeTriangle checks the winding on the sortable-int encodings of the floats, which are ordered like the floats but not evenly spaced, so the stored triangle decodes clockwise, and Component2D.pointInTriangle reports the second vertex of a clockwise triangle as outside. The same happens with projected metre-like coordinates, for example at vertex (518885,4395432) of a hexagon whose x range crosses 2^19.

The test shows that an `XYShape` INTERSECTS point query placed exactly on vertex (1,2) of the indexed clockwise triangle (0,1) (1,2) (3,3) does not find the polygon.

## Expected vs actual

Expected: 1 hit (a polygon's vertices belong to it). Actual: 0 hits.

## Files

- [src/org/apache/lucene/document/TestXYShapePointQueryAtPolygonVertex.java](src/org/apache/lucene/document/TestXYShapePointQueryAtPolygonVertex.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestXYShapePointQueryAtPolygonVertex
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
