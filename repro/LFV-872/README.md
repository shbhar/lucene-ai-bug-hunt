# IndriQuery.visit reports the whole query as a leaf and never visits its clauses, so QueryVisitor.termCollector, UnifiedHighlighter term extraction, IndexSearcher's cumulative clause limit and RamUsageEstimator.sizeOf all ignore an IndriAndQuery's clauses

Bug LFV-872 · Area: search / IndriQuery (QueryVisitor traversal of Indri composite queries; consumers: QueryVisitor.termCollector, IndexSearcher's cumulative clause limit, RamUsageEstimator.sizeOf(Query), UnifiedHighlighter) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-872.html](../../bugs/LFV-872.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build an IndriAndQuery of ordinary TermQuery clauses, for example f:a and f:b, and inspect it with a QueryVisitor, as term extraction, highlighting, IndexSearcher's clause-count check and the query RAM estimate do. You expect the visitor to reach both clauses, as it does for a BooleanQuery over the same clauses, because Query#visit is documented to 'recurse through the query tree, visiting any child queries'. Instead IndriQuery.visit only calls visitor.visitLeaf(this), so termCollector returns , with maxClauseCount=4 a 51-clause IndriAndQuery and BooleanQuery(IndriAndQuery(3), IndriAndQuery(3)) both run while the same nesting of BooleanQuery clauses throws TooManyClauses, and RamUsageEstimator.sizeOf gives 1,024 bytes for 1,000 clauses against 1,169,024 for the BooleanQuery.

The test shows that `IndriQuery.visit` calls only `visitor.visitLeaf(this)`, so a `QueryVisitor` never reaches the clauses of an `IndriAndQuery`, although `Query#visit` is documented to visit any child queries.

## Expected vs actual

`QueryVisitor.termCollector` over `IndriAndQuery(f:a, f:b)` should collect `[f:a, f:b]`, as it does for a BooleanQuery of the same clauses; actual `[]`, with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/search/TestIndriAndQueryVisit.java](src/org/apache/lucene/search/TestIndriAndQueryVisit.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestIndriAndQueryVisit
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/indri-query-family-composite-score-boost-visitor-explain-laws/](../../models/indri-query-family-composite-score-boost-visitor-explain-laws/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
