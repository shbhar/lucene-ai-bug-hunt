# Doc-values-rewritten multi-term queries report no leaf to a QueryVisitor, so the nested clause limit does not count them and, on main and the 11.0 branch, the query cache does not count the bytes of the wrapped query

Finding LFV-038 · Area: search / multi-term queries · Priority: high · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a query tree whose leaves are multi-term queries rewritten with MultiTermQuery.DOC_VALUES_REWRITE (or SortedSetDocValuesField/SortedDocValuesField.newSlowSetQuery) under IndexSearcher's maxClauseCount. You expect IndexSearcher.rewrite to count each such leaf toward the nested clause limit, and an LRUQueryCache to account for the terms a cached query holds. Instead MultiTermQueryDocValuesWrapper.visit calls visitor.getSubVisitor(FILTER, query) and throws the result away, so each leaf counts as 0: with maxClauseCount=8 a query with 10 such leaves raises no TooManyNestedClauses, and on main and the 11.0 branch, where LRUQueryCache sizes a cached query that is not Accountable through a QueryVisitor, a cache that holds one such 50,000-term TermInSetQuery (966,712 bytes by the query's own ramBytesUsed) reports 200 bytes in all (a second test, not in the repro folder; on 10.6.0, where the cache charges such a query a flat 1,024 bytes, it reports 1,184 bytes with or without this defect); search results stay correct.

## Expected vs actual

- Expected: You expect IndexSearcher.rewrite to count each such leaf toward the nested clause limit, and an LRUQueryCache to account for the terms a cached query holds.
- Actual: MultiTermQueryDocValuesWrapper.visit calls visitor.getSubVisitor(FILTER, query) and throws the result away, so each leaf counts as 0: with maxClauseCount=8 a query with 10 such leaves raises no TooManyNestedClauses, and on main and the 11.0 branch, where LRUQueryCache sizes a cached query that is not Accountable through a QueryVisitor, a cache that holds one such 50,000-term TermInSetQuery (966,712 bytes by the query's own ramBytesUsed) reports 200 bytes in all (a second test, not in the repro folder; on 10.6.0, where the cache charges such a query a flat 1,024 bytes, it reports 1,184 bytes with or without this defect); search results stay correct.

## Files

- [src/sa1verify/TestVerifySA1.java](src/sa1verify/TestVerifySA1.java) (JUnit test)

## Run it

- Tested on Lucene main f2b061406c4b (a commit of 2026-11-19): still reproduces.
- Tested on Lucene main b83101cdd2 (a commit of 2026-10-23): reproduced.
- How to read the outcome: the finding reproduces when the four testBug* tests fail and the four testControl* tests pass (each failure message states expected against actual).
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
java -ea -esa -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class sa1verify.TestVerifySA1
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
