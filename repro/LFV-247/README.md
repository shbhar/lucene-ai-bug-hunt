# Geo3DDocValuesField.newDistanceSort and newOutsideDistanceSort with a maximum radius below about 10 km test each document's 21-bit decoded point, which lies metres off the planet surface, against the circle's nearly tangent plane: about half the documents inside the circle, even at its centre, get distance +Infinity and sort last, documents up to about 8 km away get finite distances, and inside documents get outside distances of up to the radius instead of 0.0

Bug LFV-247 · Area: spatial3d / Geo3DDocValuesField distance sorts (Geo3DPointDistanceComparator, Geo3DPointOutsideDistanceComparator; GeoStandardCircle membership test on the decoded doc value) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-247.html](../../bugs/LFV-247.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You store locations with Geo3DDocValuesField on the default WGS84 model and sort hits with Geo3DDocValuesField.newDistanceSort(field, lat, lon, 1000, WGS84), expecting ascending distance from the centre with the distance in metres (and, with newOutsideDistanceSort, 0.0 for points inside the circle). Instead about half of the documents inside the 1 km radius, even ones 10 m from the centre, get +Infinity and sort last as if they had no location, some documents 2-3 km away get finite distances and sort ahead of them, and inside documents get outside distances of up to 988 m, while LatLonDocValuesField's distance sort orders the same points correctly. The cause: each doc value decodes to a point up to about 4.5 m off the planet surface, and the comparators pass that point unprojected to the circle's membership test, a single plane that for a 1 km circle lies only about 8 cm below the surface at the centre, so the decode error, not the distance, decides in or out.

The test shows that `Geo3DDocValuesField.newDistanceSort("point", 40, -74, 1000, WGS84)` gives distance +Infinity to a document stored exactly at the sort centre, because its 21-bit decoded point lies below the surface and is tested against the circle's nearly tangent plane without being projected back.

## Expected vs actual

Expected: a finite distance near 0 m (the document is at the centre of the 1 km circle). Actual: `Infinity`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/spatial3d/TestGeo3DDistanceSortCentreDocument.java](src/org/apache/lucene/spatial3d/TestGeo3DDistanceSortCentreDocument.java) (JUnit test)

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
     execute --select-class org.apache.lucene.spatial3d.TestGeo3DDistanceSortCentreDocument
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
