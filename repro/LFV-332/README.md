# Monitor.register with the same query id twice in one call keeps both versions in the query index, so the query matches a mix of both versions, and after a reopen a persistent Monitor runs the older version while getQuery returns the newer one

Bug LFV-332 · Area: monitor / query index updates (WritableQueryIndex.commitWithoutNotify and populateQueryCache, reached through both Monitor.register overloads and ConcurrentQueryLoader) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-332.html](../../bugs/LFV-332.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You register query id q twice in one call, monitor.register(List.of(new MonitorQuery("q", apple OR banana), new MonitorQuery("q", cherry))), as an application might when it applies a batch of query edits, and expect q to behave like one version, as it does when the two entries are registered in separate calls (the newer replaces the older). Instead the query index keeps both versions: q is reported for documents containing banana or cherry, a set that neither version matches, and getDisjunctCount is 3 for a one-clause query. With a persistent Monitor (q = apple, then q = cherry, in one call) the newer version runs until the Monitor is reopened, and from then on the older version apple runs instead while getQuery("q") still returns cherry.

## Expected vs actual

- Expected: q matches {cherry} with 1 disjunct, and the reopened and read-only Monitors also give {cherry}, as with two separate calls.
- Actual: q matches {banana,cherry} with 3 disjuncts; reopened {apple,banana}; read-only {apple,banana,cherry}; `getQuery` says cherry.

## Files

- [src/org/apache/lucene/monitor/TestRegisterDuplicateIdInBatch.java](src/org/apache/lucene/monitor/TestRegisterDuplicateIdInBatch.java) (JUnit test)

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
     execute --select-class org.apache.lucene.monitor.TestRegisterDuplicateIdInBatch
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestRegisterSameIdTwiceInOneCall.java](TestRegisterSameIdTwiceInOneCall.java)
- [fix-with-test.diff](fix-with-test.diff)
- [TestRejectOptionProbe.java](TestRejectOptionProbe.java)

Formal models and scripts this bug's part of the hunt used: [../../models/monitor-presearcher/](../../models/monitor-presearcher/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
