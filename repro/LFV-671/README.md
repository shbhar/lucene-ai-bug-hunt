# Geo3dRectangleShape.getCrossesDateLine tests whether a box spans longitude 0 instead of whether it crosses the dateline, so PointVectorStrategy and BBoxStrategy return wrong hits for Geo3d boxes and circles at the dateline or the prime meridian

Bug LFV-671 · Area: spatial-extras / spatial4j bridge to spatial3d (Geo3dRectangleShape.getCrossesDateLine), read by PointVectorStrategy and BBoxStrategy · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You use the Geo3d spatial context (Geo3dSpatialContextFactory) with PointVectorStrategy or BBoxStrategy and query or index a box that crosses the dateline, such as rect(170, -170, -10, 10), or one that spans longitude 0, such as rect(-10, 10, -10, 10). You expect getCrossesDateLine to follow the spatial4j Rectangle contract (true exactly when the box crosses the -180/180 meridian, that is minX &gt; maxX, as spatial4j's RectangleImpl and this class's own getWidth assume), so a point at longitude 175 is found inside 170..-170 and an indexed box -10..10 does not match a query box at 100..110. Instead Geo3dRectangleShape.getCrossesDateLine returns getMaxX &gt; 0 &amp;&amp; getMinX &lt; 0 ('spans longitude 0'), which is false for 170..-170 and true for -10..10: PointVectorStrategy searches the empty range 170..-170 and returns 0 hits (also for circle(180, 0, 5)), and BBoxStrategy stores xdl=F for the dateline box and misses it (0 hits instead of 1), but stores xdl=T for -10..10, reads it as [-10, 180] plus [-180, 10] and matches it against 100..110 (1 hit instead of 0).

## Expected vs actual

- Expected: You expect getCrossesDateLine to follow the spatial4j Rectangle contract (true exactly when the box crosses the -180/180 meridian, that is minX &gt; maxX, as spatial4j's RectangleImpl and this class's own getWidth assume), so a point at longitude 175 is found inside 170..-170 and an indexed box -10..10 does not match a query box at 100..110.
- Actual: Geo3dRectangleShape.getCrossesDateLine returns getMaxX &gt; 0 &amp;&amp; getMinX &lt; 0 ('spans longitude 0'), which is false for 170..-170 and true for -10..10: PointVectorStrategy searches the empty range 170..-170 and returns 0 hits (also for circle(180, 0, 5)), and BBoxStrategy stores xdl=F for the dateline box and misses it (0 hits instead of 1), but stores xdl=T for -10..10, reads it as [-10, 180] plus [-180, 10] and matches it against 100..110 (1 hit instead of 0).

## Files

- [src/org/apache/lucene/spatial/spatial4j/TestGeo3dRectangleCrossesDateLine.java](src/org/apache/lucene/spatial/spatial4j/TestGeo3dRectangleCrossesDateLine.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.spatial4j.TestGeo3dRectangleCrossesDateLine
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
