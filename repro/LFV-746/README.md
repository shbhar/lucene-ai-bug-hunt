# Geo3D distance and path sorts (Geo3DDocValuesField.newDistanceSort, newPathSort) skip the secondary SortField for hits outside the shape's bounding box once the queue bottom is +Infinity, so top-N keeps the earliest out-of-radius documents in doc order instead of the first N of the sorted order (released since 6.1.0)

Bug LFV-746 · Area: spatial3d / Geo3D distance sort comparator (Geo3DPointDistanceComparator.compareBottom bounds shortcut; Geo3DDocValuesField.newDistanceSort and newPathSort) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-746.html](../../bugs/LFV-746.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You sort a query with Sort(Geo3DDocValuesField.newDistanceSort(field, lat, lon, maxRadiusMeters, planet), secondary SortField) and expect search(q, n, sort) to return the first n hits of the sorted order: every hit outside the radius reports distance +Infinity (like a document without the field), so the secondary field should order them. Instead, five documents at latitude 60 sorted around (0,0) with a 1,000 km radius and a secondary 'tie' key descending give top 2 = [1, 0] although the full order is [4, 3, 2, 1, 0] (WGS84; the same on SPHERE with a 0.2 planet-radius circle; with a sixth document missing the field, [5, 1] instead of [5, 4]), and newPathSort does the same. The cause: once the queue bottom is +Infinity, compareBottom rejects (-1) every later document whose points lie outside the shape's bounding box instead of reporting a tie (0), so the secondary SortField is never consulted.

The test shows that `Geo3DDocValuesField.newDistanceSort` plus a secondary SortField returns the wrong top-N when all hits are outside the radius (all +Infinity): 5 WGS84 docs at latitude 60, radius 1,000 km around (0,0), secondary `tie` descending.

## Expected vs actual

Expected (Sort breaks the +Infinity tie with the second SortField): `search(q, 2, sort)` = first 2 hits of `search(q, 100, sort)` = [4, 3]. Actual: [1, 0] (`expected:<[[4, 3]]> but was:<[[1, 0]]>`).

## Files

- [src/org/apache/lucene/spatial3d/TestGeo3DDistanceSortOutsideRadiusTie.java](src/org/apache/lucene/spatial3d/TestGeo3DDistanceSortOutsideRadiusTie.java) (JUnit test)

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
     execute --select-class org.apache.lucene.spatial3d.TestGeo3DDistanceSortOutsideRadiusTie
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
