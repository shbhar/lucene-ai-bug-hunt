# ConcurrentQueryLoader workers exit at the first exception from Monitor.register and are never replaced, so once every worker is gone and the queue is full add() blocks forever and close() does not get to report the IOException, and an unchecked exception from register is not recorded, so close() can return normally with that worker's queries unregistered

Finding LFV-513 · Area: monitor / bulk query loading (ConcurrentQueryLoader worker error handling) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You bulk-load standing queries into a Monitor with the documented pattern, loader.add(q) in a loop inside try-with-resources, and Monitor.register fails inside the loader's worker threads, for example because the query index cannot fsync or create a file. You expect the load to stop and close() to throw the IOException, which the loader records for exactly that purpose. Instead each worker leaves its loop at its first failure and is never replaced, so once every worker is gone nothing drains the bounded queue: the next add() calls block in LinkedBlockingQueue.put forever, close() is never reached and the error is never reported (repro: 1 worker, queue size 4, one simulated fsync failure; add() is still blocked 30 s later).

## Expected vs actual

- Expected: You expect the load to stop and close() to throw the IOException, which the loader records for exactly that purpose.
- Actual: Each worker leaves its loop at its first failure and is never replaced, so once every worker is gone nothing drains the bounded queue: the next add() calls block in LinkedBlockingQueue.put forever, close() is never reached and the error is never reported (repro: 1 worker, queue size 4, one simulated fsync failure; add() is still blocked 30 s later).

## Files

- [src/org/apache/lucene/monitor/TestConcurrentQueryLoaderAfterIOException.java](src/org/apache/lucene/monitor/TestConcurrentQueryLoaderAfterIOException.java) (JUnit test)

## Run it

- Tested on Lucene main f2b061406c4b (a commit of 2026-11-19): still reproduces.
- Tested on Lucene main ea57360912ed (a commit of 2026-10-24): reproduced.
- What this folder covers: the test shows the first part of the title: add() is still blocked 30 s after the loader's only worker stopped at an IOException from Monitor.register (one worker, a queue of 4). The second part of the title, an unchecked exception from register that is not recorded, so that close() returns normally with that worker's queries unregistered (the outcome behind the impact class shown above), has no test in this folder. Run on 2026-11-18 on Lucene main 1cfa86310f76, with and without assertions: 1 test found, 1 test failed (add() still blocked 30 s after the loader's only worker stopped).
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.monitor.TestConcurrentQueryLoaderAfterIOException
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
