# Monitor's QueryDecomposer ignores minimumNumberShouldMatch=1 when a BooleanQuery has one required clause and no usable SHOULD clause, so Monitor.match reports queries that match nothing

Bug LFV-325 · Area: monitor / query decomposition (QueryDecomposer.decomposeBoolean) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-325.html](../../bugs/LFV-325.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You register with the Monitor a BooleanQuery that has one required clause and minimumNumberShouldMatch=1 but no SHOULD clause that can match, for example 'topic AND at least one of [list]' built from an empty list, (+text:alpha)~1. Core Lucene defines this query to match nothing (BooleanQuery.rewrite returns MatchNoDocsQuery and IndexSearcher counts 0 hits), so you expect the Monitor never to report it. Instead the default QueryDecomposer keeps such a query whole only when minimumNumberShouldMatch &gt; 1 and otherwise replaces it by its lone required clause (plus its MUST_NOT exclusions), which the Monitor runs as the final matcher, so Monitor.match reports the query for every document containing alpha, for example 'alpha beta'.

The test shows that a default `Monitor` reports `(+text:alpha)~1`, a BooleanQuery with one required clause, `minimumNumberShouldMatch=1` and no SHOULD clause, as matching the document `alpha beta`, although core Lucene defines that query to match nothing (`QueryDecomposer` drops the msm=1 requirement).

## Expected vs actual

Expected 0 matches; actual 1 (`expected:<0> but was:<1>`), with `-ea` and `-da`; passes with the doc's one-condition `QueryDecomposer` fix.

## Files

- [src/org/apache/lucene/monitor/TestMonitorMinShouldMatchSingleRequiredClause.java](src/org/apache/lucene/monitor/TestMonitorMinShouldMatchSingleRequiredClause.java) (JUnit test)

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
     execute --select-class org.apache.lucene.monitor.TestMonitorMinShouldMatchSingleRequiredClause
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
