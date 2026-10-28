# Tessellator bridges a later hole across an earlier hole when the earlier hole touches the outer ring at a vertex where an outer edge and a hole edge are collinear: after filterPoints drops the straight-run copy of that vertex, fetchHoleBridge breaks an equal-x ray tie at it by ring order, so LatLonShape and XYShape match points inside the hole (depends on the outer ring's start vertex)

Bug LFV-796 · Area: geo / Tessellator hole elimination (polygon indexing for LatLonShape and XYShape) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a valid polygon with LatLonShape or XYShape whose clockwise outer ring (2,3) (2,5) (4,4) (5,0) (0,0) has a counter-clockwise hole A (2,3) (1,1) (2,1), which touches the ring only at V=(2,3) where the outer edge (2,5)-(2,3) and the hole edge (2,3)-(2,1) lie on the line x = 2, and a second hole B (3,3) (4,2) (4,3) that touches nothing, and you expect the triangles to cover the polygon minus both holes (area 12.5). After hole A is merged at V, the filterPoints pass that eliminateHoles runs after every hole (Tessellator.java:347) drops the copy of V on that straight run; hole B's leftward ray from (3,3) then hits two edges exactly at V, and fetchHoleBridge keeps whichever comes first in ring order (the strict 'x &gt; qx' at:549), so with the ring written from (2,3) hole B is bridged to (0,0) straight across hole A. The triangles then cover hole A (total area 16.0), the point (1.75,1.5) inside hole A matches INTERSECTS and misses DISJOINT, and the strict path rejects the polygon as self-intersecting; the other four ring starts are exact on main (on 8.2.0-9.12.2 and 10.0.0-10.3.2 the failing start is (2,5) instead).

## Expected vs actual

- Expected: You expect the triangles to cover the polygon minus both holes (area 12.5).
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/document/TestTessellatorHoleBridgeThroughTouchVertex.java](src/org/apache/lucene/document/TestTessellatorHoleBridgeThroughTouchVertex.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.TestTessellatorHoleBridgeThroughTouchVertex
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
