# Tessellator flags a hole bridge that lies inside a longer outer-ring edge as an internal edge, so LatLonShape and XYShape CONTAINS queries match boxes that stick out of a valid polygon whose two holes touch the same outer edge

Bug LFV-794 · Area: geo / Tessellator hole-bridge edge flags (LatLonShape and XYShape CONTAINS queries) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-794.html](../../bugs/LFV-794.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a valid polygon whose two holes each touch the same straight outer edge at one point, for example the triangle (0,0) (7,12) (12,0) with the holes (2,0) (5,1) (3,3) and (5,0) (8,1) (6,3), both touching the bottom edge y = 0, and you expect a CONTAINS box query to match only boxes that lie inside the polygon. While joining the second hole to the ring, Tessellator.eliminateHole draws a bridge from (5,0) to (2,0) that runs along the outer edge but marks it as an internal edge, because it only compares the bridge with the ring edges next to its two ends, so the indexed triangle loses its 'belongs to the polygon' flag on that piece of the boundary. LatLonShape and XYShape CONTAINS queries then match the box x [3,4], y [-0.5,0.3], which sticks out below the polygon, with no error, on every release executed since 8.4.0.

## Expected vs actual

- Expected: CONTAINS for the box x [3,4], y [-0.5,0.3] finds nothing (it sticks out below y = 0), the inside box x [3,4], y [0.05,0.3] finds the document, and the edge (2,0)-(5,0) is flagged as a polygon edge.
- Actual: CONTAINS finds the document for the box that sticks out (`expected:<0> but was:<1>`, XY and LatLon), and the edge (2,0)-(5,0) is not flagged.

## Files

- [src/org/apache/lucene/document/TestTessellatorBridgeAlongOuterEdgeFlag.java](src/org/apache/lucene/document/TestTessellatorBridgeAlongOuterEdgeFlag.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.TestTessellatorBridgeAlongOuterEdgeFlag
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestHolesTouchingSameOuterEdge.java](TestHolesTouchingSameOuterEdge.java)
- [TestTessellator-testHolesTouchingSameOuterEdge.diff](TestTessellator-testHolesTouchingSameOuterEdge.diff)
- [TestHolesTouchingSameOuterEdgeShellVertices.java](TestHolesTouchingSameOuterEdgeShellVertices.java)
- [TestTessellatorBridgeAlongOuterEdgeFlag.java](TestTessellatorBridgeAlongOuterEdgeFlag.java)
- [TestTwoHolesOnOuterEdgeContains.java](TestTwoHolesOnOuterEdgeContains.java)
- [ContainsExtentProbe.java](ContainsExtentProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
