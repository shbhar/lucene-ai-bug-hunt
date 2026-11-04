# spatial3d Vector (and so GeoPoint and the Geo3DPoint distance and box queries), Plane and the longitude-slice and degenerate latitude/longitude shapes compare doubles with == in equals() but hash Double.doubleToLongBits, so objects built with 0.0 and -0.0 are equal yet have different hash codes (since 5.2.0)

Finding LFV-1341 · Area: spatial3d / geom value classes and Geo3DPoint queries (equals and hashCode contract) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build Geo3DPoint.newDistanceQuery("g", PlanetModel.WGS84, 0.0, 10.0, 1000.0) and the same query with latitude -0.0: equals() says they are equal, because the circle's centre GeoPoint is compared by Vector.equals, which uses ==, and under == the two centres (whose z is +0.0 and -0.0) are equal. By the Object#hashCode contract, which Query's javadoc asks subclasses to honour so that the query cache works, their hash codes must then be equal, but they differ by exactly 2^31, because Vector.hashCode hashes Double.doubleToLongBits(z), and those bits differ for the two zeros. Plane (its D) and the longitude-slice and degenerate latitude/longitude shapes have the same == versus doubleToLongBits pair, so box queries and the geom factories show it too, while hits and scores stay the same.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the reported behaviour is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/spatial3d/TestSignedZeroEqualsHashCode.java](src/org/apache/lucene/spatial3d/TestSignedZeroEqualsHashCode.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial3d.TestSignedZeroEqualsHashCode
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
