# LatLonShape WITHIN a circle that crosses the dateline matches a line or polygon whose vertices lie inside the circle on both sides of the dateline, although Lucene runs its edges through longitude 0, far outside the circle: Circle2D.containsLine/containsTriangle test only the vertices

Finding LFV-1134 · Area: core / geo (Circle2D.containsLine/containsTriangle for haversine circles whose bounding box crosses the dateline, behind LatLonShape.newDistanceQuery WITHIN) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1134.html](../../bugs/LFV-1134.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a two-point LatLonShape line from (lat 0, lon -179.5) to (lat 0, lon 179.5); Lucene runs its edge straight through (0, 0), so the document matches INTERSECTS a 100 km circle around (0, 0). A WITHIN query with a 100 km circle around (0, 180), 20,000 km from that point, should then return 0 hits, but it returns 1 with no error. Circle2D treats a circle that crosses the dateline as two longitude pieces, and containsLine/containsTriangle accept a shape once every vertex lies in either piece, never checking that the edges between them stay inside.

## Expected vs actual

- Expected: 0 hits: the indexed edge runs through lon 0, far outside the 100 km circle around (0, 180).
- Actual: 1 hit, no error: `expected:<0> but was:<1>`, with assertions on and off.

## Files

- [src/org/apache/lucene/document/TestLatLonShapeWithinDatelineCircle.java](src/org/apache/lucene/document/TestLatLonShapeWithinDatelineCircle.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestLatLonShapeWithinDatelineCircle
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestLatLonShapeWithinDatelineCircle.java](TestLatLonShapeWithinDatelineCircle.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
