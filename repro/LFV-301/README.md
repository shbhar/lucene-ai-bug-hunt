# PointVectorStrategy misses a point at longitude +180 or -180 when a rectangle query that does not cross the dateline has its edge on that meridian with the other sign (-180 vs +180)

Bug LFV-301 · Area: spatial-extras / PointVectorStrategy (Intersects and IsWithin rectangle queries: makeWithin x filter) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-301.html](../../bugs/LFV-301.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index a point with PointVectorStrategy in the default geo context and search it with SpatialOperation.Intersects or IsWithin and a rectangle that does not cross the dateline but has one edge on the antimeridian, expecting the point to be found whenever spatial4j's relate, which defines these predicates, says it is inside (in geo, -180 and +180 are the same meridian). Instead a point stored at x=+180 is missed by a box whose west edge is -180 (Pt(180,0) with rect(-180,-170,-10,10): 0 hits, oracle 1), and a point at x=-180 by a box whose east edge is +180 (Pt(-180,0) with rect(170,180,-10,10)). BBoxStrategy and both prefix-tree strategies in the same module return the hit; PointVectorStrategy.makeWithin filters x with one plain DoublePoint range [minX, maxX] for every non-crossing box, so +180 and -180 are different values to it.

The test shows that `PointVectorStrategy` (geo context) misses a point at longitude +180 for an Intersects box whose west edge is -180, the same meridian.

## Expected vs actual

Expected 1 hit for `rect(-180,-170,-10,10)` with `Pt(180,0)` indexed; actual 0 (`expected:<1> but was:<0>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/spatial/vector/TestPointVectorDatelineEdge.java](src/org/apache/lucene/spatial/vector/TestPointVectorDatelineEdge.java) (JUnit test)

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
     execute --select-class org.apache.lucene.spatial.vector.TestPointVectorDatelineEdge
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
