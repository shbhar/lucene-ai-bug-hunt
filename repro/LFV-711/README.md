# LatLonShape/XYShape CONTAINS with a query polygon that has holes returns indexed shapes that do not contain it, because Polygon2D.withinTriangle never tests the query's hole edges

Bug LFV-711 · Area: geo / shape queries (Polygon2D.withinTriangle; LatLonShape and XYShape CONTAINS with a query polygon that has holes) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index polygons with LatLonShape or XYShape and ask which of them CONTAIN a query polygon that has holes, for example a region with two lakes cut out; an indexed polygon whose own hole runs across the land between the two lakes does not cover that land, so it must not match. Polygon2D.withinTriangle rejects an indexed triangle only when one of its vertices is inside the query or one of its real (original-shape) edges crosses the query's outer ring; it never tests the query's hole edges, so an edge that starts and ends inside query holes and crosses the land between them goes unnoticed and the shape is returned as CONTAINS, with no error. Repro: query = the square [0,10]^2 with holes [1,3]x[4,6] and [7,9]x[4,6]; doc 0 = the square [-1,11]^2 with a hole [2,8]x[4.5,5.5] (it does not cover the query point (5,5)), doc 1 = the same square without a hole; CONTAINS returns [0, 1] instead of [1] for XYShape and LatLonShape, while a point query at (5,5) correctly returns only doc 1.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/document/TestContainsQueryPolygonHoles.java](src/org/apache/lucene/document/TestContainsQueryPolygonHoles.java) (JUnit test)
- [src/ContainsQueryPolygonHolesMain.java](src/ContainsQueryPolygonHolesMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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
     execute --select-class org.apache.lucene.document.TestContainsQueryPolygonHoles

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" ContainsQueryPolygonHolesMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
