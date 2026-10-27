# A Monitor.register call that IndexWriter rejects is not rolled back: the earlier versions of the queries in the call are deleted at the next commit or close, and until then getQuery and match disagree

Bug LFV-499 · Area: monitor / query index updates (WritableQueryIndex.commitWithoutNotify failure atomicity, reached through both Monitor.register overloads) · Priority: medium · Impact: data corruption · Novelty: new

Bug page: [../../bugs/LFV-499.html](../../bugs/LFV-499.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You update stored queries with Monitor.register and IndexWriter rejects one of the new query documents, for example register(q2 = f:y) where q2 carries a 40,000-byte value in a TermFilteredPresearcher filter field, so register throws IllegalArgumentException. You expect the queries of the call to keep their old versions, as they do when the Monitor rejects a query before writing and as IndexWriter.updateDocuments would guarantee for each id. Instead the Monitor has already queued the deletion of the old version of every query id in the call and switched its in-memory cache to the new versions: right away q2 matches nothing although getQuery("q2") still returns f:b, and the next unrelated register or deleteById, or close of a persistent Monitor, deletes the old versions for good (register(q2 rejected, q1 valid), then close and reopen, leaves neither q1 nor q2).

## Expected vs actual

- Expected: q2 keeps matching "b"; q2 survives the next register and close; q1 survives a call that threw on another query.
- Actual: q2 matches nothing while getQuery says b; the next register leaves [q3]; after close and reopen ; q1 deleted with the default configuration.

## Files

- [src/org/apache/lucene/monitor/TestFailedRegisterIsNotRolledBack.java](src/org/apache/lucene/monitor/TestFailedRegisterIsNotRolledBack.java) (JUnit test)
- [src/org/apache/lucene/monitor/TestFailedRegisterOnIOError.java](src/org/apache/lucene/monitor/TestFailedRegisterOnIOError.java) (JUnit test)

## Run it

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
     execute --select-class org.apache.lucene.monitor.TestFailedRegisterIsNotRolledBack \
       --select-class org.apache.lucene.monitor.TestFailedRegisterOnIOError
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestRejectedRegisterKeepsQueries.java](TestRejectedRegisterKeepsQueries.java)
- [option-a-with-test.diff](option-a-with-test.diff)
- [TestFailedRegisterOnIOError.java](TestFailedRegisterOnIOError.java)
- [TestFilterChangeBeforeRejected.java](TestFilterChangeBeforeRejected.java)

Formal models and scripts this bug's part of the hunt used: [../../models/monitor-lifecycle-persistence-mbt/](../../models/monitor-lifecycle-persistence-mbt/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
