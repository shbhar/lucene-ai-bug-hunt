# Flexible StandardQueryParser: an exclusive int/long point bound at MAX/MIN wraps around, so the query matches every document with the field instead of none

Bug LFV-050 · Area: queryparser / flexible · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse i:{2147483647 TO *], i&gt;2147483647, l&gt;9223372036854775807 or i&lt;"-2147483648" with the flexible StandardQueryParser and a PointsConfig for the field. You expect no hits, because no int is larger than Integer.MAX_VALUE (or smaller than MIN_VALUE). Instead PointRangeQueryNodeBuilder adds or subtracts 1 for the exclusive bound without an overflow check, so MAX+1 wraps to MIN, the query becomes the full range and matches every document with the field (5 of 5 in the repro; {MAX TO MAX} matches all but one).

The test shows that the flexible `StandardQueryParser` with an Integer `PointsConfig` turns `i:{2147483647 TO *]` (greater than `Integer.MAX_VALUE`) into a query that matches every document with the field, because `MAX_VALUE + 1` wraps to `MIN_VALUE`.

## Expected vs actual

Expected 0 hits on an index with one `IntPoint` doc (`i=0`); actual: 1 hit (`expected:<0> but was:<1>`), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/queryparser/flexible/standard/TestExclusivePointBoundAtMaxValue.java](src/org/apache/lucene/queryparser/flexible/standard/TestExclusivePointBoundAtMaxValue.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.flexible.standard.TestExclusivePointBoundAtMaxValue
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/study-bkd-points-proof/](../../models/study-bkd-points-proof/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
