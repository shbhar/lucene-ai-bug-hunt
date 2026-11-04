# SynonymQuery.Builder.addTerm stores a term before it checks the clause limit, so the term it rejects with TooManyClauses stays in the builder and build() returns a query over the limit that matches it (since 8.1.0)

Finding LFV-1350 · Area: core / search: SynonymQuery.Builder (clause limit, IndexSearcher.getMaxClauseCount) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1350.html](../../bugs/LFV-1350.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You lower the clause limit to 2 (IndexSearcher.setMaxClauseCount(2)), add terms a and b to a SynonymQuery.Builder for field f, catch the TooManyClauses that addTerm throws for a third term c, and call build(); you expect a query over a and b only, because the limit is 'the maximum number of clauses permitted' and the sibling BooleanQuery.Builder.add rejects a clause without storing it. Instead build() returns Synonym(f:a f:b f:c): addTerm stores the term (SynonymQuery.java:95) before it checks the limit (:96-98), and build() does not check again. A scored search(q, 10) then also returns the document that has only the rejected term c, while count(q), a FILTER or MUST_NOT clause and ConstantScoreQuery(q) throw TooManyClauses, because the non-scoring path rebuilds the terms as a BooleanQuery.

## Expected vs actual

- Expected: `build().getTerms()` is `[f:a, f:b]`: the rejected term `c` is not in the query.
- Actual: `AssertionError: expected:<[f:a, f:b]> but was:<[f:a, f:b, f:c]>`, with assertions on and off.

## Files

- [src/org/apache/lucene/search/TestSynonymQueryBuilderRejectedTerm.java](src/org/apache/lucene/search/TestSynonymQueryBuilderRejectedTerm.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): fails with assertions on and off (the rejected term f:c is still in the built query).
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestSynonymQueryBuilderRejectedTerm
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestSynonymQueryBuilderRejectedTerm.java](TestSynonymQueryBuilderRejectedTerm.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
