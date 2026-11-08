# Tessellator's self-intersection check divides by a determinant that can round to 0.0 when two nearly parallel edges are flagged as crossing: an invalid polygon is rejected with 'Polygon self-intersection at lat=Infinity lon=Infinity' (-da) or with a bare AssertionError from 'assert determinant != 0' (-ea) instead of an IllegalArgumentException naming a real location

Finding LFV-1247 · Area: geo / Tessellator self-intersection check (polygon indexing for LatLonShape with checkSelfIntersections = true) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1247.html](../../bugs/LFV-1247.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a polygon with LatLonShape.createIndexableFields(name, polygon, true), asking Lucene to check it for self-intersections, and the polygon is invalid because one edge runs back over part of an earlier edge (lats {0, 7, 4, 3.85, 1.4, 2, 0}, lons {0, 1, 8, 0.55, 0.2, -7, 0}). You expect the IllegalArgumentException 'Polygon self-intersection at lat=.. lon=..' naming where the ring crosses itself, as other invalid polygons get. Instead the check flags two nearly parallel edges whose determinant rounds to exactly 0.0 and divides by it: without assertions (production) the message reads 'Polygon self-intersection at lat=Infinity lon=Infinity', and with -ea the internal 'assert determinant != 0' throws a bare java.lang.AssertionError out of the indexing call.

## Expected vs actual

- Expected: `IllegalArgumentException` with a real location, as a similar binary-exact ring gets ("Polygon ring self-intersection at lat=2.0 lon=0.25").
- Actual: "Polygon self-intersection at lat=Infinity lon=Infinity" without assertions; a bare `java.lang.AssertionError` at `Tessellator.java:1044` with `-ea`.

## Files

- [src/org/apache/lucene/document/TestLatLonShapeSelfIntersectionLocation.java](src/org/apache/lucene/document/TestLatLonShapeSelfIntersectionLocation.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=3 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestLatLonShapeSelfIntersectionLocation
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestLatLonShapeSelfIntersectionLocation.java](TestLatLonShapeSelfIntersectionLocation.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
