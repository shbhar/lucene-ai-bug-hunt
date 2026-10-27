# SpatialStrategy.makeRecipDistanceValueSource takes zScaling from half the corner-to-corner great-circle distance of the query's bounding box, which goes the short way round the globe once a geo box is wider than 180 degrees of longitude, so edge scores fall far below the documented ~0.1 (0.0082 for rect(-170,170,-10,10), 0.0028 for a 5S-5N band of all longitudes; released since 4.0.0)

Bug LFV-778 · Area: spatial-extras / SpatialStrategy recip-distance scoring (every strategy with a distance value source) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You score a geo spatial query with makeRecipDistanceValueSource, whose javadoc says zScaling is one tenth of the distance from the centre to the farthest edge, so scores run from 1 at the centre down to about 0.1 at the furthest edge. For a box wider than 180 degrees of longitude the method instead takes half the great-circle distance between the box's two opposite corners, and that distance goes the short way round the globe: for rect(-170,170,-10,10) zScaling is 1.41 instead of 17.0, and the edge point (170,0) scores 0.0082 instead of about 0.091 (for the band rect(-180,180,-5,5), 0.0028). Nothing fails; distance is over-weighted, so with FunctionScoreQuery.boostByValue a hit with inner score 10 at 170 degrees ranks below a hit with inner score 1 at 10 degrees on the band query, the reverse of the documented scaling.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/spatial/TestRecipDistanceWideQueryShape.java](src/org/apache/lucene/spatial/TestRecipDistanceWideQueryShape.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/spatial-extras/build/libs/lucene-spatial-extras-$V.jar
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.TestRecipDistanceWideQueryShape
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
