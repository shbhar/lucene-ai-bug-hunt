# Tessellator.cureLocalIntersections adds each triangle it cuts off twice, so LatLonShape and XYShape index that triangle twice and the shape doc-values centroid counts its area twice

Finding LFV-792 · Area: geo / Tessellator cure step (polygon indexing for LatLonShape and XYShape, shape doc values) · Priority: low · Impact: minor · Novelty: new

Finding page: [https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-792.html](https://shbhar.github.io/lucene-ai-bug-hunt/bugs/LFV-792.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a polygon with LatLonShape or XYShape (createIndexableFields or createDocValueField) and expect each triangle of its tessellation to be stored once. When the Tessellator's cure step (cureLocalIntersections) cuts off a triangle to repair a small local self-intersection, it appends that same triangle twice (two identical tessellation.add calls at Tessellator.java:890 and :892, left by LUCENE-8620 in 8.3.0), so the shape carries one extra indexed triangle, ShapeDocValuesField.numberOfTerms is one too high and getCentroid() counts that triangle's area twice; query results did not change in the runs of this row: 57,800 box queries of 2026-10-27 gave the same counts with and without the duplicate (the extra triangle is a second copy of one that is already indexed). In the runs of this row (2026-10-27) the cure step fired on its own only for self-crossing rings, which the javadoc excludes but the default checkSelfIntersections=false path accepts (for example POLYGON((6 3, 0 6, 4 0, 3 4, 5 1, 3 2, 6 3)), duplicated on each of the 36 releases from 8.3.0 to 10.5.2 that were run with it and, in a run of 2026-11-14, on 10.6.0), and a valid polygon reached it only when it already hit LFV-210 (a hole touching the shell). A run of 2026-11-25 on main 8506598d7a98 found a second way in one valid polygon: its hole has a vertex less than one last-place unit of its longitude inside an outer edge, without touching it, and the step cuts off a triangle that lies over part of the hole and adds it twice.

The test shows that XYShape.createIndexableFields on the ring POLYGON((6 3, 0 6, 4 0, 3 4, 5 1, 3 2, 6 3)) (self-crossing, accepted by the default path) indexes the triangle cut off by Tessellator.cureLocalIntersections twice (TestTessellatorCureAddsTriangleTwice#testCuredTriangleIndexedOnce).

## Expected vs actual

Expected: each triangle indexed once (3 fields); actual: 4 fields, 3 distinct (`expected:<3> but was:<4>`), with -ea and -da.

## Files

- [src/org/apache/lucene/document/TestTessellatorCureAddsTriangleTwice.java](src/org/apache/lucene/document/TestTessellatorCureAddsTriangleTwice.java) (JUnit test)

## Run it

- Tested on Lucene main 82cf7465bb8f (a commit of 2026-11-26): still reproduces.
- Tested on Lucene main 645d4fe14a20 (a commit of 2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
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
     execute --select-class org.apache.lucene.document.TestTessellatorCureAddsTriangleTwice
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on branch_11x the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
