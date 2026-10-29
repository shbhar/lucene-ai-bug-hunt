# Polygon2D.intersectsLine/intersectsTriangle never test the query polygon's hole edges, so LatLonShape/XYShape INTERSECTS misses, and DISJOINT matches, a line or polygon whose vertices all lie in holes but which crosses the polygon between them

Bug LFV-698 · Area: geo / shape queries (Polygon2D intersectsLine/intersectsTriangle; LatLonShape and XYShape INTERSECTS and DISJOINT with a query polygon that has holes) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-698.html](../../bugs/LFV-698.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a line or polygon with LatLonShape or XYShape and search with a query polygon that has holes, for example a region with two lakes cut out; a line from one lake to the other crosses the land between them, so INTERSECTS should return it and DISJOINT should not. Polygon2D.intersectsLine/intersectsTriangle report an intersection only when a vertex of the indexed shape is inside the polygon (holes excluded) or one of its edges crosses the outer ring; they never test the hole edges, so when every vertex lies in a hole the shape counts as not intersecting. The query returns the wrong documents with no error (repro: the square [0,10]^2 with holes [1,3]x[4,6] and [7,9]x[4,6]; of the line (2,5)-(8,5), the box [2,8]x[4.5,5.5] and the point (5,5), INTERSECTS returns only the point and DISJOINT returns the line and the box).

The test shows that an XYShape INTERSECTS query with a polygon that has two holes misses a line whose end points lie in the holes but which crosses the polygon between them (Polygon2D.intersectsLine never tests the hole edges).

## Expected vs actual

Expected count 1 for the line (2,5)-(8,5) against the square [0,10]^2 minus [1,3]x[4,6] and [7,9]x[4,6]; actual 0 (`expected:<1> but was:<0>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/document/TestXYShapeLineBetweenHoles.java](src/org/apache/lucene/document/TestXYShapeLineBetweenHoles.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.TestXYShapeLineBetweenHoles
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
