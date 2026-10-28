# ShapeDocValues getCentroid returns (0, 0), far outside the shape's own bounding box, when the shape's total encoded length or area is zero (a line whose points coincide after encoding, or a polygon thinner than one encoding step)

Bug LFV-366 · Area: document / shape doc values (ShapeDocValues centroid; LatLonShapeDocValuesField and XYShapeDocValuesField getCentroid) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-366.html](../../bugs/LFV-366.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a doc-values shape with LatLonShape.createDocValueField or XYShape.createDocValueField and ask for its centroid; you expect getCentroid, documented as 'the centroid location for the geometry', to be a point of the shape, inside the bounding box that the same object returns. For a line whose points coincide after encoding (new Line({40, 40}, {-70, -70}), or two points closer than one encoding step) or a polygon thinner than one encoding step, Lucene returns Point(0.0, 0.0) instead, 7,500 km or more from the shape in these examples: the centroid is weighted by length or area, and when the total weight is zero the code skips the division and keeps the zero numerator. The other public factory, createDocValueField(name, createIndexableFields(name, line)), returns the real point for the same line.

## Expected vs actual

- Expected: Every centroid lies inside its own bounding box: lat 39.99999996, lon -70.00000002 for the repeated-point line, the same point as the `Field` factory gives.
- Actual: 6 of 8 tests fail with centroid `Point(0.0,0.0)` (or `XYPoint(0.0,0.0)`), outside boxes such as `lat=39.9999999627471 TO 39.9999999627471 lon=-70.00000001862645 TO -60.00000002793968`; the 2 controls pass.

## Files

- [src/org/apache/lucene/document/TestShapeDocValuesDegenerateCentroid.java](src/org/apache/lucene/document/TestShapeDocValuesDegenerateCentroid.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
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
     execute --select-class org.apache.lucene.document.TestShapeDocValuesDegenerateCentroid
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestShapeDocValuesZeroMeasureCentroid.java](TestShapeDocValuesZeroMeasureCentroid.java)
- [TestZeroMeasureShapeCentroid.java](TestZeroMeasureShapeCentroid.java)
- [TestShapeDocValuesDegenerateCentroid.java](TestShapeDocValuesDegenerateCentroid.java)
- [TestShapeDvCentroidIntentProbe.java](TestShapeDvCentroidIntentProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
