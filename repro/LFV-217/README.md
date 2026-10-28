# JoinUtil's numeric join on Float or Double throws 'values are out of order' when the from side has two or more distinct negative values

Bug LFV-217 · Area: join / JoinUtil numeric (point-based) query-time join · Priority: medium · Impact: crash · Novelty: new

Bug page: [../../bugs/LFV-217.html](../../bugs/LFV-217.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build JoinUtil.createJoinQuery("fromF", false, "toF", Float.class, fromQuery, searcher, ScoreMode.None) where the from documents store -1 and -2 in a FloatDocValuesField and the to documents index FloatPoint -1, -2 and 3, and expect a query that matches the two to documents with -1 and -2, because the javadoc lists Float and Double as supported numeric types with no sign restriction. Instead createJoinQuery throws IllegalArgumentException 'values are out of order: saw [40 7f ff ff] before [3f ff ff ff]' (that is, -1 before -2; releases before 10.4.0 show an UnsupportedOperationException raised while that message is built), and Double and every scoring ScoreMode fail the same way. The cause is that JoinUtil sorts the collected Float/Double join values as raw IEEE bits in signed-long order, which for negative numbers is the reverse of numeric order, and streams them in that order into PointInSetQuery / PointInSetIncludingScoreQuery, which require strictly ascending points.

## Expected vs actual

- Expected: `createJoinQuery` returns a query, and `searcher.count(join)` is 2 (the to documents -1 and -2) for `Float` and `Double`.
- Actual: `createJoinQuery` throws `IllegalArgumentException: values are out of order: saw [40 7f ff ff] before [3f ff ff ff]` (-1 before -2; Double likewise); 4 of 4 tests fail.

## Files

- [src/org/apache/lucene/search/join/TestNumericJoinNegativeFloats.java](src/org/apache/lucene/search/join/TestNumericJoinNegativeFloats.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/join/build/libs/lucene-join-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.join.TestNumericJoinNegativeFloats
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestNumericJoinNegativeFloats.java](TestNumericJoinNegativeFloats.java)
- [TestNumericJoinSignedFloatOrder.java](TestNumericJoinSignedFloatOrder.java)
- [fix.diff](fix.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
