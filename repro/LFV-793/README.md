# Tessellator covers a hole of a valid polygon when two holes and the outer ring meet at one vertex and an outer edge continues straight into a hole edge there: the filterPoints call after each hole merge drops the copy of that vertex the next hole needs, so LatLonShape and XYShape match points inside the hole (since 8.2.0)

Bug LFV-793 · Area: geo / Tessellator hole elimination (polygon indexing for LatLonShape and XYShape) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a valid polygon with LatLonShape or XYShape whose outer ring and two holes meet only at one vertex P, with an outer edge running straight into a hole edge through P, for example POLYGON((2 6, 2 7, 4 0, 0 0, 2 6), (2 6, 3 1, 3 2, 2 6), (2 6, 1 1, 2 1, 2 6)), and expect the triangles to cover the polygon minus both holes. After hole B (2,6) (1,1) (2,1) is merged at P, the filterPoints call that Tessellator.eliminateHoles runs after every hole (Tessellator.java:347) removes the copy of P that lies on the straight line (2,7)-(2,6)-(2,1); hole A, whose left-most vertex is P, is then merged at the only remaining copy of P, in the wrong wedge, and ear clipping emits triangles over hole B (twice the tessellation area is 25 instead of 20). A point inside hole B such as (1.8, 2) then matches an INTERSECTS point query and a box inside hole B is not DISJOINT; on 9.12.3, 10.4.0 and later this happens only when the outer ring is written starting at P, on 8.2.0 to 9.12.2 and 10.0.0 to 10.3.2 for every ring start tested.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/document/TestTwoHolesAtStraightSharedVertex.java](src/org/apache/lucene/document/TestTwoHolesAtStraightSharedVertex.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.TestTwoHolesAtStraightSharedVertex
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
