# NumberRangePrefixTreeStrategy with setPointsOnly(true): an Intersects query for a date or number unit coarser than full precision (a year, month, day,...) matches no documents

Bug LFV-612 · Area: spatial-extras / prefix-tree strategies (NumberRangePrefixTreeStrategy over DateRangePrefixTree or a custom NumberRangePrefixTree; the pointsOnly TermQuery shortcut in RecursivePrefixTreeStrategy.makeGridShapeIntersectsQuery) · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index full-precision dates such as 2014-10-23T21:22:33.159 with a NumberRangePrefixTreeStrategy over DateRangePrefixTree on which you called setPointsOnly(true), and search makeQuery(new SpatialArgs(SpatialOperation.Intersects, tree.parseShape("2014-10"))). You expect the documents from October 2014, because each point lies inside that month, IsWithin with the same shape and the same query with pointsOnly=false find them, and the LUCENE-7845 CHANGES entry describes this pointsOnly TermQuery shortcut as an optimization for 'a simple date interval (e.g. 1 month)'. Instead RecursivePrefixTreeStrategy.makeGridShapeIntersectsQuery builds a TermQuery on the month's token plus the leaf byte, a term a pointsOnly index never contains (each point stores every coarser level only as a non-leaf prefix term), so the query matches nothing; the same happens for a year, day, hour, minute or second and for a range that normalizes to one unit.

## Expected vs actual

- Expected: You expect the documents from October 2014, because each point lies inside that month, IsWithin with the same shape and the same query with pointsOnly=false find them, and the LUCENE-7845 CHANGES entry describes this pointsOnly TermQuery shortcut as an optimization for 'a simple date interval (e.g. 1 month)'.
- Actual: RecursivePrefixTreeStrategy.makeGridShapeIntersectsQuery builds a TermQuery on the month's token plus the leaf byte, a term a pointsOnly index never contains (each point stores every coarser level only as a non-leaf prefix term), so the query matches nothing; the same happens for a year, day, hour, minute or second and for a range that normalizes to one unit.

## Files

- [src/org/apache/lucene/spatial/prefix/TestNumberRangePointsOnlyUnitIntersects.java](src/org/apache/lucene/spatial/prefix/TestNumberRangePointsOnlyUnitIntersects.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.prefix.TestNumberRangePointsOnlyUnitIntersects
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
