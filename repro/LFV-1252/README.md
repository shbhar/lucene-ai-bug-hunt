# With checkSelfIntersections=true, XYShape and LatLonShape accept a ring whose horizontal or vertical edges overlap each other, but reject the same ring rotated by 45 degrees: the bounding-box prefilter in Tessellator.checkIntersectionPoint returns before the overlap test for collinear axis-parallel edges

Finding LFV-1252 · Area: geo / Tessellator self-intersection check (polygon indexing for XYShape and LatLonShape with checkSelfIntersections = true) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1252.html](../../bugs/LFV-1252.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a polygon with XYShape.createIndexableFields(name, polygon, true), asking Lucene to check that it is valid, and its ring runs back over part of itself along a horizontal line: (4,4) (1,1) (4,1) (2,0) (0,1) (2,1), whose edges (1,1)-(4,1) and (0,1)-(2,1) overlap on y = 1 for x from 1 to 2. You expect the IllegalArgumentException 'Polygon ring self-intersection', which is exactly what the same ring rotated by 45 degrees gets. Instead the ring is accepted and indexed as if the check were off, because checkIntersectionPoint's bounding-box prefilter skips every edge pair whose boxes only touch, and two collinear horizontal (or vertical) edges always have boxes that only touch, so the overlap test behind the prefilter is never reached for them; LatLonShape behaves the same.

## Expected vs actual

- Expected: `IllegalArgumentException: Polygon ring self-intersection ...`, as the same ring rotated by 45 degrees gets.
- Actual: `AssertionFailedError: Expected exception IllegalArgumentException but no exception was thrown`: the ring is accepted.

## Files

- [src/org/apache/lucene/geo/TestTessellatorAxisParallelOverlap.java](src/org/apache/lucene/geo/TestTessellatorAxisParallelOverlap.java) (JUnit test)

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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.geo.TestTessellatorAxisParallelOverlap
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestTessellatorAxisParallelOverlap.java](TestTessellatorAxisParallelOverlap.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
