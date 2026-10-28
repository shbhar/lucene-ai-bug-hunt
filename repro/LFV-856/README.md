# Lucene Monitor's QueryDecomposer collects a query's parts in a Set, so a sub-query that occurs more than once in a registered query (a repeated SHOULD or MUST clause, or a term shared by two nested disjunctions) is scored once by ScoringMatch and ExplainingMatch instead of once per occurrence

Bug LFV-856 · Area: monitor / query decomposition and match scoring (QueryDecomposer Set<Query> parts, ScoringMatch/ExplainingMatch resolve) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-856.html](../../bugs/LFV-856.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You register a query such as '(red apple) (green apple)' or 'apple apple' in a Monitor and match the document 'red green apple' with ScoringMatch or ExplainingMatch, expecting the score the query itself gives that document, which is what IndexSearcher returns, because BooleanQuery counts a repeated clause at every occurrence (0.5230583 and 0.26152915). Instead the Monitor reports 0.39229372 and 0.13076457: the default QueryDecomposer collects the query's parts in a Set, which keeps equal parts once, and the scoring matchers add up the scores of the distinct parts, so the shared 'apple' is counted once. Which queries match is not affected; only the scores and explanations of such queries are too low.

## Expected vs actual

- Expected: The Monitor reports the query's own score: 0.5230583 for `(red apple) (green apple)`, 0.26152915 for `apple apple` and `+apple +apple`.
- Actual: 0.39229372 and 0.13076457 (apple counted once), the same from `ExplainingMatch`; the controls pass.

## Files

- [src/org/apache/lucene/monitor/TestMonitorRepeatedSubqueryScore.java](src/org/apache/lucene/monitor/TestMonitorRepeatedSubqueryScore.java) (JUnit test)
- [src2/org/apache/lucene/monitor/TestMonitorRepeatedSubqueryScore.java](src2/org/apache/lucene/monitor/TestMonitorRepeatedSubqueryScore.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/monitor/build/libs/lucene-monitor-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestMonitorRepeatedSubqueryScore
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestMonitorRepeatedSubqueryScore
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestMonitorRepeatedPartScore.java](TestMonitorRepeatedPartScore.java)
- [TestMonitorRepeatedSubqueryScore.java](TestMonitorRepeatedSubqueryScore.java)
- [3-TestMonitorRepeatedPartScore.java](3-TestMonitorRepeatedPartScore.java)

Formal models and scripts this bug's part of the hunt used: [../../models/monitor-matcher-agreement/](../../models/monitor-matcher-agreement/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
