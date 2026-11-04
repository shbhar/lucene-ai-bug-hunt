# QueryTimeListener.timingMatcher never finishes the matcher it wraps, so wrapping ParallelMatcher or PartitionMatcher makes Monitor.match silently return no matches and no errors (since 8.2.0)

Finding LFV-1359 · Area: monitor / matcher composition (QueryTimeListener.timingMatcher wrapper around CandidateMatcher; ParallelMatcher, PartitionMatcher) · Priority: low · Impact: silent wrong results · Novelty: new

Finding page: [../../bugs/LFV-1359.html](../../bugs/LFV-1359.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You register a TermQuery f:a in a Monitor and match the document 'a b' with QueryTimeListener.timingMatcher(ParallelMatcher.factory(executor, QueryMatch.SIMPLE_MATCHER, 2), listener). You expect the 1 match that ParallelMatcher.factory alone reports, plus a timing callback per query. Instead getMatchCount() is 0 and getErrors() is empty: the wrapper forwards matchQuery() to the inner matcher, but its doFinish() only copies the inner matcher's match lists and never calls the inner finish(), and ParallelMatcher and PartitionMatcher collect their matches (and errors) only in that finish step.

## Expected vs actual

- Expected: 1 match, as `PartitionMatcher.factory(...)` alone returns.
- Actual: 0 matches (`AssertionError: expected:<1> but was:<0>`) and no exception, assertions on and off. The test checks only the count; a probe with the same input also showed 0 errors and the listener called for `q`.

## Files

- [src/org/apache/lucene/monitor/TestTimingMatcherFinish.java](src/org/apache/lucene/monitor/TestTimingMatcherFinish.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/monitor/build/libs/lucene-monitor-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestTimingMatcherFinish
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestTimingMatcherFinish.java](TestTimingMatcherFinish.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
