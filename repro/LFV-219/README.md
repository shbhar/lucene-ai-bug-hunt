# XYPolygon evaluates its winding sum in float, so valid polygons with large finite coordinates (differences of about 1.8e19 or more) can get the wrong winding order, and XYShape rejects them as 'Unable to Tessellate shape'

Bug LFV-219 · Area: geo / XY shapes (XYPolygon winding order, Tessellator.tessellate(XYPolygon), XYShape.createIndexableFields and createDocValueField) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an XYPolygon with large but finite float coordinates, which the constructor accepts (any finite float), for example the triangle (0,0), (2^66,2^66), (2^67,2^66), and index it with XYShape.createIndexableFields; you expect it to index exactly like the same triangle at scale 1, because multiplying every coordinate by a power of two is exact and changes neither the shape nor its orientation. The constructor computes the winding sum with float products, which overflow to Infinity once coordinate differences reach about 1.8e19; Infinity minus Infinity is NaN, and a NaN sum is labelled CW. The Tessellator then links the ring the wrong way round, finds no ear to cut, and rejects the valid polygon with 'Unable to Tessellate shape. Possible malformed shape detected.'

## Expected vs actual

- Expected: You expect it to index exactly like the same triangle at scale 1, because multiplying every coordinate by a power of two is exact and changes neither the shape nor its orientation.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/geo/TestXYPolygonLargeCoordinates.java](src/org/apache/lucene/geo/TestXYPolygonLargeCoordinates.java) (JUnit test)
- [src/XYPolygonLargeCoordinatesMain.java](src/XYPolygonLargeCoordinatesMain.java) (main() class)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual); a main() class that exits non-zero, with an exception or AssertionError, is the bug reproducing.
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
     execute --select-class org.apache.lucene.geo.TestXYPolygonLargeCoordinates

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" XYPolygonLargeCoordinatesMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
