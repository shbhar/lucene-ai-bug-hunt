# GeoUtils.lineCrossesLineWithBoundary treats collinear segments that do not overlap as crossing, and EdgeTree.crossesTriangle and crossesLine (as withinTriangle calls it) let such pairs through, so LatLonShape/XYShape polygon and line INTERSECTS queries match disjoint shapes, DISJOINT queries drop them, and CONTAINS queries drop polygons that contain the query shape and return disjoint ones

Finding LFV-215 · Area: geo / shape queries (GeoUtils.lineCrossesLineWithBoundary, EdgeTree.crossesTriangle, Polygon2D and Line2D intersectsTriangle, EdgeTree.crossesLine, Polygon2D and Line2D withinTriangle; LatLonShape and XYShape polygon and line queries) · Priority: low · Impact: silent wrong results · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a polygon with LatLonShape or XYShape and search with a polygon or line using INTERSECTS (or DISJOINT); you expect a match only when the two shapes share at least one point. When one of the indexed shape's triangles has an edge on exactly the same straight line as a query edge, but further along that line so the two segments do not overlap, and the triangle's third corner reaches back over the query edge, Lucene reports a crossing: GeoUtils.lineCrossesLineWithBoundary answers true for any two collinear segments, and EdgeTree.crossesTriangle only pre-filters by the triangle's bounding box. INTERSECTS then returns the disjoint document and DISJOINT drops it, with no error (repro: query square [0,2]x[0,2] and indexed triangle (3,0),(4,0),(1.5,4), separated by the line x + 0.375y = 3; a box query over the same square answers correctly). A polygon or line CONTAINS query goes wrong on the same predicate, through EdgeTree.crossesLine as Polygon2D.withinTriangle and Line2D.withinTriangle call it: it drops an indexed polygon that contains the query shape (the same square, or the line (0,0)-(2,0), inside the polygon (-1,-1), (3,-1), (3,0), (4,0), (-1,10): 0 hits instead of 1, in every writing of the polygon's ring) and returns a disjoint one (the polygon (3,0), (3.5,-1.5), (4,0), (1.5,4): 1 hit instead of 0); a box query answers both correctly.

## Expected vs actual

- Expected: You expect a match only when the two shapes share at least one point.
- Actual: While the reported behaviour is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/document/TestIntersectsCollinearDisjointEdge.java](src/org/apache/lucene/document/TestIntersectsCollinearDisjointEdge.java) (JUnit test)
- [src/IntersectsCollinearMain.java](src/IntersectsCollinearMain.java) (main() class)

## Run it

- Tested on Lucene main 1ae70c6e45b8 (a commit of 2026-11-19): still reproduces.
- Tested on Lucene main ea57360912ed (a commit of 2026-10-24): reproduced.
- What this folder covers: the two files test the INTERSECTS cases (a polygon query and a line query that match a disjoint shape). The CONTAINS cases named above have no test in this folder. Run on 2026-11-17 on Lucene main 9965d34e3c77 and on the 11.0 release branch (branch_11x 9d2b5a15427a), with and without assertions: TestIntersectsCollinearDisjointEdge reports 5 tests found and 4 tests failed, and IntersectsCollinearMain exits non-zero.
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
     execute --select-class org.apache.lucene.document.TestIntersectsCollinearDisjointEdge

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" IntersectsCollinearMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
