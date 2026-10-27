# Monitor.deleteById and Monitor.clear issue their IndexWriter delete outside the commit lock, so a delete or clear that runs while register is indexing a decomposed query leaves that query partly indexed: it stays registered but silently stops matching what its deleted parts match, also after a reopen

Bug LFV-501 · Area: monitor / query index updates (WritableQueryIndex.deleteQueries and clear versus commitWithoutNotify, reached through Monitor.deleteById, Monitor.clear and Monitor.register) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-501.html](../../bugs/LFV-501.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

One thread registers query q = a OR b OR c, which the Monitor indexes as three parts, one per clause, while another thread calls monitor.deleteById("q") or monitor.clear; you expect the two calls to take effect in some order, so that afterwards q is either fully registered or gone. Instead the delete goes to the IndexWriter without the commit lock that register holds, lands between register's per-part addDocument calls and removes only the parts already added. q is still reported as registered with its full query, getDisjunctCount is 2, and a document that contains only 'a' no longer matches, also after the Monitor is closed and reopened.

## Expected vs actual

- Expected: q is either complete (3 parts; documents a, b and c each match) or gone (0 parts).
- Actual: q is still registered (getQueryIds=[q]) with 2 of 3 parts, and a document with 'a' gets 0 matches; an independent reproducer's persistent Monitor shows the same after close and reopen.

## Files

- [src/org/apache/lucene/monitor/TestDeleteByIdDuringRegister.java](src/org/apache/lucene/monitor/TestDeleteByIdDuringRegister.java) (JUnit test)

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
     execute --select-class org.apache.lucene.monitor.TestDeleteByIdDuringRegister
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestDeleteByIdDuringRegister.java](TestDeleteByIdDuringRegister.java)
- [TestDeleteDuringRegisterStress.java](TestDeleteDuringRegisterStress.java)

Formal models and scripts this bug's part of the hunt used: [../../models/monitor-lifecycle-persistence-mbt/](../../models/monitor-lifecycle-persistence-mbt/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
