# Monitor's QueryDecomposer turns a BooleanQuery whose only required clause is a single FILTER clause into that clause as a scoring query, so ScoringMatch and ExplainingMatch report a positive score for a query (or query part) that scores 0, and twice the true score when such a query is nested in a disjunction

Bug LFV-853 · Area: monitor / query decomposition (QueryDecomposer.decomposeBoolean; ScoringMatch, ExplainingMatch) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-853.html](../../bugs/LFV-853.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You register with the Monitor a BooleanQuery whose only required clause is a FILTER clause, for example '#body:apple' or '(#body:apple) body:banana', and ask for scores with ScoringMatch or ExplainingMatch; since FILTER clauses 'do not participate in scoring', you expect the score IndexSearcher gives the same query: 0 for '#body:apple' and 0.13076457 for the nested query on the document 'apple banana'. Instead the default QueryDecomposer treats the FILTER clause like a MUST clause and replaces the query by that clause as a plain scoring query, so ExplainingMatch and ScoringMatch report 0.13076457 for '#body:apple' (0.26152915 for '(#body:apple)^2') and 0.26152915, twice the true score, for the nested query. Matching is correct; only the reported scores and explanations are wrong.

The test shows that the Monitor's default QueryDecomposer turns a FILTER-only clause into a scoring query, so ScoringMatch reports twice the true score for '(#body:apple) body:banana'.

## Expected vs actual

Expected IndexSearcher's score 0.13076457 (the FILTER clause does not score); actual 0.26152915.

## Files

- [src/org/apache/lucene/monitor/TestMonitorFilterClauseScore.java](src/org/apache/lucene/monitor/TestMonitorFilterClauseScore.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/monitor/build/libs/lucene-monitor-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestMonitorFilterClauseScore
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/monitor-matcher-agreement/](../../models/monitor-matcher-agreement/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
