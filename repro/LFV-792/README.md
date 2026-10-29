# Tessellator.cureLocalIntersections adds each triangle it cuts off twice, so LatLonShape and XYShape index that triangle twice and the shape doc-values centroid counts its area twice

Bug LFV-792 · Area: geo / Tessellator cure step (polygon indexing for LatLonShape and XYShape, shape doc values) · Priority: low · Impact: minor · Novelty: new

Bug page: [../../bugs/LFV-792.html](../../bugs/LFV-792.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a polygon with LatLonShape or XYShape (createIndexableFields or createDocValueField) and expect each triangle of its tessellation to be stored once. When the Tessellator's cure step (cureLocalIntersections) cuts off a triangle to repair a small local self-intersection, it appends that same triangle twice (two identical tessellation.add calls at Tessellator.java:890 and:892, left by LUCENE-8620 in 8.3.0), so the shape carries one extra indexed triangle, ShapeDocValuesField.numberOfTerms is one too high and getCentroid counts that triangle's area twice; query results do not change. On its own the cure step fires only for self-crossing rings, which the javadoc excludes but the default checkSelfIntersections=false path accepts (for example POLYGON((6 3, 0 6, 4 0, 3 4, 5 1, 3 2, 6 3)), duplicated on every release from 8.3.0), and a valid polygon reaches it only when it already hits LFV-210 (a hole touching the shell), whose fix removes it.

The test shows that XYShape.createIndexableFields on the ring POLYGON((6 3, 0 6, 4 0, 3 4, 5 1, 3 2, 6 3)) (self-crossing, accepted by the default path) indexes the triangle cut off by Tessellator.cureLocalIntersections twice (TestTessellatorCureAddsTriangleTwice#testCuredTriangleIndexedOnce).

## Expected vs actual

Expected: each triangle indexed once (3 fields); actual: 4 fields, 3 distinct (`expected:<3> but was:<4>`), with -ea and -da.

## Files

- [src/org/apache/lucene/document/TestTessellatorCureAddsTriangleTwice.java](src/org/apache/lucene/document/TestTessellatorCureAddsTriangleTwice.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.TestTessellatorCureAddsTriangleTwice
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
