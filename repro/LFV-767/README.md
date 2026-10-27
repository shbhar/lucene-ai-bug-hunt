# XYShape circle queries miss lines and polygons that enter the circle only near its extreme x or y, and can report CONTAINS for a polygon the circle sticks out of, when the centre coordinate is large relative to the radius, because XYRectangle.fromPointDistance rounds the circle's bounding box inward

Bug LFV-767 · Area: geo / XYShape circle queries (XYRectangle.fromPointDistance, Circle2D.CartesianDistance) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index an XYShape line that passes just inside a circle and search with XYShape.newDistanceQuery (INTERSECTS) for an XYCircle whose centre coordinate is large compared with the radius, for example centre (2^23, 2^23+1), radius 1.3 and the segment (2^23+1, 2^23)-(2^23+2, 2^23+3), which passes 1.265 from the centre, expecting the hit you get for the same shapes moved exactly to the origin. Instead INTERSECTS finds nothing and DISJOINT matches the document, because XYRectangle.fromPointDistance builds the circle's bounding box as x + nextUp(r) in float, which rounds to nearest (2^23 + 1.3 becomes 2^23 + 1), and since 8.6.0 Circle2D's Cartesian contains rejects every point outside that too-small box before its exact distance test, including the segment's closest point to the centre. The same box makes CONTAINS report that a polygon contains a circle that sticks out of it.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: INTERSECTS finds nothing and DISJOINT matches the document, because XYRectangle.fromPointDistance builds the circle's bounding box as x + nextUp(r) in float, which rounds to nearest (2^23 + 1.3 becomes 2^23 + 1), and since 8.6.0 Circle2D's Cartesian contains rejects every point outside that too-small box before its exact distance test, including the segment's closest point to the centre.

## Files

- [src/org/apache/lucene/document/TestXYCircleBoundingBoxRounding.java](src/org/apache/lucene/document/TestXYCircleBoundingBoxRounding.java) (JUnit test)
- [src2/org/apache/lucene/document/TestXYCircleBoundingBoxRounding.java](src2/org/apache/lucene/document/TestXYCircleBoundingBoxRounding.java) (JUnit test)

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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestXYCircleBoundingBoxRounding
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestXYCircleBoundingBoxRounding
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
