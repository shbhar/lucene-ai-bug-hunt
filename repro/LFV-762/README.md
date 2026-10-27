# Tessellator: when three or more holes meet at one vertex P, maybeMergeHoleWithSharedVertices joins the hole whose left-most vertex is P to the first copy of P in ring order (since #15700) even when the hole lies outside that copy's wedge, so LatLonShape and XYShape reject a valid polygon with 'Unable to Tessellate shape' depending on the order of its holes; upstream's own LUCENE-9400 test polygon is rejected in 3 of its 6 hole orders (regression in 10.4.0)

Bug LFV-762 · Area: geo / Tessellator hole elimination (polygon indexing for LatLonShape and XYShape) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a valid polygon whose three holes meet only at one corner P, for example the triangle (0,0) (7,12) (12,2) with the holes A (8,8) (6,7) (7,6), B (9,3) (10,4) (7,6) and C (6,6) (6,4) (7,4) (7,6), which all touch at P=(7,6) (the Tessellator javadoc allows 'Holes may only touch at one vertex'), and you expect XYShape.createIndexableFields or LatLonShape.createIndexableFields to return its triangles, as 10.3.x does for every hole order. Since #15700 (first released in 10.4.0), once the first holes are joined at P the ring holds several copies of P, each bounding a different wedge around P, and when hole B (whose left-most vertex is P) is joined, the Tessellator takes the first copy of P in ring order, through an index comparison that depends on the input order of the holes, without checking that B lies in that copy's wedge. For the hole orders ABC, ACB and BAC this picks a wrong copy and both calls throw IllegalArgumentException 'Unable to Tessellate shape', and upstream's own LUCENE-9400 regression polygon (TestTessellator.testComplexPolygon41) is likewise rejected in 3 of its 6 hole orders.

## Expected vs actual

- Expected: You expect XYShape.createIndexableFields or LatLonShape.createIndexableFields to return its triangles, as 10.3.x does for every hole order.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/document/TestTessellatorThreeHolesSharedVertex.java](src/org/apache/lucene/document/TestTessellatorThreeHolesSharedVertex.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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
     execute --select-class org.apache.lucene.document.TestTessellatorThreeHolesSharedVertex
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
