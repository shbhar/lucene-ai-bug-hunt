# XYPointField.newDistanceQuery and XYDocValuesField.newSlowDistanceQuery document the radius as 'must be non-negative and finite', but a radius of 0 throws IllegalArgumentException ('radius must be bigger than 0, got 0.0') because XYCircle requires radius &gt; 0; documentation defect (the rejection is intended), since 8.5.0

Finding LFV-1434 · Area: core / geo: Cartesian (XY) point distance queries (XYPointField.newDistanceQuery and XYDocValuesField.newSlowDistanceQuery javadoc vs the XYCircle radius check) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1434.html](../../bugs/LFV-1434.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call XYPointField.newDistanceQuery("p", 1f, 2f, 0f), or XYDocValuesField.newSlowDistanceQuery with the same arguments, to find the points exactly at (1, 2), because both javadocs say the radius 'must be non-negative and finite' and promise IllegalArgumentException only if the radius 'is invalid'. Instead both throw IllegalArgumentException 'radius must be bigger than 0, got 0.0' at query construction: they pass the radius to XYCircle, whose constructor rejects radius &lt;= 0, while the lat/lon twins with the same wording (LatLonPoint.newDistanceQuery, LatLonDocValuesField.newSlowDistanceQuery) accept 0 and match the point at the centre. The rejection is deliberate (the maintainers have called radius 0 'not allowed' and changed Lucene's own random tests to avoid it), so the defect is the javadoc, whose likely fix is 'must be positive and finite'.

## Expected vs actual

- Expected: Both calls return a query (radius 0 is "non-negative and finite"), as the lat/lon twins do.
- Actual: Both throw `IllegalArgumentException: radius must be bigger than 0, got 0.0` (2 of 2 fail), assertions on and off, on main and 10.6.0.

## Files

- [src/org/apache/lucene/document/TestXYDistanceZeroRadius.java](src/org/apache/lucene/document/TestXYDistanceZeroRadius.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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
     execute --select-class org.apache.lucene.document.TestXYDistanceZeroRadius
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestXYDistanceZeroRadius.java](TestXYDistanceZeroRadius.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
