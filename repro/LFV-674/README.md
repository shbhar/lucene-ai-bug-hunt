# Geo3dDistanceCalculator.within returns the opposite of 'distance &lt;= limit' for every finite distance, so the default distance calculator of the Geo3d spatial context answers every within call backwards

Bug LFV-674 · Area: spatial-extras / spatial4j bridge to spatial3d (Geo3dDistanceCalculator.within, the default DistanceCalculator that Geo3dSpatialContextFactory installs) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create a spatial context with Geo3dSpatialContextFactory and ask its distance calculator whether the point (1, 0) is within 10 degrees of (0, 0), with ctx.getDistCalc.within(from, 1, 0, 10), and expect true: distance returns 1.0 degree, and the spatial4j DistanceCalculator contract says within is true when the distance is &lt;= the limit. Instead it returns false, and within(from, 50, 0, 10) returns true although the distance is 50 degrees. The method body is 'return (distance &lt; distance(from, toX, toY));', the exact negation of the contract, so every answer with a finite distance is inverted, including within(p, p, 0).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: It returns false, and within(from, 50, 0, 10) returns true although the distance is 50 degrees.

## Files

- [src/org/apache/lucene/spatial/spatial4j/TestGeo3dDistanceCalculatorWithin.java](src/org/apache/lucene/spatial/spatial4j/TestGeo3dDistanceCalculatorWithin.java) (JUnit test)
- [src2/org/apache/lucene/spatial/spatial4j/TestGeo3dDistanceCalculatorWithin.java](src2/org/apache/lucene/spatial/spatial4j/TestGeo3dDistanceCalculatorWithin.java) (JUnit test)

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

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.spatial4j.TestGeo3dDistanceCalculatorWithin
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.spatial4j.TestGeo3dDistanceCalculatorWithin
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
