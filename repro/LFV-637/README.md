# Monitor's ParallelMatcher starts blocking worker tasks in its constructor that only finish releases: a Monitor#match that fails before finish (an exception such as TooManyClauses from the presearcher, or a rejected submit) leaves its workers parked on the executor forever, and an executor that runs a worker on the calling thread (CallerRunsPolicy on a saturated pool) makes Monitor#match hang

Bug LFV-637 · Area: monitor / ParallelMatcher worker lifecycle (Monitor#match) · Priority: low · Impact: hang · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You match documents with Monitor#match using ParallelMatcher.factory(executor, matcher, threads), for example on Executors.newFixedThreadPool(threads), and you expect what the default serial matcher does: Monitor#match returns the matches or throws, and leaves nothing running on your executor. Instead the ParallelMatcher constructor submits `threads` workers that only finish can release, so when the match throws before finish (here IndexSearcher.TooManyClauses from the presearcher for a document with terms in 1,025 fields used by registered queries, the same exception the serial matcher throws) the workers stay parked forever and the next valid match on the same pool never returns. A rejected submit leaks the already-accepted workers the same way, and with CallerRunsPolicy on a saturated pool the calling thread runs a worker itself and Monitor#match never returns.

## Expected vs actual

- Expected: You expect what the default serial matcher does: Monitor#match returns the matches or throws, and leaves nothing running on your executor.
- Actual: The ParallelMatcher constructor submits `threads` workers that only finish can release, so when the match throws before finish (here IndexSearcher.TooManyClauses from the presearcher for a document with terms in 1,025 fields used by registered queries, the same exception the serial matcher throws) the workers stay parked forever and the next valid match on the same pool never returns.

## Files

- [src/org/apache/lucene/monitor/TestParallelMatcherWorkersReleasedOnFailure.java](src/org/apache/lucene/monitor/TestParallelMatcherWorkersReleasedOnFailure.java) (JUnit test)
- [src2/org/apache/lucene/monitor/TestParallelMatcherWorkersReleasedOnFailure.java](src2/org/apache/lucene/monitor/TestParallelMatcherWorkersReleasedOnFailure.java) (JUnit test)

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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestParallelMatcherWorkersReleasedOnFailure
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestParallelMatcherWorkersReleasedOnFailure
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/executor-pluggable-behaviour-sweep/](../../models/executor-pluggable-behaviour-sweep/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
