# Geo3DPoint polygon and path queries report a constant 592-byte ramBytesUsed regardless of shape size, because PointInGeo3DShapeQuery prices its GeoShape as a 256-byte unknown object, so LRUQueryCache can hold many times its maxRamBytesUsed (since 8.2.0)

Bug LFV-400 · Area: spatial3d / PointInGeo3DShapeQuery RAM accounting (Accountable#ramBytesUsed; core LRUQueryCache RAM budget) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You filter with a Geo3DPoint polygon or path query, for example newLargePolygonQuery with 2,000 edges or newPathQuery with 200 points, and expect its ramBytesUsed to estimate the heap it holds, which is what Accountable promises and what LRUQueryCache charges against its maxRamBytesUsed budget. Instead PointInGeo3DShapeQuery.ramBytesUsed passes the shape and its bounds to RamUsageEstimator.sizeOfObject, which prices every object that is not Accountable (no GeoShape is) at a fixed 256 bytes, so every such query reports 592 bytes whatever its size: 4.9x too low for an 8-edge polygon, 47x for 99 edges, about 2,300x for the 2,000-edge large polygon (1,360,960 bytes retained). An LRUQueryCache with a 1 MiB budget therefore keeps 8 such filters that retain 10.9 MB, accounts about 6 KB for them and evicts nothing; the hits are correct.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: PointInGeo3DShapeQuery.ramBytesUsed passes the shape and its bounds to RamUsageEstimator.sizeOfObject, which prices every object that is not Accountable (no GeoShape is) at a fixed 256 bytes, so every such query reports 592 bytes whatever its size: 4.9x too low for an 8-edge polygon, 47x for 99 edges, about 2,300x for the 2,000-edge large polygon (1,360,960 bytes retained).

## Files

- [src/org/apache/lucene/spatial3d/TestGeo3DShapeQueryRamBytesUsed.java](src/org/apache/lucene/spatial3d/TestGeo3DShapeQueryRamBytesUsed.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
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
     execute --select-class org.apache.lucene.spatial3d.TestGeo3DShapeQueryRamBytesUsed
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
