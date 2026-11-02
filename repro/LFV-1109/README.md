# PointRangeQuery rewrites a range that covers every indexed point to FieldExistsQuery without checking that each leaf has points, so on a MultiReader whose other index stores the field without points it also matches (or, under MUST_NOT, drops) documents that have no point

Finding LFV-1109 · Area: core / search: PointRangeQuery.rewrite (GITHUB#14609 full-coverage rewrite to FieldExistsQuery, new in 10.3.0) on composite readers · Priority: medium · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open a MultiReader over two separately built indexes, one whose document has field p only as a doc value (5000, no point) and one whose document has LongField p=100, and count LongPoint.newRangeQuery("p", 0, 1000); you expect 1, which is what each index gives on its own (0 + 1) and what 10.2.2 returns. You get 2 with no error: since 10.3.0 (#14609) PointRangeQuery.rewrite sees that the reader-wide point range [100, 100] lies inside the query and, because every leaf has doc values, norms or vectors for p, replaces the query with FieldExistsQuery(p), which also matches the document that has no point. Under a MUST_NOT clause the same rewrite drops that document instead.

## Expected vs actual

- Expected: 1 hit: only index 2's document has a point, and 100 is in [0, 1000] (10.2.2 returns 1).
- Actual: `java.lang.AssertionError: expected:<1> but was:<2>`: the point-less document with value 5000 also matches.

## Files

- [src/org/apache/lucene/search/TestPointRangeRewriteLeafWithoutPoints.java](src/org/apache/lucene/search/TestPointRangeRewriteLeafWithoutPoints.java) (JUnit test)

## Run it

- Tested on Lucene main e1faeba08bde (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestPointRangeRewriteLeafWithoutPoints
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this finding's part of the hunt used: [../../models/pointrange-rewrite-fieldexists-ignores-leaves-without-points/](../../models/pointrange-rewrite-fieldexists-ignores-leaves-without-points/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
