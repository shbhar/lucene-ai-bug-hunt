# Monitor's ParallelMatcher and PartitionMatcher catch only IOException per query: one registered query that throws an unchecked exception at match time makes Monitor.match throw and lose every match of the batch, and ParallelMatcher blocks forever once all its workers have died

Bug LFV-326 · Area: monitor / concurrent candidate matchers (ParallelMatcher, PartitionMatcher) · Priority: low · Impact: hang · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You register queries in a Monitor and match documents with ParallelMatcher or PartitionMatcher, which the package docs offer as a performance option, and one registered query throws an unchecked exception at match time, for example an IntPoint range query meeting a field indexed as LongPoint (IllegalArgumentException). You expect what the default serial matcher does: that query is reported in getErrors and every other match is returned. Instead the worker threads catch only IOException, so Monitor.match throws RuntimeException('Interrupted during match') and the whole batch's matches are lost, and with ParallelMatcher, once every worker has died, the caller blocks forever in LinkedBlockingQueue.put (executed: 4 threads, one registered query with 4 failing disjuncts plus 1,100 other queries, still blocked after 20 s, 77 interrupts to stop).

## Expected vs actual

- Expected: You expect what the default serial matcher does: that query is reported in getErrors and every other match is returned.
- Actual: The worker threads catch only IOException, so Monitor.match throws RuntimeException('Interrupted during match') and the whole batch's matches are lost, and with ParallelMatcher, once every worker has died, the caller blocks forever in LinkedBlockingQueue.put (executed: 4 threads, one registered query with 4 failing disjuncts plus 1,100 other queries, still blocked after 20 s, 77 interrupts to stop).

## Files

- [src/org/apache/lucene/monitor/TestConcurrentMatcherQueryErrors.java](src/org/apache/lucene/monitor/TestConcurrentMatcherQueryErrors.java) (JUnit test)

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
     execute --select-class org.apache.lucene.monitor.TestConcurrentMatcherQueryErrors
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
