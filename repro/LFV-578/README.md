# LatLonPoint and LatLonDocValuesField box queries at exactly latitude 90 (box lat 90..90) or longitude 180..180 match nothing through newBoxQuery/newSlowBoxQuery but match the edge point through the Rectangle2D-based paths of the same field, so one point is reported as neither INTERSECTS nor DISJOINT (8.8.0 to main)

Bug LFV-578 · Area: core document + geo / LatLon box queries (the newBoxQuery/newSlowBoxQuery 90/180 special case vs Rectangle2D.create and LatLonShapeBoundingBoxQuery) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index one point at (90, 0) with LatLonPoint (stored at the top encoded latitude, 89.99999995809048) and query the box lat 90..90, lon -180..180 through LatLonPoint.newGeometryQuery, expecting the point to be either INTERSECTS or DISJOINT and every box path of the field to agree. Instead INTERSECTS, which newGeometryQuery routes to newBoxQuery and its deliberate MatchNoDocsQuery for minLatitude == 90, matches 0 docs; DISJOINT, which goes through Rectangle2D.create (commented 'behavior of LatLonPoint.newBoxQuery' but copying only the dateline part of that special case, then rounding the box up onto the top row with a saturating encodeLatitudeCeil), also matches 0; WITHIN, INTERSECTS(box, box), LatLonPointQuery and LatLonShape.newBoxQuery match 1. LatLonDocValuesField's slow queries behave the same, a box lon 180..180 over a point at (0, 180) gives the same pattern, and upstream's own equivalence assertion (BaseLatLonPointTestCase.testBoundingBoxQueriesEquivalence: count(newBoxQuery) == count(LatLonPointQuery INTERSECTS)) fails for this box, 0 vs 1.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: INTERSECTS, which newGeometryQuery routes to newBoxQuery and its deliberate MatchNoDocsQuery for minLatitude == 90, matches 0 docs; DISJOINT, which goes through Rectangle2D.create (commented 'behavior of LatLonPoint.newBoxQuery' but copying only the dateline part of that special case, then rounding the box up onto the top row with a saturating encodeLatitudeCeil), also matches 0; WITHIN, INTERSECTS(box, box), LatLonPointQuery and LatLonShape.newBoxQuery match 1.

## Files

- [src/org/apache/lucene/document/TestBoxAtPoleSpecialCase.java](src/org/apache/lucene/document/TestBoxAtPoleSpecialCase.java) (JUnit test)
- [src/org/apache/lucene/document/TestBoxAtPoleSpecialCaseOld.java](src/org/apache/lucene/document/TestBoxAtPoleSpecialCaseOld.java) (JUnit test)

## Run it

- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.document.TestBoxAtPoleSpecialCase

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestBoxAtPoleSpecialCaseOld
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/latlon-geo-queries-reference/](../../models/latlon-geo-queries-reference/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
