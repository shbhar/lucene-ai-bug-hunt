# A ReferenceManager refresh that throws is reported to its refresh listeners as afterRefresh(false), the same signal as 'nothing changed', so ControlledRealTimeReopenThread.waitForGeneration returns true for a change the current searcher does not contain and LiveFieldValues drops the values it buffered

Bug LFV-312 · Area: search / ReferenceManager refresh listeners (ControlledRealTimeReopenThread, LiveFieldValues) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You add, delete or update a document and wait for it with ControlledRealTimeReopenThread.waitForGeneration (or record its value in LiveFieldValues), and the next refresh of the SearcherManager throws, for example because a warm-up query in the SearcherFactory fails or an I/O error hits while the new segment is opened. You expect waitForGeneration to keep waiting or time out, and LiveFieldValues to keep the value, because the old searcher is still current and does not contain the change. Instead ReferenceManager.doMaybeRefresh calls every listener's afterRefresh(false) from a finally block, the same call a refresh that found nothing to do makes, so ControlledRealTimeReopenThread advances its searching generation and wakes the waiters with true, and LiveFieldValues throws away its buffered values.

## Expected vs actual

- Expected: You expect waitForGeneration to keep waiting or time out, and LiveFieldValues to keep the value, because the old searcher is still current and does not contain the change.
- Actual: ReferenceManager.doMaybeRefresh calls every listener's afterRefresh(false) from a finally block, the same call a refresh that found nothing to do makes, so ControlledRealTimeReopenThread advances its searching generation and wakes the waiters with true, and LiveFieldValues throws away its buffered values.

## Files

- [src/org/apache/lucene/search/TestFailedRefreshReportedAsNoChange.java](src/org/apache/lucene/search/TestFailedRefreshReportedAsNoChange.java) (JUnit test)
- [src2/org/apache/lucene/search/TestFailedRefreshReportedAsNoChange.java](src2/org/apache/lucene/search/TestFailedRefreshReportedAsNoChange.java) (JUnit test)
- [src3/org/apache/lucene/search/TestFailedRefreshReportedAsNoChange.java](src3/org/apache/lucene/search/TestFailedRefreshReportedAsNoChange.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes-src -cp "$CP" $(find src -name '*.java')
javac -d classes-src2 -cp "$CP" $(find src2 -name '*.java')
javac -d classes-src3 -cp "$CP" $(find src3 -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestFailedRefreshReportedAsNoChange
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src2:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestFailedRefreshReportedAsNoChange
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes-src3:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestFailedRefreshReportedAsNoChange
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/nrt-reference-managers/](../../models/nrt-reference-managers/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
