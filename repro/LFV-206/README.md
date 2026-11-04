# Polygon2D.containsLine/containsTriangle accept a segment or triangle whose vertices lie on the boundary of a non-convex polygon even when it passes outside between them, so LatLonShape/XYShape WITHIN queries return false positives

Finding LFV-206 · Area: geo / shape queries (Polygon2D containsLine/containsTriangle, LatLonShape and XYShape WITHIN) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-206.html](../../bugs/LFV-206.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a line or polygon whose corner points lie exactly on the boundary of a non-convex (dented) query polygon, for example a road between two corners of a district, and search for shapes WITHIN that polygon; you expect only shapes that lie inside it. Polygon2D.containsLine/containsTriangle accept a segment or triangle when every vertex is contained, with boundary points counting as inside, and no polygon edge crosses it strictly; that shortcut is only valid for convex polygons, so a segment that cuts across a dent between two boundary points, or a triangle that fills the dent and whose interior lies outside, is reported as contained. The WITHIN query returns those documents with no error (repro: for the polygon (0,0),(4,0),(4,4),(2,1),(0,4), WITHIN returns the indexed segment (0,4)-(4,4) and the indexed notch triangle (0,4),(4,4),(2,1) although (2,4) and (2,3) are outside the polygon).

## Expected vs actual

- Expected: WITHIN returns 0 documents: the segment's midpoint (2,4) and the triangle's centre (2,3) are outside the polygon.
- Actual: WITHIN returns 2 documents, the segment and the notch triangle (`expected:<0> but was:<2>`), with no error.

## Files

- [src/org/apache/lucene/document/TestPolygonWithinSharedVertices.java](src/org/apache/lucene/document/TestPolygonWithinSharedVertices.java) (JUnit test)
- [src/WithinSharedVerticesMain.java](src/WithinSharedVerticesMain.java) (main() class)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the finding reproducing.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestPolygonWithinSharedVertices

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" WithinSharedVerticesMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestXYShapeWithinNotchedPolygon.java](TestXYShapeWithinNotchedPolygon.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
