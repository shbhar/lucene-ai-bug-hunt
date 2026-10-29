# Geo3D outside distance to a lat/lon box that reaches a pole (GeoNorthRectangle / GeoSouthRectangle) ignores the pole, so Geo3DDocValuesField.newOutsideBoxSort gives documents beyond the pole the distance to a far corner (39.88 instead of 10 degrees for the box lat 60..90 x lon -10..10 and the point (80, 180)) and sorts them after nearer documents

Bug LFV-782 · Area: spatial3d / geom polar rectangles outside distance (GeoNorthRectangle, GeoSouthRectangle) and the Geo3DDocValuesField outside-box sort · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-782.html](../../bugs/LFV-782.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You sort documents by their outside distance from a lat/lon box that reaches the North Pole, for example latitude 60..90 x longitude -10..10, with Geo3DDocValuesField.newOutsideBoxSort. A document at (80, 180) is only 10 degrees (about 1,112 km) from that box, because the box contains the pole, so it should sort before a document at (35, 0), which is 25 degrees south of the box. Instead GeoNorthRectangle.outsideDistance never considers the pole, where both side edges of the box end, and returns the distance to a far corner (39.88 degrees on the sphere; 4,434,716 m against 2,779,879 m in the WGS84 sort), so the nearer document sorts last; GeoSouthRectangle has the mirror bug at the South Pole.

The test shows that the Geo3D outside distance from the point (80, 180) to the box lat 60..90 x lon -10..10 (a `GeoNorthRectangle`, which contains the north pole) ignores the pole.

## Expected vs actual

Expected: 10 degrees (the distance to the pole, which is in the box); actual (-ea and -da): 39.882280145867114 degrees (a far corner), which is the value `Geo3DDocValuesField.newOutsideBoxSort` sorts by.

## Files

- [src/org/apache/lucene/spatial3d/geom/TestPolarRectangleOutsideDistance.java](src/org/apache/lucene/spatial3d/geom/TestPolarRectangleOutsideDistance.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial3d.geom.TestPolarRectangleOutsideDistance
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
