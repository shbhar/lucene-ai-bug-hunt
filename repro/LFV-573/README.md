# Lat/lon distance queries can drop a point whose distance equals the radius and match a point one ulp beyond it, because GeoUtils.distanceQuerySortKey does not return the largest sort key within the radius (every release since 6.1.0)

Bug LFV-573 · Area: core / geo distance queries (GeoUtils.distanceQuerySortKey, shared by LatLonPoint, LatLonDocValuesField and LatLonShape circle queries) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-573.html](../../bugs/LFV-573.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index points with LatLonPoint (or LatLonDocValuesField or LatLonShape) and run a distance query whose radius is exactly a point's distance from the center as Lucene computes it (SloppyMath.haversinMeters, the same value a distance sort or LatLonPoint.nearest reports), and expect that point to match, because the query matches 'points within the specified distance' and Lucene's own test oracle is haversinMeters(center, point) &lt;= radius; with the radius one ulp smaller you expect it not to match. Instead the point is often missing in the first case and often still matches in the second (each in roughly a fifth to a third of random cases, depending on the points): for example, two points that both the distance sort and haversinMeters put 8154434.476304402 m apart are not matched by any of the three distance queries with exactly that radius. The cause is GeoUtils.distanceQuerySortKey, which turns the radius into a sort-key threshold by binary search: it returns the first key it meets whose distance equals the radius, not the largest such key, so a point on a larger key of the same distance fails 'key &lt;= threshold', and when no key has exactly that distance it returns the smallest key whose distance exceeds the radius.

The test shows that `LatLonPoint.newDistanceQuery` does not match a point whose `SloppyMath.haversinMeters` distance equals the radius, because `GeoUtils.distanceQuerySortKey` does not return the largest sort key within the radius.

## Expected vs actual

Expected count 1 at radius 8154434.476304402 m (the point's own distance); actual 0 (`expected:<1> but was:<0>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/document/TestDistanceQueryRadiusBoundary.java](src/org/apache/lucene/document/TestDistanceQueryRadiusBoundary.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.TestDistanceQueryRadiusBoundary
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
