# Geo3dShapeFactory passes a line string's buffer distance to spatial3d's GeoPathFactory, which takes radians, without the degrees-to-radians conversion its circle applies, so in the Geo3d spatial context a line buffered by 1 degree is 1 radian (57.3 degrees) wide and buffers above about 1.57 throw

Bug LFV-678 · Area: spatial-extras / spatial4j bridge to spatial3d (Geo3dShapeFactory line-string builder, Geo3dLineStringBuilder.build), reached through the public LineStringBuilder API, the deprecated lineString(List, double), multi-line builders and the Geo3d context's polyshape reader (and the GeoJSON reader when its optional parser is present) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-678.html](../../bugs/LFV-678.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use the Geo3d spatial context, build a line from (0,0) to (10,0) with buffer(1), and expect a corridor reaching 1 degree from the line, because spatial4j distances are in the units of x and y (degrees in a geo context) and spatial4j's standard geo context and the same factory's 1-degree circle both say a point at (5, 20) is DISJOINT. Instead the line CONTAINS that point and its bounding box is about [-57.3, 67.3] x [-57.3, 57.3]: Geo3dLineStringBuilder.build hands the buffer straight to GeoPathFactory.makeGeoPath, whose width is an angle in radians, while the factory's circle and Geo3dRectangleShape.getBuffered convert degrees with DEGREES_TO_RADIANS. The same happens when the Geo3d context reads spatial4j's polyshape string for a 1-degree buffered line, and every buffer above about 1.5708 is rejected with 'Cutoff angle out of bounds', so a buffer of 2 degrees cannot be built at all.

The test shows that in the Geo3d spatial context a line string's buffer(1) is used as 1 radian (57.3 degrees) instead of 1 degree.

## Expected vs actual

Expected lineString (0,0)-(10,0) with buffer(1) to be DISJOINT from point (5,20); actual: CONTAINS (`expected:<DISJOINT> but was:<CONTAINS>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/spatial/spatial4j/TestGeo3dLineStringBuffer.java](src/org/apache/lucene/spatial/spatial4j/TestGeo3dLineStringBuffer.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/spatial-extras/build/libs/lucene-spatial-extras-$V.jar
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.spatial4j.TestGeo3dLineStringBuffer
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
