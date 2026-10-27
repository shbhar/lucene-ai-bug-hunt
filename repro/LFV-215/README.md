# GeoUtils.lineCrossesLineWithBoundary treats collinear segments that do not overlap as crossing and EdgeTree.crossesTriangle lets such pairs through, so LatLonShape/XYShape polygon and line INTERSECTS queries match disjoint shapes and DISJOINT queries drop them

Bug LFV-215 · Area: geo / shape queries (GeoUtils.lineCrossesLineWithBoundary, EdgeTree.crossesTriangle, Polygon2D and Line2D intersectsTriangle; LatLonShape and XYShape polygon and line queries) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a polygon with LatLonShape or XYShape and search with a polygon or line using INTERSECTS (or DISJOINT); you expect a match only when the two shapes share at least one point. When one of the indexed shape's triangles has an edge on exactly the same straight line as a query edge, but further along that line so the two segments do not overlap, and the triangle's third corner reaches back over the query edge, Lucene reports a crossing: GeoUtils.lineCrossesLineWithBoundary answers true for any two collinear segments, and EdgeTree.crossesTriangle only pre-filters by the triangle's bounding box. INTERSECTS then returns the disjoint document and DISJOINT drops it, with no error (repro: query square [0,2]x[0,2] and indexed triangle (3,0),(4,0),(1.5,4), separated by the line x + 0.375y = 3; a box query over the same square answers correctly).

## Expected vs actual

- Expected: You expect a match only when the two shapes share at least one point.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/document/TestIntersectsCollinearDisjointEdge.java](src/org/apache/lucene/document/TestIntersectsCollinearDisjointEdge.java) (JUnit test)
- [src/IntersectsCollinearMain.java](src/IntersectsCollinearMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.document.TestIntersectsCollinearDisjointEdge

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" IntersectsCollinearMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
