# PartitionMatcher's worker threads report failed queries into one unsynchronized HashMap, so MatchingQueries.getErrors() can miss queries that failed with an IOException in different partitions of one match, and such a query reads as one that ran and did not match (8.2.0 to 10.6.0, branch_11x and main)

Finding LFV-1551 · Area: monitor / concurrent candidate matchers (PartitionMatcher: the error report of a match) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You register 256 queries whose rewrite throws an IOException and match one document through PartitionMatcher.factory(executor, QueryMatch.SIMPLE_MATCHER, 8) on a pool of 8 threads. You expect MatchingQueries.getErrors() to hold all 256 query ids. Instead one or more ids were missing from it in 59 of 60 runs of that one match on main ec3b3cca8f10 (30 with assertions and 30 without, on a machine with 64 hardware threads), with no exception, so each missing query reads as one that ran and did not match. An earlier form of the test, 64 such queries and the match repeated up to 50 times, lost ids in each run that had the machine's 64 hardware threads (the latest first loss on record is the 17th match), with the matches themselves right, while the serial matcher, ParallelMatcher, one partition and a one-thread pool reported all 64 errors in the same runs. PartitionMatcher's workers run on the executor's threads and each reports a failed query with PartitionMatcher.this.reportError, an unsynchronized put into the outer matcher's one java.util.HashMap, so two reports made at the same time can lose an entry; it is a data race, so one run can pass with the defect present: pinned to one CPU the one match lost nothing in 5 of 5 runs, and the earlier form passed 30 of 30 times pinned to one CPU and 15 of 50 times pinned to two.

## Expected vs actual

- Expected: You expect MatchingQueries.getErrors() to hold all 256 query ids.
- Actual: One or more ids were missing from it in 59 of 60 runs of that one match on main ec3b3cca8f10 (30 with assertions and 30 without, on a machine with 64 hardware threads), with no exception, so each missing query reads as one that ran and did not match.

## Files

- [src/org/apache/lucene/monitor/TestPartitionMatcherReportsEveryError.java](src/org/apache/lucene/monitor/TestPartitionMatcherReportsEveryError.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main ec3b3cca8f10 (a commit of 2026-11-18): reproduced.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.AssertionError: failed queries missing from getErrors() expected:&lt;[]&gt; but was:&lt;[...]&gt; (other ids from run to run); it is a data race, so one run can pass with the finding present, more often on a machine with few cores: repeat the run before reading a pass.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestPartitionMatcherReportsEveryError
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
