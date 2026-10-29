# LongRange/DoubleRange#getQuery(fastMatchQuery,...) range queries report themselves as a leaf in visit and never visit their fast-match query, so IndexSearcher's maxClauseCount check never counts the fast-match query

Bug LFV-917 · Area: facet / range queries (LongRange/DoubleRange ValueSourceQuery and MultiValueSourceQuery QueryVisitor traversal; consumers: IndexSearcher's cumulative clause limit, QueryVisitor.termCollector, RamUsageEstimator.sizeOf(Query)) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build LongRange#getQuery(fastMatch, LongValuesSource) (or the DoubleRange or multi-valued overloads) with a fast-match query of 1,200 term clauses (two required 600-term disjunctions) and search it with the default maxClauseCount of 1024. You expect TooManyNestedClauses, which you do get when the same fast-match query is a sibling FILTER clause, because the check counts 'total clauses cumulatively in all of its children', Query#visit is documented to visit 'any child queries', and getQuery documents fastMatchQuery as 'a query to use as a filter'. Instead the search runs and returns the correct hits: ValueSourceQuery.visit and MultiValueSourceQuery.visit only call visitor.visitLeaf(this), so the fast-match query, which stays in the rewritten query and is executed as the approximation in createWeight, is seen by no clause check at all, and QueryVisitor.termCollector returns none of its terms.

The test shows that `LongRange#getQuery(fastMatchQuery, valuesSource)` with a 1,200-clause fast-match query is searched without `TooManyNestedClauses` under the default `maxClauseCount` of 1024, because the range query's `visit()` never visits its fast-match query.

## Expected vs actual

Expected `IndexSearcher.TooManyNestedClauses`; actual: the search runs with no exception, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/facet/range/TestRangeQueryFastMatchClauseLimit.java](src/org/apache/lucene/facet/range/TestRangeQueryFastMatchClauseLimit.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.range.TestRangeQueryFastMatchClauseLimit
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
