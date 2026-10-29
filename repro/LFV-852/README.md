# Lucene Monitor's ScoringMatch and ExplainingMatch score a registered DisjunctionMaxQuery with a tie-breaker below 1 as the sum of its matching disjuncts instead of max + tieBreaker * others

Bug LFV-852 · Area: monitor / query decomposition and match scoring (QueryDecomposer DisjunctionMaxQuery split, ScoringMatch/ExplainingMatch resolve) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-852.html](../../bugs/LFV-852.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You register a DisjunctionMaxQuery such as (title:apple | body:apple) with tie-breaker 0 in a Monitor and match a document that has apple in both fields with ScoringMatch or ExplainingMatch, expecting the score the query itself gives that document: the best field's score plus tie times the others, which is what IndexSearcher returns (0.1798013 in an independent test). Instead the Monitor reports the sum of both field scores (0.3105659), because the default QueryDecomposer splits the DisjunctionMaxQuery into one query per disjunct and the scoring matchers add up the scores of the parts. In the 'albino elephant' example of DisjunctionMaxQuery's own javadoc, this makes the document with 'albino' in both fields tie with the document that matches both words, which the query is designed to rank higher.

The test shows that Lucene Monitor's ScoringMatch reports a registered DisjunctionMaxQuery with tie-breaker 0 as the sum of its matching disjuncts, because the default QueryDecomposer splits it and the scoring matcher adds the parts.

## Expected vs actual

Expected the query's own score on the document, max of the two field scores (`0.1798013`); actual: their sum (`0.3105659` = 0.1798013 + 0.1307646), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/monitor/TestMonitorDisMaxScore.java](src/org/apache/lucene/monitor/TestMonitorDisMaxScore.java) (JUnit test)

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
     execute --select-class org.apache.lucene.monitor.TestMonitorDisMaxScore
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/monitor-matcher-agreement/](../../models/monitor-matcher-agreement/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
