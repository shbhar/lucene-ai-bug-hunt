# LatLonShapeDocValues and XYShapeDocValues getEncodedMinX/MinY/MaxX/MaxY return a tree node's box instead of the shape's bounding box after a relate() call that descends the shape's tree, because the getters read the reader's reusable scratch box

Finding LFV-1130 · Area: core / document: shape doc values (ShapeDocValues.ShapeComparator bounding box behind LatLonShapeDocValues / XYShapeDocValues getEncodedMinX, getEncodedMinY, getEncodedMaxX, getEncodedMaxY) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1130.html](../../bugs/LFV-1130.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a LatLonShapeDocValues (or XYShapeDocValues) from a shape's doc-value bytes, call relate() with a query box, and then read getEncodedMinX/MinY/MaxX/MaxY, which are documented as 'the shape's bounding box'; you expect the same four values as before the relate() call. Instead, when the query box crosses the shape's box so that relate() walks down the shape's tree, the getters afterwards return the box of the last tree node visited: for the points (0,0), (10,10), (20,20) and a small box around (0,0), relate() correctly answers CELL_CROSSES_QUERY but getEncodedMaxX() then returns 0 instead of encodeLongitude(20) = 238609294, with no error. The getters read the reader's single reusable scratch box, which relate() overwrites for every node it visits, while getBoundingBox() of the same object, computed once in the constructor, stays correct.

## Expected vs actual

- Expected: `getEncodedMinX()` is still 0 after `relate()`, as before it.
- Actual: `AssertionError: expected:<0> but was:<119304647>`: longitude 10, a tree node's minX.

## Files

- [src/org/apache/lucene/document/TestShapeDocValuesBBoxAfterRelate.java](src/org/apache/lucene/document/TestShapeDocValuesBBoxAfterRelate.java) (JUnit test)

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
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.document.TestShapeDocValuesBBoxAfterRelate
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestShapeDocValuesBBoxAfterRelate.java](TestShapeDocValuesBBoxAfterRelate.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
