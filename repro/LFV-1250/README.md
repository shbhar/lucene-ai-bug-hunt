# Tessellator self-intersection check misses crossings in polygons with more than 80 points, so LatLonShape/XYShape.createIndexableFields with checkSelfIntersections=true accept and index a self-crossing polygon

Finding LFV-1250 · Area: geo / Tessellator self-intersection check (z-order path above 80 points; LatLonShape and XYShape with checkSelfIntersections=true) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1250.html](../../bugs/LFV-1250.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index the self-crossing ring (x y) 5 2, 3 2, 6 0, 9 9, 7 5, 1 1 with XYShape.createIndexableFields("f", polygon, true), written with 74 extra points on its straight edge from (5,2) to (3,2), so 81 points in all. You expect the IllegalArgumentException 'Polygon self-intersection at ...' that the same call throws for the 7-point and the 80-point forms of the same ring, because the javadoc says that with checkSelfIntersections the validity of the polygon is checked. Instead the call returns triangle fields and the self-crossing polygon is indexed: above 80 points the Tessellator runs the check on its z-order (Morton) path, which never uses the two edges at the start vertex as primary edges and finds other edges only when their start vertex lies in the primary edge's bounding box, so it does not compare every pair of edges.

## Expected vs actual

- Expected: `IllegalArgumentException: Polygon self-intersection at lat=1.6363636363636365 lon=3.5454545454545454`, as for the same ring with 7 or 80 points.
- Actual: No exception: the call returns 4 triangle fields, the same as with the check off (`Expected exception IllegalArgumentException but no exception was thrown`).

## Files

- [src/org/apache/lucene/geo/TestTessellatorSelfIntersectionOver80Points.java](src/org/apache/lucene/geo/TestTessellatorSelfIntersectionOver80Points.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.geo.TestTessellatorSelfIntersectionOver80Points
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestTessellatorSelfIntersectionOver80Points.java](TestTessellatorSelfIntersectionOver80Points.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
