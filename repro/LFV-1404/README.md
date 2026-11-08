# ReplicaNode.newNRTPoint leaves curNRTCopy set to a cancelled, never-launched CopyJob when CopyJob.start() throws NodeCommunicationException, so isCopying() stays true although nothing is being copied (NRT replicator, every release since 6.0.0)

Finding LFV-1404 · Area: replicator / NRT replication: ReplicaNode.newNRTPoint copy bookkeeping (curNRTCopy, isCopying) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1404.html](../../bugs/LFV-1404.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

A replica receives a new NRT point; newNRTPoint creates the copy job (reading the primary's copy state) and calls job.start(), which throws NodeCommunicationException because the connection to the primary broke, exactly as the module's reference SimpleCopyJob.start() does after cancelling itself. You expect newNRTPoint to give up on that point and go back to idle, so isCopying() returns false, as it does when the job cannot even be created. Instead newNRTPoint returns null and resets its state to idle, but curNRTCopy was already set to the job before start() and the catch block never clears it; the job is never launched, so its OnceDone (finishNRTCopy) never runs either, and isCopying() stays true until a later, newer NRT point replaces the job.

## Expected vs actual

- Expected: `newNRTPoint(0, 1)` returns `null` and `isCopying()` returns `false`.
- Actual: `newNRTPoint(0, 1)` returns `null` but `isCopying()` returns `true`: `AssertionError` at line 36, with assertions on and off.

## Files

- [src/org/apache/lucene/replicator/nrt/TestReplicaNodeIsCopyingAfterFailedStart.java](src/org/apache/lucene/replicator/nrt/TestReplicaNodeIsCopyingAfterFailedStart.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/replicator/build/libs/lucene-replicator-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.replicator.nrt.TestReplicaNodeIsCopyingAfterFailedStart
```

The module(s) facet, replicator also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestReplicaNodeIsCopyingAfterFailedStart.java](TestReplicaNodeIsCopyingAfterFailedStart.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
