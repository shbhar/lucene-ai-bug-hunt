# LatLonShape.createDocValueField throws IllegalArgumentException ("invalid longitude -180.00000000000003") for a valid Line along longitude -180 (and "invalid latitude -90.00000000000001" for one along latitude -90), because ShapeDocValues computes the line's length-weighted centroid in double, which can round one ulp past the bound, and encodes it without clamping it into the shape's bounding box (since 9.4.0)

Finding LFV-1331 · Area: document / shape doc values (ShapeDocValues centroid encoding; LatLonShapeDocValuesField, LatLonShape.createDocValueField) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1331.html](../../bugs/LFV-1331.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a valid three-point Line whose first segment lies on longitude -180 and whose short second segment steps one encoding unit east (lats {86.51443598326296, 0.9756675688549876, 0.9756777109578252}, lons {-180, -180, -179.99999991618097}) and call LatLonShape.createDocValueField("f", line). You expect a doc-value field whose centroid lies inside the line's bounding box, since the Line constructor and createIndexableFields accept the same line. Instead the constructor throws IllegalArgumentException: invalid longitude -180.00000000000003, because ShapeDocValues sums the length-weighted segment midpoints in double, the rounding puts the mean one ulp below -180, and Writer.writeTree passes it unclamped to GeoEncodingUtils.encodeLongitude, which range-checks it.

## Expected vs actual

- Expected: A doc-value field whose centroid lies in the line's box (longitude -180 to -179.99999992).
- Actual: IllegalArgumentException: invalid longitude -180.00000000000003; must be between -180.0 and 180.0

## Files

- [src/org/apache/lucene/document/TestShapeDocValuesCentroidOnMinus180.java](src/org/apache/lucene/document/TestShapeDocValuesCentroidOnMinus180.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): fails with assertions on and off (IllegalArgumentException: invalid longitude -180.00000000000003).
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestShapeDocValuesCentroidOnMinus180
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestShapeDocValuesCentroidOnMinus180.java](TestShapeDocValuesCentroidOnMinus180.java)

1 other file(s) of the recorded repro are not exported.

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
