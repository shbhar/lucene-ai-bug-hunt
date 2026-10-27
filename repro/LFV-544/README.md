# Geo3D distance, path and outside-distance sorts (Geo3DDocValuesField) drop hits under IndexSearcher.searchAfter: compareTop compares the cursor, which is in meters, with the document's distance in planet units, so on WGS84 every page after the first loses all finite-distance hits (released since 6.1.0)

Bug LFV-544 · Area: spatial3d / Geo3DDocValuesField sort comparators (Geo3DPointDistanceComparator, Geo3DPointOutsideDistanceComparator: compareTop under searchAfter paging) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-544.html](../../bugs/LFV-544.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You sort by Geo3DDocValuesField.newDistanceSort (or the path or outside-distance sorts) on the default WGS84 planet model, take the last FieldDoc of page 1 and call IndexSearcher.searchAfter(last, query, n, sort), expecting the next hits in distance order, as searchAfter documents. Instead the comparator's value returns the distance in meters (as the factories' javadoc says), but compareTop compares that meters cursor with the document's distance in planet units (distances on a planet of mean radius 1), so every document with a finite distance looks 'already collected on a previous page' and is skipped. With six documents 3 to 8 degrees north of the origin and page size 2, page 2 should be [2, 3, 4, 5] and is ; on PlanetModel.SPHERE, where meters and planet units coincide, the same paging is correct.

## Expected vs actual

- Expected: Page 2 is `[2, 3, 4, 5]`, the rest of the sorted order, for every sort.
- Actual: Page 2 is `` for all seven sorts on WGS84 and for CLARKE_1866 (8 of 9 tests fail); only the SPHERE control passes.

## Files

- [src/org/apache/lucene/spatial3d/TestGeo3DSortSearchAfter.java](src/org/apache/lucene/spatial3d/TestGeo3DSortSearchAfter.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.spatial3d.TestGeo3DSortSearchAfter
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestGeo3DSortSearchAfter.java](TestGeo3DSortSearchAfter.java)
- [2-TestGeo3DSortSearchAfter.java](2-TestGeo3DSortSearchAfter.java)
- [TestGeo3DSortPaging.java](TestGeo3DSortPaging.java)
- [TestGeo3DComparatorTopRoundTrip.java](TestGeo3DComparatorTopRoundTrip.java)
- [TestGeo3DCursorUnits.java](TestGeo3DCursorUnits.java)
- [Geo3DSearchAfterHistoryProbe.java](Geo3DSearchAfterHistoryProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
