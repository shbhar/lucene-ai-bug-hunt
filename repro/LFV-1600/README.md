# Tessellator can return a triangle that lies over part of a hole of a valid polygon when a hole vertex is within rounding of an outer edge: for one such polygon LatLonShape indexes it without an error and a point well inside the hole matches

Finding LFV-1600 · Area: geo / Tessellator cure step and orientation test (polygon indexing for LatLonShape) · Priority: low · Impact: minor · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a polygon with one triangular hole through LatLonShape.createIndexableFields. The hole's left-most vertex lies just inside an outer edge, 8.5e-17 degrees of longitude from it (0.77 of one unit in the last place of that coordinate), and you expect a point in the middle of the hole, 0.11 degrees from the hole's nearest edge, to match nothing, as Lucene's own point-in-polygon test (LatLonGeometry) says for that point. Instead an INTERSECTS point query there matches the document, without an error and with either setting of checkSelfIntersections (run on main 1640b5c394de, branch_11x 8a72db074350 and the 10.6.0 release, with and without assertions): the Tessellator's step that cures small local self-intersections cuts off the triangle (1.5, 7.5) (-1.6, 4.3) (4.5, 6.8), which lies over part of the hole. The polygon is valid in exact arithmetic on its doubles and passes Lucene's own self-intersection check; it is also one last-place unit from a polygon whose hole vertex is outside the ring, Lucene refuses it ('Illegal hole detected', run on main) once every coordinate is encoded and decoded, and the same polygon with that vertex two last-place units above 0.53125 instead of one gets the right triangles.

## Expected vs actual

- Expected: You expect a point in the middle of the hole, 0.11 degrees from the hole's nearest edge, to match nothing, as Lucene's own point-in-polygon test (LatLonGeometry) says for that point.
- Actual: An INTERSECTS point query there matches the document, without an error and with either setting of checkSelfIntersections (run on main 1640b5c394de, branch_11x 8a72db074350 and the 10.6.0 release, with and without assertions): the Tessellator's step that cures small local self-intersections cuts off the triangle (1.5, 7.5) (-1.6, 4.3) (4.5, 6.8), which lies over part of the hole.

## Files

- [src/TestLatLonShapeHoleVertexNextToOuterEdge.java](src/TestLatLonShapeHoleVertexNextToOuterEdge.java) (JUnit test)

## Run it

- Tested on Lucene main 77d56fefb355 (a commit of 2026-11-25): reproduced on 2026-11-26, with assertions on and off.
- Tested on Lucene branch_11x 4e8fd3dc3bc4 (a commit of 2026-11-25): reproduced on 2026-11-26, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene main 8506598d7a98 (a commit of 2026-11-25): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 55ef56a69344 (a commit of 2026-11-25): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (a commit of 2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- The trigger is of hairline size: one vertex of the polygon's hole lies 8.5e-17 degrees of longitude inside an outer edge (the literal 0.5312500000000001 is the double one last-place unit above 0.53125), and the same test with that vertex one unit further in (0.5312500000000002) passes (run on main 1640b5c394de, with and without assertions); the polygon is valid in exact arithmetic on its doubles and is one last-place unit from a polygon whose hole vertex is outside the ring, and no rate for real data was measured. Not new in 11.0: the test fails the same way with the 10.6.0 release jars, on builds of branch_11x (55ef56a69344 and 4e8fd3dc3bc4) and of Lucene main (8506598d7a98 and 77d56fefb355), with and without assertions. Known outside Lucene in part: wrong triangulations that involve the cure routine are reported in the tracker of the library that the Tessellator's class notes name as the source of its algorithm, in mapbox/earcut#16 (a sensitivity to the last digits of one coordinate) and mapbox/earcut#121 ('a race condition' in that routine), both closed and both read on 2026-11-25; neither names this input, and the searches of 2026-11-25 found no report of it to Lucene. What the test does: it indexes one polygon (four outer vertices and one triangular hole, coordinates between -1.6 and 13.5) with LatLonShape.createIndexableFields and a default IndexWriterConfig, and counts an INTERSECTS point query at latitude 6.5, longitude 2.0, a point inside the hole (at longitude 2.0 the hole spans latitudes 6.26 to 6.61) and 0.11 degrees from the hole's nearest edge. It expects 0 and gets 1: java.lang.AssertionError: expected:&lt;0&gt; but was:&lt;1&gt;. Lucene throws nothing. The test is plain JUnit 4 with no randomness; the seed in the command is not read. On the older releases a first form of the test ran (a LuceneTestCase with one more assertion), unchanged or as a copy for the older API: it failed on each of the 13 releases on which it ran, from 7.5.0, the first release with the class, to 10.6.0. A branch_11x checkout builds 11.0.0-SNAPSHOT jars, so set V=11.0.0-SNAPSHOT there; for the release, set V=10.6.0 and use the three 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework) in place of the three $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/core: with the test copied to lucene/core/src/test/ it reports 1 test completed, 1 failed, with the same message, on branch_11x 55ef56a69344 and on main 8506598d7a98, with -Ptests.asserts=true and with -Ptests.asserts=false.
- This finding was re-run on Lucene main 82cf7465bb8f on 2026-11-26: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.AssertionError: expected:&lt;0&gt; but was:&lt;1&gt; (the point query inside the hole counts one document); Lucene throws no exception.
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

# run 1: assertions on, seed 80A9885
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=80A9885 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestLatLonShapeHoleVertexNextToOuterEdge
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

Formal models and scripts this finding's part of the hunt used: [../../models/tessellator-hole-vertex-near-outer-edge/](../../models/tessellator-hole-vertex-near-outer-edge/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
