# IntervalQuery equals/hashCode ignore the score function (pivot/exp), so BooleanQuery merges two differently scored IntervalQuery clauses into one and returns wrong, clause-order-dependent scores

Bug LFV-292 · Area: queries / intervals (IntervalQuery equality, consumed by BooleanQuery and DisjunctionMaxQuery duplicate-clause handling) · Priority: low · Impact: silent wrong results · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build two IntervalQuery objects on the same field and term that differ only in their scoring pivot, new IntervalQuery("f", Intervals.term("a"), 1f) and the same with 100f, and combine them as SHOULD clauses of a BooleanQuery. You expect the document 'a b a c a' to score the sum of the two clause scores, 0.75 + 0.0291 = 0.7791, because the javadoc documents pivot and exp as parameters that change the score. Instead IntervalQuery.equals/hashCode compare only the field and the intervals source, so the two queries are equal, BooleanQuery.rewrite merges them into the single clause (f:a)^2.0 that keeps only the first clause's pivot, and the document scores 1.5; with the clauses swapped it scores 2 x 0.0291 = 0.0583, so the score also depends on clause order.

## Expected vs actual

- Expected: You expect the document 'a b a c a' to score the sum of the two clause scores, 0.75 + 0.0291 = 0.7791, because the javadoc documents pivot and exp as parameters that change the score.
- Actual: IntervalQuery.equals/hashCode compare only the field and the intervals source, so the two queries are equal, BooleanQuery.rewrite merges them into the single clause (f:a)^2.0 that keeps only the first clause's pivot, and the document scores 1.5; with the clauses swapped it scores 2 x 0.0291 = 0.0583, so the score also depends on clause order.

## Files

- [src/org/apache/lucene/queries/intervals/TestIntervalQueryEqualsIgnoresScoreFunction.java](src/org/apache/lucene/queries/intervals/TestIntervalQueryEqualsIgnoresScoreFunction.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queries.intervals.TestIntervalQueryEqualsIgnoresScoreFunction
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
