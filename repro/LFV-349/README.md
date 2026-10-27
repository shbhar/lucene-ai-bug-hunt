# Monitor.register throws IllegalArgumentException 'Cannot build a conjunction (or disjunction) with no children' for a legal query, or a part of one, that reports no terms to the query visitor (an empty TermInSetQuery, an inverted TermRangeQuery, RegexpQuery '#', a term-less PhraseQuery or SynonymQuery), and the other queries of that commit chunk are not registered either

Bug LFV-349 · Area: monitor / presearcher query analysis (QueryAnalyzer, QueryTree; default TermFilteredPresearcher) · Priority: medium · Impact: crash · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You register a query with a Lucene Monitor that contains a clause matching no term, for example 'text:apple OR id:' where the id list happens to be empty, or the inverted range 'text:[m TO c]' typed into the classic QueryParser. You expect it to be registered: it is a legal query, and the Monitor already indexes queries it cannot analyse under a catch-all token. Instead register throws IllegalArgumentException 'Cannot build a conjunction with no children', and the other queries passed in the same call (the same internal chunk) are not registered either.

## Expected vs actual

- Expected: You expect it to be registered: it is a legal query, and the Monitor already indexes queries it cannot analyse under a catch-all token.
- Actual: register throws IllegalArgumentException 'Cannot build a conjunction with no children', and the other queries passed in the same call (the same internal chunk) are not registered either.

## Files

- [src/org/apache/lucene/monitor/TestRegisterQueryWithoutTerms.java](src/org/apache/lucene/monitor/TestRegisterQueryWithoutTerms.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
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
     execute --select-class org.apache.lucene.monitor.TestRegisterQueryWithoutTerms
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
