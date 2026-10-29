# Rectangle.fromPointDistance throws 'invalid longitude NaN' for some legal circles whose edge stops just short of a pole, so LatLon distance queries on them fail with IllegalArgumentException

Bug LFV-771 · Area: core / geo (Rectangle.fromPointDistance circle bounding box, used by LatLon distance and Circle queries) · Priority: low · Impact: crash · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run LatLonPoint.newDistanceQuery("p", 0.43945312499999994, 0, 9958692.080819247), a valid centre and a finite, non-negative radius whose circle stops about 7 cm short of the north pole, and expect it to match the two indexed points inside the circle. Instead IndexSearcher.count throws IllegalArgumentException 'invalid longitude NaN; must be between -180.0 and 180.0' from Rectangle.fromPointDistance, and LatLonDocValuesField.newSlowDistanceQuery throws the same when it is built. The bounding box computes asin(sin(r) / cos(lat)) without a clamp, and SloppyMath.cos, which is not monotone across its lookup-table switches, makes the quotient 1.0000000000000002 although its exact value is below 1, so asin returns NaN.

The test shows that `LatLonPoint.newDistanceQuery` on a legal circle (centre 0.43945312499999994, 0; radius 9958692.080819247 m, the edge stops about 7 cm short of the north pole) fails at search time: `Rectangle.fromPointDistance` takes `asin` of a SloppyMath quotient that rounds to 1.0000000000000002 and gets NaN.

## Expected vs actual

Expected 1 hit (the indexed centre point); actual: `IllegalArgumentException: invalid longitude NaN; must be between -180.0 and 180.0` from `LatLonPointDistanceQuery.createWeight`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/document/TestDistanceQueryNearPoleNaN.java](src/org/apache/lucene/document/TestDistanceQueryNearPoleNaN.java) (JUnit test)

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
     execute --select-class org.apache.lucene.document.TestDistanceQueryNearPoleNaN
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/sloppymath-and-geo-distance-documented-error-bounds/](../../models/sloppymath-and-geo-distance-documented-error-bounds/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
