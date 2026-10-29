# geo.Rectangle checks minLat &lt;= maxLat only with an assert, so a lat/lon box with swapped latitudes (new Rectangle, LatLonShape.newBoxQuery, a WKT ENVELOPE) throws a bare AssertionError with -ea and is silently accepted with -da, where LatLonShape box queries then return hits that match no region; the Cartesian twin XYRectangle throws IllegalArgumentException for the same mistake

Bug LFV-1014 · Area: core / geo Rectangle (LatLonShape box queries, newGeometryQuery(Rectangle), SimpleWKTShapeParser ENVELOPE) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a lat/lon box with the minimum and maximum latitude swapped, for example LatLonShape.newBoxQuery(field, INTERSECTS, 10, 5, 0, 1) or the WKT text ENVELOPE(0, 1, 5, 10), and expect an IllegalArgumentException, as XYRectangle gives for the same mistake and as Rectangle itself gives for an out-of-range latitude. Rectangle's constructor checks the order only with 'assert maxLat &gt;= minLat', so with assertions enabled you get a bare AssertionError, and with assertions disabled the inverted box is accepted. The shape query then matches only shapes that cross both latitude lines (INTERSECTS) or the complement of those (DISJOINT), which is neither the empty result that LatLonPoint.newBoxQuery gives for the same box nor the result of the swapped box.

The test shows that `new Rectangle(minLat 10, maxLat 5, minLon 0, maxLon 1)` is not rejected with an IllegalArgumentException: the latitude order is checked only by `assert maxLat >= minLat` (XYRectangle throws IllegalArgumentException for the same mistake).

## Expected vs actual

Expected: IllegalArgumentException; actual: a bare `java.lang.AssertionError` from `Rectangle.<init>(Rectangle.java:94)` with `-ea`, and `Expected exception IllegalArgumentException but no exception was thrown` (box accepted) with `-da`.

## Files

- [src/org/apache/lucene/geo/TestRectangleInvertedLatitude.java](src/org/apache/lucene/geo/TestRectangleInvertedLatitude.java) (JUnit test)

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
     execute --select-class org.apache.lucene.geo.TestRectangleInvertedLatitude
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
