# GroupingSearch#search never rewrites its group sort or within-group sort against the searcher, unlike IndexSearcher#search(Query, int, Sort): sorts that need a rewrite throw, and a legacy query() function sort silently orders groups, group documents and group heads with the default Similarity

Finding LFV-816 · Area: grouping / GroupingSearch (convenience API) · Priority: medium · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-816.html](../../bugs/LFV-816.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You group with GroupingSearch and set a group sort or within-group sort that depends on the query or the searcher, for example a sort on new QueryValueSource(q, 0).asDoubleValuesSource(), and expect the groups and their documents in the order IndexSearcher#search(query, n, sort) gives for the same Sort on the same searcher, because IndexSearcher rewrites the Sort and GroupingSearch#search is handed that searcher. Instead GroupingSearch passes both Sorts to its collectors as given (GroupingSearch.java:148, 160, 201-202, 228-233): on a ClassicSimilarity searcher the documents of one group come back as doc0=0.101, doc1=0.088, doc2=0.088 (default-BM25 scores) instead of doc1=1.0, doc2=1.0, doc0=0.894, the first group is g0 instead of g1, and ValueSource sort fields or fromQuery sorts throw. Rewriting both Sorts against the searcher at the start of groupByFieldOrFunction and groupByDocBlock fixes every case.

## Expected vs actual

- Expected: doc1=1.0 doc0=0.894: the order and sort values of `searcher.search(new MatchAllDocsQuery(), 10, sort)` on the same searcher.
- Actual: doc0=0.147 doc1=0.125 (`expected:<doc[1=1.0 doc0=0.8944271802902222]> but was:<doc[0=0.14679136872291565 doc1=0.12456752359867096]>`), with assertions on and off.

## Files

- [src/org/apache/lucene/search/grouping/TestGroupingSearchRewritesSorts.java](src/org/apache/lucene/search/grouping/TestGroupingSearchRewritesSorts.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces (the repro adapted to an API change on this commit).
- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- On 9.1 to 9.12, GroupDocs has public fields: write scoreDocs and groupSortValues instead of scoreDocs() and groupSortValues() (two lines).
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/grouping/build/libs/lucene-grouping-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.grouping.TestGroupingSearchRewritesSorts
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestGroupingSearchSortRewrite.java](TestGroupingSearchSortRewrite.java)

1 other file(s) of the recorded repro are not exported.

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
