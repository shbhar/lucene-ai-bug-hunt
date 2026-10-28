# Float/Byte/Float16VectorSimilarityQuery.visit never visits the filter, so the filter's clauses escape IndexSearcher's cumulative maxClauseCount check and QueryVisitor term collection

Bug LFV-916 · Area: search / vector similarity queries (AbstractVectorSimilarityQuery QueryVisitor traversal; consumers: IndexSearcher's cumulative clause limit, QueryVisitor.termCollector, RamUsageEstimator.sizeOf(Query)) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build BooleanQuery(MUST a 600-term disjunction, MUST FloatVectorSimilarityQuery.Adaptive(field, target, 0f, filter = another 600-term disjunction)), 1,200 term clauses in all, and search it with the default maxClauseCount of 1024. You expect TooManyNestedClauses, which you do get when the same 600 clauses are a sibling FILTER clause, because the check counts 'total clauses cumulatively in all of its children' and Query#visit is documented to visit 'any child queries'. Instead the search runs: AbstractVectorSimilarityQuery.visit only calls visitor.visitLeaf(this), so the filter, which stays in the rewritten query and is executed in createWeight, is checked only on its own; ByteVectorSimilarityQuery and Float16VectorSimilarityQuery behave the same, and QueryVisitor.termCollector returns none of the filter's terms.

## Expected vs actual

- Expected: You expect TooManyNestedClauses, which you do get when the same 600 clauses are a sibling FILTER clause, because the check counts 'total clauses cumulatively in all of its children' and Query#visit is documented to visit 'any child queries'.
- Actual: The search runs: AbstractVectorSimilarityQuery.visit only calls visitor.visitLeaf(this), so the filter, which stays in the rewritten query and is executed in createWeight, is checked only on its own; ByteVectorSimilarityQuery and Float16VectorSimilarityQuery behave the same, and QueryVisitor.termCollector returns none of the filter's terms.

## Files

- [src/org/apache/lucene/search/TestVectorSimilarityQueryFilterClauseLimit.java](src/org/apache/lucene/search/TestVectorSimilarityQueryFilterClauseLimit.java) (JUnit test)
- [src/org/apache/lucene/search/TestVectorSimilarityQueryFilterClauseLimitRelease.java](src/org/apache/lucene/search/TestVectorSimilarityQueryFilterClauseLimitRelease.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestVectorSimilarityQueryFilterClauseLimit

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestVectorSimilarityQueryFilterClauseLimitRelease
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
