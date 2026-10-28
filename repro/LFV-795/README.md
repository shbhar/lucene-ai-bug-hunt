# Tessellator rejects a valid polygon whose holes touch nothing, depending on the outer ring's start vertex, when an earlier hole's bridge continues an outer edge: isLocallyInside treats the resulting straight angle as never inside, so fetchHoleBridge cannot switch to the only correct copy of the bridge end (regression in 8.2.0)

Bug LFV-795 · Area: geo / Tessellator hole bridges (fetchHoleBridge, isLocallyInside) behind LatLonShape and XYShape polygon indexing · Priority: low · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a valid polygon with two holes that touch nothing, for example the clockwise outer ring (4,5) (5,1) (0,0) (1,1) with the counter-clockwise holes (2,2) (4,3) (3,3) and (3,2) (3,1) (4,1), and you expect XYShape or LatLonShape.createIndexableFields to return its triangles whichever vertex the outer ring starts at. The first hole is joined to the ring by a bridge from (2,2) to (1,1) that continues the outer edge (0,0)-(1,1) in a straight line; the second hole's bridge search then lands on (1,1), and the only copy of (1,1) whose angle contains that hole has a straight angle, which Tessellator.isLocallyInside always rejects ('area == 0 -&gt; return false'). When the outer ring starts at (5,1) or (0,0) the search keeps the other copy, the bridge crosses the ring and indexing throws 'Unable to Tessellate shape. Possible malformed shape detected.', while the starts (4,5) and (1,1) give the exact area 9; 8.1.1 accepts every start, 8.2.0 to main reject two of them.

## Expected vs actual

- Expected: You expect XYShape or LatLonShape.createIndexableFields to return its triangles whichever vertex the outer ring starts at.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/document/TestTessellatorDisjointHolesCollinearBridge.java](src/org/apache/lucene/document/TestTessellatorDisjointHolesCollinearBridge.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.TestTessellatorDisjointHolesCollinearBridge
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
