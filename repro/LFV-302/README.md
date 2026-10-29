# ShapeAreaValueSource leaves its multiplier out of equals/hashCode, so area sources with different multipliers compare equal and BooleanQuery merges or drops their clauses: wrong, clause-order-dependent scores, and wrong hits for area filters

Bug LFV-302 · Area: spatial-extras / value sources (ShapeAreaValueSource equality, consumed by FunctionScoreQuery, area filters and BooleanQuery duplicate-clause handling) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You index one rectangle with BBoxStrategy (planar area 800) and score it with two FunctionScoreQuery SHOULD clauses whose sources differ only in the multiplier, new ShapeAreaValueSource(strategy.makeShapeValueSource, ctx, false, 1.0) and the same with 3.0, expecting the sum 800 + 2400 = 3200. Instead ShapeAreaValueSource equals/hashCode compare only geoArea and the shape source, so the two clauses are equal, BooleanQuery.rewrite merges them into one clause with boost 2 that keeps only the first multiplier, and the document scores 1600 (4800 with the clauses swapped). Area filters built on such sources are de-duplicated the same way, so a FILTER or MUST_NOT area clause can be silently dropped and the hit set changes (2550 hits expected, 3800 returned).

The test shows that two ShapeAreaValueSource instances that differ only in the multiplier compare equal, so a SHOULD disjunction of FunctionScoreQuery clauses over them is merged into one clause and loses the second multiplier.

## Expected vs actual

Expected: one rectangle of area 800 scores 800 x 1 + 800 x 3 = 3200. Actual (-ea and -da): 1600.

## Files

- [src/org/apache/lucene/spatial/util/TestShapeAreaValueSourceMultiplier.java](src/org/apache/lucene/spatial/util/TestShapeAreaValueSourceMultiplier.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/spatial-extras/build/libs/lucene-spatial-extras-$V.jar
CP=$CP:$LUCENE/lucene/spatial3d/build/libs/lucene-spatial3d-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.spatial.util.TestShapeAreaValueSourceMultiplier
```

The module(s) spatial-extras also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
