# ShapeField.encodeTriangle puts the wrong edge flag on the first edge when it flips a clockwise triangle, so LatLonShape and XYShape CONTAINS queries return wrong hits

Bug LFV-204 · Area: document / geo shape fields (ShapeField triangle encoding; LatLonShape and XYShape CONTAINS queries) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-204.html](../../bugs/LFV-204.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a polygon with LatLonShape or XYShape and run a CONTAINS query that asks whether the polygon fully contains a box (or another query shape); you expect a match exactly when the box lies inside the polygon. When ShapeField.encodeTriangle turns one of the polygon's clockwise tessellation triangles counter-clockwise, it swaps two vertices but gives the new first edge the 'belongs to the polygon boundary' flag of the wrong old edge, so a real boundary edge can lose its flag and an internal diagonal can gain one. CONTAINS then silently says yes for boxes that stick out of the polygon (in one executed case a box 99.9998% outside it) and no for boxes well inside it.

The test shows that `ShapeField.encodeTriangle`, when it flips a clockwise triangle to counter-clockwise, moves the "edge belongs to the shape" flag of the old edge b-c onto the new first edge (the old edge c-a), so after `decodeTriangle` the wrong geometric edges are flagged (index-free; this is the root cause of the LatLonShape/XYShape CONTAINS wrong hits).

## Expected vs actual

Expected for the clockwise triangle (0,0) (0,10) (10,0) with only edge (0,10)-(10,0) flagged: only that edge flagged after decode; actual: `expected:<[(0,10)-(10,0)]> but was:<[(0,0)-(10,0), (0,10)-(10,0)]>`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/document/TestEncodeTriangleClockwiseEdgeFlag.java](src/org/apache/lucene/document/TestEncodeTriangleClockwiseEdgeFlag.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.TestEncodeTriangleClockwiseEdgeFlag
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/geo-tessellator-shapes/](../../models/geo-tessellator-shapes/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
